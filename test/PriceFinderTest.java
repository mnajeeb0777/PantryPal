import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.Flow;
import student.TestCase;

/** Pricing arithmetic, bounded retrieval, cache/store isolation and conservative parsing. */
public class PriceFinderTest extends TestCase
{
    private RecipeHttpStub http;
    private PriceFinder finder;
    private Ingredient rice;
    @Override public void setUp()
    {
        http = new RecipeHttpStub(); finder = PriceFixtures.finder(http);
        http.body = PriceFixtures.response(PriceFixtures.product("a", "White Rice, 400 g", "2.50"));
        rice = TestFixtures.ingredient(1, "Rice", 600, Unit.GRAM);
    }
    public void testWholePackagesAndMoney()
    {
        StoreOffer offer = PriceFixtures.offer("a", 400, Unit.GRAM, "2.50");
        assertEquals(2, finder.calculateRequiredPackages(rice, offer));
        assertEquals("5.00", finder.calculatePurchaseCost(rice, offer).toPlainString());
        Ingredient exact = TestFixtures.ingredient(2, "Rice", 1, Unit.KILOGRAM);
        assertEquals(1, finder.calculateRequiredPackages(exact, PriceFixtures.offer("b", 1000, Unit.GRAM, "3")));
        assertEquals(1, finder.calculateRequiredPackages(TestFixtures.ingredient(3,"Rice",16,Unit.OUNCE),
            PriceFixtures.offer("c",1,Unit.POUND,"2")));
    }
    public void testRankingUsesPurchaseCost()
    {
        StoreOffer large = PriceFixtures.offer("a", 2000, Unit.GRAM, "5");
        StoreOffer small = PriceFixtures.offer("b", 400, Unit.GRAM, "2");
        assertSame(small, finder.findCheapestOffer(rice, new StoreOffer[] {null, large, small}));
        assertNull(finder.findCheapestOffer(rice, new StoreOffer[0]));
        TestFixtures.invalid(() -> finder.calculatePurchaseCost(rice, null));
        TestFixtures.invalid(() -> finder.calculatePurchaseCost(rice, PriceFixtures.offer("c",1,Unit.CUP,"2")));
    }
    public void testConversionRoundoffAndOverflow()
    {
        Ingredient requirement = TestFixtures.ingredient(2,"Rice",0.1+0.2,Unit.KILOGRAM);
        assertEquals(3,finder.calculateRequiredPackages(requirement,PriceFixtures.offer("a",100,Unit.GRAM,"1")));
        TestFixtures.invalid(() -> finder.calculateRequiredPackages(rice,
            PriceFixtures.offer("tiny",1e-20,Unit.GRAM,"1")));
    }
    public void testQueryCacheAndStoreValidation() throws Exception
    {
        PriceFinder.SearchResult result = finder.findOffers(rice);
        assertEquals(2,http.calls); assertEquals(1,http.searchCalls);
        String uri = http.lastRequest.uri().toString();
        assertTrue(uri.contains("store_id=1852")); assertTrue(uri.contains("engine=walmart"));
        assertTrue(uri.contains("query=rice")); assertTrue(uri.contains("page=1"));
        assertEquals(Duration.ofSeconds(45),http.lastRequest.timeout().get());
        assertEquals("Lake Ridge, VA",result.getLocation()); assertEquals(1,result.getOffers().length);
        StoreOffer[] copy=result.getOffers();copy[0]=null;assertNotNull(result.getOffers()[0]);
        assertSame(result,finder.findOffers(rice)); assertEquals(2,http.calls);
        assertTrue(finder.isCurrent(result));
        http.body=PriceFixtures.response("","999");
        failure(() -> finder.findOffers(rice,"123"),"reference store");
        try { finder.findOffers(null); fail(); } catch (IllegalArgumentException expected) { /* Required input. */ }
    }
    public void testFilterStockSellerCurrencyAndDuplicates() throws Exception
    {
        String good=PriceFixtures.product("a","White Rice, 400 g","2");
        String seller=PriceFixtures.product("b","Rice, 400 g","1").replace("Walmart.com","Other Seller");
        String currency=PriceFixtures.product("c","Rice, 400 g","1").replace("USD","CAD");
        String stock=PriceFixtures.product("d","Rice, 400 g","1").replace("false","true");
        http.body=PriceFixtures.response(good+","+good+","+seller+","+currency+","+stock);
        StoreOffer[] offers=finder.findOffers(rice).getOffers();
        assertEquals(2,offers.length);assertTrue(offers[0].hasComparablePackage());
        assertFalse(offers[1].hasComparablePackage());
    }
    public void testUnknownFieldsAndPromotions() throws Exception
    {
        String p=PriceFixtures.product("a","Rice, 400 g","2").replace(
            "\"out_of_stock\":false,", "");
        String variable=PriceFixtures.product("b","Rice, 400 g","3").replace("\"unit\":\"each\"","\"unit\":\"pack_weight\"");
        String promotion=PriceFixtures.product("c","Rice, 400 g","2").replace(
            "\"offer_price\":2", "\"min_price\":2,\"max_price\":8,\"offer_price\":2");
        http.body=PriceFixtures.response(p+","+variable+","+promotion);
        for(StoreOffer offer:finder.findOffers(rice).getOffers()) {assertFalse(offer.hasComparablePackage());}
    }
    public void testEmptyAndMalformedResults() throws Exception
    {
        http.body=PriceFixtures.response("");assertEquals(0,finder.findOffers(rice).getOffers().length);
        http.body="{}";failure(() -> finder.findOffers(TestFixtures.ingredient(2,"Flour",1,Unit.GRAM)),"successfully");
        http.body="{bad";failure(() -> finder.findOffers(TestFixtures.ingredient(3,"Sugar",1,Unit.GRAM)),"JSON");
        http.body=PriceFixtures.response("").replace("\"organic_results\":[]", "\"wrong_list\":[]");
        failure(() -> finder.findOffers(TestFixtures.ingredient(4,"Milk",1,Unit.LITER)),"product list");
    }
    public void testTransportAndAccessErrorsAreSafe() throws Exception
    {
        for(int status:new int[]{401,403,402,429,500})
        {
            setUp();http.status=status;
            failure(() -> finder.findOffers(rice), status==429?"rate limit":status==402?"quota":status==500?"500":"denied");
        }
        setUp();http.failure=new IOException("https://evil.test/?api_key=test-api-key");
        failure(() -> finder.findOffers(rice),"connect");
        setUp();http.interrupt=true;
        try{finder.findOffers(rice);fail();}catch(InterruptedException expected){assertFalse(expected.toString().contains("test-api-key"));}
    }
    public void testQuotaFailureAndProviderError() throws Exception
    {
        http.accountBody="{\"total_searches_left\":0}";
        failure(() -> finder.findOffers(rice),"quota");assertEquals(0,http.searchCalls);
        setUp();http.accountBody="{}";failure(() -> finder.findOffers(rice),"quota");
        setUp();http.body="{\"error\":\"secret test-api-key\"}";
        failure(() -> finder.findOffers(rice),"provider");
    }
    public void testCacheExpiryAndFreshness() throws Exception
    {
        MutableClock clock=new MutableClock();
        finder=new PriceFinder(URI.create("https://example.test/"),"test-api-key",http,new IngredientMatcher(),"1852",clock);
        PriceFinder.SearchResult first=finder.findOffers(rice);
        clock.now=clock.now.plusSeconds(901);assertFalse(finder.isCurrent(first));
        TestFixtures.invalid(() -> finder.calculatePurchaseCost(rice,first.getOffers()[0]));
        finder.findOffers(rice);assertEquals(2,http.searchCalls);
        clock.now=clock.now.plusSeconds(3601);
        failure(() -> finder.findOffers(rice),"stale");
    }
    public void testPackageParsing()
    {
        size("Rice 16 oz (454 g)",Unit.GRAM,16,Unit.OUNCE);
        size("Milk, Gallon",Unit.LITER,128,Unit.FLUID_OUNCE);
        size("Milk, Half Gallon, 64 fl oz",Unit.LITER,0.5*128,Unit.FLUID_OUNCE);
        size("Butter, 4 Sticks, 1 lb",Unit.GRAM,1,Unit.POUND);
        size("Large eggs, 12 Ct, 24 oz",Unit.COUNT,12,Unit.COUNT);
        size("Oil 17 fl oz",Unit.LITER,17,Unit.FLUID_OUNCE);
        for(String value:new String[]{"Rice 400 g (1 lb)","Chicken 0.7-2.9 lb","Rice 4 x 100 g", "Rice pack of 4, 100g","Milk 64 oz","Rice", "Oil 16 oz"})
        {assertNull(value,PriceFinder.packageSize(value,Unit.GRAM));}
    }
    public void testFoodForms()
    {
        assertEquals("",PriceFinder.matchWarning("Rice","White rice, 400 g"));
        assertFalse(PriceFinder.matchWarning("Milk","Chocolate milk 1 gallon").isEmpty());
        assertFalse(PriceFinder.matchWarning("Tomatoes","Tomato ketchup 16 oz").isEmpty());
        assertFalse(PriceFinder.matchWarning("Chicken breast","Cooked chicken breast bites").isEmpty());
        assertFalse(PriceFinder.matchWarning("Salted butter","Unsalted butter 1 lb").isEmpty());
    }
    public void testConfigurationIsLocalAndEnvironmentWins() throws Exception
    {
        Path dir=TestFixtures.temporaryDirectory();
        try
        {
            Path file=dir.resolve(".env.local");Files.writeString(file,"SERPAPI_API_KEY=local-fake-key\nWALMART_STORE_ID=1852\n");
            String[] values=PriceFinder.readConfiguration(file,Collections.singletonMap("SERPAPI_API_KEY","env-fake-key"));
            assertEquals("env-fake-key",values[0]);assertEquals("1852",values[1]);
            Files.writeString(file,"SERPAPI_API_KEY=\"local-fake-key\"\n");
            assertEquals("local-fake-key",PriceFinder.readConfiguration(file,Collections.emptyMap())[0]);
            TestFixtures.invalid(() -> new PriceFinder(URI.create("http://remote.test/"),"test-api-key",http,new IngredientMatcher()));
        }
        finally{TestFixtures.deleteDirectory(dir);}
    }
    public void testConfigurationFoundOutsideProjectWorkingDirectory() throws Exception
    {
        Path dir=TestFixtures.temporaryDirectory();
        try
        {
            Path project=dir.resolve("PantryPal");
            Path classes=project.resolve("build/classes");
            Path eclipse=project.resolve("bin");
            Path elsewhere=dir.resolve("elsewhere");
            Files.createDirectories(classes);Files.createDirectories(eclipse);
            Files.createDirectories(elsewhere);Files.createDirectories(project.resolve("src"));
            Files.writeString(project.resolve("src/PantryPalGUI.java"), "// Project marker");
            Path config=project.resolve(".env.local");
            Files.writeString(config, "SERPAPI_API_KEY=local-fake-key\n");
            assertEquals(config, PriceFinder.configurationPath(elsewhere, classes, null));
            assertEquals(config, PriceFinder.configurationPath(elsewhere, eclipse, null));
            assertEquals("local-fake-key", PriceFinder.readConfiguration(
                PriceFinder.configurationPath(elsewhere, classes, null), Collections.emptyMap())[0]);
            Files.writeString(elsewhere.resolve(".env.local"), "SERPAPI_API_KEY=working-fake-key\n");
            assertEquals(elsewhere.resolve(".env.local"),
                PriceFinder.configurationPath(elsewhere, classes, null));
            Path override=elsewhere.resolve("custom.properties");
            assertEquals(override, PriceFinder.configurationPath(elsewhere, classes, "custom.properties"));
            assertEquals(config, PriceFinder.configurationPath(elsewhere, classes, config.toString()));
        }
        finally { TestFixtures.deleteDirectory(dir); }
    }

