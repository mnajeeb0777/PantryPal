import student.TestCase;

/**
 * Tests recipe storage, identity, searches, filtering, and snapshots.
 *
 * @author PantryPal team (GenAI-assisted tests)
 * @version 2026.10.14
 */
public class RecipeBookTest extends TestCase
{
    /** Empty books return empty results and reject no-op removals safely. */
    public void testEmptyBook()
    {
        RecipeBook book = new RecipeBook();
        assertTrue(book.isEmpty());
        assertEquals(0, book.size());
        assertEquals(0, book.getAllRecipes().length);
        assertEquals(0, book.searchRecipes("Rice").length);
        assertEquals(0, book.filterRecipes(null, null).length);
        assertNull(book.findRecipeById(TestFixtures.id(1)));
        assertFalse(book.removeRecipe(TestFixtures.id(1)));
        assertTrue(new RecipeBook(new Recipe[0]).isEmpty());
    }


    /** Initial data is copied, and malformed lists are rejected. */
    public void testConstructorAndInvalidLists()
    {
        Recipe original = TestFixtures.recipe(1, "Rice", 20, Effort.EASY);
        Recipe[] input = { original };
        RecipeBook book = new RecipeBook(input);
        input[0] = null;
        assertEquals(1, book.size());
        assertFalse(book.isEmpty());
        assertNotSame(original, book.findRecipeById(TestFixtures.id(1)));
        TestFixtures.invalid(() -> new RecipeBook(null));
        TestFixtures.invalid(() -> new RecipeBook(new Recipe[] { null }));
        TestFixtures.invalid(() -> new RecipeBook(
            new Recipe[] { original, new Recipe(original) }));
    }


    /** Add permits repeated names while rejecting repeated local IDs. */
    public void testAddAndDuplicates()
    {
        RecipeBook book = new RecipeBook();
        assertTrue(book.addRecipe(
            TestFixtures.recipe(1, "Rice", 20, Effort.EASY)));
        assertTrue(book.addRecipe(
            TestFixtures.recipe(2, "Rice", 30, Effort.HARD)));
        assertFalse(book.addRecipe(
            TestFixtures.recipe(1, "Other", 99, Effort.HARD)));
        assertEquals(2, book.size());
        assertEquals("Rice", book.findRecipeById(TestFixtures.id(1)).getName());
        TestFixtures.invalid(() -> book.addRecipe(null));
        assertEquals(2, book.size());
    }


    /** Returned recipes and nested arrays do not expose book state. */
    public void testSnapshots()
    {
        RecipeBook book = new RecipeBook(new Recipe[] {
            TestFixtures.recipe(1, "Rice", 20, Effort.EASY) });
        Recipe[] all = book.getAllRecipes();
        all[0].getRequiredIngredients()[0].setQuantity(90);
        all[0] = null;
        Recipe found = book.findRecipeById(TestFixtures.id(1));
        found.getInstructions()[0] = "Changed";
        book.searchRecipes("Rice")[0]
            .getRequiredIngredients()[0].setQuantity(80);
        book.filterRecipes(null, null)[0]
            .getRequiredIngredients()[0].setQuantity(70);
        Recipe stored = book.findRecipeById(TestFixtures.id(1));
        assertEquals(2.0,
            stored.getRequiredIngredients()[0].getQuantity(), 0.0);
        assertEquals("Cook the rice.", stored.getInstructions()[0]);
        assertEquals(1, book.size());
        assertNotSame(found, stored);
    }


    /** Search supports case-insensitive substrings and empty results. */
    public void testSearch()
    {
        RecipeBook book = new RecipeBook(new Recipe[] {
            TestFixtures.recipe(1, "Rice bowl", 20, Effort.EASY),
            TestFixtures.recipe(2, "Fried rice", 30, Effort.HARD),
            TestFixtures.recipe(3, "Soup", 15, Effort.MEDIUM) });
        assertEquals(2, book.searchRecipes(" rIcE ").length);
        assertEquals(3, book.searchRecipes("").length);
        assertEquals(0, book.searchRecipes("Cake").length);
        TestFixtures.invalid(() -> book.searchRecipes(null));
    }


    /** Filters combine with AND and exclude unknown active cook times. */
    public void testFilters()
    {
        RecipeBook book = new RecipeBook(new Recipe[] {
            TestFixtures.recipe(1, "Fast", 10, Effort.EASY),
            TestFixtures.recipe(2, "Slow", 30, Effort.EASY),
            TestFixtures.recipe(3, "Hard", 10, Effort.HARD),
            TestFixtures.recipe(4, "Unknown", null, Effort.UNKNOWN),
            TestFixtures.recipe(5, "No-cook", 0, Effort.EASY) });
        assertEquals(5, book.filterRecipes(null, null).length);
        assertEquals(3, book.filterRecipes(10, null).length);
        assertEquals(2, book.filterRecipes(10, Effort.EASY).length);
        assertEquals(3, book.filterRecipes(null, Effort.EASY).length);
        assertEquals(1, book.filterRecipes(null, Effort.UNKNOWN).length);
        assertEquals(0, book.filterRecipes(100, Effort.UNKNOWN).length);
        assertEquals(0, book.filterRecipes(5, Effort.HARD).length);
        assertEquals(TestFixtures.id(5),
            book.filterRecipes(0, null)[0].getId());
        TestFixtures.invalid(() -> book.filterRecipes(-1, null));
        assertEquals(5, book.size());
    }


    /** Lookup and removal use local ID and preserve other same-name recipes. */
    public void testFindAndRemove()
    {
        RecipeBook book = new RecipeBook(new Recipe[] {
            TestFixtures.recipe(1, "Rice", 10, Effort.EASY),
            TestFixtures.recipe(2, "Rice", 20, Effort.HARD) });
        assertEquals(20, book.findRecipeById(TestFixtures.id(2))
            .getCookTimeMinutes().intValue());
        assertNull(book.findRecipeById("missing"));
        assertTrue(book.removeRecipe(TestFixtures.id(1)));
        assertFalse(book.removeRecipe(TestFixtures.id(1)));
        assertEquals(1, book.size());
        assertNotNull(book.findRecipeById(TestFixtures.id(2)));
        for (String id : new String[] { null, "", " " })
        {
            TestFixtures.invalid(() -> book.findRecipeById(id));
            TestFixtures.invalid(() -> book.removeRecipe(id));
        }
        assertTrue(book.removeRecipe(TestFixtures.id(2)));
        assertTrue(book.isEmpty());
        assertEquals(0, book.size());
    }
}
