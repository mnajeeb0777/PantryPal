import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Owns independent saved recipes, allowing names to repeat.
 *
 * @author PantryPal team (GenAI-assisted implementation)
 * @version 2026.10.14
 */
public class RecipeBook
{
    private final LinkedChain<Recipe> recipes;


    /**
     * Creates an empty saved-recipe collection.
     */
    public RecipeBook()
    {
        recipes = new LinkedChain<Recipe>();
    }


    /**
     * Creates a book from independent copies of recipes.
     * @param initialRecipes initial saved recipes to copy
     * @throws IllegalArgumentException if entries are null or IDs repeat
     */
    public RecipeBook(Recipe[] initialRecipes)
    {
        this();
        if (initialRecipes == null)
        {
            throw new IllegalArgumentException("Initial recipes are null.");
        }
        Set<String> ids = new HashSet<String>();
        for (int i = initialRecipes.length - 1; i >= 0; i--)
        {
            Recipe recipe = initialRecipes[i];
            if (recipe == null || !ids.add(recipe.getId()))
            {
                throw new IllegalArgumentException(
                    "Initial recipes must have unique IDs.");
            }
            recipes.add(new Recipe(recipe));
        }
    }


    /**
     * Stores an independent copy unless the local ID is already saved.
     * @param recipe recipe to copy
     * @return false for a duplicate local ID
     * @throws IllegalArgumentException if recipe is null
     */
    public boolean addRecipe(Recipe recipe)
    {
        if (recipe == null)
        {
            throw new IllegalArgumentException("Recipe must not be null.");
        }
        if (recipes.contains(recipe))
        {
            return false;
        }
        return recipes.add(new Recipe(recipe));
    }


    /**
     * Returns independent snapshots of all saved recipes.
     * @return recipe snapshots, or an empty array
     */
    public Recipe[] getAllRecipes()
    {
        Recipe[] result = entries();
        for (int i = 0; i < result.length; i++)
        {
            result[i] = new Recipe(result[i]);
        }
        return result;
    }


    /**
     * Looks up a recipe using its local ID.
     * @param id stable entry identifier
     * @return a snapshot, or null if absent
     * @throws IllegalArgumentException if ID is null or blank
     */
    public Recipe findRecipeById(String id)
    {
        Recipe recipe = findStored(id);
        return recipe == null ? null : new Recipe(recipe);
    }


    /**
     * Finds recipes by case-insensitive title substring.
     * @param query case-insensitive name substring; blank matches all
     * @return matching snapshots; blank query returns all
     * @throws IllegalArgumentException if query is null
     */
    public Recipe[] searchRecipes(String query)
    {
        if (query == null)
        {
            throw new IllegalArgumentException("Query must not be null.");
        }
        String search = query.strip().toLowerCase(Locale.ROOT);
        Recipe[] result = new Recipe[recipes.size()];
        int count = 0;
        for (Recipe recipe : entries())
        {
            if (recipe.getName().toLowerCase(Locale.ROOT).contains(search))
            {
                result[count++] = new Recipe(recipe);
            }
        }
        return Arrays.copyOf(result, count);
    }


    /**
     * Applies both active filters; unknown times fail an active time filter.
     * @param maxCookTimeMinutes maximum cook time; null disables the filter
     * @param effort effort category; null disables an effort filter
     * @return matching recipe snapshots
     * @throws IllegalArgumentException if the time limit is negative
     */
    public Recipe[] filterRecipes(Integer maxCookTimeMinutes, Effort effort)
    {
        if (maxCookTimeMinutes != null && maxCookTimeMinutes < 0)
        {
            throw new IllegalArgumentException(
                "Maximum cook time must not be negative.");
        }
        Recipe[] result = new Recipe[recipes.size()];
        int count = 0;
        for (Recipe recipe : entries())
        {
            Integer time = recipe.getCookTimeMinutes();
            boolean timeMatches = maxCookTimeMinutes == null
                || (time != null && time <= maxCookTimeMinutes);
            boolean effortMatches = effort == null
                || recipe.getEffort() == effort;
            if (timeMatches && effortMatches)
            {
                result[count++] = new Recipe(recipe);
            }
        }
        return Arrays.copyOf(result, count);
    }


    /**
     * Removes only the saved recipe with the supplied local ID.
     * @param id stable entry identifier
     * @return false if absent, including an empty book
     * @throws IllegalArgumentException if ID is null or blank
     */
    public boolean removeRecipe(String id)
    {
        Recipe recipe = findStored(id);
        return recipe != null && recipes.remove(recipe);
    }


    /**
     * Counts saved recipes.
     * @return the recipe count
     */
    public int size()
    {
        return recipes.size();
    }


    /**
     * Checks whether any recipes are saved.
     * @return true when the book is empty
     */
    public boolean isEmpty()
    {
        return recipes.isEmpty();
    }


    /**
     * Gets internal references for private traversal only.
     * @return entries in chain order
     */
    private Recipe[] entries()
    {
        return recipes.toArray(new Recipe[recipes.size()]);
    }


    /**
     * Finds an internal recipe by local ID.
     * @param id selected ID
     * @return stored recipe, or null
     */
    private Recipe findStored(String id)
    {
        if (id == null || id.isBlank())
        {
            throw new IllegalArgumentException("ID must not be blank.");
        }
        for (Recipe recipe : entries())
        {
            if (recipe.getId().equals(id))
            {
                return recipe;
            }
        }
        return null;
    }
}
