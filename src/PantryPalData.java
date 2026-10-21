/**
 * An independent snapshot of pantry entries, validated recipes and import drafts.
 * Drafts may contain unfinished edits; they are not validated Recipe objects.
 *
 * @author PantryPal team (GenAI-assisted implementation)
 * @version 2026.10.14
 */
public class PantryPalData
{
    private final Ingredient[] pantryIngredients;
    private final Recipe[] savedRecipes;
    private final RecipeImportDraft[] savedDrafts;


    /**
     * Validates and copies both collections, preserving their entry order.
     * IDs must be unique within each collection; names may repeat.
     * @param pantryIngredients pantry entries, possibly empty
     * @param savedRecipes saved recipes, possibly empty
     * @throws IllegalArgumentException if arrays/entries are null or IDs repeat
     */
    public PantryPalData(Ingredient[] pantryIngredients, Recipe[] savedRecipes)
    {
        this(pantryIngredients, savedRecipes, new RecipeImportDraft[0]);
    }

    /**
     * Creates a snapshot including immutable unfinished recipe imports.
     * @param pantryIngredients pantry entries
     * @param savedRecipes validated recipes
     * @param savedDrafts unfinished imports, unique by TheMealDB ID
     */
    public PantryPalData(Ingredient[] pantryIngredients, Recipe[] savedRecipes,
        RecipeImportDraft[] savedDrafts)
    {
        this.pantryIngredients =
            new Pantry(pantryIngredients).getAllIngredients();
        this.savedRecipes = new RecipeBook(savedRecipes).getAllRecipes();
        if (savedDrafts == null)
        {
            throw new IllegalArgumentException("Draft array is required.");
        }
        java.util.Set<String> ids = new java.util.HashSet<String>();
        for (RecipeImportDraft draft : savedDrafts)
        {
            if (draft == null || !ids.add(draft.getExternalId()))
            {
                throw new IllegalArgumentException("Drafts must have unique IDs.");
            }
        }
        this.savedDrafts = savedDrafts.clone();
    }

    /** @return independent array of immutable unfinished imports */
    public RecipeImportDraft[] getSavedDrafts() { return savedDrafts.clone(); }


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
