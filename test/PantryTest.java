import student.TestCase;

/**
 * Tests pantry ownership, queries, mutations, and atomic stock deduction.
 *
 * @author PantryPal team (GenAI-assisted tests)
 * @version 2026.10.14
 */
public class PantryTest extends TestCase
{
    /** Empty collections handle queries and missing removals safely. */
    public void testEmptyPantry()
    {
        Pantry pantry = new Pantry();
        assertTrue(pantry.isEmpty());
        assertEquals(0, pantry.size());
        assertEquals(0, pantry.getAllIngredients().length);
        assertEquals(0, pantry.searchIngredients("Rice").length);
        assertNull(pantry.findIngredientById(TestFixtures.id(1)));
        assertFalse(pantry.removeIngredient(TestFixtures.id(1)));
        assertFalse(pantry.updateIngredient(
            TestFixtures.id(1), "Rice", 1, Unit.CUP));
        assertTrue(new Pantry(new Ingredient[0]).isEmpty());
    }


    /** Construction copies entries, preserves IDs, and rejects bad lists. */
    public void testInitialEntries()
    {
        Ingredient rice = rice(1, 2);
        Ingredient salt = TestFixtures.ingredient(2, "Salt", 1, Unit.GRAM);
        Ingredient[] input = { rice, salt };
        Pantry pantry = new Pantry(input);
        rice.setQuantity(99);
        input[1] = null;
        assertEquals(2, pantry.size());
        assertFalse(pantry.isEmpty());
        assertEquals(2.0, quantity(pantry, 1), 0.0);
        assertEquals("Salt",
            pantry.findIngredientById(TestFixtures.id(2)).getName());
        TestFixtures.invalid(() -> new Pantry(null));
        TestFixtures.invalid(
            () -> new Pantry(new Ingredient[] { rice, null }));
        TestFixtures.invalid(() -> new Pantry(
            new Ingredient[] { rice, new Ingredient(rice) }));
    }


    /** Duplicate names are allowed; duplicate IDs never overwrite stock. */
    public void testAddAndDuplicateIdentity()
    {
        Pantry pantry = new Pantry();
        Ingredient rice = rice(1, 2);
        assertTrue(pantry.addIngredient(rice));
        rice.setQuantity(90);
        assertEquals(2.0, quantity(pantry, 1), 0.0);
        assertTrue(pantry.addIngredient(rice(2, 3)));
        assertFalse(pantry.addIngredient(
            TestFixtures.ingredient(1, "Flour", 20, Unit.GRAM)));
        assertEquals(2, pantry.size());
        assertEquals(2.0, quantity(pantry, 1), 0.0);
        TestFixtures.invalid(() -> pantry.addIngredient(null));
        assertEquals(2, pantry.size());
    }


    /** Every externally returned ingredient is an independent copy. */
    public void testSnapshotOwnership()
    {
        Pantry pantry = new Pantry(new Ingredient[] { rice(1, 2) });
        Ingredient[] all = pantry.getAllIngredients();
        all[0].setQuantity(90);
        all[0] = null;
        pantry.findIngredientById(TestFixtures.id(1)).setQuantity(80);
        pantry.searchIngredients("Rice")[0].setQuantity(70);
        assertEquals(2.0, quantity(pantry, 1), 0.0);
        assertEquals(1, pantry.size());
    }


    /** Searching is case-insensitive and handles empty/missing queries. */
    public void testSearch()
    {
        Pantry pantry = new Pantry(new Ingredient[] {
            rice(1, 2),
            TestFixtures.ingredient(2, "Brown Rice", 3, Unit.CUP),
            TestFixtures.ingredient(3, "Salt", 1, Unit.GRAM) });
        assertEquals(2, pantry.searchIngredients(" rIcE ").length);
        assertEquals(3, pantry.searchIngredients(" ").length);
        assertEquals(0, pantry.searchIngredients("Sugar").length);
        TestFixtures.invalid(() -> pantry.searchIngredients(null));
    }


