import student.TestCase;

/**
 * Tests immutable requirement snapshots and quantity comparison boundaries.
 *
 * @author PantryPal team (GenAI-assisted tests)
 * @version 2026.10.14
 */
public class IngredientMatchTest extends TestCase
{
    /** A known amount yields a shortage expressed in the requirement's unit. */
    public void testKnownShortage()
    {
        Ingredient required = required(5);
        IngredientMatch match = new IngredientMatch(required, 3.0);
        assertEquals(3.0, match.getAvailableQuantity().doubleValue(), 0.0);
        assertEquals(2.0, match.getMissingQuantity().doubleValue(), 0.0);
        assertTrue(match.canCompareQuantities());
        assertFalse(match.isSufficient());
        Ingredient snapshot = match.getRequiredIngredient();
        assertEquals(required.getId(), snapshot.getId());
        assertEquals("Rice", snapshot.getName());
        assertEquals(5.0, snapshot.getQuantity(), 0.0);
        assertEquals(Unit.CUP, snapshot.getUnit());
    }


    /** Both input mutation and returned-snapshot mutation are isolated. */
    public void testRequirementOwnership()
    {
        Ingredient source = required(5);
        IngredientMatch match = new IngredientMatch(source, 3.0);
        source.updateDetails("Flour", 90, Unit.GRAM);
        Ingredient returned = match.getRequiredIngredient();
        returned.updateDetails("Salt", 80, Unit.COUNT);
        Ingredient unchanged = match.getRequiredIngredient();
        assertNotSame(source, unchanged);
        assertNotSame(returned, unchanged);
        assertEquals(TestFixtures.id(1), unchanged.getId());
        assertEquals("Rice", unchanged.getName());
        assertEquals(5.0, unchanged.getQuantity(), 0.0);
        assertEquals(Unit.CUP, unchanged.getUnit());
        assertEquals(2.0, match.getMissingQuantity().doubleValue(), 0.0);
    }


    /** An unknown amount remains distinct from zero stock. */
    public void testUncomparableQuantity()
    {
        IngredientMatch match = new IngredientMatch(required(5), null);
        assertNull(match.getAvailableQuantity());
        assertNull(match.getMissingQuantity());
        assertFalse(match.canCompareQuantities());
        assertFalse(match.isSufficient());
        assertEquals(5.0, match.getRequiredIngredient().getQuantity(), 0.0);
    }


    /** Zero stock is comparable but cannot satisfy a positive requirement. */
    public void testZeroAvailable()
    {
        IngredientMatch match = new IngredientMatch(required(5), 0.0);
        assertEquals(0.0, match.getAvailableQuantity().doubleValue(), 0.0);
        assertEquals(5.0, match.getMissingQuantity().doubleValue(), 0.0);
        assertTrue(match.canCompareQuantities());
        assertFalse(match.isSufficient());
    }


    /** Exact coverage and excess stock never produce a negative shortage. */
    public void testExactAndExcessAvailability()
    {
        for (double amount : new double[] { 5, 8, 2000000, Double.MAX_VALUE })
        {
            IngredientMatch match = new IngredientMatch(required(5), amount);
            assertEquals(amount,
                match.getAvailableQuantity().doubleValue(), 0.0);
            assertEquals(0.0, match.getMissingQuantity().doubleValue(), 0.0);
            assertTrue(match.canCompareQuantities());
            assertTrue(match.isSufficient());
        }
    }


    /** Invalid requirements and invalid known quantities fail construction. */
    public void testInvalidInput()
    {
        TestFixtures.invalid(() -> new IngredientMatch(null, 1.0));
        TestFixtures.invalid(() -> new IngredientMatch(null, null));
        for (double amount : new double[] {
            -1, -Double.MIN_VALUE, Double.NaN,
            Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY })
        {
            TestFixtures.invalid(
                () -> new IngredientMatch(required(5), amount));
        }
    }


    /** A conversion-scale rounding residual is reported as zero shortage. */
    public void testRoundoffTolerance()
    {
        IngredientMatch match = new IngredientMatch(required(0.1 + 0.2), 0.3);
        assertTrue(match.isSufficient());
        assertEquals(0.0, match.getMissingQuantity().doubleValue(), 0.0);
        double limit = 4 * Math.ulp(1.0);
        IngredientMatch boundary = new IngredientMatch(required(1), 1 - limit);
        assertTrue(boundary.isSufficient());
        assertEquals(0.0, boundary.getMissingQuantity().doubleValue(), 0.0);
    }


    /** A shortage outside the four-ULP boundary must not disappear. */
    public void testShortageBeyondTolerance()
    {
        double available = 1 - 5 * Math.ulp(1.0);
        IngredientMatch match = new IngredientMatch(required(1), available);
        assertFalse(match.isSufficient());
        assertEquals(1 - available,
            match.getMissingQuantity().doubleValue(), 0.0);
    }


    /** Very small quantities remain meaningful, including subnormal values. */
    public void testTinyAndSubnormalShortages()
    {
        IngredientMatch tiny = new IngredientMatch(required(2e-200), 1e-200);
        assertFalse(tiny.isSufficient());
        assertEquals(1e-200, tiny.getMissingQuantity().doubleValue(), 0.0);

        IngredientMatch subnormal = new IngredientMatch(
            required(5 * Double.MIN_VALUE), Double.MIN_VALUE);
        assertFalse(subnormal.isSufficient());
        assertEquals(4 * Double.MIN_VALUE,
            subnormal.getMissingQuantity().doubleValue(), 0.0);
        IngredientMatch none = new IngredientMatch(
            required(Double.MIN_VALUE), 0.0);
        assertFalse(none.isSufficient());
        assertEquals(Double.MIN_VALUE,
            none.getMissingQuantity().doubleValue(), 0.0);
    }


    /** Single-requirement comparisons use the pantry's rounding decisions. */
    public void testPantryToleranceParity()
    {
        double[][] cases = {
            { 5, 3 }, { 5, 5 }, { 5, 8 }, { 5, 0 },
            { 0.1 + 0.2, 0.3 },
            { 1, 1 - 4 * Math.ulp(1.0) },
            { 1, 1 - 5 * Math.ulp(1.0) },
            { 2e-200, 1e-200 },
            { 5 * Double.MIN_VALUE, Double.MIN_VALUE } };
        for (double[] values : cases)
        {
            Ingredient requirement = required(values[0]);
            IngredientMatch match = new IngredientMatch(
                requirement, values[1]);
            Pantry pantry = new Pantry();
            if (values[1] > 0)
            {
                pantry.addIngredient(TestFixtures.ingredient(
                    2, "Rice", values[1], Unit.CUP));
            }
            assertEquals(match.isSufficient(), pantry.deductIngredients(
                new Ingredient[] { requirement }, new IngredientMatcher()));
        }
    }


    private Ingredient required(double amount)
    {
        return TestFixtures.ingredient(1, "Rice", amount, Unit.CUP);
    }
}
