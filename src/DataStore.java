import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.LinkOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Properties;

/**
 * Saves versioned Java properties using only the JDK. Unicode and multiline
 * text are escaped by Properties, so every model string round-trips exactly.
 * Each save replaces the destination atomically after a complete temporary
 * file has been written. Filesystems without atomic moves report an error.
 *
 * @author PantryPal team (GenAI-assisted implementation)
 * @version 2026.10.14
 */
public class DataStore
{
    private final Path filePath;


    /**
     * Creates a store without reading, creating, or modifying any files.
     * @param filePath destination file, relative to the working directory
     *     or absolute
     * @throws IllegalArgumentException if the path is null, empty, or a root
     */
    public DataStore(Path filePath)
    {
        if (filePath == null || filePath.toString().isBlank()
            || filePath.toAbsolutePath().normalize().getFileName() == null)
        {
            throw new IllegalArgumentException("A save-file path is required.");
        }
        this.filePath = filePath.toAbsolutePath().normalize();
    }


    /**
     * Writes a complete snapshot before atomically replacing the old save.
     * Parent directories are created on the first save attempt.
     * @param data independent snapshot to save
     * @throws IllegalArgumentException if data is null
     * @throws IOException if writing or atomic replacement fails
     */
    public void save(PantryPalData data)
        throws IOException
    {
        if (data == null)
        {
            throw new IllegalArgumentException("Save data is required.");
        }
        Properties values = new Properties();
        values.setProperty("version", "2");
        putIngredients(values, "pantry", data.getPantryIngredients());
        Recipe[] recipes = data.getSavedRecipes();
        values.setProperty("recipes.count", Integer.toString(recipes.length));
        for (int i = 0; i < recipes.length; i++)
        {
            String prefix = "recipes." + i + ".";
            Recipe recipe = recipes[i];
            values.setProperty(prefix + "id", recipe.getId());
            values.setProperty(prefix + "name", recipe.getName());
            values.setProperty(prefix + "prepTime",
                time(recipe.getPrepTimeMinutes()));
            values.setProperty(prefix + "cookTime",
                time(recipe.getCookTimeMinutes()));
            values.setProperty(prefix + "effort", recipe.getEffort().name());
            values.setProperty(prefix + "sourceUrl", recipe.getSourceUrl());
            values.setProperty(prefix + "externalId",
                recipe.getExternalRecipeId());
            putIngredients(values, prefix + "ingredients",
                recipe.getRequiredIngredients());
            String[] steps = recipe.getInstructions();
            values.setProperty(prefix + "steps.count",
                Integer.toString(steps.length));
            for (int j = 0; j < steps.length; j++)
            {
                values.setProperty(prefix + "steps." + j, steps[j]);
            }
        }
        putDrafts(values, data.getSavedDrafts());
        Files.createDirectories(filePath.getParent());
        Path temporary = Files.createTempFile(filePath.getParent(),
            "pantrypal-", ".tmp");
        try
        {
            try (OutputStream output = Files.newOutputStream(temporary))
            {
                values.store(output, "PantryPal save - format version 2");
            }
            Files.move(temporary, filePath, StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING);
        }
        catch (IOException | RuntimeException exception)
        {
            try
            {
                Files.deleteIfExists(temporary);
            }
            catch (IOException cleanupFailure)
            {
                exception.addSuppressed(cleanupFailure);
            }
            throw exception;
        }
    }


    /**
     * Reads and validates the entire save without modifying it.
     * @return empty collections for a missing file, otherwise saved snapshots
     * @throws IOException if the file is unreadable, corrupt, or unsupported
     */
    public PantryPalData load()
        throws IOException
    {
        Properties values = new Properties();
        try (InputStream input = Files.newInputStream(filePath))
        {
            values.load(input);
        }
        catch (NoSuchFileException exception)
        {
            verifyMissingFile();
            return new PantryPalData(new Ingredient[0], new Recipe[0]);
        }
        catch (IllegalArgumentException exception)
        {
            throw new IOException("The save file contains invalid text.",
                exception);
        }
        try
        {
            String version = take(values, "version");
            if (!"1".equals(version) && !"2".equals(version))
            {
                throw new IllegalArgumentException("Unsupported save version.");
            }
            Ingredient[] pantry = readIngredients(values, "pantry");
            Recipe[] recipes = new Recipe[count(values, "recipes.count")];
            for (int i = 0; i < recipes.length; i++)
            {
                String prefix = "recipes." + i + ".";
                String id = take(values, prefix + "id");
                String name = take(values, prefix + "name");
                Integer prep = readTime(take(values, prefix + "prepTime"));
                Integer cook = readTime(take(values, prefix + "cookTime"));
                Effort effort = Effort.valueOf(take(values, prefix + "effort"));
                String source = take(values, prefix + "sourceUrl");
                String external = take(values, prefix + "externalId");
                Ingredient[] ingredients =
                    readIngredients(values, prefix + "ingredients");
                String[] steps =
                    new String[count(values, prefix + "steps.count")];
                for (int j = 0; j < steps.length; j++)
                {
                    steps[j] = take(values, prefix + "steps." + j);
                }
                recipes[i] = new Recipe(id, name, ingredients, steps, prep,
                    cook, effort, source, external);
            }
            RecipeImportDraft[] drafts = "2".equals(version)
                ? readDrafts(values) : new RecipeImportDraft[0];
            if (!values.isEmpty())
            {
                throw new IllegalArgumentException("Unexpected save fields.");
            }
            return new PantryPalData(pantry, recipes, drafts);
        }
        catch (IllegalArgumentException exception)
        {
            throw new IOException("Invalid PantryPal save: "
                + exception.getMessage(), exception);
        }
    }


