import java.io.IOException;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.nio.file.Path;
import java.util.Map;
import student.TestCase;

/**
 * Provider decoding, conservative measurements, and saved import workflows.
 * @author PantryPal team (GenAI-assisted tests)
 * @version 2026.10.18
 */
public class RecipeImporterTest extends TestCase
{
    /** Representative recipe with one supported and one ambiguous measurement. */
    static final String MEAL = "{\"meals\":[{\"idMeal\":\"52771\","
        + "\"strMeal\":\"Rice & beans\",\"strIngredient1\":\"Rice\","
        + "\"strMeasure1\":\"1 1/2 cups\",\"strIngredient2\":\"Beans\","
        + "\"strMeasure2\":\"1 tin\",\"strInstructions\":\"Boil.\\nServe.\","
        + "\"strSource\":\"https://example.org/rice\"}]}";
    private RecipeHttpStub transport;
    private RecipeImporter importer;

    @Override
    public void setUp()
    {
        transport = new RecipeHttpStub();
        transport.body = MEAL;
        importer = new RecipeImporter(URI.create("https://example.org/api/v1/"),
            "1", transport);
    }

    /** @throws Exception for unexpected failures */
    public void testSearchEncodesQueryAndRetainsOriginalRows() throws Exception
    {
        RecipeImportDraft draft = importer.searchRecipes("Rice & beans")[0];
        assertEquals("https://example.org/api/v1/1/search.php?s=Rice+%26+beans",
            transport.lastRequest.uri().toString());
        assertEquals(20L, transport.lastRequest.timeout().get().getSeconds());
        assertEquals("GET", transport.lastRequest.method());
        assertEquals("1 tin", draft.getMeasurements()[1]);
        assertEquals("Boil.\nServe.", draft.getInstructions());
        String[] names = draft.getIngredientNames();
        names[0] = "Changed";
        assertEquals("Rice", draft.getIngredientNames()[0]);
        assertEquals("52771", importer.fetchRecipe("52771").getExternalId());
    }

    /** @throws Exception for unexpected failures */
    public void testEmptyResponsesAndWrongLookup() throws Exception
    {
        transport.body = "{\"meals\":null}";
        assertEquals(0, importer.searchRecipes("none").length);
        expectIO(() -> importer.fetchRecipe("52771"));
        transport.body = "{\"meals\":[]}";
        assertEquals(0, importer.searchRecipes("none").length);
        transport.body = MEAL;
        expectIO(() -> importer.fetchRecipe("999"));
    }

    /** @throws Exception for unexpected failures */
    public void testProviderFailuresAndCancellation() throws Exception
    {
        transport.status = 429;
        expectIO(() -> importer.searchRecipes("rice"));
        transport.status = 200;
        transport.failure = new HttpTimeoutException("Timed out");
        expectIO(() -> importer.searchRecipes("rice"));
        transport.failure = null;
        transport.interrupt = true;
        try { importer.searchRecipes("rice"); fail("Must propagate interruption"); }
        catch (InterruptedException expected) { assertNotNull(expected); }
    }

    /** @throws Exception for unexpected failures */
    public void testInvalidRequests() throws Exception
    {
        expectInvalid(() -> importer.searchRecipes(" "));
        expectInvalid(() -> importer.searchRecipes("a".repeat(201)));
        expectInvalid(() -> importer.fetchRecipe("../secret"));
        TestFixtures.invalid(() -> new RecipeImporter(URI.create("file:///api/"),
            "1", transport));
        assertNull(transport.lastRequest);
        assertNotNull(RecipeImporter.educationalClient());
    }

    /** @throws Exception for unexpected failures */
    public void testMalformedProviderShapes() throws Exception
    {
        String[] invalid = { "{}", "[]", "{\"meals\":{}}", "{\"meals\":[null]}",
            MEAL.replace("\"Rice & beans\"", "32"),
            MEAL.replace("\"52771\"", "null"),
            "{\"meals\":[{\"idMeal\":\"1\",\"strMeal\":\"Empty\"}]}" };
        for (String body : invalid)
        {
            expectIO(() -> RecipeImporter.parseResponse(body));
        }
    }

