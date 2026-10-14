import java.util.HashMap;
import java.util.Map;
import student.TestCase;

/**
 * Tests recipe allocation, unit handling, ownership, and deduction parity.
 *
 * @author PantryPal team (GenAI-assisted tests)
 * @version 2026.10.14
 */
public class RecipeMatcherTest extends TestCase
{
    /** Rejects missing inputs, null entries, and duplicate stock identities. */
    public void testInvalidInputs()
    {
        RecipeMatcher matcher = matcher();
        Recipe recipe = recipe(rice(1, 2));
        Ingredient stock = rice(20, 3);
        TestFixtures.invalid(() -> new RecipeMatcher(null));
        TestFixtures.invalid(() -> matcher.compareRecipe(null,
            new Ingredient[0]));
        TestFixtures.invalid(() -> matcher.compareRecipe(recipe, null));
        TestFixtures.invalid(() -> matcher.compareRecipe(recipe,
            new Ingredient[] { stock, null }));
        TestFixtures.invalid(() -> matcher.compareRecipe(recipe,
            new Ingredient[] { stock, stock }));
        TestFixtures.invalid(() -> matcher.compareRecipe(recipe,
            new Ingredient[] { stock, new Ingredient(stock) }));
        assertEquals(3.0, stock.getQuantity(), 0.0);
    }


    /** Excess stock is reported as the amount allocated, not all stock. */
    public void testCappedAllocation()
    {
        Recipe recipe = recipe(rice(1, 2), rice(2, 2));
        RecipeMatchResult result = matcher().compareRecipe(recipe,
            new Ingredient[] { rice(20, 5) });
        assertTrue(result.canCook());
        assertEquals(recipe.getId(), result.getRecipeId());
        assertAmounts(result, 2, 2);
        assertEquals(0, result.getMissingIngredients().length);
        assertEquals(0, result.getUncomparableIngredients().length);
    }


    /** Empty or unrelated stock is a known shortage, not unknown. */
    public void testAbsentStock()
    {
        Recipe recipe = recipe(rice(1, 2));
        for (Ingredient[] stock : new Ingredient[][] {
            new Ingredient[0],
            { TestFixtures.ingredient(20, "Flour", 3, Unit.CUP) } })
        {
            RecipeMatchResult result = matcher().compareRecipe(recipe, stock);
            assertFalse(result.canCook());
            assertAmounts(result, 0);
            assertEquals(2.0,
                result.getMissingIngredients()[0].getQuantity(), 0.0);
            assertEquals(0, result.getUncomparableIngredients().length);
        }
    }


    /** Insufficient stock is shared in recipe order without double counting. */
    public void testRepeatedRequirements()
    {
        Recipe recipe = recipe(rice(1, 2), rice(2, 2), rice(3, 1));
        RecipeMatchResult result = matcher().compareRecipe(recipe,
            new Ingredient[] { rice(20, 3) });
        assertAmounts(result, 2, 1, 0);
        assertFalse(result.canCook());
        Ingredient[] missing = result.getMissingIngredients();
        assertEquals(2, missing.length);
        assertEquals(TestFixtures.id(2), missing[0].getId());
        assertEquals(TestFixtures.id(3), missing[1].getId());
        assertEquals(1.0, missing[0].getQuantity(), 0.0);
        assertEquals(1.0, missing[1].getQuantity(), 0.0);
    }


    /** Partial allocation cannot be reused even when its requirement fails. */
    public void testShortageBeforeLaterRequirement()
    {
        RecipeMatchResult result = matcher().compareRecipe(
            recipe(rice(1, 4), rice(2, 1)),
            new Ingredient[] { rice(20, 3) });
        assertAmounts(result, 3, 0);
        assertEquals(2, result.getMissingIngredients().length);
    }


    /** Compatible stock combines into the current requirement's unit. */
    public void testMultipleEntriesAndConversions()
    {
        Recipe recipe = recipe(
            TestFixtures.ingredient(1, "Rice", 20, Unit.TABLESPOON),
            rice(2, 1));
        Ingredient[] stock = { rice(20, 1),
            TestFixtures.ingredient(21, " rice ", 8, Unit.TABLESPOON) };
        RecipeMatchResult result = matcher().compareRecipe(recipe, stock);
        assertAmounts(result, 20, 0.25);
        assertFalse(result.canCook());
        Ingredient missing = result.getMissingIngredients()[0];
        assertEquals(Unit.CUP, missing.getUnit());
        assertEquals(0.75, missing.getQuantity(), 1e-12);
        assertEquals(1.0, stock[0].getQuantity(), 0.0);
        assertEquals(8.0, stock[1].getQuantity(), 0.0);
    }


