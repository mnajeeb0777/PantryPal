import java.io.IOException;
import student.TestCase;

/**
 * Tests session coordination, ownership, and atomic application actions.
 *
 * @author PantryPal team (GenAI-assisted tests)
 * @version 2026.10.14
 */
public class PantryPalControllerTest extends TestCase
{
    /**
     * New sessions are independent and begin with empty collections.
     * @throws IOException if the test operation fails unexpectedly
     */
    public void testSessionConstruction()
        throws IOException
    {
        PantryPalController first = new PantryPalController();
        assertEquals(0, first.getPantryIngredients().length);
        assertEquals(0, first.getSavedRecipes().length);
        assertFalse(first.hasUnsavedChanges());
        assertFalse(first.isBrokeModeEnabled());
        first.addPantryIngredient("Rice", 2, Unit.CUP);
        assertTrue(first.hasUnsavedChanges());
        assertEquals(0,
            new PantryPalController().getPantryIngredients().length);
        TestFixtures.invalid(
            () -> new PantryPalController(null, null, null, null));
    }


    /**
     * A supplied matcher controls recipe comparison and cooking.
     * @throws IOException if the test operation fails unexpectedly
     */
    public void testSuppliedMatcher()
        throws IOException
    {
        java.util.Map<String, String> aliases =
            new java.util.HashMap<String, String>();
        aliases.put("caster sugar", "sugar");
        PantryPalController controller = new PantryPalController(null,
            new IngredientMatcher(aliases), null, null);
        controller.addPantryIngredient("Caster sugar", 2, Unit.CUP);
        Recipe recipe = controller.addCustomRecipe("Sugar dish",
            new Ingredient[] {
                TestFixtures.ingredient(1, "Sugar", 1, Unit.CUP) },
            new String[0], null, null, Effort.UNKNOWN);
        assertTrue(controller.compareRecipeWithPantry(recipe).canCook());
        assertTrue(controller.cookRecipe(recipe));
        assertEquals(1.0,
            controller.getPantryIngredients()[0].getQuantity(), 0.0);
    }


    /**
     * Pantry CRUD generates distinct IDs and returns independent snapshots.
     * @throws IOException if the test operation fails unexpectedly
     */
    public void testPantryWorkflow()
        throws IOException
    {
        PantryPalController controller = new PantryPalController();
        Ingredient first = controller.addPantryIngredient("Rice", 2, Unit.CUP);
        Ingredient second = controller.addPantryIngredient("Rice", 3, Unit.CUP);
        assertFalse(first.getId().equals(second.getId()));
        first.setQuantity(9);
        assertEquals(2.0, controller.findPantryIngredientById(first.getId())
            .getQuantity(), 0.0);
        Ingredient[] returned = controller.getPantryIngredients();
        returned[0].setQuantity(10);
        assertEquals(3.0, controller.findPantryIngredientById(second.getId())
            .getQuantity(), 0.0);
        assertEquals(2, controller.searchPantryIngredients("RI").length);
        assertEquals(0, controller.searchPantryIngredients("beans").length);
        assertTrue(controller.updatePantryIngredient(
            first.getId(), "Brown rice", 4, Unit.CUP));
        assertEquals("Brown rice",
            controller.findPantryIngredientById(first.getId()).getName());
        assertTrue(controller.removePantryIngredient(first.getId()));
        assertNull(controller.findPantryIngredientById(first.getId()));
        assertEquals(1, controller.getPantryIngredients().length);
    }


    /**
     * Rejected pantry input and absent actions do not dirty a new session.
     * @throws IOException if the test operation fails unexpectedly
     */
    public void testInvalidPantryActions()
        throws IOException
    {
        PantryPalController controller = new PantryPalController();
        invalid(() -> controller.addPantryIngredient("Rice", 0, Unit.CUP));
        invalid(() -> controller.addPantryIngredient("", 1, Unit.CUP));
        invalid(() -> controller.updatePantryIngredient(null,
            "Rice", 1, Unit.CUP));
        invalid(() -> controller.removePantryIngredient(""));
        TestFixtures.invalid(() -> controller.findPantryIngredientById(null));
        TestFixtures.invalid(() -> controller.searchPantryIngredients(null));
        assertFalse(controller.removePantryIngredient(TestFixtures.id(99)));
        assertFalse(controller.updatePantryIngredient(
            TestFixtures.id(99), "Rice", 1, Unit.CUP));
        assertFalse(controller.hasUnsavedChanges());
        assertEquals(0, controller.getPantryIngredients().length);
        Ingredient entry = controller.addPantryIngredient("Rice", 2, Unit.CUP);
        invalid(() -> controller.updatePantryIngredient(
            entry.getId(), "Rice", -1, Unit.CUP));
        assertEquals(2.0, controller.findPantryIngredientById(entry.getId())
            .getQuantity(), 0.0);
    }


