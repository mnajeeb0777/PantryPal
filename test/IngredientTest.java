import student.TestCase;

/**
 * Tests ingredient validation, atomic mutation, copying, and identity.
 *
 * @author PantryPal team (GenAI-assisted tests)
 * @version 2026.10.14
 */
public class IngredientTest extends TestCase
{
    /** Getters expose valid constructor values, including boundaries. */
    public void testConstructorAndGetters()
    {
        Ingredient item = TestFixtures.ingredient(1, "Rice", 2, Unit.CUP);
        assertEquals(TestFixtures.id(1), item.getId());
        assertEquals("Rice", item.getName());
        assertEquals(2.0, item.getQuantity(), 0.0);
        assertEquals(Unit.CUP, item.getUnit());
        Ingredient upper = TestFixtures.ingredient(
            2, "a".repeat(100), 1000000, Unit.GRAM);
        assertEquals(100, upper.getName().length());
        assertEquals(1000000.0, upper.getQuantity(), 0.0);
        assertEquals(TestFixtures.id(2), upper.getId());
        assertEquals(Unit.GRAM, upper.getUnit());
        Ingredient tiny = TestFixtures.ingredient(
            3, "Salt", Double.MIN_VALUE, Unit.GRAM);
        assertEquals(Double.MIN_VALUE, tiny.getQuantity(), 0.0);
        assertEquals("Chef's salt (fine)", TestFixtures.ingredient(
            4, "Chef's salt (fine)", 1, Unit.GRAM).getName());
    }


    /** Rejects malformed IDs and all invalid detail categories. */
    public void testConstructorRejectsBadInput()
    {
        for (String id : new String[] { null, "", "bad", "1-1-1-1-1" })
        {
            TestFixtures.invalid(
                () -> new Ingredient(id, "Rice", 1, Unit.CUP));
        }
        for (String name : new String[] {
            null, "", " ", "123", "a\nb", "x".repeat(101) })
        {
            TestFixtures.invalid(
                () -> TestFixtures.ingredient(1, name, 1, Unit.CUP));
        }
        for (double amount : new double[] {
            0, -1, Double.NaN, Double.POSITIVE_INFINITY,
            Double.NEGATIVE_INFINITY, 1000001 })
        {
            TestFixtures.invalid(
                () -> TestFixtures.ingredient(1, "Rice", amount, Unit.CUP));
        }
        TestFixtures.invalid(
            () -> TestFixtures.ingredient(1, "Rice", 1, null));
    }


    /** Copies preserve identity but do not share mutable details. */
    public void testCopyConstructor()
    {
        Ingredient source = TestFixtures.ingredient(1, "Rice", 2, Unit.CUP);
        Ingredient copy = new Ingredient(source);
        assertNotSame(source, copy);
        assertEquals(source, copy);
        copy.updateDetails("Flour", 3, Unit.GRAM);
        assertEquals("Rice", source.getName());
        assertEquals(2.0, source.getQuantity(), 0.0);
        assertEquals(Unit.CUP, source.getUnit());
        TestFixtures.invalid(() -> new Ingredient((Ingredient)null));
    }


    /** A valid update changes all details without changing identity. */
    public void testUpdateDetails()
    {
        Ingredient item = TestFixtures.ingredient(1, "Rice", 2, Unit.CUP);
        item.updateDetails("Flour", 4, Unit.GRAM);
        assertEquals(TestFixtures.id(1), item.getId());
        assertEquals("Flour", item.getName());
        assertEquals(4.0, item.getQuantity(), 0.0);
        assertEquals(Unit.GRAM, item.getUnit());
    }


    /** Failed updates leave every field untouched. */
    public void testUpdateDetailsIsAtomic()
    {
        Ingredient item = TestFixtures.ingredient(1, "Rice", 2, Unit.CUP);
        TestFixtures.invalid(() -> item.updateDetails("Flour", -1, Unit.GRAM));
        TestFixtures.invalid(() -> item.updateDetails(null, 4, Unit.GRAM));
        TestFixtures.invalid(() -> item.updateDetails("Flour", 4, null));
        TestFixtures.invalid(
            () -> item.updateDetails("x".repeat(101), 4, Unit.GRAM));
        TestFixtures.invalid(
            () -> item.updateDetails("Flour", Double.NaN, Unit.GRAM));
        assertEquals(TestFixtures.id(1), item.getId());
        assertEquals("Rice", item.getName());
        assertEquals(2.0, item.getQuantity(), 0.0);
        assertEquals(Unit.CUP, item.getUnit());
    }


    /** Quantity changes obey the same limits as construction. */
    public void testSetQuantityAndInvalidUpdates()
    {
        Ingredient item = TestFixtures.ingredient(1, "Rice", 2, Unit.CUP);
        item.setQuantity(1000000);
        assertEquals(1000000.0, item.getQuantity(), 0.0);
        for (double amount : new double[] {
            0, -1, Double.NaN, Double.POSITIVE_INFINITY,
            Double.NEGATIVE_INFINITY, 1000001 })
        {
            TestFixtures.invalid(() -> item.setQuantity(amount));
            assertEquals(1000000.0, item.getQuantity(), 0.0);
        }
        assertEquals("Rice", item.getName());
        assertEquals(Unit.CUP, item.getUnit());
    }


    /** Equality and hash codes depend on ID, not mutable values. */
    public void testIdentityAndHashCode()
    {
        Ingredient first = TestFixtures.ingredient(1, "Rice", 2, Unit.CUP);
        Ingredient same = TestFixtures.ingredient(1, "Flour", 9, Unit.GRAM);
        Ingredient different = TestFixtures.ingredient(
            2, "Rice", 2, Unit.CUP);
        assertTrue(first.equals(first));
        assertTrue(first.equals(same));
        assertTrue(same.equals(first));
        assertEquals(first.hashCode(), same.hashCode());
        int hash = first.hashCode();
        first.setQuantity(3);
        assertEquals(hash, first.hashCode());
        assertFalse(first.equals(different));
        assertFalse(first.equals(null));
        Object unrelated = "Rice";
        assertFalse(first.equals(unrelated));
    }
}
