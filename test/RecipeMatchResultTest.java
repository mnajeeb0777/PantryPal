import student.TestCase;

/**
 * Tests recipe comparison aggregation, defensive copies, and validation.
 *
 * @author PantryPal team (GenAI-assisted tests)
 * @version 2026.10.14
 */
public class RecipeMatchResultTest extends TestCase
{
    /** The result preserves recipe identity and supplied requirement order. */
    public void testIdentityAndEntryOrder()
    {
        IngredientMatch first = match(1, 5, 3.0);
        IngredientMatch second = match(2, 2, 2.0);
        RecipeMatchResult result = result(first, second);
        assertEquals(TestFixtures.id(10), result.getRecipeId());
        IngredientMatch[] matches = result.getIngredientMatches();
        assertEquals(2, matches.length);
        assertEquals(TestFixtures.id(1),
            matches[0].getRequiredIngredient().getId());
        assertEquals(TestFixtures.id(2),
            matches[1].getRequiredIngredient().getId());
        assertEquals(3.0, matches[0].getAvailableQuantity().doubleValue(), 0.0);
        assertEquals(2.0, matches[1].getAvailableQuantity().doubleValue(), 0.0);
    }


    /** Input arrays and returned arrays cannot change owned comparisons. */
    public void testDefensiveCopies()
    {
        Ingredient requirement = TestFixtures.ingredient(
            1, "Rice", 5, Unit.CUP);
        IngredientMatch original = new IngredientMatch(requirement, 3.0);
        IngredientMatch[] input = { original, match(2, 1, null) };
        RecipeMatchResult result = new RecipeMatchResult(
            TestFixtures.id(10), input);
        requirement.setQuantity(50);
        input[0] = null;
        IngredientMatch[] returned = result.getIngredientMatches();
        assertNotSame(original, returned[0]);
        returned[0].getRequiredIngredient().setQuantity(40);
        returned[1] = null;
        IngredientMatch[] unchanged = result.getIngredientMatches();
        assertNotSame(returned, unchanged);
        assertEquals(2, unchanged.length);
        assertNotNull(unchanged[1]);
        assertEquals(5.0,
            unchanged[0].getRequiredIngredient().getQuantity(), 0.0);
        assertEquals(2.0,
            unchanged[0].getMissingQuantity().doubleValue(), 0.0);
        assertFalse(result.canCook());
    }


    /** Only known positive shortages appear, retaining ID, name, and unit. */
    public void testMissingIngredients()
    {
        RecipeMatchResult result = result(
            match(1, 5, 3.0), match(2, 4, null),
            match(3, 2, 7.0), match(4, 1, 0.0));
        Ingredient[] missing = result.getMissingIngredients();
        assertEquals(2, missing.length);
        assertEquals(TestFixtures.id(1), missing[0].getId());
        assertEquals("Rice", missing[0].getName());
        assertEquals(Unit.CUP, missing[0].getUnit());
        assertEquals(2.0, missing[0].getQuantity(), 0.0);
        assertEquals(TestFixtures.id(4), missing[1].getId());
        assertEquals(1.0, missing[1].getQuantity(), 0.0);
        missing[0].updateDetails("Flour", 99, Unit.GRAM);
        missing[1] = null;
        assertEquals("Rice", result.getMissingIngredients()[0].getName());
        assertEquals(2.0,
            result.getMissingIngredients()[0].getQuantity(), 0.0);
        assertEquals(5.0, result.getIngredientMatches()[0]
            .getRequiredIngredient().getQuantity(), 0.0);
    }


    /** Unknown requirements retain the full requirement rather than a guess. */
    public void testUncomparableIngredients()
    {
        RecipeMatchResult result = result(
            match(1, 5, 3.0), match(2, 4, null), match(3, 2, null));
        Ingredient[] unknown = result.getUncomparableIngredients();
        assertEquals(2, unknown.length);
        assertEquals(TestFixtures.id(2), unknown[0].getId());
        assertEquals(4.0, unknown[0].getQuantity(), 0.0);
        assertEquals(TestFixtures.id(3), unknown[1].getId());
        unknown[0].setQuantity(80);
        unknown[1] = null;
        assertEquals(4.0,
            result.getUncomparableIngredients()[0].getQuantity(), 0.0);
        assertFalse(result.canCook());
    }