    /**
     * Recipe creation, searches, filters, and removal use saved snapshots.
     * @throws IOException if the test operation fails unexpectedly
     */
    public void testRecipeWorkflow()
        throws IOException
    {
        PantryPalController controller = new PantryPalController();
        Ingredient source = TestFixtures.ingredient(1, "Rice", 2, Unit.CUP);
        Recipe recipe = controller.addCustomRecipe("Rice bowl",
            new Ingredient[] { source }, new String[] { "Cook rice." },
            5, 10, Effort.EASY);
        source.setQuantity(9);
        assertEquals(2.0, controller.findSavedRecipeById(recipe.getId())
            .getRequiredIngredients()[0].getQuantity(), 0.0);
        assertEquals("", recipe.getSourceUrl());
        assertEquals("", recipe.getExternalRecipeId());
        assertEquals(1, controller.getSavedRecipes().length);
        assertEquals(1, controller.searchSavedRecipes("RICE").length);
        assertEquals(0, controller.searchSavedRecipes("beans").length);
        assertEquals(1, controller.filterSavedRecipes(10, Effort.EASY).length);
        assertEquals(0, controller.filterSavedRecipes(9, null).length);
        assertEquals(0,
            controller.filterSavedRecipes(null, Effort.HARD).length);
        assertTrue(controller.removeSavedRecipe(recipe.getId()));
        assertNull(controller.findSavedRecipeById(recipe.getId()));
        assertFalse(controller.removeSavedRecipe(recipe.getId()));
    }


    /**
     * Invalid recipes and lookups leave empty saved collections unchanged.
     * @throws IOException if the test operation fails unexpectedly
     */
    public void testInvalidRecipeActions()
        throws IOException
    {
        PantryPalController controller = new PantryPalController();
        invalid(() -> controller.addCustomRecipe("Empty", new Ingredient[0],
            new String[0], 0, 0, Effort.EASY));
        invalid(() -> controller.addCustomRecipe("Rice",
            new Ingredient[] {
                TestFixtures.ingredient(1, "Rice", 1, Unit.CUP) },
            new String[0], -1, 0, Effort.EASY));
        invalid(() -> controller.removeSavedRecipe(null));
        TestFixtures.invalid(() -> controller.findSavedRecipeById(""));
        TestFixtures.invalid(() -> controller.searchSavedRecipes(null));
        TestFixtures.invalid(() -> controller.filterSavedRecipes(-1, null));
        assertNull(controller.findSavedRecipeById(TestFixtures.id(99)));
        assertFalse(controller.removeSavedRecipe(TestFixtures.id(99)));
        assertEquals(0, controller.getSavedRecipes().length);
        assertFalse(controller.hasUnsavedChanges());
    }


    /**
     * Importing already-shaped recipe data rejects duplicate IDs.
     * @throws IOException if the test operation fails unexpectedly
     */
    public void testImportSnapshot()
        throws IOException
    {
        PantryPalController controller = new PantryPalController();
        Recipe recipe = TestFixtures.recipe(1, "Rice", 10, Effort.EASY);
        assertTrue(controller.importRecipe(recipe));
        assertFalse(controller.importRecipe(recipe));
        invalid(() -> controller.importRecipe(null));
        assertEquals(1, controller.getSavedRecipes().length);
        assertNotSame(recipe, controller.findSavedRecipeById(recipe.getId()));
    }


    /**
     * Cooking uses fresh stock instead of trusting an earlier comparison.
     * @throws IOException if the test operation fails unexpectedly
     */
    public void testCompareThenCook()
        throws IOException
    {
        PantryPalController controller = new PantryPalController();
        Recipe recipe = TestFixtures.recipe(1, "Rice", 10, Effort.EASY);
        Ingredient entry = controller.addPantryIngredient("Rice", 2, Unit.CUP);
        assertTrue(controller.compareRecipeWithPantry(recipe).canCook());
        assertEquals(2.0,
            controller.getPantryIngredients()[0].getQuantity(), 0.0);
        controller.updatePantryIngredient(entry.getId(), "Rice", 1, Unit.CUP);
        assertFalse(controller.cookRecipe(recipe));
        assertEquals(1.0,
            controller.getPantryIngredients()[0].getQuantity(), 0.0);
        controller.updatePantryIngredient(entry.getId(), "Rice", 2, Unit.CUP);
        assertTrue(controller.cookRecipe(recipe));
        assertEquals(0, controller.getPantryIngredients().length);
        assertFalse(controller.cookRecipe(recipe));
    }


