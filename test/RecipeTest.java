import student.TestCase;

/**
 * Tests recipe ownership, validation, metadata, and local identity.
 *
 * @author PantryPal team (GenAI-assisted tests)
 * @version 2026.10.14
 */
public class RecipeTest extends TestCase
{
    /** All scalar getters expose supplied metadata. */
    public void testConstructorAndGetters()
    {
        Recipe recipe = create(TestFixtures.id(1), "Rice bowl",
            requirements(), new String[] { "Rinse", "Cook" },
            5, 20, Effort.EASY, "https://example.com/rice", "provider-1");
        assertEquals(TestFixtures.id(1), recipe.getId());
        assertEquals("Rice bowl", recipe.getName());
        assertEquals(5, recipe.getPrepTimeMinutes().intValue());
        assertEquals(20, recipe.getCookTimeMinutes().intValue());
        assertEquals(Effort.EASY, recipe.getEffort());
        assertEquals("https://example.com/rice", recipe.getSourceUrl());
        assertEquals("provider-1", recipe.getExternalRecipeId());
        assertEquals("Rinse", recipe.getInstructions()[0]);
        assertEquals("Cook", recipe.getInstructions()[1]);
    }


    /** Unknown/zero times, empty steps, and custom metadata are valid. */
    public void testUnknownAndBoundaryMetadata()
    {
        Recipe recipe = create(TestFixtures.id(2), "Unclassified",
            requirements(), new String[0], null, null, Effort.UNKNOWN, "", "");
        assertEquals(TestFixtures.id(2), recipe.getId());
        assertEquals("Unclassified", recipe.getName());
        assertNull(recipe.getPrepTimeMinutes());
        assertNull(recipe.getCookTimeMinutes());
        assertEquals(Effort.UNKNOWN, recipe.getEffort());
        assertEquals("", recipe.getSourceUrl());
        assertEquals("", recipe.getExternalRecipeId());
        assertEquals(0, recipe.getInstructions().length);
        Recipe zero = create(TestFixtures.id(3), "No-cook",
            requirements(), new String[0], 0, 0, Effort.EASY, "", "");
        assertEquals(0, zero.getPrepTimeMinutes().intValue());
        assertEquals(0, zero.getCookTimeMinutes().intValue());
    }


    /** Constructor inputs and returned arrays never expose owned data. */
    public void testDefensiveCopiesAndOrder()
    {
        Ingredient[] input = requirements();
        String[] steps = { "Rinse", "Cook" };
        Recipe recipe = create(TestFixtures.id(1), "Rice",
            input, steps, 0, 20, Effort.EASY, "", "");
        input[0].setQuantity(99);
        input[1] = null;
        steps[0] = "Changed";
        Ingredient[] result = recipe.getRequiredIngredients();
        assertEquals("Rice", result[0].getName());
        assertEquals("Salt", result[1].getName());
        assertEquals(2.0, result[0].getQuantity(), 0.0);
        result[0].setQuantity(88);
        result[1] = null;
        String[] returnedSteps = recipe.getInstructions();
        returnedSteps[0] = "Also changed";
        assertEquals(2.0,
            recipe.getRequiredIngredients()[0].getQuantity(), 0.0);
        assertEquals("Salt", recipe.getRequiredIngredients()[1].getName());
        assertEquals("Rinse", recipe.getInstructions()[0]);
    }


    /** Rejects missing, empty, or duplicate-ID requirements. */
    public void testInvalidRequirements()
    {
        TestFixtures.invalid(() -> withIngredients(null));
        TestFixtures.invalid(() -> withIngredients(new Ingredient[0]));
        TestFixtures.invalid(
            () -> withIngredients(new Ingredient[] { null }));
        Ingredient rice = requirements()[0];
        TestFixtures.invalid(() -> withIngredients(
            new Ingredient[] { rice, new Ingredient(rice) }));
        Recipe repeatedName = withIngredients(new Ingredient[] {
            rice, TestFixtures.ingredient(3, "Rice", 1, Unit.CUP) });
        assertEquals(2, repeatedName.getRequiredIngredients().length);
    }


