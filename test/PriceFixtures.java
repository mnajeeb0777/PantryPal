import java.math.BigDecimal;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

/** Synthetic Walmart fixtures; no personal credentials or live requests. */
final class PriceFixtures
{
    static final Instant NOW = Instant.parse("2026-10-21T17:30:00Z");
    private PriceFixtures() { }
    static PriceFinder finder(RecipeHttpStub http)
    {
        http.priceMode = true;
        return new PriceFinder(URI.create("https://example.test/"), "test-api-key", http,
            new IngredientMatcher(), "1852", Clock.fixed(NOW, ZoneOffset.UTC));
    }
    static String product(String id, String title, String price)
    {
        return "{\"product_id\":\"" + id + "\",\"title\":\"" + title
            + "\",\"seller_name\":\"Walmart.com\",\"primary_offer\":{\"offer_id\":\"" + id
            + "\",\"offer_price\":" + price + ",\"currency\":\"USD\"},"
            + "\"price_per_unit\":{\"unit\":\"each\"},\"out_of_stock\":false,"
            + "\"product_page_url\":\"https://www.walmart.com/ip/" + id + "\"}";
    }
    static String response(String products)
    { return response(products, "1852"); }
    static String response(String products, String store)
    {
        return "{\"search_metadata\":{\"status\":\"Success\",\"created_at\":\"2026-10-21 17:30:00 UTC\"},"
            + "\"search_information\":{\"location\":{\"store_id\":\"" + store
            + "\",\"city\":\"Lake Ridge\",\"province_code\":\"VA\"}},\"organic_results\":["
            + products + "]}";
    }
    static StoreOffer offer(String id, double amount, Unit unit, String price)
    {
        return new StoreOffer(id, "Rice package", "Rice", "1852", "Lake Ridge, VA",
            URI.create("https://www.walmart.com/ip/" + id), new BigDecimal(price), amount,
            unit, StoreOffer.PriceBasis.EACH, StoreOffer.Availability.IN_STOCK,
            NOW, NOW, "", "");
    }
}
