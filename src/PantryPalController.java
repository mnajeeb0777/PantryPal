import java.io.IOException;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Coordinates application operations.
 * The default constructor starts a session-only prototype.
 * A configured DataStore enables autosave and explicit startup loading.
 *
 * @author PantryPal team (GenAI-assisted implementation)
 * @version 2026.10.14
 */
public class PantryPalController
{
    private Pantry pantry;
    private RecipeBook recipeBook;
    private IngredientMatcher ingredientMatcher;
    private RecipeMatcher recipeMatcher;
    private RecipeImporter recipeImporter;
    private PriceFinder priceFinder;
    private DataStore dataStore;
    private boolean brokeModeEnabled;
    private boolean unsavedChanges;
    private boolean loadRequired;


    /**
     * Starts an empty session with no file or external-service access.
     */
    public PantryPalController()
    {
        this(null, new IngredientMatcher(), null, null);
    }


    /**
     * Creates empty collections with a required matcher and optional services.
     * Null services disable their features for the session-only prototype.
     * @param dataStore persistence service, or null
     * @param ingredientMatcher required name and unit service
     * @param recipeImporter external recipe service, or null
     * @param priceFinder external price service, or null
     */
    public PantryPalController(
        DataStore dataStore,
        IngredientMatcher ingredientMatcher,
        RecipeImporter recipeImporter,
        PriceFinder priceFinder)
    {
        if (ingredientMatcher == null)
        {
            throw new IllegalArgumentException(
                "Ingredient matcher is required.");
        }
        this.dataStore = dataStore;
        this.ingredientMatcher = ingredientMatcher;
        this.recipeImporter = recipeImporter;
        this.priceFinder = priceFinder;
        pantry = new Pantry();
        recipeBook = new RecipeBook();
        recipeMatcher = new RecipeMatcher(ingredientMatcher);
        loadRequired = dataStore != null && dataStore.hasSaveFile();
    }


    /**
     * Validates both loaded collections before replacing current state.
     * @throws IOException as defined by the class reference error contract
     */
    public void loadData()
        throws IOException
    {
        requireDataStore();
        // A failed load must not allow autosave to replace the unreadable file.
        loadRequired = true;
        PantryPalData data = dataStore.load();
        Pantry loadedPantry = new Pantry(data.getPantryIngredients());
        RecipeBook loadedRecipes = new RecipeBook(data.getSavedRecipes());
        pantry = loadedPantry;
        recipeBook = loadedRecipes;
        unsavedChanges = false;
        loadRequired = false;
    }


    /**
     * Saves current snapshots and clears the dirty flag only on success.
     * @throws IOException as defined by the class reference error contract
     */
    public void saveData()
        throws IOException
    {
        requireDataStore();
        requireLoaded();
        dataStore.save(new PantryPalData(
            pantry.getAllIngredients(), recipeBook.getAllRecipes()));
        unsavedChanges = false;
    }


    /**
     * Reports whether changes have not been persisted.
     * @return true when collection changes have not been saved
     */
    public boolean hasUnsavedChanges()
    {
        return unsavedChanges;
    }


    /**
     * Reports whether this controller can save and load local data.
     * @return true when a persistence service is configured
     */
    public boolean isPersistenceEnabled()
    {
        return dataStore != null;
    }


    /**
     * Creates a new pantry entry with its own UUID.
     * @param name display name
     * @param quantity positive finite amount, at most 1,000,000
     * @param unit measurement unit
     * @return an independent snapshot of the new pantry entry
     * @throws IOException as defined by the class reference error contract
     */
    public Ingredient addPantryIngredient(
        String name,
        double quantity,
        Unit unit)
        throws IOException
    {
        requireLoaded();
        Ingredient entry = new Ingredient(UUID.randomUUID().toString(),
            name, quantity, unit);
        pantry.addIngredient(entry);
        changed();
        return new Ingredient(entry);
    }


    /**
     * Returns independent pantry snapshots.
     * @return current pantry snapshots, possibly empty
     */
    public Ingredient[] getPantryIngredients()
    {
        return pantry.getAllIngredients();
    }


    /**
     * Returns the selected pantry entry, or null if absent.
     * @param id stable entry ID
     * @return a snapshot, or null if no entry has the ID
     */
    public Ingredient findPantryIngredientById(String id)
    {
        return pantry.findIngredientById(id);
    }


    /**
     * Searches pantry names; blank matches all entries.
     * @param query name substring; blank matches all
     * @return matching pantry snapshots, possibly empty
     */
    public Ingredient[] searchPantryIngredients(String query)
    {
        return pantry.searchIngredients(query);
    }


