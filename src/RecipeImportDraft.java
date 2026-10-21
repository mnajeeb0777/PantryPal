import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Immutable provider text awaiting review, separate from a validated Recipe.
 * @author PantryPal team (GenAI-assisted implementation)
 * @version 2026.10.18
 */
public final class RecipeImportDraft
{
    private static final Pattern AMOUNT = Pattern.compile(
        "^([0-9]+\\s+[0-9]+/[0-9]+|[0-9]+/[0-9]+|[0-9]+(?:\\.[0-9]+)?|\\.[0-9]+)\\s*([a-z ]+)\\.?$");

    private final String externalId;
    private final String name;
    private final String[] ingredientNames;
    private final String[] measurements;
    private final String instructions;
    private final String originalSource;
    private final String[][] reviewRows;
    private final String prepText;
    private final String cookText;
    private final Effort effort;

    /**
     * Preserves raw text, including missing or unrecognized measurements.
     * @param externalId numeric provider ID
     * @param name recipe title
     * @param ingredientNames original ingredient rows
     * @param measurements corresponding measurement text
     * @param instructions original instructions, possibly empty
     * @param originalSource original publisher URL, possibly empty
     */
    public RecipeImportDraft(String externalId, String name,
        String[] ingredientNames, String[] measurements, String instructions,
        String originalSource)
    {
        this(externalId, name, ingredientNames, measurements, instructions,
            originalSource, null, "", "", Effort.UNKNOWN);
    }

    private RecipeImportDraft(String externalId, String name,
        String[] ingredientNames, String[] measurements, String instructions,
        String originalSource, String[][] reviewRows, String prepText,
        String cookText, Effort effort)
    {
        if (externalId == null || !externalId.matches("[0-9]+")
            || name == null || name.isBlank() || ingredientNames == null
            || measurements == null || ingredientNames.length == 0
            || ingredientNames.length != measurements.length
            || instructions == null || originalSource == null
            || prepText == null || cookText == null || effort == null)
        {
            throw new IllegalArgumentException("Incomplete import draft.");
        }
        for (int i = 0; i < ingredientNames.length; i++)
        {
            if (ingredientNames[i] == null || measurements[i] == null)
            {
                throw new IllegalArgumentException("Null ingredient text.");
            }
        }
        this.externalId = externalId;
        this.name = name;
        this.ingredientNames = ingredientNames.clone();
        this.measurements = measurements.clone();
        this.instructions = instructions;
        this.originalSource = originalSource;
        this.prepText = prepText;
        this.cookText = cookText;
        this.effort = effort;
        if (reviewRows == null)
        {
            this.reviewRows = new String[ingredientNames.length][3];
            for (int i = 0; i < ingredientNames.length; i++)
            {
                Ingredient suggestion = parseMeasurement(ingredientNames[i], measurements[i]);
                this.reviewRows[i] = new String[] { ingredientNames[i],
                    suggestion == null ? "" : Double.toString(suggestion.getQuantity()),
                    suggestion == null ? "" : suggestion.getUnit().name() };
            }
        }
        else
        {
            if (reviewRows.length != ingredientNames.length)
            {
                throw new IllegalArgumentException("Draft ingredient rows must be preserved.");
            }
            this.reviewRows = copyRows(reviewRows);
        }
    }

    /** @return independent rows containing reviewed name, quantity text, and unit */
    public String[][] getReviewRows() { return copyRows(reviewRows); }
    /** @return preparation-time text, possibly blank or unfinished */
    public String getPrepText() { return prepText; }
    /** @return cooking-time text, possibly blank or unfinished */
    public String getCookText() { return cookText; }
    /** @return effort selected during review */
    public Effort getEffort() { return effort; }

    /**
     * Captures unfinished edits without requiring valid ingredient quantities.
     * @param rows reviewed name, quantity text, and unit name (or blank), per row
     * @param prep unfinished preparation-time text
     * @param cook unfinished cooking-time text
     * @param selectedEffort effort selected during review
     * @return independent draft preserving original data and partial edits
     */
    public RecipeImportDraft withReview(String[][] rows, String prep,
        String cook, Effort selectedEffort)
    {
        if (rows == null)
        {
            throw new IllegalArgumentException("Review rows are required.");
        }
        return new RecipeImportDraft(externalId, name, ingredientNames,
            measurements, instructions, originalSource, rows, prep, cook, selectedEffort);
    }

    /**
     * Validates the saved review and promotes it to a cookable-model recipe.
     * @return validated recipe; drafts themselves cannot be cooked
     * @throws IllegalArgumentException if any requirement remains unresolved
     */
    public Recipe toRecipe()
    {
        Ingredient[] ingredients = new Ingredient[reviewRows.length];
        for (int i = 0; i < ingredients.length; i++)
        {
            try
            {
                ingredients[i] = new Ingredient(UUID.randomUUID().toString(),
                    reviewRows[i][0].strip(), Double.parseDouble(reviewRows[i][1].strip()),
                    Unit.valueOf(reviewRows[i][2]));
            }
            catch (IllegalArgumentException exception)
            {
                throw new IllegalArgumentException("Row " + (i + 1)
                    + ": enter a name, positive quantity, and unit, or Save for later.", exception);
            }
        }
        return toRecipe(ingredients, minutes(prepText), minutes(cookText), effort);
    }