    /** Alias names share the same remaining stock. */
    public void testDefaultAliases()
    {
        Recipe recipe = recipe(
            TestFixtures.ingredient(1, "Scallions", 2, Unit.COUNT),
            TestFixtures.ingredient(2, "green onion", 2, Unit.COUNT));
        RecipeMatchResult result = matcher().compareRecipe(recipe,
            new Ingredient[] {
                TestFixtures.ingredient(20, "GREEN   ONIONS", 3, Unit.COUNT) });
        assertAmounts(result, 2, 1);
        assertFalse(result.canCook());
    }


    /** The supplied service determines aliases; food states stay distinct. */
    public void testSuppliedMatcher()
    {
        Map<String, String> aliases = new HashMap<String, String>();
        aliases.put("caster sugar", "sugar");
        RecipeMatcher matcher = new RecipeMatcher(new IngredientMatcher(
            aliases));
        Recipe recipe = recipe(
            TestFixtures.ingredient(1, "Sugar", 100, Unit.GRAM),
            TestFixtures.ingredient(2, "Cooked rice", 1, Unit.CUP));
        RecipeMatchResult result = matcher.compareRecipe(recipe,
            new Ingredient[] {
                TestFixtures.ingredient(20, "Caster sugar", 0.2,
                    Unit.KILOGRAM), rice(21, 5) });
        assertAmounts(result, 100, 0);
        assertFalse(result.canCook());
    }


    /** Matching names with incompatible-only units produce unknown amounts. */
    public void testIncompatibleOnly()
    {
        Recipe recipe = recipe(rice(1, 2));
        for (Unit unit : new Unit[] { Unit.GRAM, Unit.COUNT })
        {
            RecipeMatchResult result = matcher().compareRecipe(recipe,
                new Ingredient[] {
                    TestFixtures.ingredient(20, "Rice", 100, unit) });
            assertFalse(result.canCook());
            assertNull(result.getIngredientMatches()[0].getAvailableQuantity());
            assertEquals(0, result.getMissingIngredients().length);
            assertEquals(1, result.getUncomparableIngredients().length);
            assertEquals(2.0,
                result.getUncomparableIngredients()[0].getQuantity(), 0.0);
        }
    }


    /** Incompatible stock does not hide a known compatible shortage. */
    public void testMixedUnits()
    {
        Recipe recipe = recipe(rice(1, 2));
        Ingredient incompatible = TestFixtures.ingredient(
            20, "Rice", 100, Unit.GRAM);
        for (Ingredient[] stock : new Ingredient[][] {
            { incompatible, rice(21, 1) }, { rice(21, 1), incompatible } })
        {
            RecipeMatchResult result = matcher().compareRecipe(recipe, stock);
            assertAmounts(result, 1);
            assertFalse(result.canCook());
            assertEquals(1, result.getMissingIngredients().length);
            assertEquals(0, result.getUncomparableIngredients().length);
        }
        assertTrue(matcher().compareRecipe(recipe,
            new Ingredient[] { incompatible, rice(21, 2) }).canCook());
    }


    /** Consumed compatible stock still means known zero for later needs. */
    public void testDepletedCompatibleStock()
    {
        RecipeMatchResult result = matcher().compareRecipe(
            recipe(rice(1, 2), rice(2, 1)), new Ingredient[] {
                rice(20, 2),
                TestFixtures.ingredient(21, "Rice", 100, Unit.GRAM) });
        assertAmounts(result, 2, 0);
        assertEquals(0, result.getUncomparableIngredients().length);
        assertEquals(1, result.getMissingIngredients().length);
    }


    /** Different dimensions can use their own stock for the same food. */
    public void testSeparateUnitCategories()
    {
        Recipe recipe = recipe(rice(1, 1),
            TestFixtures.ingredient(2, "Rice", 100, Unit.GRAM),
            TestFixtures.ingredient(3, "Rice", 2, Unit.COUNT));
        RecipeMatchResult result = matcher().compareRecipe(recipe,
            new Ingredient[] { rice(20, 1),
                TestFixtures.ingredient(21, "Rice", 0.1, Unit.KILOGRAM),
                TestFixtures.ingredient(22, "Rice", 2, Unit.COUNT) });
        assertTrue(result.canCook());
        assertAmounts(result, 1, 100, 2);
    }


    /** Repeated calls start fresh and cannot change recipe or pantry data. */
    public void testComparisonIsolation()
    {
        RecipeMatcher matcher = matcher();
        Recipe recipe = recipe(rice(1, 2), rice(2, 2));
        Ingredient source = rice(20, 3);
        Ingredient[] stock = { source };
        RecipeMatchResult first = matcher.compareRecipe(recipe, stock);
        RecipeMatchResult second = matcher.compareRecipe(recipe, stock);
        assertAmounts(first, 2, 1);
        assertAmounts(second, 2, 1);
        assertSame(source, stock[0]);
        assertEquals(3.0, source.getQuantity(), 0.0);
        assertEquals(2.0,
            recipe.getRequiredIngredients()[1].getQuantity(), 0.0);
        source.setQuantity(9);
        stock[0] = rice(21, 10);
        first.getIngredientMatches()[0].getRequiredIngredient().setQuantity(8);
        assertAmounts(first, 2, 1);
        assertEquals(2.0, first.getIngredientMatches()[0]
            .getRequiredIngredient().getQuantity(), 0.0);
        assertTrue(matcher.compareRecipe(recipe, stock).canCook());
    }