    /**
     * Checks existence only; loading is needed to check validity/readability.
     * @return whether the configured destination exists
     */
    public boolean hasSaveFile()
    {
        return Files.exists(filePath);
    }


    private void verifyMissingFile() throws IOException
    {
        if (Files.exists(filePath, LinkOption.NOFOLLOW_LINKS))
        {
            throw new IOException("The save path exists but cannot be opened.");
        }
        // Windows may report a missing child when its parent is a regular file.
        // Only absent directories ending at an actual directory mean first run.
        for (Path parent = filePath.getParent(); parent != null;
            parent = parent.getParent())
        {
            try
            {
                BasicFileAttributes attributes = Files.readAttributes(parent,
                    BasicFileAttributes.class);
                if (!attributes.isDirectory())
                {
                    throw new IOException("The save parent is not a directory.");
                }
                return;
            }
            catch (NoSuchFileException missingParent)
            {
                // Continue upward to verify the first existing ancestor.
            }
        }
        throw new IOException("The save location is unavailable.");
    }


    private static void putDrafts(Properties values, RecipeImportDraft[] drafts)
    {
        values.setProperty("drafts.count", Integer.toString(drafts.length));
        for (int i = 0; i < drafts.length; i++)
        {
            String prefix = "drafts." + i + ".";
            RecipeImportDraft draft = drafts[i];
            values.setProperty(prefix + "id", draft.getExternalId());
            values.setProperty(prefix + "name", draft.getName());
            values.setProperty(prefix + "instructions", draft.getInstructions());
            values.setProperty(prefix + "source", draft.getOriginalSource());
            values.setProperty(prefix + "prepText", draft.getPrepText());
            values.setProperty(prefix + "cookText", draft.getCookText());
            values.setProperty(prefix + "effort", draft.getEffort().name());
            String[] names = draft.getIngredientNames();
            String[] measures = draft.getMeasurements();
            String[][] review = draft.getReviewRows();
            values.setProperty(prefix + "rows.count", Integer.toString(names.length));
            for (int j = 0; j < names.length; j++)
            {
                String row = prefix + "rows." + j + ".";
                values.setProperty(row + "originalName", names[j]);
                values.setProperty(row + "measurement", measures[j]);
                values.setProperty(row + "name", review[j][0]);
                values.setProperty(row + "quantity", review[j][1]);
                values.setProperty(row + "unit", review[j][2]);
            }
        }
    }

    private static RecipeImportDraft[] readDrafts(Properties values)
    {
        RecipeImportDraft[] drafts = new RecipeImportDraft[count(values, "drafts.count")];
        for (int i = 0; i < drafts.length; i++)
        {
            String prefix = "drafts." + i + ".";
            String id = take(values, prefix + "id");
            String name = take(values, prefix + "name");
            String instructions = take(values, prefix + "instructions");
            String source = take(values, prefix + "source");
            String prep = take(values, prefix + "prepText");
            String cook = take(values, prefix + "cookText");
            Effort effort = Effort.valueOf(take(values, prefix + "effort"));
            int length = count(values, prefix + "rows.count");
            String[] names = new String[length];
            String[] measures = new String[length];
            String[][] review = new String[length][3];
            for (int j = 0; j < length; j++)
            {
                String row = prefix + "rows." + j + ".";
                names[j] = take(values, row + "originalName");
                measures[j] = take(values, row + "measurement");
                review[j][0] = take(values, row + "name");
                review[j][1] = take(values, row + "quantity");
                review[j][2] = take(values, row + "unit");
            }
            drafts[i] = new RecipeImportDraft(id, name, names, measures, instructions, source)
                .withReview(review, prep, cook, effort);
        }
        return drafts;
    }

    private static void putIngredients(Properties values, String prefix,
        Ingredient[] ingredients)
    {
        values.setProperty(prefix + ".count",
            Integer.toString(ingredients.length));
        for (int i = 0; i < ingredients.length; i++)
        {
            String key = prefix + "." + i + ".";
            Ingredient entry = ingredients[i];
            values.setProperty(key + "id", entry.getId());
            values.setProperty(key + "name", entry.getName());
            values.setProperty(key + "quantity",
                Double.toString(entry.getQuantity()));
            values.setProperty(key + "unit", entry.getUnit().name());
        }
    }


    private static Ingredient[] readIngredients(Properties values,
        String prefix)
    {
        Ingredient[] result = new Ingredient[count(values, prefix + ".count")];
        for (int i = 0; i < result.length; i++)
        {
            String key = prefix + "." + i + ".";
            result[i] = new Ingredient(take(values, key + "id"),
                take(values, key + "name"),
                Double.parseDouble(take(values, key + "quantity")),
                Unit.valueOf(take(values, key + "unit")));
        }
        return result;
    }


    private static String take(Properties values, String key)
    {
        String value = (String)values.remove(key);
        if (value == null)
        {
            throw new IllegalArgumentException("Missing field: " + key);
        }
        return value;
    }


    private static int count(Properties values, String key)
    {
        int result = Integer.parseInt(take(values, key));
        // Each element needs a field; reject impossible array allocations.
        if (result < 0 || result > values.size())
        {
            throw new IllegalArgumentException("Invalid count: " + key);
        }
        return result;
    }


    private static String time(Integer minutes)
    {
        return minutes == null ? "" : minutes.toString();
    }


    private static Integer readTime(String value)
    {
        return value.isEmpty() ? null : Integer.valueOf(value);
    }
}