    /** @throws Exception for unexpected failures */
    public void testMissingNameRowIsPreservedForCorrection() throws Exception
    {
        RecipeImportDraft draft = RecipeImporter.parseResponse(MEAL.replace(
            "\"strIngredient2\":\"Beans\"", "\"strIngredient2\":null"))[0];
        assertEquals(2, draft.getIngredientNames().length);
        assertEquals("", draft.getIngredientNames()[1]);
        assertNull(RecipeImportDraft.parseMeasurement("", "1 cup"));
    }

    /** Fractions and explicit supported units can be suggested without guessing. */
    public void testSupportedMeasurements()
    {
        assertAmount("1 1/2 cups", 1.5, Unit.CUP);
        assertAmount("1/4 CUP", 0.25, Unit.CUP);
        assertAmount("1\u00bd tbsp", 1.5, Unit.TABLESPOON);
        assertAmount("\u00be teaspoon", 0.75, Unit.TEASPOON);
        assertAmount("250ml", 250, Unit.MILLILITER);
        assertAmount(".5 kg", 0.5, Unit.KILOGRAM);
        assertAmount("2 fl oz", 2, Unit.FLUID_OUNCE);
        assertAmount("2 count", 2, Unit.COUNT);
        assertAmount("3 oz", 3, Unit.OUNCE);
    }

    /** Package sizes and ingredient forms are never silently guessed. */
    public void testUnresolvedMeasurements()
    {
        String[] inputs = { "1 tin", "3 cloves", "sprinkling", "to taste", "",
            "2", "1-2 cups", "2 x 400g", "1 cup chopped", "0 g", "1/0 cup",
            "-1 g", "NaN cups", "1000001 g" };
        for (String input : inputs)
        {
            assertNull(input, RecipeImportDraft.parseMeasurement("Beans", input));
        }
    }

    /** @throws Exception for unexpected failures */
    public void testJsonEscapesAndUnknownFields() throws Exception
    {
        Object value = RecipeImporter.parseJson("{\"text\":\"A\\u00e9\\n\\\"\\\\\\/\\t\\b\\f\\r\","
            + "\"extra\":[true,false,null,-1.25e2]}");
        assertEquals("A\u00e9\n\"\\/\t\b\f\r", ((Map<?, ?>)value).get("text"));
    }

    /** @throws Exception for unexpected failures */
    public void testJsonRejectsMalformedAndExcessiveInput() throws Exception
    {
        String[] invalid = { "", "{", "[1,]", "{\"x\":1,}", "{\"x\":null,\"x\":2}",
            "{\"x\":01}", "[+1]", "[1.]", "[.1]", "true false", "\"\\x\"",
            "\"bad\ntext\"", "\"\\uZZZZ\"", "\"\\u123\"", "[".repeat(34) + "]".repeat(34),
            " ".repeat(2_000_001) };
        for (String input : invalid) { expectIO(() -> RecipeImporter.parseJson(input)); }
    }

