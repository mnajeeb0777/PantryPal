import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import student.TestCase;

/**
 * Verifies autosave and recovery across real controller/store boundaries.
 *
 * @author PantryPal team (GenAI-assisted tests)
 * @version 2026.10.14
 */
public class PantryPalPersistenceTest extends TestCase
{
    private Path directory;
    private Path file;
    private DataStore store;
    private PantryPalController controller;


    /**
     * Creates isolated persistence and a loaded empty controller.
     * @throws IOException if setup fails
     */
    @Override
    public void setUp() throws IOException
    {
        directory = TestFixtures.temporaryDirectory();
        file = directory.resolve("data.properties");
        store = new DataStore(file);
        controller = controller(store);
        controller.loadData();
    }


    /**
     * Cleans only this test's files.
     * @throws IOException if cleanup fails
     */
    @Override
    public void tearDown() throws IOException
    {
        TestFixtures.deleteDirectory(directory);
    }


    /**
     * Pantry, custom/imported recipes, cooking, and removals survive reopening.
     * @throws IOException if persistence unexpectedly fails
     */
    public void testAutosaveAcrossFullWorkflow() throws IOException
    {
        assertTrue(controller.isPersistenceEnabled());
        assertFalse(new PantryPalController().isPersistenceEnabled());
        Ingredient rice = controller.addPantryIngredient("Rice", 5, Unit.CUP);
        assertSavedQuantity(5);
        controller.updatePantryIngredient(rice.getId(), "Rice", 6, Unit.CUP);
        assertSavedQuantity(6);
        Recipe custom = controller.addCustomRecipe("Rice bowl",
            new Ingredient[] { TestFixtures.ingredient(80, "Rice", 2, Unit.CUP) },
            new String[] { "Boil rice." }, null, 20, Effort.EASY);
        assertEquals(custom.getId(), store.load().getSavedRecipes()[0].getId());
        Recipe imported = new Recipe(TestFixtures.id(90), "Imported Rice",
            custom.getRequiredIngredients(), custom.getInstructions(),
            5, 10, Effort.UNKNOWN, "https://example.org/recipe", "external-90");
        assertTrue(controller.importRecipe(imported));
        assertEquals(2, store.load().getSavedRecipes().length);
        PantryPalController reopened = controller(new DataStore(file));
        reopened.loadData();
        assertFalse(reopened.hasUnsavedChanges());
        assertEquals(2, reopened.getSavedRecipes().length);
        assertEquals("external-90", reopened.findSavedRecipeById(imported.getId())
            .getExternalRecipeId());
        byte[] before = Files.readAllBytes(file);
        assertNotNull(reopened.selectMealRouletteRecipe());
        assertTrue(Arrays.equals(before, Files.readAllBytes(file)));
        assertTrue(reopened.cookRecipe(custom));
        assertSavedQuantity(4);
        assertTrue(reopened.removeSavedRecipe(imported.getId()));
        assertEquals(1, store.load().getSavedRecipes().length);
        assertTrue(reopened.removePantryIngredient(rice.getId()));
        assertEquals(0, store.load().getPantryIngredients().length);
        assertFalse(reopened.hasUnsavedChanges());
    }


    /**
     * Rejected/no-op actions preserve both disk contents and clean state.
     * @throws IOException if persistence unexpectedly fails
     */
    public void testRejectedActionsDoNotSave() throws IOException
    {
        Recipe recipe = TestFixtures.recipe(1, "Rice", 10, Effort.EASY);
        controller.importRecipe(recipe);
        byte[] before = Files.readAllBytes(file);
        assertFalse(controller.importRecipe(recipe));
        assertFalse(controller.cookRecipe(recipe));
        assertFalse(controller.removePantryIngredient(TestFixtures.id(99)));
        assertFalse(controller.removeSavedRecipe(TestFixtures.id(99)));
        assertFalse(controller.updatePantryIngredient(TestFixtures.id(99),
            "Rice", 2, Unit.CUP));
        try
        {
            controller.addPantryIngredient("Rice", -1, Unit.CUP);
            fail("Expected invalid quantity.");
        }
        catch (IllegalArgumentException expected)
        {
            assertFalse(controller.hasUnsavedChanges());
        }
        assertTrue(Arrays.equals(before, Files.readAllBytes(file)));
    }