    /** Lookup and removal use ID rather than a shared display name. */
    public void testFindAndRemove()
    {
        Pantry pantry = new Pantry(new Ingredient[] { rice(1, 2), rice(2, 3) });
        assertEquals(3.0, quantity(pantry, 2), 0.0);
        assertNull(pantry.findIngredientById("unknown"));
        assertTrue(pantry.removeIngredient(TestFixtures.id(1)));
        assertEquals(1, pantry.size());
        assertFalse(pantry.removeIngredient(TestFixtures.id(1)));
        assertEquals(3.0, quantity(pantry, 2), 0.0);
        assertTrue(pantry.removeIngredient(TestFixtures.id(2)));
        assertTrue(pantry.isEmpty());
        for (String id : new String[] { null, "", " " })
        {
            TestFixtures.invalid(() -> pantry.findIngredientById(id));
            TestFixtures.invalid(() -> pantry.removeIngredient(id));
        }
    }


    /** Updates change all details or leave the original untouched. */
    public void testUpdateAndBadInput()
    {
        Pantry pantry = new Pantry(new Ingredient[] { rice(1, 2) });
        assertTrue(pantry.updateIngredient(
            TestFixtures.id(1), "Flour", 5, Unit.GRAM));
        Ingredient updated = pantry.findIngredientById(TestFixtures.id(1));
        assertEquals("Flour", updated.getName());
        assertEquals(5.0, updated.getQuantity(), 0.0);
        assertEquals(Unit.GRAM, updated.getUnit());
        TestFixtures.invalid(() -> pantry.updateIngredient(
            TestFixtures.id(1), "Salt", 0, Unit.CUP));
        TestFixtures.invalid(() -> pantry.updateIngredient(
            TestFixtures.id(1), null, 1, Unit.CUP));
        TestFixtures.invalid(() -> pantry.updateIngredient(
            TestFixtures.id(1), "Salt", 1, null));
        TestFixtures.invalid(
            () -> pantry.updateIngredient(null, "Rice", 1, Unit.CUP));
        assertEquals("Flour",
            pantry.findIngredientById(TestFixtures.id(1)).getName());
        assertEquals(5.0, quantity(pantry, 1), 0.0);
        assertFalse(pantry.updateIngredient("missing", "Rice", 1, Unit.CUP));
    }


    /** A requirement can consume multiple matching packages with aliases. */
    public void testDeductAcrossEntriesAndUnits()
    {
        Pantry pantry = new Pantry(new Ingredient[] {
            TestFixtures.ingredient(1, "Scallions", 1, Unit.CUP),
            TestFixtures.ingredient(2, "Green onions", 8, Unit.TABLESPOON),
            TestFixtures.ingredient(3, "Salt", 5, Unit.GRAM) });
        Ingredient requirement = TestFixtures.ingredient(
            4, "green onion", 20, Unit.TABLESPOON);
        assertTrue(pantry.deductIngredients(
            new Ingredient[] { requirement }, new IngredientMatcher()));
        assertNull(pantry.findIngredientById(TestFixtures.id(1)));
        assertEquals(4.0, quantity(pantry, 2), 1e-12);
        assertEquals(5.0, quantity(pantry, 3), 0.0);
        assertEquals(20.0, requirement.getQuantity(), 0.0);
    }


    /** Repeated requirements share remaining stock instead of reusing it. */
    public void testRepeatedRequirements()
    {
        Pantry pantry = new Pantry(new Ingredient[] { rice(1, 3) });
        Ingredient[] enough = { rice(2, 1), rice(3, 2) };
        assertTrue(pantry.deductIngredients(enough, new IngredientMatcher()));
        assertTrue(pantry.isEmpty());
        assertEquals(0, pantry.size());

        pantry.addIngredient(rice(1, 3));
        Ingredient[] tooMuch = { rice(2, 2), rice(3, 2) };
        assertFalse(pantry.deductIngredients(
            tooMuch, new IngredientMatcher()));
        assertEquals(3.0, quantity(pantry, 1), 0.0);
    }


