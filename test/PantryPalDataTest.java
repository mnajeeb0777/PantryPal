import student.TestCase;

/**
 * Validates save snapshots and their independence from callers.
 *
 * @author PantryPal team (GenAI-assisted tests)
 * @version 2026.10.14
 */
public class PantryPalDataTest extends TestCase
{
    /** Empty collections are valid and remain independent. */
    public void testEmptySnapshot()
    {
        PantryPalData data = new PantryPalData(new Ingredient[0], new Recipe[0]);
        assertEquals(0, data.getPantryIngredients().length);
        assertEquals(0, data.getSavedRecipes().length);
        assertNotSame(data.getPantryIngredients(), data.getPantryIngredients());
        assertNotSame(data.getSavedRecipes(), data.getSavedRecipes());
    }


    /** Input/output mutations cannot change the snapshot; order is retained. */
    public void testOwnershipAndOrder()
    {
        Ingredient first = TestFixtures.ingredient(1, "Rice", 3, Unit.CUP);
        Ingredient second = TestFixtures.ingredient(2, "Rice", 4, Unit.CUP);
        Ingredient[] pantry = { first, second };
        Recipe recipe = TestFixtures.recipe(3, "Rice bowl", null, Effort.UNKNOWN);
        Recipe[] recipes = { recipe,
            TestFixtures.recipe(4, "Rice bowl", 12, Effort.EASY) };
        PantryPalData data = new PantryPalData(pantry, recipes);
        first.setQuantity(99);
        pantry[1] = null;
        recipes[0] = null;
        assertEquals(3.0, data.getPantryIngredients()[0].getQuantity(), 0.0);
        assertEquals(second.getId(), data.getPantryIngredients()[1].getId());
        assertEquals(recipe.getId(), data.getSavedRecipes()[0].getId());
        Ingredient[] returned = data.getPantryIngredients();
        returned[0].setQuantity(50);
        returned[1] = null;
        Recipe[] returnedRecipes = data.getSavedRecipes();
        returnedRecipes[0].getRequiredIngredients()[0].setQuantity(70);
        returnedRecipes[0].getInstructions()[0] = "Changed";
        returnedRecipes[0] = null;
        assertEquals(3.0, data.getPantryIngredients()[0].getQuantity(), 0.0);
        assertNotNull(data.getPantryIngredients()[1]);
        assertEquals(2.0, data.getSavedRecipes()[0]
            .getRequiredIngredients()[0].getQuantity(), 0.0);
        assertEquals("Cook the rice.", data.getSavedRecipes()[0]
            .getInstructions()[0]);
        assertNull(data.getSavedRecipes()[0].getCookTimeMinutes());
    }


    /** Null collections/entries and duplicate IDs are rejected. */
    public void testInvalidSnapshots()
    {
        Ingredient entry = TestFixtures.ingredient(1, "Rice", 1, Unit.CUP);
        Recipe recipe = TestFixtures.recipe(2, "Rice", 10, Effort.EASY);
        TestFixtures.invalid(() -> new PantryPalData(null, new Recipe[0]));
        TestFixtures.invalid(() -> new PantryPalData(new Ingredient[0], null));
        TestFixtures.invalid(() -> new PantryPalData(
            new Ingredient[] { null }, new Recipe[0]));
        TestFixtures.invalid(() -> new PantryPalData(new Ingredient[0],
            new Recipe[] { null }));
        TestFixtures.invalid(() -> new PantryPalData(
            new Ingredient[] { entry, new Ingredient(entry) }, new Recipe[0]));
        TestFixtures.invalid(() -> new PantryPalData(new Ingredient[0],
            new Recipe[] { recipe, new Recipe(recipe) }));
    }
}