    /**
     * Failed or invalid cooking cannot partially deduct available entries.
     * @throws IOException if the test operation fails unexpectedly
     */
    public void testFailedCooking()
        throws IOException
    {
        PantryPalController controller = new PantryPalController();
        Recipe recipe = new Recipe(TestFixtures.id(1), "Rice and beans",
            new Ingredient[] {
                TestFixtures.ingredient(2, "Rice", 2, Unit.CUP),
                TestFixtures.ingredient(3, "Beans", 1, Unit.CUP) },
            new String[0], null, null, Effort.UNKNOWN, "", "");
        controller.addPantryIngredient("Rice", 2, Unit.CUP);
        assertFalse(controller.compareRecipeWithPantry(recipe).canCook());
        assertFalse(controller.cookRecipe(recipe));
        TestFixtures.invalid(() -> controller.compareRecipeWithPantry(null));
        invalid(() -> controller.cookRecipe(null));
        assertEquals(2.0,
            controller.getPantryIngredients()[0].getQuantity(), 0.0);
    }


    /**
     * Session-only mode never pretends data was saved or loaded.
     * @throws IOException if the test operation fails unexpectedly
     */
    public void testUnavailablePersistence()
        throws IOException
    {
        PantryPalController controller = new PantryPalController();
        unavailable(() -> controller.loadData());
        unavailable(() -> controller.saveData());
        assertFalse(controller.hasUnsavedChanges());
        controller.addPantryIngredient("Rice", 2, Unit.CUP);
        unavailable(() -> controller.saveData());
        unavailable(() -> controller.loadData());
        assertTrue(controller.hasUnsavedChanges());
        assertEquals(1, controller.getPantryIngredients().length);
    }


    /**
     * External features are explicitly unavailable without configured services.
     * @throws Exception if the test operation fails unexpectedly
     */
    public void testUnavailableExternalServices()
        throws Exception
    {
        PantryPalController controller = new PantryPalController();
        Ingredient ingredient = TestFixtures.ingredient(1, "Rice", 1, Unit.CUP);
        unavailable(() -> controller.searchExternalRecipes("Rice"));
        unavailable(() -> controller.findNearbyOffers(ingredient, "24060", 5));
        controller.setBrokeModeEnabled(true);
        assertTrue(controller.isBrokeModeEnabled());
        unavailable(() -> controller.findNearbyOffers(ingredient, "24060", 5));
        unavailable(() -> controller.findCheapestOffer(
            ingredient, new StoreOffer[0]));
        unavailable(() -> controller.calculateRequiredPackages(
            ingredient, null));
        unavailable(() -> controller.calculatePurchaseCost(ingredient, null));
        controller.setBrokeModeEnabled(false);
        assertFalse(controller.isBrokeModeEnabled());
        assertFalse(controller.hasUnsavedChanges());
    }


    /**
     * Empty recipes/pantry and known shortages yield no selection.
     * @throws IOException if a session operation unexpectedly fails
     */
    public void testRouletteWithoutCookableRecipes()
        throws IOException
    {
        PantryPalController controller = new PantryPalController();
        assertNull(controller.selectMealRouletteRecipe());
        assertFalse(controller.hasUnsavedChanges());
        Recipe recipe = TestFixtures.recipe(1, "Rice", 10, Effort.EASY);
        controller.importRecipe(recipe);
        assertNull(controller.selectMealRouletteRecipe());
        Ingredient entry = controller.addPantryIngredient("Rice", 1, Unit.CUP);
        assertNull(controller.selectMealRouletteRecipe());
        controller.updatePantryIngredient(entry.getId(), "Rice", 2, Unit.CUP);
        assertEquals(recipe, controller.selectMealRouletteRecipe());
        controller.removeSavedRecipe(recipe.getId());
        assertNull(controller.selectMealRouletteRecipe());
        assertEquals(2.0,
            controller.getPantryIngredients()[0].getQuantity(), 0.0);
    }


    /**
     * Unknown units and repeated requirements cannot falsely qualify a recipe.
     * @throws IOException if a session operation unexpectedly fails
     */
    public void testRouletteEligibilityRules()
        throws IOException
    {
        PantryPalController controller = new PantryPalController();
        Recipe incompatible = new Recipe(TestFixtures.id(1), "Weighed rice",
            new Ingredient[] {
                TestFixtures.ingredient(10, "Rice", 2, Unit.GRAM) },
            new String[0], null, null, Effort.UNKNOWN, "", "");
        Recipe repeated = new Recipe(TestFixtures.id(2), "Rice twice",
            new Ingredient[] {
                TestFixtures.ingredient(11, "Rice", 2, Unit.CUP),
                TestFixtures.ingredient(12, "Rice", 2, Unit.CUP) },
            new String[0], null, null, Effort.UNKNOWN, "", "");
        controller.importRecipe(incompatible);
        controller.importRecipe(repeated);
        controller.addPantryIngredient("Rice", 3, Unit.CUP);
        assertNull(controller.selectMealRouletteRecipe());
        Recipe eligible = TestFixtures.recipe(3, "Rice bowl", 10, Effort.EASY);
        controller.importRecipe(eligible);
        assertEquals(eligible, controller.selectMealRouletteRecipe());
        assertEquals(3.0,
            controller.getPantryIngredients()[0].getQuantity(), 0.0);
    }


