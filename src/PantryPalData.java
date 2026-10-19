/**
 * An independent, validated snapshot of both saved collections.
 * Live collection storage remains in Pantry and RecipeBook.
 *
 * @author PantryPal team (GenAI-assisted implementation)
 * @version 2026.10.14
 */
public class PantryPalData
{
    private final Ingredient[] pantryIngredients;
    private final Recipe[] savedRecipes;


    /**
     * Validates and copies both collections, preserving their entry order.
     * IDs must be unique within each collection; names may repeat.
     * @param pantryIngredients pantry entries, possibly empty
     * @param savedRecipes saved recipes, possibly empty
     * @throws IllegalArgumentException if arrays/entries are null or IDs repeat
     */
    public PantryPalData(Ingredient[] pantryIngredients, Recipe[] savedRecipes)
    {
        this.pantryIngredients =
            new Pantry(pantryIngredients).getAllIngredients();
        this.savedRecipes = new RecipeBook(savedRecipes).getAllRecipes();
    }


    /**
     * Returns independent copies of the saved pantry entries.
     * @return pantry snapshots in their original order
     */
    public Ingredient[] getPantryIngredients()
    {
        Ingredient[] result = new Ingredient[pantryIngredients.length];
        for (int i = 0; i < result.length; i++)
        {
            result[i] = new Ingredient(pantryIngredients[i]);
        }
        return result;
    }


    /**
     * Returns independent copies, including recipe requirements and steps.
     * @return recipe snapshots in their original order
     */
    public Recipe[] getSavedRecipes()
    {
        Recipe[] result = new Recipe[savedRecipes.length];
        for (int i = 0; i < result.length; i++)
        {
            result[i] = new Recipe(savedRecipes[i]);
        }
        return result;
    }
}
