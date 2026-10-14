import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;

/**
 * Retrieves recipes from an external provider.
 * Skeleton based on the UML and class reference; behavior is not implemented.
 *
 * @author PantryPal team (GenAI-assisted skeleton draft)
 * @version 2026.10.14
 */
public class RecipeImporter
{
    private URI apiBaseUri;
    private String apiKey;
    private HttpClient httpClient;


    /**
     * TODO: implement construction per the class reference.
     * @param apiBaseUri apiBaseUri input
     * @param apiKey apiKey input
     * @param httpClient httpClient input
     * @throws UnsupportedOperationException until implemented
     */
    public RecipeImporter(URI apiBaseUri, String apiKey, HttpClient httpClient)
    {
        throw new UnsupportedOperationException("Not implemented yet.");
    }


    /**
     * TODO: implement searchRecipes per the class reference.
     * @param query query input
     * @return the result specified by the class reference
     * @throws IOException as defined by the class reference error contract
     * @throws InterruptedException if an external request is interrupted
     * @throws UnsupportedOperationException until implemented
     */
    public Recipe[] searchRecipes(String query)
        throws IOException, InterruptedException
    {
        throw new UnsupportedOperationException("Not implemented yet.");
    }


    /**
     * TODO: implement fetchRecipe per the class reference.
     * @param externalRecipeId externalRecipeId input
     * @return the result specified by the class reference
     * @throws IOException as defined by the class reference error contract
     * @throws InterruptedException if an external request is interrupted
     * @throws UnsupportedOperationException until implemented
     */
    public Recipe fetchRecipe(String externalRecipeId)
        throws IOException, InterruptedException
    {
        throw new UnsupportedOperationException("Not implemented yet.");
    }
}
