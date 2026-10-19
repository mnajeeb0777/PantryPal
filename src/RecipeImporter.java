import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.CharBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Fetches reviewable TheMealDB drafts without modifying saved recipes.
 * @author PantryPal team (GenAI-assisted implementation)
 * @version 2026.10.18
 */
public class RecipeImporter
{
    private final URI apiBaseUri;
    private HttpClient httpClient;

    /**
     * Creates an injectable API client. Construction performs no requests.
     * @param apiBaseUri root before the key, e.g. /api/json/v1/
     * @param apiKey development or supporter key
     * @param httpClient transport with a connection timeout
     */
    public RecipeImporter(URI apiBaseUri, String apiKey, HttpClient httpClient)
    {
        if (apiBaseUri == null || httpClient == null || apiKey == null
            || !apiKey.matches("[A-Za-z0-9_-]+")
            || !("https".equals(apiBaseUri.getScheme())
                || "http".equals(apiBaseUri.getScheme()))
            || apiBaseUri.getHost() == null || apiBaseUri.getQuery() != null
            || apiBaseUri.getFragment() != null || apiBaseUri.getUserInfo() != null)
        {
            throw new IllegalArgumentException("Invalid recipe API configuration.");
        }
        this.apiBaseUri = URI.create(apiBaseUri.toString().replaceAll("/+$", "")
            + "/" + apiKey + "/");
        this.httpClient = httpClient;
    }

    /** @return educational client using the public development key */
    public static RecipeImporter educationalClient()
    {
        return new RecipeImporter();
    }

    private RecipeImporter()
    {
        apiBaseUri = URI.create("https://www.themealdb.com/api/json/v1/1/");
    }