    /**
     * Every mutation and explicit save are blocked before loading existing data.
     * @throws IOException if setup/loading fails
     */
    public void testExistingSaveRequiresLoad() throws IOException
    {
        Ingredient rice = controller.addPantryIngredient("Rice", 3, Unit.CUP);
        Recipe recipe = TestFixtures.recipe(1, "Rice", 10, Effort.EASY);
        controller.importRecipe(recipe);
        PantryPalController fresh = controller(new DataStore(file));
        byte[] before = Files.readAllBytes(file);
        IoAction[] actions = {
            () -> fresh.saveData(),
            () -> fresh.addPantryIngredient("Salt", 1, Unit.TEASPOON),
            () -> fresh.updatePantryIngredient(rice.getId(), "Rice", 1, Unit.CUP),
            () -> fresh.removePantryIngredient(rice.getId()),
            () -> fresh.addCustomRecipe("New", recipe.getRequiredIngredients(),
                recipe.getInstructions(), null, null, Effort.UNKNOWN),
            () -> fresh.importRecipe(recipe),
            () -> fresh.removeSavedRecipe(recipe.getId()),
            () -> fresh.cookRecipe(recipe)
        };
        for (IoAction action : actions)
        {
            try
            {
                action.run();
                fail("Existing save must be loaded first.");
            }
            catch (IllegalStateException expected)
            {
                assertTrue(expected.getMessage().contains("retry loading"));
            }
        }
        assertTrue(Arrays.equals(before, Files.readAllBytes(file)));
        assertEquals(0, fresh.getPantryIngredients().length);
        assertFalse(fresh.hasUnsavedChanges());
        fresh.loadData();
        assertEquals(rice.getId(), fresh.getPantryIngredients()[0].getId());
        fresh.saveData();
    }


    /**
     * Save failure retains changed memory and dirty state; retry saves once.
     * @throws IOException if setup/recovery fails
     */
    public void testFailedAutosaveAndRetry() throws IOException
    {
        controller.addPantryIngredient("Rice", 3, Unit.CUP);
        Path backup = directory.resolve("previous.properties");
        Files.move(file, backup);
        Files.createDirectory(file);
        Files.writeString(file.resolve("block"), "retain");
        try
        {
            controller.addPantryIngredient("Egg", 2, Unit.COUNT);
            fail("Expected failed autosave.");
        }
        catch (IOException expected)
        {
            assertTrue(controller.hasUnsavedChanges());
            assertEquals(2, controller.getPantryIngredients().length);
            assertEquals(1, new DataStore(backup).load().getPantryIngredients().length);
        }
        Files.delete(file.resolve("block"));
        Files.delete(file);
        Files.move(backup, file);
        controller.saveData();
        assertFalse(controller.hasUnsavedChanges());
        assertEquals(2, store.load().getPantryIngredients().length);
        assertEquals(1, controller.searchPantryIngredients("Egg").length);
    }


    /**
     * A corrupt load leaves both collections intact and protects the bad file.
     * @throws IOException if setup/recovery fails
     */
    public void testFailedLoadAndRecovery() throws IOException
    {
        Ingredient rice = controller.addPantryIngredient("Rice", 3, Unit.CUP);
        controller.importRecipe(TestFixtures.recipe(1, "Rice", 10, Effort.EASY));
        byte[] valid = Files.readAllBytes(file);
        Files.writeString(file, "version=99");
        try
        {
            controller.loadData();
            fail("Expected corrupt save error.");
        }
        catch (IOException expected)
        {
            assertEquals(rice.getId(), controller.getPantryIngredients()[0].getId());
            assertEquals(1, controller.getSavedRecipes().length);
            assertFalse(controller.hasUnsavedChanges());
        }
        try
        {
            controller.saveData();
            fail("Do not overwrite a failed load.");
        }
        catch (IllegalStateException expected)
        {
            assertEquals("version=99", Files.readString(file));
        }
        Files.write(file, valid);
        controller.loadData();
        controller.addPantryIngredient("Salt", 1, Unit.TEASPOON);
        assertEquals(2, store.load().getPantryIngredients().length);
        Files.delete(file);
        controller.loadData();
        assertEquals(0, controller.getPantryIngredients().length);
        assertEquals(0, controller.getSavedRecipes().length);
        assertFalse(controller.hasUnsavedChanges());
    }