    /** Storage, comparison, and deduction work together across chain types. */
    public void testPantryAndRecipeBookIntegration()
    {
        Recipe original = recipe(rice(1, 2), rice(2, 1));
        RecipeBook book = new RecipeBook();
        assertTrue(book.addRecipe(original));
        Recipe saved = book.findRecipeById(original.getId());
        Pantry pantry = new Pantry(new Ingredient[] { rice(20, 3) });
        RecipeMatcher matcher = matcher();
        assertTrue(matcher.compareRecipe(saved,
            pantry.getAllIngredients()).canCook());
        assertEquals(1, pantry.size());
        assertTrue(pantry.deductIngredients(saved.getRequiredIngredients(),
            new IngredientMatcher()));
        assertTrue(pantry.isEmpty());
        assertFalse(matcher.compareRecipe(saved,
            pantry.getAllIngredients()).canCook());
        assertEquals(2.0, book.findRecipeById(original.getId())
            .getRequiredIngredients()[0].getQuantity(), 0.0);
    }


    /** Comparison and deduction agree at rounding and tiny-value boundaries. */
    public void testNumericDeductionParity()
    {
        double[][] cases = {
            { 0.1 + 0.2, 0.3 }, { 0.3, 0.1, 0.2 },
            { 1, 0.5, 0.5 - 4 * Math.ulp(1.0) },
            { 1, 0.5, 0.5 - 5 * Math.ulp(1.0) },
            { 2e-200, 1e-200 },
            { 5 * Double.MIN_VALUE, Double.MIN_VALUE },
            { Double.MIN_VALUE, Double.MIN_VALUE } };
        boolean[] expected = { true, true, true, false, false, false, true };
        for (int i = 0; i < cases.length; i++)
        {
            Recipe recipe = recipe(rice(1, cases[i][0]));
            Ingredient[] stock = new Ingredient[cases[i].length - 1];
            for (int j = 0; j < stock.length; j++)
            {
                stock[j] = rice(20 + j, cases[i][j + 1]);
            }
            RecipeMatchResult result = matcher().compareRecipe(recipe, stock);
            assertEquals("Comparison case " + i, expected[i],
                result.canCook());
            Pantry pantry = new Pantry(stock);
            assertEquals("Deduction case " + i, result.canCook(),
                pantry.deductIngredients(recipe.getRequiredIngredients(),
                    new IngredientMatcher()));
        }
    }


    /** A real shortage stays positive even far below a fixed epsilon. */
    public void testTinyMissingQuantity()
    {
        RecipeMatchResult result = matcher().compareRecipe(
            recipe(rice(1, 5 * Double.MIN_VALUE)),
            new Ingredient[] { rice(20, Double.MIN_VALUE) });
        assertFalse(result.canCook());
        assertEquals(4 * Double.MIN_VALUE,
            result.getMissingIngredients()[0].getQuantity(), 0.0);
    }


    /** Unrepresentable conversions fail without changing the caller's stock. */
    public void testUnrepresentableConversion()
    {
        Ingredient stock = TestFixtures.ingredient(
            20, "Rice", Double.MIN_VALUE, Unit.MILLILITER);
        TestFixtures.invalid(() -> matcher().compareRecipe(
            recipe(rice(1, 1)), new Ingredient[] { stock }));
        assertEquals(Double.MIN_VALUE, stock.getQuantity(), 0.0);
    }


    private RecipeMatcher matcher()
    {
        return new RecipeMatcher(new IngredientMatcher());
    }


    private Ingredient rice(int id, double amount)
    {
        return TestFixtures.ingredient(id, "Rice", amount, Unit.CUP);
    }


    private Recipe recipe(Ingredient... requirements)
    {
        return new Recipe(TestFixtures.id(100), "Rice dish", requirements,
            new String[] { "Prepare the ingredients." }, 5, 10,
            Effort.EASY, "", "");
    }


    private void assertAmounts(RecipeMatchResult result, double... expected)
    {
        IngredientMatch[] matches = result.getIngredientMatches();
        assertEquals(expected.length, matches.length);
        for (int i = 0; i < matches.length; i++)
        {
            assertEquals(TestFixtures.id(i + 1),
                matches[i].getRequiredIngredient().getId());
            assertNotNull(matches[i].getAvailableQuantity());
            assertEquals(expected[i],
                matches[i].getAvailableQuantity().doubleValue(), 1e-12);
        }
    }
}