    private static String[][] copyRows(String[][] rows)
    {
        String[][] copy = new String[rows.length][];
        for (int i = 0; i < rows.length; i++)
        {
            if (rows[i] == null || rows[i].length != 3
                || rows[i][0] == null || rows[i][1] == null || rows[i][2] == null)
            {
                throw new IllegalArgumentException("Invalid draft review row.");
            }
            if (!rows[i][2].isEmpty()) { Unit.valueOf(rows[i][2]); }
            copy[i] = rows[i].clone();
        }
        return copy;
    }

    private static Integer minutes(String text)
    {
        if (text.isBlank()) { return null; }
        try
        {
            int value = Integer.parseInt(text.strip());
            if (value < 0) { throw new NumberFormatException(); }
            return value;
        }
        catch (NumberFormatException exception)
        {
            throw new IllegalArgumentException(
                "Times must be nonnegative whole minutes or blank.", exception);
        }
    }

    /** @return TheMealDB ID */
    public String getExternalId() { return externalId; }
    /** @return recipe title */
    public String getName() { return name; }
    /** @return independent original ingredient names */
    public String[] getIngredientNames() { return ingredientNames.clone(); }
    /** @return independent original measurements */
    public String[] getMeasurements() { return measurements.clone(); }
    /** @return original instructions */
    public String getInstructions() { return instructions; }
    /** @return original publisher link, if available */
    public String getOriginalSource() { return originalSource; }
    /** @return provider attribution link */
    public String getProviderUrl()
    {
        return "https://www.themealdb.com/meal/" + externalId;
    }

    /**
     * Creates a validated recipe only when every original row is resolved.
     * @param ingredients reviewed rows, in original order
     * @param prep preparation minutes or null
     * @param cook cooking minutes or null
     * @param selectedEffort selected effort, possibly UNKNOWN
     * @return recipe with stable, namespaced identity and source attribution
     */
    public Recipe toRecipe(Ingredient[] ingredients, Integer prep,
        Integer cook, Effort selectedEffort)
    {
        if (ingredients == null || ingredients.length != ingredientNames.length)
        {
            throw new IllegalArgumentException("Review every ingredient before importing.");
        }
        String identity = "themealdb:" + externalId;
        String attribution = "Recipe data: TheMealDB - " + getProviderUrl();
        if (!originalSource.isBlank())
        {
            attribution += "\nOriginal source: " + originalSource;
        }
        String[] steps = instructions.isBlank() ? new String[] { attribution }
            : new String[] { instructions, attribution };
        return new Recipe(UUID.nameUUIDFromBytes(identity.getBytes(
            StandardCharsets.UTF_8)).toString(), name, ingredients, steps,
            prep, cook, selectedEffort, getProviderUrl(), identity);
    }

    /**
     * Suggests a measured ingredient only for unambiguous supported units.
     * Bare numbers, cloves, tins, ranges and seasoning instructions need review.
     * @param ingredientName ingredient name
     * @param text original measurement
     * @return suggested ingredient, or null if unresolved
     */
    public static Ingredient parseMeasurement(String ingredientName, String text)
    {
        if (ingredientName == null || text == null) { return null; }
        String normalized = text.strip().toLowerCase(Locale.ROOT)
            .replace('\u00a0', ' ').replace('\u2044', '/');
        String[] fractions = { "1/4", "1/2", "3/4", "1/3", "2/3", "1/8", "3/8", "5/8", "7/8" };
        String symbols = "\u00bc\u00bd\u00be\u2153\u2154\u215b\u215c\u215d\u215e";
        for (int i = 0; i < symbols.length(); i++)
        {
            normalized = normalized.replace(String.valueOf(symbols.charAt(i)),
                " " + fractions[i]);
        }
        Matcher match = AMOUNT.matcher(normalized.strip().replaceAll("\\s+", " "));
        if (!match.matches()) { return null; }
        Unit unit = unit(match.group(2).strip());
        if (unit == null) { return null; }
        try
        {
            double quantity = 0;
            for (String part : match.group(1).split(" "))
            {
                String[] fraction = part.split("/");
                quantity += fraction.length == 1 ? Double.parseDouble(part)
                    : Double.parseDouble(fraction[0]) / Double.parseDouble(fraction[1]);
            }
            return new Ingredient(UUID.randomUUID().toString(), ingredientName, quantity, unit);
        }
        catch (IllegalArgumentException exception) { return null; }
    }

    private static Unit unit(String text)
    {
        switch (text)
        {
            case "g": case "gram": case "grams": return Unit.GRAM;
            case "kg": case "kilogram": case "kilograms": return Unit.KILOGRAM;
            case "oz": case "ounce": case "ounces": return Unit.OUNCE;
            case "lb": case "lbs": case "pound": case "pounds": return Unit.POUND;
            case "ml": case "milliliter": case "milliliters":
            case "millilitre": case "millilitres": return Unit.MILLILITER;
            case "l": case "liter": case "liters": case "litre":
            case "litres": return Unit.LITER;
            case "tsp": case "tsps": case "teaspoon":
            case "teaspoons": return Unit.TEASPOON;
            case "tbsp": case "tbs": case "tablespoon":
            case "tablespoons": return Unit.TABLESPOON;
            case "cup": case "cups": return Unit.CUP;
            case "fl oz": case "fluid ounce": case "fluid ounces":
                return Unit.FLUID_OUNCE;
            case "count": return Unit.COUNT;
            default: return null;
        }
    }
}
