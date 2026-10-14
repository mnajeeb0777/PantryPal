import java.util.HashSet;
import java.util.Set;

/**
 * Compares recipe requirements with pantry stock.
 * Allocates a private stock snapshot without changing pantry or recipe data.
 *
 * @author PantryPal team (GenAI-assisted implementation)
 * @version 2026.10.14
 */
public class RecipeMatcher
{
    private final IngredientMatcher ingredientMatcher;


    /**
     * Uses the supplied name aliases and unit conversion rules.
     * @param ingredientMatcher name and unit matching service
     * @throws IllegalArgumentException if the service is null
     */
    public RecipeMatcher(IngredientMatcher ingredientMatcher)
    {
        if (ingredientMatcher == null)
        {
            throw new IllegalArgumentException("Matcher must not be null.");
        }
        this.ingredientMatcher = ingredientMatcher;
    }


    /**
     * Reserves compatible stock in recipe order, without counting it twice.
     * Available amounts report the allocation, capped at each requirement.
     * Unknown means matching stock exists only in incompatible units.
     * @param recipe recipe whose requirements should be compared
     * @param pantryIngredients pantry snapshot with unique entry IDs
     * @return one comparison per requirement, in recipe order
     * @throws IllegalArgumentException if inputs or conversions are invalid
     */
    public RecipeMatchResult compareRecipe(
        Recipe recipe,
        Ingredient[] pantryIngredients)
    {
        if (recipe == null || pantryIngredients == null)
        {
            throw new IllegalArgumentException(
                "Recipe and pantry snapshot must not be null.");
        }
        Ingredient[] stock = copyStock(pantryIngredients);
        double[] remaining = new double[stock.length];
        for (int i = 0; i < stock.length; i++)
        {
            remaining[i] = stock[i].getQuantity();
        }
        LinkedChain<IngredientMatch> matches =
            new LinkedChain<IngredientMatch>();
        for (Ingredient required : recipe.getRequiredIngredients())
        {
            matches.add(allocate(required, stock, remaining));
        }
        IngredientMatch[] results = matches.toArray(
            new IngredientMatch[matches.size()]);
        // Front insertion reverses order; restore it in one linear pass.
        for (int left = 0, right = results.length - 1; left < right;
            left++, right--)
        {
            IngredientMatch entry = results[left];
            results[left] = results[right];
            results[right] = entry;
        }
        return new RecipeMatchResult(recipe.getId(), results);
    }


    /**
     * Validates every entry before allocating any stock.
     * @param source caller-owned stock snapshot
     * @return independent entries with distinct IDs
     */
    private Ingredient[] copyStock(Ingredient[] source)
    {
        Ingredient[] stock = new Ingredient[source.length];
        Set<String> ids = new HashSet<String>();
        for (int i = 0; i < source.length; i++)
        {
            Ingredient entry = source[i];
            if (entry == null || !ids.add(entry.getId()))
            {
                throw new IllegalArgumentException(
                    "Pantry entries must be non-null with unique IDs.");
            }
            stock[i] = new Ingredient(entry);
        }
        return stock;
    }


    /**
     * Allocates one requirement from the remaining native-unit quantities.
     * @param required current recipe requirement
     * @param stock validated pantry entries in snapshot order
     * @param remaining mutable local stock amounts in each entry's unit
     * @return the amount reserved, or unknown for incompatible-only stock
     */
    private IngredientMatch allocate(
        Ingredient required,
        Ingredient[] stock,
        double[] remaining)
    {
        boolean matchingName = false;
        boolean compatible = false;
        double needed = required.getQuantity();
        double allocated = 0;
        for (int i = 0; i < stock.length; i++)
        {
            if (!ingredientMatcher.namesMatch(
                stock[i].getName(), required.getName()))
            {
                continue;
            }
            matchingName = true;
            if (!ingredientMatcher.areUnitsCompatible(
                stock[i].getUnit(), required.getUnit()))
            {
                continue;
            }
            // A depleted compatible entry still makes zero stock known.
            compatible = true;
            if (needed == 0 || remaining[i] == 0)
            {
                continue;
            }
            double available = ingredientMatcher.convertQuantity(
                remaining[i], stock[i].getUnit(), required.getUnit());
            if (available <= needed)
            {
                allocated += available;
                remaining[i] = 0;
                double difference = needed - available;
                needed = isRoundoff(difference, needed, available)
                    ? 0 : difference;
            }
            else
            {
                double used = ingredientMatcher.convertQuantity(
                    needed, required.getUnit(), stock[i].getUnit());
                double difference = remaining[i] - used;
                remaining[i] = isRoundoff(
                    difference, remaining[i], used) ? 0 : difference;
                needed = 0;
            }
        }
        if (matchingName && !compatible)
        {
            return new IngredientMatch(required, null);
        }
        // Roundoff-sized residuals count as fulfilled, as in deduction.
        double amount = needed == 0 ? required.getQuantity()
            : Math.min(allocated, required.getQuantity());
        return new IngredientMatch(required, amount);
    }


    /**
     * Matches the pantry and result model's bounded rounding tolerance.
     * @param difference residual amount
     * @param first first operand
     * @param second second operand
     * @return whether the residual is representational rounding
     */
    private static boolean isRoundoff(
        double difference,
        double first,
        double second)
    {
        double scale = Math.max(Math.abs(first), Math.abs(second));
        return difference == 0 || (Math.abs(difference) / scale <= 1e-12
            && Math.abs(difference) <= 4 * Math.ulp(scale));
    }
}