    public void testConfigurationBesideJarAndNoUnrelatedAncestorFallback() throws Exception
    {
        Path dir=TestFixtures.temporaryDirectory();
        try
        {
            Path deployed=dir.resolve("deployed");Path elsewhere=dir.resolve("elsewhere");
            Files.createDirectories(deployed);Files.createDirectories(elsewhere);
            Path jar=deployed.resolve("PantryPal.jar");Files.writeString(jar, "placeholder");
            Path config=deployed.resolve(".env.local");Files.writeString(config, "SERPAPI_API_KEY=fake-key\n");
            assertEquals(config, PriceFinder.configurationPath(elsewhere, jar, null));
            Files.delete(config);Files.writeString(dir.resolve(".env.local"), "SERPAPI_API_KEY=unrelated-key\n");
            assertEquals(elsewhere.resolve(".env.local"), PriceFinder.configurationPath(elsewhere, jar, null));
            assertEquals(elsewhere.resolve(".env.local"), PriceFinder.configurationPath(elsewhere, null, null));
        }
        finally { TestFixtures.deleteDirectory(dir); }
    }

    public void testBoundedBody() throws Exception
    {
        java.net.http.HttpResponse.BodySubscriber<String> body=PriceFinder.limitedBody();
        final boolean[] cancelled={false};
        body.onSubscribe(new Flow.Subscription(){public void request(long n){}public void cancel(){cancelled[0]=true;}});
        body.onNext(Collections.singletonList(ByteBuffer.wrap(new byte[2_000_001])));
        assertTrue(cancelled[0]);assertTrue(body.getBody().toCompletableFuture().isCompletedExceptionally());
    }
    private void size(String title,Unit requested,double amount,Unit unit)
    {double[] actual=PriceFinder.packageSize(title,requested);assertNotNull(title,actual);assertEquals(amount,actual[0],0.00001);assertEquals(unit.ordinal(),(int)actual[1]);}
    private interface Action {void run() throws Exception;}
    private void failure(Action action,String message) throws Exception
    {
        try{action.run();fail("Expected failure");}
        catch(IOException expected){assertTrue(expected.getMessage(),expected.getMessage().contains(message));
            assertFalse(expected.toString().contains("test-api-key"));assertNull(expected.getCause());}
    }
    private static final class MutableClock extends Clock
    {
        private Instant now=PriceFixtures.NOW;
        @Override public ZoneId getZone(){return ZoneOffset.UTC;}
        @Override public Clock withZone(ZoneId zone){return this;}
        @Override public Instant instant(){return now;}
    }
}
