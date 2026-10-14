import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;

/**
 * Retrieves offers and compares purchase costs.
 * Skeleton based on the UML and class reference; behavior is not implemented.
 *
 * @author PantryPal team (GenAI-assisted skeleton draft)
 * @version 2026.10.14
 */
public class PriceFinder
{
    private URI apiBaseUri;
    private String apiKey;
    private HttpClient httpClient;
    private IngredientMatcher ingredientMatcher;


    /**
     * TODO: implement construction per the class reference.
     * @param apiBaseUri apiBaseUri input
     * @param apiKey apiKey input
     * @param httpClient httpClient input
     * @param ingredientMatcher ingredientMatcher input
     * @throws UnsupportedOperationException until implemented
     */
    public PriceFinder(
        URI apiBaseUri,
        String apiKey,
        HttpClient httpClient,
        IngredientMatcher ingredientMatcher)
    {
        throw new UnsupportedOperationException("Not implemented yet.");
    }


    /**
     * TODO: implement findNearbyOffers per the class reference.
     * @param ingredient ingredient input
     * @param zipCode zipCode input
     * @param radiusMiles radiusMiles input
     * @return the result specified by the class reference
     * @throws IOException as defined by the class reference error contract
     * @throws InterruptedException if an external request is interrupted
     * @throws UnsupportedOperationException until implemented
     */
    public StoreOffer[] findNearbyOffers(
        Ingredient ingredient,
        String zipCode,
        double radiusMiles)
        throws IOException, InterruptedException
    {
        throw new UnsupportedOperationException("Not implemented yet.");
    }


    /**
     * TODO: implement findCheapestOffer per the class reference.
     * @param ingredient ingredient input
     * @param offers offers input
     * @return the result specified by the class reference
     * @throws UnsupportedOperationException until implemented
     */
    public StoreOffer findCheapestOffer(
        Ingredient ingredient,
        StoreOffer[] offers)
    {
        throw new UnsupportedOperationException("Not implemented yet.");
    }


    /**
     * TODO: implement calculateRequiredPackages per the class reference.
     * @param ingredient ingredient input
     * @param offer offer input
     * @return the result specified by the class reference
     * @throws UnsupportedOperationException until implemented
     */
    public int calculateRequiredPackages(
        Ingredient ingredient,
        StoreOffer offer)
    {
        throw new UnsupportedOperationException("Not implemented yet.");
    }


    /**
     * TODO: implement calculatePurchaseCost per the class reference.
     * @param ingredient ingredient input
     * @param offer offer input
     * @return the result specified by the class reference
     * @throws UnsupportedOperationException until implemented
     */
    public BigDecimal calculatePurchaseCost(
        Ingredient ingredient,
        StoreOffer offer)
    {
        throw new UnsupportedOperationException("Not implemented yet.");
    }
}
