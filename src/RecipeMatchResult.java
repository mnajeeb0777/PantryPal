import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Owns immutable comparison results for a nonempty recipe.
 * Missing and uncomparable requirements are reported separately.
 *
 * @author PantryPal team (GenAI-assisted implementation)
 * @version 2026.10.14
 */
public class RecipeMatchResult
{
    private final String recipeId;
    private final IngredientMatch[] ingredientMatches;


    /**
     * Copies one comparison entry per distinct requirement ID.
     * @param recipeId local recipe UUID to retain
     * @param ingredientMatches nonempty array of comparison entries
     * @throws IllegalArgumentException if an ID or comparison entry is invalid
     */
    public RecipeMatchResult(
        String recipeId,
        IngredientMatch[] ingredientMatches)
    {
        validateId(recipeId);
        if (ingredientMatches == null || ingredientMatches.length == 0)
        {
            throw new IllegalArgumentException(
                "A recipe comparison must contain at least one entry.");
        }
        this.ingredientMatches =
            new IngredientMatch[ingredientMatches.length];
        Set<String> ids = new HashSet<String>();
        for (int i = 0; i < ingredientMatches.length; i++)
        {
            IngredientMatch match = ingredientMatches[i];
            if (match == null)
            {
                throw new IllegalArgumentException(
                    "Comparison entries must not be null.");
            }
            Ingredient requirement = match.getRequiredIngredient();
            IngredientMatch snapshot = new IngredientMatch(
                requirement, match.getAvailableQuantity());
            if (!ids.add(requirement.getId()))
            {
                throw new IllegalArgumentException(
                    "Comparison requirements must have unique IDs.");
            }
            this.ingredientMatches[i] = snapshot;
        }
        this.recipeId = recipeId;
    }


    /**
     * Returns the identity of the recipe that was compared.
     * @return the local recipe ID
     */
    public String getRecipeId()
    {
        return recipeId;
    }


    /**
     * Returns a new array containing the immutable comparison entries.
     * @return entries in their original requirement order
     */
    public IngredientMatch[] getIngredientMatches()
    {
        return ingredientMatches.clone();
    }


    /**
     * Returns independent ingredients containing only known shortages.
     * @return positive shortages, excluding sufficient/unknown requirements
     */
    public Ingredient[] getMissingIngredients()
    {
        Ingredient[] result = new Ingredient[ingredientMatches.length];
        int count = 0;
        for (IngredientMatch match : ingredientMatches)
        {
            Double missing = match.getMissingQuantity();
            if (missing != null && missing > 0)
            {
                Ingredient shortage = match.getRequiredIngredient();
                shortage.setQuantity(missing);
                result[count++] = shortage;
            }
        }
        return Arrays.copyOf(result, count);
    }


    /**
     * Returns independent snapshots whose quantities cannot be compared.
     * @return unknown requirements with their full required amounts
     */
    public Ingredient[] getUncomparableIngredients()
    {
        Ingredient[] result = new Ingredient[ingredientMatches.length];
        int count = 0;
        for (IngredientMatch match : ingredientMatches)
        {
            if (!match.canCompareQuantities())
            {
                result[count++] = match.getRequiredIngredient();
            }
        }
        return Arrays.copyOf(result, count);
    }


    /**
     * Requires every comparison entry to be known and sufficient.
     * @return whether the supplied comparison reports all requirements met
     */
    public boolean canCook()
    {
        for (IngredientMatch match : ingredientMatches)
        {
            if (!match.isSufficient())
            {
                return false;
            }
        }
        return true;
    }


    /**
     * Checks full UUID spelling without altering the supplied recipe ID.
     * @param value local recipe identifier
     */
    private static void validateId(String value)
    {
        if (value == null)
        {
            throw new IllegalArgumentException("Recipe ID must not be null.");
        }
        try
        {
            if (!UUID.fromString(value).toString().equalsIgnoreCase(value))
            {
                throw new IllegalArgumentException("Recipe ID must be a UUID.");
            }
        }
        catch (IllegalArgumentException exception)
        {
            throw new IllegalArgumentException("Recipe ID must be a UUID.",
                exception);
        }
    }
}