    /** A later shortage rolls back earlier allocations and depleted entries. */
    public void testFailedDeductionLeavesStockUnchanged()
    {
        Pantry pantry = new Pantry(new Ingredient[] { rice(1, 2) });
        Ingredient[] requests = {
            rice(2, 2),
            TestFixtures.ingredient(3, "Salt", 1, Unit.GRAM) };
        assertFalse(pantry.deductIngredients(
            requests, new IngredientMatcher()));
        assertEquals(2.0, quantity(pantry, 1), 0.0);
        assertEquals(1, pantry.size());
        assertFalse(pantry.deductIngredients(new Ingredient[] {
            TestFixtures.ingredient(4, "Rice", 1, Unit.GRAM) },
            new IngredientMatcher()));
        assertEquals(2.0, quantity(pantry, 1), 0.0);
        assertFalse(new Pantry().deductIngredients(
            new Ingredient[] { rice(2, 1) }, new IngredientMatcher()));
    }


    /** Null inputs are rejected before any allocation changes live stock. */
    public void testDeductionValidationAndEmptyRequirements()
    {
        Pantry pantry = new Pantry(new Ingredient[] { rice(1, 2) });
        IngredientMatcher matcher = new IngredientMatcher();
        TestFixtures.invalid(() -> pantry.deductIngredients(null, matcher));
        TestFixtures.invalid(() -> pantry.deductIngredients(
            new Ingredient[] { rice(2, 1) }, null));
        TestFixtures.invalid(() -> pantry.deductIngredients(
            new Ingredient[] { rice(2, 1), null }, matcher));
        assertEquals(2.0, quantity(pantry, 1), 0.0);
        assertTrue(pantry.deductIngredients(new Ingredient[0], matcher));
        assertEquals(2.0, quantity(pantry, 1), 0.0);
    }


    /** Conversion supports partial mass deductions without changing units. */
    public void testMassDeduction()
    {
        Pantry pantry = new Pantry(new Ingredient[] {
            TestFixtures.ingredient(1, "Flour", 1, Unit.KILOGRAM) });
        assertTrue(pantry.deductIngredients(new Ingredient[] {
            TestFixtures.ingredient(2, "Flour", 250, Unit.GRAM) },
            new IngredientMatcher()));
        assertEquals(0.75, quantity(pantry, 1), 1e-12);
        assertEquals(Unit.KILOGRAM,
            pantry.findIngredientById(TestFixtures.id(1)).getUnit());
    }


    /** Floating-point rounding must not create phantom leftover entries. */
    public void testRoundingAndTinyAmounts()
    {
        Pantry pantry = new Pantry(new Ingredient[] {
            rice(1, 0.1), rice(2, 0.2) });
        assertTrue(pantry.deductIngredients(
            new Ingredient[] { rice(3, 0.3) }, new IngredientMatcher()));
        assertTrue(pantry.isEmpty());

        pantry.addIngredient(rice(1, 1e-200));
        assertFalse(pantry.deductIngredients(
            new Ingredient[] { rice(2, 2e-200) }, new IngredientMatcher()));
        assertEquals(1e-200, quantity(pantry, 1), 0.0);
        assertTrue(pantry.deductIngredients(
            new Ingredient[] { rice(2, 5e-201) }, new IngredientMatcher()));
        assertEquals(5e-201, quantity(pantry, 1), 1e-215);
    }


    /** A shortage among subnormal values is not rounding tolerance. */
    public void testSubnormalShortage()
    {
        Pantry pantry = new Pantry(new Ingredient[] {
            rice(1, Double.MIN_VALUE) });
        assertFalse(pantry.deductIngredients(new Ingredient[] {
            rice(2, 5 * Double.MIN_VALUE) }, new IngredientMatcher()));
        assertEquals(Double.MIN_VALUE, quantity(pantry, 1), 0.0);
    }


    private Ingredient rice(int id, double amount)
    {
        return TestFixtures.ingredient(id, "Rice", amount, Unit.CUP);
    }


    private double quantity(Pantry pantry, int id)
    {
        return pantry.findIngredientById(TestFixtures.id(id)).getQuantity();
    }
}