    /**
     * Aliases and unit conversion qualify recipes using the existing matcher.
     * @throws IOException if a session operation unexpectedly fails
     */
    public void testRouletteAliasesAndConversions()
        throws IOException
    {
        PantryPalController controller = new PantryPalController();
        Recipe eligible = new Recipe(TestFixtures.id(1), "Onion rice",
            new Ingredient[] {
                TestFixtures.ingredient(10, "Rice", 16, Unit.TABLESPOON),
                TestFixtures.ingredient(11, "Scallions", 2, Unit.COUNT) },
            new String[0], null, null, Effort.UNKNOWN, "", "");
        controller.importRecipe(eligible);
        controller.addPantryIngredient("Rice", 1, Unit.CUP);
        controller.addPantryIngredient("Green onions", 2, Unit.COUNT);
        assertEquals(eligible, controller.selectMealRouletteRecipe());
        assertTrue(controller.cookRecipe(eligible));
        assertNull(controller.selectMealRouletteRecipe());
        assertEquals(0, controller.getPantryIngredients().length);
    }


    /**
     * Each draw returns a cookable saved snapshot without reserving any stock.
     * @throws IOException if a session operation unexpectedly fails
     */
    public void testRouletteMultipleCandidatesAndIsolation()
        throws IOException
    {
        PantryPalController controller = new PantryPalController();
        Recipe first = TestFixtures.recipe(1, "Rice bowl", 10, Effort.EASY);
        Recipe second = TestFixtures.recipe(2, "Rice bowl", 30, Effort.HARD);
        Recipe unavailable = new Recipe(TestFixtures.id(3), "Bean bowl",
            new Ingredient[] {
                TestFixtures.ingredient(10, "Beans", 1, Unit.CUP) },
            new String[0], null, null, Effort.UNKNOWN, "", "");
        controller.importRecipe(first);
        controller.importRecipe(second);
        controller.importRecipe(unavailable);
        Ingredient stock = controller.addPantryIngredient("Rice", 2, Unit.CUP);
        boolean dirty = controller.hasUnsavedChanges();
        for (int i = 0; i < 30; i++)
        {
            Recipe choice = controller.selectMealRouletteRecipe();
            assertNotNull(choice);
            assertTrue(choice.equals(first) || choice.equals(second));
            assertTrue(controller.compareRecipeWithPantry(choice).canCook());
            choice.getRequiredIngredients()[0].setQuantity(9);
            assertEquals(2.0, controller.findSavedRecipeById(choice.getId())
                .getRequiredIngredients()[0].getQuantity(), 0.0);
        }
        assertEquals(dirty, controller.hasUnsavedChanges());
        assertEquals(3, controller.getSavedRecipes().length);
        assertEquals(2.0, controller.findPantryIngredientById(stock.getId())
            .getQuantity(), 0.0);
        controller.removeSavedRecipe(first.getId());
        assertEquals(second, controller.selectMealRouletteRecipe());
        controller.removeSavedRecipe(second.getId());
        assertNull(controller.selectMealRouletteRecipe());
    }


    /**
     * Unrepresentable conversions report an error without changing stock.
     * @throws IOException if a session operation unexpectedly fails
     */
    public void testRouletteConversionFailure()
        throws IOException
    {
        PantryPalController controller = new PantryPalController();
        controller.importRecipe(
            TestFixtures.recipe(1, "Rice bowl", 10, Effort.EASY));
        controller.addPantryIngredient("Rice", Double.MIN_VALUE,
            Unit.MILLILITER);
        TestFixtures.invalid(() -> controller.selectMealRouletteRecipe());
        assertEquals(Double.MIN_VALUE,
            controller.getPantryIngredients()[0].getQuantity(), 0.0);
        assertEquals(1, controller.getSavedRecipes().length);
    }


    private void invalid(Operation action)
    {
        try
        {
            action.run();
            fail("Expected invalid input.");
        }
        catch (IllegalArgumentException expected)
        {
            // Model validation must reach the controller caller.
        }
        catch (Exception unexpected)
        {
            fail("Unexpected exception: " + unexpected);
        }
    }


    private void unavailable(Operation action)
    {
        try
        {
            action.run();
            fail("Expected unavailable feature.");
        }
        catch (IllegalStateException expected)
        {
            assertFalse(expected.getMessage().isBlank());
        }
        catch (Exception unexpected)
        {
            fail("Unexpected exception: " + unexpected);
        }
    }


    @FunctionalInterface
    private interface Operation
    {
        void run() throws Exception;
    }
}