    /** @throws Exception for unexpected failures */
    public void testImportSaveReopenCompareCookAndDuplicate() throws Exception
    {
        Path directory = TestFixtures.temporaryDirectory();
        try
        {
            DataStore store = new DataStore(directory.resolve("import.properties"));
            PantryPalController controller = new PantryPalController(store,
                new IngredientMatcher(), importer, null);
            RecipeImportDraft draft = controller.searchExternalRecipes("rice")[0];
            Ingredient[] rows = {
                RecipeImportDraft.parseMeasurement("Rice", "1 1/2 cups"),
                RecipeImportDraft.parseMeasurement("Beans", "200 g") };
            TestFixtures.invalid(() -> draft.toRecipe(new Ingredient[] { rows[0] },
                null, null, Effort.UNKNOWN));
            Recipe recipe = draft.toRecipe(rows, null, null, Effort.UNKNOWN);
            assertTrue(controller.importRecipe(recipe));
            assertNull(recipe.getCookTimeMinutes());
            assertEquals(Effort.UNKNOWN, recipe.getEffort());
            assertTrue(String.join("\n", recipe.getInstructions()).contains("https://example.org/rice"));
            PantryPalController reopened = new PantryPalController(store,
                new IngredientMatcher(), importer, null);
            reopened.loadData();
            assertFalse(reopened.importRecipe(draft.toRecipe(rows, 10, 20, Effort.EASY)));
            assertNull(reopened.getSavedRecipes()[0].getCookTimeMinutes());
            assertEquals(1, reopened.getSavedRecipes().length);
            Recipe saved = reopened.getSavedRecipes()[0];
            reopened.addPantryIngredient("Rice", 2, Unit.CUP);
            reopened.addPantryIngredient("Beans", 300, Unit.GRAM);
            assertTrue(reopened.compareRecipeWithPantry(saved).canCook());
            assertEquals(saved.getId(), reopened.selectMealRouletteRecipe().getId());
            assertTrue(reopened.cookRecipe(saved));
            assertEquals(0.5, reopened.searchPantryIngredients("Rice")[0].getQuantity(), 0.00001);
            assertEquals(100.0, reopened.searchPantryIngredients("Beans")[0].getQuantity(), 0.00001);
            assertFalse(reopened.compareRecipeWithPantry(saved).canCook());
            assertEquals("themealdb:52771", store.load().getSavedRecipes()[0].getExternalRecipeId());
        }
        finally { TestFixtures.deleteDirectory(directory); }
    }

    /** @throws Exception for unexpected failures */
    public void testMissingInstructionsAndSourceRemainUnknown() throws Exception
    {
        RecipeImportDraft draft = RecipeImporter.parseResponse(
            "{\"meals\":[{\"idMeal\":\"42\",\"strMeal\":\"Rice\","
                + "\"strIngredient1\":\"Rice\",\"strMeasure1\":\"1 cup\","
                + "\"strInstructions\":null,\"strSource\":null}]}")[0];
        assertEquals("", draft.getInstructions());
        assertEquals("", draft.getOriginalSource());
        Recipe recipe = draft.toRecipe(new Ingredient[] {
            RecipeImportDraft.parseMeasurement("Rice", "1 cup") }, null, null, Effort.UNKNOWN);
        assertNull(recipe.getPrepTimeMinutes());
        assertNull(recipe.getCookTimeMinutes());
        assertEquals(1, recipe.getInstructions().length);
        assertTrue(recipe.getInstructions()[0].startsWith("Recipe data: TheMealDB"));
    }

    /** @throws Exception for unexpected failures */
    public void testExternalIdentityPreventsDuplicateWithDifferentLocalId() throws Exception
    {
        RecipeImportDraft draft = importer.fetchRecipe("52771");
        Recipe first = draft.toRecipe(new Ingredient[] {
            RecipeImportDraft.parseMeasurement("Rice", "1 cup"),
            RecipeImportDraft.parseMeasurement("Beans", "200 g") }, null, null, Effort.UNKNOWN);
        Recipe second = new Recipe(java.util.UUID.randomUUID().toString(), "Changed",
            first.getRequiredIngredients(), first.getInstructions(), 5, 10, Effort.EASY,
            first.getSourceUrl(), first.getExternalRecipeId());
        PantryPalController controller = new PantryPalController();
        assertTrue(controller.importRecipe(first));
        assertFalse(controller.importRecipe(second));
        assertEquals("Rice & beans", controller.getSavedRecipes()[0].getName());
    }

    private void assertAmount(String text, double quantity, Unit unit)
    {
        Ingredient ingredient = RecipeImportDraft.parseMeasurement("Rice", text);
        assertNotNull(text, ingredient);
        assertEquals(quantity, ingredient.getQuantity(), 0.000001);
        assertEquals(unit, ingredient.getUnit());
    }

    private void expectInvalid(Checked action) throws Exception
    {
        try { action.run(); fail("Expected IllegalArgumentException"); }
        catch (IllegalArgumentException expected) { assertNotNull(expected.getMessage()); }
    }

    private void expectIO(Checked action) throws Exception
    {
        try { action.run(); fail("Expected IOException"); }
        catch (IOException expected) { assertNotNull(expected.getMessage()); }
    }

    @FunctionalInterface
    private interface Checked { void run() throws Exception; }
}