    /** Invalid metadata is rejected with the agreed model exception. */
    public void testInvalidMetadata()
    {
        for (String id : new String[] { null, "", "invalid", "1-1-1-1-1" })
        {
            TestFixtures.invalid(() -> create(id, "Rice", requirements(),
                new String[0], 0, 1, Effort.EASY, "", ""));
        }
        for (String name : new String[] { null, "", " ", "Rice\n" })
        {
            TestFixtures.invalid(() -> create(TestFixtures.id(1), name,
                requirements(), new String[0], 0, 1, Effort.EASY, "", ""));
        }
        TestFixtures.invalid(() -> create(TestFixtures.id(1), "Rice",
            requirements(), null, 0, 1, Effort.EASY, "", ""));
        for (String step : new String[] { null, "", " " })
        {
            TestFixtures.invalid(() -> create(TestFixtures.id(1), "Rice",
                requirements(), new String[] { step },
                0, 1, Effort.EASY, "", ""));
        }
        TestFixtures.invalid(() -> create(TestFixtures.id(1), "Rice",
            requirements(), new String[0], -1, 1, Effort.EASY, "", ""));
        TestFixtures.invalid(() -> create(TestFixtures.id(1), "Rice",
            requirements(), new String[0], 0, -1, Effort.EASY, "", ""));
        TestFixtures.invalid(() -> create(TestFixtures.id(1), "Rice",
            requirements(), new String[0], 0, 1, null, "", ""));
        TestFixtures.invalid(() -> create(TestFixtures.id(1), "Rice",
            requirements(), new String[0], 0, 1, Effort.EASY, null, ""));
        TestFixtures.invalid(() -> create(TestFixtures.id(1), "Rice",
            requirements(), new String[0], 0, 1, Effort.EASY, "", null));
    }


    /** Copying retains identifiers and independent nested snapshots. */
    public void testCopyConstructor()
    {
        Recipe original = create(TestFixtures.id(1), "Rice", requirements(),
            new String[] { "Cook" }, 2, 5, Effort.HARD, "source", "external");
        Recipe copy = new Recipe(original);
        assertNotSame(original, copy);
        assertEquals(original, copy);
        assertEquals(original.getName(), copy.getName());
        assertEquals(original.getPrepTimeMinutes().intValue(),
            copy.getPrepTimeMinutes().intValue());
        assertEquals(original.getCookTimeMinutes().intValue(),
            copy.getCookTimeMinutes().intValue());
        assertEquals(original.getEffort(), copy.getEffort());
        assertEquals("source", copy.getSourceUrl());
        assertEquals("external", copy.getExternalRecipeId());
        copy.getRequiredIngredients()[0].setQuantity(50);
        copy.getInstructions()[0] = "Changed";
        assertEquals(2.0,
            original.getRequiredIngredients()[0].getQuantity(), 0.0);
        assertEquals("Cook", original.getInstructions()[0]);
        TestFixtures.invalid(() -> new Recipe((Recipe)null));
    }


    /** Matching titles or provider IDs alone do not establish identity. */
    public void testEqualityAndHashCode()
    {
        Recipe first = TestFixtures.recipe(1, "Rice", 10, Effort.EASY);
        Recipe same = TestFixtures.recipe(1, "Other", null, Effort.UNKNOWN);
        Recipe other = TestFixtures.recipe(2, "Rice", 10, Effort.EASY);
        assertTrue(first.equals(first));
        assertTrue(first.equals(same));
        assertTrue(same.equals(first));
        assertEquals(first.hashCode(), same.hashCode());
        assertFalse(first.equals(other));
        assertFalse(first.equals(null));
        Object unrelated = "Rice";
        assertFalse(first.equals(unrelated));
    }


    private Ingredient[] requirements()
    {
        return new Ingredient[] {
            TestFixtures.ingredient(1, "Rice", 2, Unit.CUP),
            TestFixtures.ingredient(2, "Salt", 1, Unit.TEASPOON) };
    }


    private Recipe withIngredients(Ingredient[] ingredients)
    {
        return create(TestFixtures.id(1), "Rice", ingredients,
            new String[] { "Cook" }, 0, 20, Effort.EASY, "", "");
    }


    private Recipe create(
        String id,
        String name,
        Ingredient[] ingredients,
        String[] instructions,
        Integer prep,
        Integer cook,
        Effort effort,
        String source,
        String external)
    {
        return new Recipe(id, name, ingredients, instructions,
            prep, cook, effort, source, external);
    }
}