    /**
     * Updates an existing pantry entry; false means absent.
     * @param id stable entry ID
     * @param name display name
     * @param quantity positive finite amount, at most 1,000,000
     * @param unit measurement unit
     * @return whether an entry was updated
     * @throws IOException as defined by the class reference error contract
     */
    public boolean updatePantryIngredient(
        String id,
        String name,
        double quantity,
        Unit unit)
        throws IOException
    {
        requireLoaded();
        boolean updated = pantry.updateIngredient(id, name, quantity, unit);
        if (updated)
        {
            changed();
        }
        return updated;
    }


    /**
     * Removes an existing pantry entry; false means absent.
     * @param id stable entry ID
     * @return whether an entry was removed
     * @throws IOException as defined by the class reference error contract
     */
    public boolean removePantryIngredient(String id)
        throws IOException
    {
        requireLoaded();
        boolean removed = pantry.removeIngredient(id);
        if (removed)
        {
            changed();
        }
        return removed;
    }


    /**
     * Stores a new custom recipe with a generated UUID.
     * @param name display name
     * @param ingredients nonempty array of recipe requirements
     * @param instructions ordered cooking steps
     * @param prepTimeMinutes prep time, or null if unknown
     * @param cookTimeMinutes cook time, or null if unknown
     * @param effort required effort category; UNKNOWN is allowed
     * @return an independent snapshot of the new recipe
     * @throws IOException as defined by the class reference error contract
     */
    public Recipe addCustomRecipe(
        String name,
        Ingredient[] ingredients,
        String[] instructions,
        Integer prepTimeMinutes,
        Integer cookTimeMinutes,
        Effort effort)
        throws IOException
    {
        requireLoaded();
        Recipe recipe = new Recipe(UUID.randomUUID().toString(), name,
            ingredients, instructions, prepTimeMinutes, cookTimeMinutes,
            effort, "", "");
        recipeBook.addRecipe(recipe);
        changed();
        return new Recipe(recipe);
    }


    /**
     * Returns independent recipe snapshots.
     * @return current recipe snapshots, possibly empty
     */
    public Recipe[] getSavedRecipes()
    {
        return recipeBook.getAllRecipes();
    }


    /**
     * Returns the selected recipe, or null if absent.
     * @param id stable entry ID
     * @return a snapshot, or null if no recipe has the ID
     */
    public Recipe findSavedRecipeById(String id)
    {
        return recipeBook.findRecipeById(id);
    }


    /**
     * Searches saved recipe names; blank matches all recipes.
     * @param query name substring; blank matches all
     * @return matching recipe snapshots, possibly empty
     */
    public Recipe[] searchSavedRecipes(String query)
    {
        return recipeBook.searchRecipes(query);
    }


    /**
     * Combines active time and effort filters.
     * @param maxCookTimeMinutes maximum cook time, or null to disable
     * @param effort effort category; null disables a filter
     * @return recipes satisfying every active filter
     */
    public Recipe[] filterSavedRecipes(
        Integer maxCookTimeMinutes,
        Effort effort)
    {
        return recipeBook.filterRecipes(maxCookTimeMinutes, effort);
    }


    /**
     * Removes a saved recipe; false means absent.
     * @param id stable entry ID
     * @return whether a recipe was removed
     * @throws IOException as defined by the class reference error contract
     */
    public boolean removeSavedRecipe(String id)
        throws IOException
    {
        requireLoaded();
        boolean removed = recipeBook.removeRecipe(id);
        if (removed)
        {
            changed();
        }
        return removed;
    }


    /**
     * Delegates to the optional external recipe service.
     * @param query nonblank recipe name
     * @return drafts requiring review before saving
     * @throws IOException as defined by the class reference error contract
     * @throws InterruptedException if an external request is interrupted
     */
    public RecipeImportDraft[] searchExternalRecipes(String query)
        throws IOException, InterruptedException
    {
        if (recipeImporter == null)
        {
            throw new IllegalStateException(
                "External recipe search is not configured for this session.");
        }
        return recipeImporter.searchRecipes(query);
    }


    /**
     * Stores a snapshot unless its local or external source identity is present.
     * @param recipe recipe snapshot to process
     * @return false if this recipe is already saved, preserving the existing copy
     * @throws IOException as defined by the class reference error contract
     */
    public boolean importRecipe(Recipe recipe)
        throws IOException
    {
        requireLoaded();
        if (recipe == null)
        {
            throw new IllegalArgumentException("Recipe is required.");
        }
        for (Recipe saved : recipeBook.getAllRecipes())
        {
            if (!recipe.getExternalRecipeId().isBlank()
                && recipe.getExternalRecipeId().equals(saved.getExternalRecipeId())
                && recipe.getSourceUrl().equals(saved.getSourceUrl()))
            {
                return false;
            }
        }
        boolean added = recipeBook.addRecipe(recipe);
        if (added)
        {
            changed();
        }
        return added;
    }


    /**
     * Compares requirements against current pantry stock.
     * @param recipe recipe snapshot to process
     * @return one allocation result per recipe requirement
     */
    public RecipeMatchResult compareRecipeWithPantry(Recipe recipe)
    {
        return recipeMatcher.compareRecipe(recipe, pantry.getAllIngredients());
    }