    /** Sufficient results produce no shortage or unknown-quantity entries. */
    public void testAllRequirementsSatisfied()
    {
        RecipeMatchResult result = result(match(1, 5, 5.0), match(2, 2, 8.0));
        assertTrue(result.canCook());
        assertEquals(0, result.getMissingIngredients().length);
        assertEquals(0, result.getUncomparableIngredients().length);
    }


    /** Either a known shortage or an uncomparable entry prevents cooking. */
    public void testCookingFailures()
    {
        RecipeMatchResult shortResult = result(
            match(1, 5, 5.0), match(2, 2, 1.0));
        assertFalse(shortResult.canCook());
        assertEquals(1, shortResult.getMissingIngredients().length);
        assertEquals(0, shortResult.getUncomparableIngredients().length);
        RecipeMatchResult unknownResult = result(match(1, 5, null));
        assertFalse(unknownResult.canCook());
        assertEquals(0, unknownResult.getMissingIngredients().length);
        assertEquals(1, unknownResult.getUncomparableIngredients().length);
    }


    /** Empty arrays, null arrays, and null entries are invalid. */
    public void testInvalidArrays()
    {
        TestFixtures.invalid(
            () -> new RecipeMatchResult(TestFixtures.id(10), null));
        TestFixtures.invalid(() -> result(new IngredientMatch[0]));
        TestFixtures.invalid(() -> result((IngredientMatch)null));
        TestFixtures.invalid(() -> result(match(1, 5, 5.0), null));
    }


    /** Result IDs use the same UUID validation as Recipe. */
    public void testInvalidAndBoundaryRecipeIds()
    {
        for (String id : new String[] {
            null, "", " ", "not-a-uuid", "1-1-1-1-1" })
        {
            TestFixtures.invalid(() -> new RecipeMatchResult(
                id, new IngredientMatch[] { match(1, 5, 5.0) }));
        }
        String uppercase = "AAAAAAAA-BBBB-CCCC-DDDD-EEEEEEEEEEEE";
        RecipeMatchResult result = new RecipeMatchResult(
            uppercase, new IngredientMatch[] { match(1, 5, 5.0) });
        assertEquals(uppercase, result.getRecipeId());
    }


    /** IDs must be unique while repeated ingredient names remain valid. */
    public void testDuplicateRequirementIds()
    {
        IngredientMatch first = match(1, 5, 5.0);
        TestFixtures.invalid(() -> result(first, first));
        TestFixtures.invalid(() -> result(first, match(1, 2, 0.0)));
        RecipeMatchResult repeatedNames = result(first, match(2, 2, 2.0));
        assertEquals(2, repeatedNames.getIngredientMatches().length);
        assertTrue(repeatedNames.canCook());
    }


    /** Roundoff creates neither false shortages nor zero-quantity entries. */
    public void testRoundoffAndRealShortages()
    {
        RecipeMatchResult rounded = result(match(1, 0.1 + 0.2, 0.3));
        assertTrue(rounded.canCook());
        assertEquals(0, rounded.getMissingIngredients().length);
        assertEquals(0, rounded.getUncomparableIngredients().length);

        double available = 1 - 5 * Math.ulp(1.0);
        RecipeMatchResult shortResult = result(match(1, 1, available));
        assertFalse(shortResult.canCook());
        assertEquals(1 - available,
            shortResult.getMissingIngredients()[0].getQuantity(), 0.0);

        RecipeMatchResult tiny = result(match(1, Double.MIN_VALUE, 0.0));
        assertFalse(tiny.canCook());
        assertEquals(Double.MIN_VALUE,
            tiny.getMissingIngredients()[0].getQuantity(), 0.0);
    }


    private IngredientMatch match(int id, double required, Double available)
    {
        return new IngredientMatch(
            TestFixtures.ingredient(id, "Rice", required, Unit.CUP), available);
    }


    private RecipeMatchResult result(IngredientMatch... matches)
    {
        return new RecipeMatchResult(TestFixtures.id(10), matches);
    }
}
