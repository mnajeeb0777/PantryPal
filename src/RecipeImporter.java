import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
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

    /** Shared JSON reader retained as a package-level compatibility entry. */
    static Object parseJson(String text) throws IOException
    {
        return Json.parse(text);
    }
}