    /**
     * Drafts persist independently and become real recipes only after review.
     * @throws IOException for unexpected persistence errors
     */
    public void testSavedDraftResumePromoteAndRemove() throws IOException
    {
        RecipeImportDraft draft = new RecipeImportDraft("42", "Bean soup",
            new String[] { "Beans" }, new String[] { "1 tin" }, "Heat.", "");
        controller.addPantryIngredient("Beans", 1000, Unit.GRAM);
        controller.saveRecipeDraft(draft);
        assertEquals(0, controller.getSavedRecipes().length);
        assertNull(controller.selectMealRouletteRecipe());
        assertEquals(1000.0, controller.getPantryIngredients()[0].getQuantity(), 0.0);
        PantryPalController reopened = controller(new DataStore(file));
        reopened.loadData();
        assertEquals(1, reopened.getSavedDrafts().length);
        RecipeImportDraft ready = reopened.getSavedDrafts()[0].withReview(
            new String[][] { { "Beans", "200", "GRAM" } }, "", "", Effort.UNKNOWN);
        reopened.saveRecipeDraft(ready);
        assertEquals(1, reopened.getSavedDrafts().length);
        assertEquals("200", store.load().getSavedDrafts()[0].getReviewRows()[0][1]);
        assertTrue(reopened.importRecipe(ready.toRecipe()));
        assertEquals(0, reopened.getSavedDrafts().length);
        assertEquals(0, store.load().getSavedDrafts().length);
        assertEquals(1, store.load().getSavedRecipes().length);
        assertTrue(reopened.cookRecipe(reopened.getSavedRecipes()[0]));
        assertEquals(800.0, reopened.getPantryIngredients()[0].getQuantity(), 0.0);
        assertTrue(reopened.removeSavedRecipe(reopened.getSavedRecipes()[0].getId()));
        reopened.saveRecipeDraft(draft);
        assertTrue(reopened.removeRecipeDraft("42"));
        assertFalse(reopened.removeRecipeDraft("42"));
        assertEquals(0, store.load().getSavedDrafts().length);
    }

    /**
     * Failed autosave retains both draft progress and completed promotion in memory.
     * @throws IOException for unexpected setup errors
     */
    public void testDraftSaveFailureAndPromotionRetry() throws IOException
    {
        final boolean[] failure = { true };
        DataStore flaky = new DataStore(file)
        {
            @Override
            public void save(PantryPalData snapshot) throws IOException
            {
                if (failure[0]) { throw new IOException("Test failure"); }
                super.save(snapshot);
            }
        };
        PantryPalController session = controller(flaky);
        RecipeImportDraft draft = new RecipeImportDraft("42", "Beans",
            new String[] { "Beans" }, new String[] { "1 tin" }, "", "");
        try { session.saveRecipeDraft(draft); fail("Expected save failure"); }
        catch (IOException expected) { assertTrue(session.hasUnsavedChanges()); }
        assertEquals(1, session.getSavedDrafts().length);
        failure[0] = false;
        session.saveData();
        assertEquals(1, store.load().getSavedDrafts().length);
        failure[0] = true;
        Recipe ready = draft.withReview(new String[][] { { "Beans", "200", "GRAM" } },
            "", "", Effort.UNKNOWN).toRecipe();
        try { session.importRecipe(ready); fail("Expected save failure"); }
        catch (IOException expected) { assertTrue(session.hasUnsavedChanges()); }
        assertEquals(0, session.getSavedDrafts().length);
        assertEquals(1, session.getSavedRecipes().length);
        assertEquals(1, store.load().getSavedDrafts().length);
        failure[0] = false;
        session.saveData();
        assertEquals(0, store.load().getSavedDrafts().length);
        assertEquals(1, store.load().getSavedRecipes().length);
    }

    /**
     * Unreadable existing saves block draft writes and protect all collections.
     * @throws IOException for unexpected file errors
     */
    public void testDraftLoadGuard() throws IOException
    {
        RecipeImportDraft draft = new RecipeImportDraft("42", "Beans",
            new String[] { "Beans" }, new String[] { "1 tin" }, "", "");
        Files.writeString(file, "broken save");
        PantryPalController blocked = controller(new DataStore(file));
        try { blocked.saveRecipeDraft(draft); fail("Expected load guard"); }
        catch (IllegalStateException expected) { assertEquals(0, blocked.getSavedDrafts().length); }
        try { blocked.removeRecipeDraft("42"); fail("Expected load guard"); }
        catch (IllegalStateException expected) { assertEquals("broken save", Files.readString(file)); }
    }

    private PantryPalController controller(DataStore dataStore)
    {
        return new PantryPalController(dataStore, new IngredientMatcher(),
            null, null);
    }


    private void assertSavedQuantity(double quantity) throws IOException
    {
        assertEquals(quantity, store.load().getPantryIngredients()[0]
            .getQuantity(), 0.0);
        assertFalse(controller.hasUnsavedChanges());
    }


    @FunctionalInterface
    private interface IoAction
    {
        void run() throws IOException;
    }
}