    /**
     * Randomly chooses among all saved recipes the current pantry can supply.
     * Every eligible saved recipe has an equal chance; repeats are allowed.
     * Selection does not consume stock, save data, or change session flags.
     * @return an independent recipe snapshot, or null if none can be cooked
     * @throws IllegalArgumentException if a conversion is unrepresentable
     */
    public Recipe selectMealRouletteRecipe()
    {
        Ingredient[] stock = pantry.getAllIngredients();
        Recipe[] saved = recipeBook.getAllRecipes();
        Recipe[] eligible = new Recipe[saved.length];
        int count = 0;
        for (Recipe recipe : saved)
        {
            if (recipeMatcher.compareRecipe(recipe, stock).canCook())
            {
                eligible[count++] = recipe;
            }
        }
        return count == 0 ? null
            : eligible[ThreadLocalRandom.current().nextInt(count)];
    }


    /**
     * Rechecks stock and deducts all requirements or changes nothing.
     * @param recipe recipe snapshot to process
     * @return true on complete deduction; false without changes on shortage
     * @throws IOException as defined by the class reference error contract
     */
    public boolean cookRecipe(Recipe recipe)
        throws IOException
    {
        requireLoaded();
        if (!compareRecipeWithPantry(recipe).canCook())
        {
            return false;
        }
        boolean cooked = pantry.deductIngredients(
            recipe.getRequiredIngredients(), ingredientMatcher);
        if (cooked)
        {
            changed();
        }
        return cooked;
    }


    /**
     * Changes the session's Broke Mode flag.
     * @param enabled whether to enable Broke Mode
     */
    public void setBrokeModeEnabled(boolean enabled)
    {
        brokeModeEnabled = enabled;
    }


    /**
     * Returns the session's Broke Mode flag.
     * @return whether Broke Mode is enabled
     */
    public boolean isBrokeModeEnabled()
    {
        return brokeModeEnabled;
    }


    /**
     * Delegates only when Broke Mode and a price service are available.
     * @param ingredient ingredient requirement
     * @param zipCode search postal code
     * @param radiusMiles search radius in miles
     * @return offers returned by the configured service
     * @throws IOException as defined by the class reference error contract
     * @throws InterruptedException if an external request is interrupted
     */
    public StoreOffer[] findNearbyOffers(
        Ingredient ingredient,
        String zipCode,
        double radiusMiles)
        throws IOException, InterruptedException
    {
        if (!brokeModeEnabled)
        {
            throw new IllegalStateException("Broke Mode must be enabled.");
        }
        requirePriceFinder();
        return priceFinder.findNearbyOffers(ingredient, zipCode, radiusMiles);
    }


    /**
     * Delegates to the optional price service.
     * @param ingredient ingredient requirement
     * @param offers offers to compare
     * @return the cheapest eligible offer, or null if none qualifies
     */
    public StoreOffer findCheapestOffer(
        Ingredient ingredient,
        StoreOffer[] offers)
    {
        requirePriceFinder();
        return priceFinder.findCheapestOffer(ingredient, offers);
    }


    /**
     * Delegates to the optional price service.
     * @param ingredient ingredient requirement
     * @param offer selected store offer
     * @return the number of whole packages required
     */
    public int calculateRequiredPackages(
        Ingredient ingredient,
        StoreOffer offer)
    {
        requirePriceFinder();
        return priceFinder.calculateRequiredPackages(ingredient, offer);
    }


    /**
     * Delegates to the optional price service.
     * @param ingredient ingredient requirement
     * @param offer selected store offer
     * @return the total cost of the required packages
     */
    public BigDecimal calculatePurchaseCost(
        Ingredient ingredient,
        StoreOffer offer)
    {
        requirePriceFinder();
        return priceFinder.calculatePurchaseCost(ingredient, offer);
    }

    /**
     * Marks a successful mutation, saving only when persistence is configured.
     * @throws IOException if the configured save fails
     */
    private void changed()
        throws IOException
    {
        unsavedChanges = true;
        if (dataStore != null)
        {
            saveData();
        }
    }


    /** Prevents edits/saves until an existing or failed save has been loaded. */
    private void requireLoaded()
    {
        if (loadRequired)
        {
            throw new IllegalStateException(
                "Load the save file successfully before changing data. "
                    + "Restore or move the file, then retry loading.");
        }
    }


    /** Rejects persistence requests in session-only mode. */
    private void requireDataStore()
    {
        if (dataStore == null)
        {
            throw new IllegalStateException(
                "Saving/loading is unavailable "
                    + "in this session-only prototype.");
        }
    }


    /** Rejects price operations when no price service is configured. */
    private void requirePriceFinder()
    {
        if (priceFinder == null)
        {
            throw new IllegalStateException(
                "Price lookup is unavailable in this prototype.");
        }
    }
}
