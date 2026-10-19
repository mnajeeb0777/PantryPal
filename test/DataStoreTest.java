import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Properties;
import student.TestCase;

/**
 * Exercises real files, schema validation, replacement, and I/O failures.
 *
 * @author PantryPal team (GenAI-assisted tests)
 * @version 2026.10.14
 */
public class DataStoreTest extends TestCase
{
    private Path directory;
    private Path file;
    private DataStore store;


    /**
     * Creates an isolated save destination.
     * @throws IOException if setup fails
     */
    @Override
    public void setUp() throws IOException
    {
        directory = TestFixtures.temporaryDirectory();
        file = directory.resolve("data.properties");
        store = new DataStore(file);
    }


    /**
     * Deletes only this test's files.
     * @throws IOException if cleanup fails
     */
    @Override
    public void tearDown() throws IOException
    {
        TestFixtures.deleteDirectory(directory);
    }


    /**
     * Missing files return empty data and construction performs no writes.
     * @throws IOException if file operations fail
     */
    public void testMissingFileAndInvalidConstruction() throws IOException
    {
        assertFalse(store.hasSaveFile());
        assertEquals(0, store.load().getPantryIngredients().length);
        assertEquals(0, store.load().getSavedRecipes().length);
        assertFalse(store.hasSaveFile());
        TestFixtures.invalid(() -> new DataStore(null));
        TestFixtures.invalid(() -> new DataStore(Path.of("")));
        TestFixtures.invalid(() -> new DataStore(file.getRoot()));
        DataStore nested = new DataStore(directory.resolve("new/sub/save"));
        assertEquals(0, nested.load().getSavedRecipes().length);
        assertFalse(Files.exists(directory.resolve("new")));
        nested.save(empty());
        assertTrue(nested.hasSaveFile());
    }


    /**
     * All metadata, units, quantities, text, and array ordering round-trip.
     * @throws IOException if saving/loading fails
     */
    public void testCompleteRoundTrip() throws IOException
    {
        Ingredient[] pantry = new Ingredient[Unit.values().length];
        for (int i = 0; i < pantry.length; i++)
        {
            pantry[i] = TestFixtures.ingredient(i + 1, "Cr\u00e8me & rice",
                i == 0 ? Double.MIN_VALUE : 1000000.0 / (i + 1), Unit.values()[i]);
        }
        Recipe[] recipes = new Recipe[Effort.values().length];
        for (int i = 0; i < recipes.length; i++)
        {
            recipes[i] = new Recipe(TestFixtures.id(100 + i), "Rice \uD83C\uDF5A",
                new Ingredient[] { pantry[2], pantry[1] },
                i == 0 ? new String[0] : new String[] {
                    "  Mix = rice: & \\ milk\r\nnext line\t\u0000", "Serve warm." },
                i == 0 ? null : 0, i == 0 ? null : Integer.MAX_VALUE,
                Effort.values()[i], "https://example.org/?a=1&b=2", "source:42");
        }
        store.save(new PantryPalData(pantry, recipes));
        assertTrue(store.hasSaveFile());
        PantryPalData loaded = new DataStore(file).load();
        Ingredient[] loadedPantry = loaded.getPantryIngredients();
        assertEquals(pantry.length, loadedPantry.length);
        for (int i = 0; i < pantry.length; i++)
        {
            assertIngredient(pantry[i], loadedPantry[i]);
        }
        Recipe[] loadedRecipes = loaded.getSavedRecipes();
        assertEquals(recipes.length, loadedRecipes.length);
        for (int i = 0; i < recipes.length; i++)
        {
            Recipe expected = recipes[i];
            Recipe actual = loadedRecipes[i];
            assertEquals(expected.getId(), actual.getId());
            assertEquals(expected.getName(), actual.getName());
            assertTrue(java.util.Objects.equals(expected.getPrepTimeMinutes(),
                actual.getPrepTimeMinutes()));
            assertTrue(java.util.Objects.equals(expected.getCookTimeMinutes(),
                actual.getCookTimeMinutes()));
            assertEquals(expected.getEffort(), actual.getEffort());
            assertEquals(expected.getSourceUrl(), actual.getSourceUrl());
            assertEquals(expected.getExternalRecipeId(), actual.getExternalRecipeId());
            assertTrue(Arrays.equals(expected.getInstructions(), actual.getInstructions()));
            Ingredient[] requirements = actual.getRequiredIngredients();
            assertEquals(2, requirements.length);
            assertIngredient(pantry[2], requirements[0]);
            assertIngredient(pantry[1], requirements[1]);
        }
        store.save(empty());
        assertEquals(0, store.load().getPantryIngredients().length);
        assertEquals(0, store.load().getSavedRecipes().length);
        try (java.util.stream.Stream<Path> files = Files.list(directory))
        {
            assertEquals(1L, files.count());
        }
    }


    /**
     * Invalid saves do not touch the last valid file.
     * @throws IOException if initial save fails
     */
    public void testNullSavePreservesPreviousFile() throws IOException
    {
        store.save(empty());
        byte[] previous = Files.readAllBytes(file);
        try
        {
            store.save(null);
            fail("Expected invalid data.");
        }
        catch (IllegalArgumentException expected)
        {
            assertTrue(Arrays.equals(previous, Files.readAllBytes(file)));
        }
    }