    private synchronized HttpClient client() throws IOException
    {
        if (httpClient == null)
        {
            try
            {
                httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10)).build();
            }
            catch (java.io.UncheckedIOException exception)
            {
                throw new IOException("Network access is unavailable.", exception);
            }
        }
        return httpClient;
    }

    /**
     * Searches by name, returning full drafts without saving them.
     * @param query nonblank recipe name
     * @return drafts, or an empty array
     * @throws IOException for HTTP or response errors
     * @throws InterruptedException when cancelled
     */
    public RecipeImportDraft[] searchRecipes(String query)
        throws IOException, InterruptedException
    {
        if (query == null || query.isBlank() || query.length() > 200)
        {
            throw new IllegalArgumentException("Enter a recipe name (1-200 characters).");
        }
        return request("search.php?s=" + URLEncoder.encode(query.strip(),
            StandardCharsets.UTF_8));
    }

    /**
     * Looks up one recipe by provider ID.
     * @param externalRecipeId numeric TheMealDB ID
     * @return the draft
     * @throws IOException if unavailable or invalid
     * @throws InterruptedException when cancelled
     */
    public RecipeImportDraft fetchRecipe(String externalRecipeId)
        throws IOException, InterruptedException
    {
        if (externalRecipeId == null || !externalRecipeId.matches("[0-9]+"))
        {
            throw new IllegalArgumentException("A numeric recipe ID is required.");
        }
        RecipeImportDraft[] results = request("lookup.php?i=" + externalRecipeId);
        if (results.length != 1
            || !results[0].getExternalId().equals(externalRecipeId))
        {
            throw new IOException("The requested recipe was not found.");
        }
        return results[0];
    }

    private RecipeImportDraft[] request(String path)
        throws IOException, InterruptedException
    {
        HttpRequest request = HttpRequest.newBuilder(apiBaseUri.resolve(path))
            .timeout(Duration.ofSeconds(20)).header("Accept", "application/json")
            .GET().build();
        HttpResponse<String> response = client().send(request,
            HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200)
        {
            throw new IOException("TheMealDB returned HTTP " + response.statusCode()
                + ". Try searching again later.");
        }
        return parseResponse(response.body());
    }

    /**
     * Decodes provider data without dropping unresolved ingredient rows.
     * @param body JSON response
     * @return complete drafts
     * @throws IOException if the response is invalid
     */
    static RecipeImportDraft[] parseResponse(String body) throws IOException
    {
        Object root = parseJson(body);
        if (!(root instanceof Map<?, ?>)
            || !((Map<?, ?>)root).containsKey("meals"))
        {
            throw new IOException("Invalid TheMealDB response: missing meals.");
        }
        Object meals = ((Map<?, ?>)root).get("meals");
        if (meals == null) { return new RecipeImportDraft[0]; }
        if (!(meals instanceof List<?>))
        {
            throw new IOException("Invalid TheMealDB recipe list.");
        }
        List<RecipeImportDraft> results = new ArrayList<RecipeImportDraft>();
        for (Object entry : (List<?>)meals)
        {
            if (!(entry instanceof Map<?, ?>))
            {
                throw new IOException("Invalid TheMealDB recipe.");
            }
            Map<?, ?> meal = (Map<?, ?>)entry;
            List<String> names = new ArrayList<String>();
            List<String> measures = new ArrayList<String>();
            for (int i = 1; i <= 20; i++)
            {
                String name = string(meal, "strIngredient" + i);
                String measure = string(meal, "strMeasure" + i);
                if (!name.isBlank() || !measure.isBlank())
                {
                    names.add(name);
                    measures.add(measure);
                }
            }
            try
            {
                results.add(new RecipeImportDraft(string(meal, "idMeal"),
                    string(meal, "strMeal"), names.toArray(new String[0]),
                    measures.toArray(new String[0]), string(meal, "strInstructions"),
                    string(meal, "strSource")));
            }
            catch (IllegalArgumentException exception)
            {
                throw new IOException("Invalid TheMealDB recipe details.", exception);
            }
        }
        return results.toArray(new RecipeImportDraft[0]);
    }

    private static String string(Map<?, ?> object, String key) throws IOException
    {
        Object value = object.get(key);
        if (value == null) { return ""; }
        if (!(value instanceof String))
        {
            throw new IOException("Invalid TheMealDB text field: " + key);
        }
        return (String)value;
    }

    /**
     * Decodes JSON with a request-local cursor and no shared parser state.
     * Limits input to two million characters and 32 nesting levels.
     * @param text JSON response
     * @return strings, maps, lists, booleans, BigDecimals, or null
     * @throws IOException for malformed or oversized input
     */
    static Object parseJson(String text) throws IOException
    {
        if (text == null || text.length() > 2_000_000)
        {
            throw new IOException("Recipe response is missing or too large.");
        }
        CharBuffer input = CharBuffer.wrap(text);
        Object value = jsonValue(input, 0);
        jsonWhitespace(input);
        if (input.hasRemaining()) { throw invalidJson(input); }
        return value;
    }

    private static Object jsonValue(CharBuffer input, int depth) throws IOException
    {
        jsonWhitespace(input);
        if (depth > 32 || !input.hasRemaining()) { throw invalidJson(input); }
        char next = input.charAt(0);
        if (next == '"') { return jsonString(input); }
        if (next == '{')
        {
            input.get();
            Map<String, Object> object = new LinkedHashMap<String, Object>();
            if (jsonTake(input, '}')) { return object; }
            do
            {
                jsonWhitespace(input);
                String key = jsonString(input);
                if (!jsonTake(input, ':') || object.containsKey(key))
                {
                    throw invalidJson(input);
                }
                object.put(key, jsonValue(input, depth + 1));
                if (jsonTake(input, '}')) { return object; }
            }
            while (jsonTake(input, ','));
            throw invalidJson(input);
        }
        if (next == '[')
        {
            input.get();
            List<Object> values = new ArrayList<Object>();
            if (jsonTake(input, ']')) { return values; }
            do
            {
                values.add(jsonValue(input, depth + 1));
                if (jsonTake(input, ']')) { return values; }
            }
            while (jsonTake(input, ','));
            throw invalidJson(input);
        }
        if (next == 'n') { jsonLiteral(input, "null"); return null; }
        if (next == 't') { jsonLiteral(input, "true"); return Boolean.TRUE; }
        if (next == 'f') { jsonLiteral(input, "false"); return Boolean.FALSE; }
        StringBuilder digits = new StringBuilder();
        while (input.hasRemaining() && "0123456789-+.eE".indexOf(input.charAt(0)) >= 0)
        {
            digits.append(input.get());
        }
        String number = digits.toString();
        if (!number.matches("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?"))
        {
            throw invalidJson(input);
        }
        try { return new BigDecimal(number); }
        catch (NumberFormatException exception) { throw invalidJson(input); }
    }

    private static String jsonString(CharBuffer input) throws IOException
    {
        if (!input.hasRemaining() || input.get() != '"') { throw invalidJson(input); }
        StringBuilder result = new StringBuilder();
        while (input.hasRemaining())
        {
            char character = input.get();
            if (character == '"') { return result.toString(); }
            if (character < 32) { throw invalidJson(input); }
            if (character != '\\') { result.append(character); continue; }
            if (!input.hasRemaining()) { throw invalidJson(input); }
            char escape = input.get();
            switch (escape)
            {
                case '"': case '\\': case '/': result.append(escape); break;
                case 'b': result.append('\b'); break;
                case 'f': result.append('\f'); break;
                case 'n': result.append('\n'); break;
                case 'r': result.append('\r'); break;
                case 't': result.append('\t'); break;
                case 'u':
                    if (input.remaining() < 4) { throw invalidJson(input); }
                    char[] hex = new char[4];
                    input.get(hex);
                    String code = new String(hex);
                    if (!code.matches("[0-9a-fA-F]{4}")) { throw invalidJson(input); }
                    result.append((char)Integer.parseInt(code, 16));
                    break;
                default: throw invalidJson(input);
            }
        }
        throw invalidJson(input);
    }

    private static void jsonLiteral(CharBuffer input, String literal) throws IOException
    {
        for (int i = 0; i < literal.length(); i++)
        {
            if (!input.hasRemaining() || input.get() != literal.charAt(i))
            {
                throw invalidJson(input);
            }
        }
    }

    private static boolean jsonTake(CharBuffer input, char character)
    {
        jsonWhitespace(input);
        if (input.hasRemaining() && input.charAt(0) == character)
        {
            input.get();
            return true;
        }
        return false;
    }

    private static void jsonWhitespace(CharBuffer input)
    {
        while (input.hasRemaining() && " \t\r\n".indexOf(input.charAt(0)) >= 0)
        {
            input.get();
        }
    }

    private static IOException invalidJson(CharBuffer input)
    {
        return new IOException("Invalid recipe JSON at character " + input.position() + ".");
    }
}
