import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import student.TestCase;

/**
 * Tests name normalization, alias ownership, and dimensional conversions.
 *
 * @author PantryPal team (GenAI-assisted tests)
 * @version 2026.10.14
 */
public class IngredientMatcherTest extends TestCase
{
    /** Names normalize case, whitespace, and the documented default aliases. */
    public void testDefaultNormalizationAndMatching()
    {
        IngredientMatcher matcher = new IngredientMatcher();
        assertEquals("green onion", matcher.normalizeName(" Scallion "));
        assertEquals("green onion", matcher.normalizeName("SCALLIONS"));
        assertEquals("green onion", matcher.normalizeName("Green   onions"));
        assertEquals("brown rice", matcher.normalizeName(" Brown\tRice "));
        assertTrue(matcher.namesMatch("Scallion", "green onion"));
        assertTrue(matcher.namesMatch("Rice", " RICE "));
        assertFalse(matcher.namesMatch("Flour", "Sugar"));
        assertFalse(matcher.namesMatch("chicken", "cooked chicken"));
        for (String name : new String[] { null, "", " " })
        {
            TestFixtures.invalid(() -> matcher.normalizeName(name));
            TestFixtures.invalid(() -> matcher.namesMatch(name, "Rice"));
            TestFixtures.invalid(() -> matcher.namesMatch("Rice", name));
        }
    }


    /** Normalization behaves consistently under the operating system locale. */
    public void testLocaleIndependentNormalization()
    {
        Locale original = Locale.getDefault();
        try
        {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertEquals("rice", new IngredientMatcher().normalizeName("RICE"));
        }
        finally
        {
            Locale.setDefault(original);
        }
    }


    /** Caller changes do not alter the copied alias map. */
    public void testCustomAliasesAndCopying()
    {
        Map<String, String> aliases = new HashMap<String, String>();
        aliases.put(" Garbanzo ", " CHICKPEA ");
        aliases.put("chickpea", "chickpeas");
        IngredientMatcher matcher = new IngredientMatcher(aliases);
        aliases.put("garbanzo", "beans");
        aliases.clear();
        assertEquals("chickpeas", matcher.normalizeName("garbanzo"));
        assertTrue(matcher.namesMatch("garbanzo", "CHICKPEAS"));
        assertEquals("rice", new IngredientMatcher(
            new HashMap<String, String>()).normalizeName("RICE"));
        Map<String, String> same = new HashMap<String, String>();
        same.put("rice", "rice");
        assertEquals("rice", new IngredientMatcher(same).normalizeName("Rice"));
    }


    /** Invalid entries, conflicts, and alias cycles fail at construction. */
    public void testInvalidAliasMaps()
    {
        TestFixtures.invalid(() -> new IngredientMatcher(null));
        for (String invalid : new String[] { null, "", " " })
        {
            Map<String, String> badKey = new HashMap<String, String>();
            badKey.put(invalid, "rice");
            TestFixtures.invalid(() -> new IngredientMatcher(badKey));
            Map<String, String> badValue = new HashMap<String, String>();
            badValue.put("rice", invalid);
            TestFixtures.invalid(() -> new IngredientMatcher(badValue));
        }
        Map<String, String> conflict = new HashMap<String, String>();
        conflict.put("Rice", "grain");
        conflict.put("rice", "flour");
        TestFixtures.invalid(() -> new IngredientMatcher(conflict));
        Map<String, String> cycle = new HashMap<String, String>();
        cycle.put("first", "second");
        cycle.put("second", "first");
        TestFixtures.invalid(() -> new IngredientMatcher(cycle));
    }


    /** Every enum value belongs to exactly one supported measurement group. */
    public void testCompatibilityGroups()
    {
        IngredientMatcher matcher = new IngredientMatcher();
        Unit[][] groups = {
            { Unit.GRAM, Unit.KILOGRAM, Unit.OUNCE, Unit.POUND },
            { Unit.MILLILITER, Unit.LITER, Unit.TEASPOON,
                Unit.TABLESPOON, Unit.CUP, Unit.FLUID_OUNCE },
            { Unit.COUNT } };
        for (int i = 0; i < groups.length; i++)
        {
            for (int j = 0; j < groups.length; j++)
            {
                for (Unit first : groups[i])
                {
                    for (Unit second : groups[j])
                    {
                        assertEquals(i == j,
                            matcher.areUnitsCompatible(first, second));
                    }
                }
            }
        }
        TestFixtures.invalid(
            () -> matcher.areUnitsCompatible(null, Unit.CUP));
        TestFixtures.invalid(
            () -> matcher.areUnitsCompatible(Unit.CUP, null));
    }


    /** Known conversions use metric mass and US customary volume. */
    public void testConversions()
    {
        IngredientMatcher matcher = new IngredientMatcher();
        assertEquals(1000.0,
            matcher.convertQuantity(1, Unit.KILOGRAM, Unit.GRAM), 1e-10);
        assertEquals(16.0,
            matcher.convertQuantity(1, Unit.POUND, Unit.OUNCE), 1e-10);
        assertEquals(453.59237,
            matcher.convertQuantity(1, Unit.POUND, Unit.GRAM), 1e-10);
        assertEquals(28.349523125,
            matcher.convertQuantity(1, Unit.OUNCE, Unit.GRAM), 1e-10);
        assertEquals(1000.0,
            matcher.convertQuantity(1, Unit.LITER, Unit.MILLILITER), 1e-10);
        assertEquals(16.0,
            matcher.convertQuantity(1, Unit.CUP, Unit.TABLESPOON), 1e-10);
        assertEquals(3.0,
            matcher.convertQuantity(1, Unit.TABLESPOON, Unit.TEASPOON),
            1e-10);
        assertEquals(8.0,
            matcher.convertQuantity(1, Unit.CUP, Unit.FLUID_OUNCE), 1e-10);
        assertEquals(236.5882365,
            matcher.convertQuantity(1, Unit.CUP, Unit.MILLILITER), 1e-10);
        for (Unit unit : Unit.values())
        {
            assertEquals(2.0,
                matcher.convertQuantity(2, unit, unit), 0.0);
            assertEquals(0.0,
                matcher.convertQuantity(0, unit, unit), 0.0);
        }
    }


    /** Conversion rejects incompatible dimensions and invalid quantities. */
    public void testInvalidConversions()
    {
        IngredientMatcher matcher = new IngredientMatcher();
        TestFixtures.invalid(
            () -> matcher.convertQuantity(1, Unit.CUP, Unit.GRAM));
        TestFixtures.invalid(
            () -> matcher.convertQuantity(1, Unit.COUNT, Unit.GRAM));
        TestFixtures.invalid(
            () -> matcher.convertQuantity(1, null, Unit.GRAM));
        TestFixtures.invalid(
            () -> matcher.convertQuantity(1, Unit.GRAM, null));
        for (double value : new double[] {
            -1, Double.NaN, Double.POSITIVE_INFINITY,
            Double.NEGATIVE_INFINITY })
        {
            TestFixtures.invalid(
                () -> matcher.convertQuantity(value, Unit.GRAM, Unit.GRAM));
        }
        TestFixtures.invalid(() -> matcher.convertQuantity(
            Double.MAX_VALUE, Unit.KILOGRAM, Unit.GRAM));
        TestFixtures.invalid(() -> matcher.convertQuantity(
            Double.MIN_VALUE, Unit.GRAM, Unit.KILOGRAM));
        assertEquals(Double.MIN_VALUE, matcher.convertQuantity(
            Double.MIN_VALUE, Unit.GRAM, Unit.GRAM), 0.0);
    }
}