    /**
     * Invalid fields are rejected rather than partially loading a collection.
     * @throws IOException if fixture setup fails
     */
    public void testCorruptFields() throws IOException
    {
        String[][] corruptions = {
            { "version", "2" }, { "version", null },
            { "pantry.count", "-1" }, { "pantry.count", "2147483647" },
            { "pantry.0.id", "bad-id" }, { "pantry.0.name", "" },
            { "pantry.0.quantity", "NaN" }, { "pantry.0.quantity", "0" },
            { "pantry.0.quantity", "Infinity" }, { "pantry.0.unit", "BUCKET" },
            { "recipes.0.effort", "BAD" }, { "recipes.0.prepTime", "-1" },
            { "recipes.0.cookTime", "wrong" }, { "recipes.0.name", null },
            { "recipes.0.ingredients.0.quantity", "1000001" },
            { "recipes.0.steps.count", "-1" }, { "recipes.0.steps.0", "" },
            { "unexpected", "value" }
        };
        for (String[] corruption : corruptions)
        {
            store.save(sample());
            Properties values = readProperties();
            if (corruption[1] == null)
            {
                values.remove(corruption[0]);
            }
            else
            {
                values.setProperty(corruption[0], corruption[1]);
            }
            writeProperties(values);
            byte[] previous = Files.readAllBytes(file);
            expectIOException(() -> store.load());
            assertTrue(Arrays.equals(previous, Files.readAllBytes(file)));
        }
    }


    /**
     * Duplicate entry IDs, malformed escapes, and truncated files are invalid.
     * @throws IOException if fixture setup fails
     */
    public void testDuplicateIdsAndMalformedText() throws IOException
    {
        store.save(sample());
        Properties values = readProperties();
        values.setProperty("pantry.count", "2");
        for (String field : new String[] { "id", "name", "quantity", "unit" })
        {
            values.setProperty("pantry.1." + field,
                values.getProperty("pantry.0." + field));
        }
        writeProperties(values);
        expectIOException(() -> store.load());
        store.save(sample());
        values = readProperties();
        values.setProperty("recipes.count", "2");
        for (String key : values.stringPropertyNames())
        {
            if (key.startsWith("recipes.0."))
            {
                values.setProperty(key.replace("recipes.0.", "recipes.1."),
                    values.getProperty(key));
            }
        }
        writeProperties(values);
        expectIOException(() -> store.load());
        for (String text : new String[] { "", "version=1\npantry.count=1",
            "version=\\uZZZZ" })
        {
            Files.writeString(file, text);
            assertTrue(store.hasSaveFile());
            expectIOException(() -> store.load());
        }
    }


    /**
     * Failed replacement cleans temporary data and preserves the destination.
     * @throws IOException if fixture setup fails
     */
    public void testIoFailuresAndCleanup() throws IOException
    {
        Files.createDirectory(file);
        Path marker = file.resolve("keep.txt");
        Files.writeString(marker, "keep this data");
        assertTrue(store.hasSaveFile());
        expectIOException(() -> store.load());
        expectIOException(() -> store.save(sample()));
        assertEquals("keep this data", Files.readString(marker));
        try (java.util.stream.Stream<Path> files = Files.list(directory))
        {
            assertEquals(1L, files.count());
        }
        Path blockedParent = directory.resolve("not-a-directory");
        Files.writeString(blockedParent, "keep");
        DataStore blocked = new DataStore(blockedParent.resolve("save"));
        expectIOException(() -> blocked.save(empty()));
        expectIOException(() -> blocked.load());
        assertEquals("keep", Files.readString(blockedParent));
    }


    /**
     * A Windows read-only destination cannot destroy the previous valid save.
     * @throws IOException if fixture setup or restoration fails
     */
    public void testReadOnlySavePreservesPreviousData() throws IOException
    {
        org.junit.Assume.assumeTrue("Windows DOS attributes are required.",
            System.getProperty("os.name").startsWith("Windows"));
        store.save(sample());
        byte[] previous = Files.readAllBytes(file);
        Files.setAttribute(file, "dos:readonly", true);
        try
        {
            expectIOException(() -> store.save(empty()));
            assertTrue(Arrays.equals(previous, Files.readAllBytes(file)));
            assertEquals(1, store.load().getSavedRecipes().length);
            try (java.util.stream.Stream<Path> files = Files.list(directory))
            {
                assertEquals(1L, files.count());
            }
        }
        finally
        {
            Files.setAttribute(file, "dos:readonly", false);
        }
    }


    private PantryPalData sample()
    {
        return new PantryPalData(new Ingredient[] {
            TestFixtures.ingredient(1, "Rice", 3, Unit.CUP) },
            new Recipe[] { TestFixtures.recipe(2, "Rice bowl", 10, Effort.EASY) });
    }


    private PantryPalData empty()
    {
        return new PantryPalData(new Ingredient[0], new Recipe[0]);
    }


    private void assertIngredient(Ingredient expected, Ingredient actual)
    {
        assertEquals(expected.getId(), actual.getId());
        assertEquals(expected.getName(), actual.getName());
        assertEquals(expected.getQuantity(), actual.getQuantity(), 0.0);
        assertEquals(expected.getUnit(), actual.getUnit());
    }


    private Properties readProperties() throws IOException
    {
        Properties values = new Properties();
        try (InputStream input = Files.newInputStream(file))
        {
            values.load(input);
        }
        return values;
    }


    private void writeProperties(Properties values) throws IOException
    {
        try (OutputStream output = Files.newOutputStream(file))
        {
            values.store(output, "Test fixture");
        }
    }


    private void expectIOException(IoAction action)
    {
        try
        {
            action.run();
            fail("Expected IOException.");
        }
        catch (IOException expected)
        {
            assertFalse(expected.getMessage().isBlank());
        }
    }


    @FunctionalInterface
    private interface IoAction
    {
        void run() throws IOException;
    }
}
