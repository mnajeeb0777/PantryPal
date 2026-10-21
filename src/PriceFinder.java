import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Walmart candidates and sufficient-package estimates; construction makes no requests. */
public class PriceFinder
{
    private static final int BODY_LIMIT = 2_000_000;
    private static final Duration CACHE_LIFE = Duration.ofMinutes(15);
    private final URI apiBaseUri;
    private final String apiKey;
    private HttpClient httpClient;
    private final IngredientMatcher ingredientMatcher;
    private final Clock clock;
    private final String referenceStore;
    private final Map<String, SearchResult> cache = new LinkedHashMap<String, SearchResult>();
    private Instant quotaChecked;
    private int searchesRemaining = -1;
    private static final Pattern SIZE = Pattern.compile(
        "(?i)(\\d+(?:\\.\\d+)?)\\s*(fluid\\s+ounces?|fl\\.?\\s*oz\\.?|"
        + "kilograms?|kg|grams?|g|milliliters?|ml|liters?|litres?|l|"
        + "pounds?|lbs?\\.?|ounces?|oz\\.?|gallons?|quarts?|pints?|ct|counts?|pcs?)\\b");

    /** A bounded immutable result; empty candidates are a successful empty search. */
    public static final class SearchResult
    {
        private final StoreOffer[] offers;
        private final String storeId;
        private final String location;
        private final Instant checkedAt;
        private final Instant expiresAt;
        private final int excluded;
        SearchResult(List<StoreOffer> offers, String storeId, String location,
            Instant checkedAt, Instant expiresAt, int excluded)
        {
            this.offers = offers.toArray(new StoreOffer[0]);
            this.storeId = storeId;
            this.location = location;
            this.checkedAt = checkedAt;
            this.expiresAt = expiresAt;
            this.excluded = excluded;
        }
        public StoreOffer[] getOffers() { return offers.clone(); }
        public String getStoreId() { return storeId; }
        public String getLocation() { return location; }
        public Instant getCheckedAt() { return checkedAt; }
        public Instant getExpiresAt() { return expiresAt; }
        public int getExcludedCount() { return excluded; }
    }

    /** Injects transport and matching. The default reference is verified store 1852. */
    public PriceFinder(URI apiBaseUri, String apiKey, HttpClient httpClient,
        IngredientMatcher ingredientMatcher)
    {
        this(apiBaseUri, apiKey, httpClient, ingredientMatcher, "1852", Clock.systemUTC());
    }

    /** Fully injectable construction for deterministic tests and reference-store configuration. */
    public PriceFinder(URI apiBaseUri, String apiKey, HttpClient httpClient,
        IngredientMatcher ingredientMatcher, String referenceStore, Clock clock)
    {
        if (apiBaseUri == null || apiBaseUri.getHost() == null
            || !("https".equals(apiBaseUri.getScheme()) || ("http".equals(apiBaseUri.getScheme())
                && ("localhost".equals(apiBaseUri.getHost()) || "127.0.0.1".equals(apiBaseUri.getHost()))))
            || apiBaseUri.getQuery() != null || apiBaseUri.getFragment() != null
            || apiBaseUri.getUserInfo() != null || apiKey == null
            || !apiKey.matches("[A-Za-z0-9_-]{8,200}") || ingredientMatcher == null || clock == null)
        { throw new IllegalArgumentException("Invalid price-service configuration."); }
        validateStore(referenceStore);
        this.apiBaseUri = URI.create(apiBaseUri.toString().replaceAll("/+$", "") + "/");
        this.apiKey = apiKey;
        this.httpClient = httpClient;
        this.ingredientMatcher = ingredientMatcher;
        this.referenceStore = referenceStore;
        this.clock = clock;
    }

    /** Reads named settings without saving secrets, executing configuration, or making requests. */
    public static PriceFinder configuredClient()
    {
        if (Boolean.getBoolean("pantrypal.pricingDisabled")) { return null; }
        try
        {
            Path path = configurationPath(Path.of(System.getProperty("user.dir")),
                applicationLocation(), System.getProperty("pantrypal.priceConfig"));
            Map<String, String> environment = new LinkedHashMap<String, String>();
            environment.put("SERPAPI_API_KEY", System.getenv("SERPAPI_API_KEY"));
            environment.put("WALMART_STORE_ID", System.getenv("WALMART_STORE_ID"));
            String[] values = readConfiguration(path, environment);
            return values[0].isBlank() ? null : new PriceFinder(URI.create("https://serpapi.com/"),
                values[0], null, new IngredientMatcher(), values[1], Clock.systemUTC());
        }
        catch (IOException | IllegalArgumentException exception) { return null; }
    }

    private static Path applicationLocation()
    {
        try
        {
            java.security.CodeSource source = PriceFinder.class.getProtectionDomain().getCodeSource();
            return source == null ? null : Path.of(source.getLocation().toURI());
        }
        catch (java.net.URISyntaxException | IllegalArgumentException | SecurityException exception)
        { return null; }
    }

    /** Resolves local settings independently of an IDE or shortcut's working directory. */
    static Path configurationPath(Path workingDirectory, Path application, String override)
    {
        if (override != null)
        { return workingDirectory.resolve(Path.of(override)).normalize(); }
        Path local = workingDirectory.resolve(".env.local");
        if (Files.exists(local)) { return local; }
        Path directory = application == null ? null
            : Files.isRegularFile(application) ? application.getParent() : application;
        for (int depth=0; directory != null && depth<=3; depth++, directory=directory.getParent())
        {
            Path candidate = directory.resolve(".env.local");
            boolean projectRoot = Files.isRegularFile(directory.resolve("src/PantryPalGUI.java"));
            if ((depth == 0 || projectRoot) && Files.isRegularFile(candidate)) { return candidate; }
        }
        return local;
    }

    /** Package-level configuration seam: tests never read the user's real credentials. */
    static String[] readConfiguration(Path path, Map<String, String> environment) throws IOException
    {
        Properties settings = new Properties();
        if (Files.exists(path))
        {
            if (Files.size(path) > 16_384) { throw new IOException("Price configuration is too large."); }
            String text = Files.readString(path, StandardCharsets.UTF_8).replace("\uFEFF", "");
            settings.load(new java.io.StringReader(text));
        }
        String key = setting(environment.get("SERPAPI_API_KEY"), settings.getProperty("SERPAPI_API_KEY", ""));
        String store = setting(environment.get("WALMART_STORE_ID"), settings.getProperty("WALMART_STORE_ID", "1852"));
        return new String[] { key, store };
    }

    private static String setting(String preferred, String fallback)
    {
        String value = preferred == null || preferred.isBlank() ? fallback.strip() : preferred.strip();
        if (value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\""))
            || (value.startsWith("'") && value.endsWith("'"))))
        { value = value.substring(1, value.length() - 1); }
        return value;
    }

    public String getReferenceStore() { return referenceStore; }

    /** Fetches one page. A synchronized cache serializes requests and deduplicates concurrent work. */
    public synchronized SearchResult findOffers(Ingredient shortage, String storeId)
        throws IOException, InterruptedException
    {
        if (shortage == null) { throw new IllegalArgumentException("A shortage is required."); }
        validateStore(storeId);
        if (Thread.currentThread().isInterrupted()) { throw new InterruptedException("Search cancelled."); }
        String query = ingredientMatcher.normalizeName(shortage.getName());
        if (query.length() > 200) { throw new IllegalArgumentException("Ingredient name is too long to search."); }
        String key = storeId + ":" + query + ":" + shortage.getUnit();
        SearchResult previous = cache.get(key);
        if (previous != null && clock.instant().isBefore(previous.expiresAt)) { return previous; }
        checkQuota();
        if (searchesRemaining <= 0) { throw new IOException("SerpApi search quota is exhausted."); }
        searchesRemaining--; // Conservatively reserve a credit even if the response fails.
        Map<?, ?> root = request("search.json", "engine=walmart&walmart_domain=walmart.com&page=1&store_id="
            + storeId + "&query=" + URLEncoder.encode(query, StandardCharsets.UTF_8));
        SearchResult result = parseResponse(root, shortage, storeId, clock.instant());
        cache.put(key, result);
        while (cache.size() > 100) { cache.remove(cache.keySet().iterator().next()); }
        return result;
    }

    /** Searches only the configured reference store. */
    public SearchResult findOffers(Ingredient shortage) throws IOException, InterruptedException
    { return findOffers(shortage, referenceStore); }

    /** Forces no requests: callers can label whether an existing result is still usable. */
    public boolean isCurrent(SearchResult result)
    { return result != null && clock.instant().isBefore(result.expiresAt); }

    private void checkQuota() throws IOException, InterruptedException
    {
        if (quotaChecked == null || !clock.instant().isBefore(quotaChecked.plusSeconds(60)))
        {
            Map<?, ?> account = request("account.json", "");
            Object remaining = account.get("total_searches_left");
            if (!(remaining instanceof BigDecimal))
            { throw new IOException("Unable to verify SerpApi quota. Try again later."); }
            try { searchesRemaining = ((BigDecimal)remaining).intValueExact(); }
            catch (ArithmeticException exception) { throw new IOException("Invalid quota response."); }
            quotaChecked = clock.instant();
        }
    }

    private Map<?, ?> request(String endpoint, String parameters) throws IOException, InterruptedException
    {
        HttpRequest request = HttpRequest.newBuilder(apiBaseUri.resolve(endpoint + "?" + parameters
            + (parameters.isEmpty() ? "" : "&") + "api_key=" + apiKey))
            .timeout(Duration.ofSeconds(45)).header("Accept", "application/json")
            .header("User-Agent", "PantryPal/1.0 (course project)").GET().build();
        HttpResponse<String> response;
        try
        {
            if (httpClient == null)
            { httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER).build(); }
            response = httpClient.send(request, info -> limitedBody());
        }
        catch (IOException | java.io.UncheckedIOException exception)
        { throw new IOException("Walmart price search could not connect or timed out. Try again later."); }
        catch (InterruptedException exception) { throw new InterruptedException("Price search cancelled."); }
        int status = response.statusCode();
        if (status == 401 || status == 403) { throw new IOException("SerpApi access was denied. Check your local API key and plan."); }
        if (status == 402) { searchesRemaining = 0; throw new IOException("SerpApi search quota is exhausted."); }
        if (status == 429) { throw new IOException("SerpApi rate limit reached. Wait before retrying."); }
        if (status != 200) { throw new IOException("Price service returned HTTP " + status + ". Try again later."); }
        Object parsed = Json.parse(response.body());
        if (!(parsed instanceof Map<?, ?>)) { throw new IOException("Invalid price-service response."); }
        Map<?, ?> root = (Map<?, ?>)parsed;
        if (root.containsKey("error")) { throw new IOException("Price provider reported an error. Check quota or retry later."); }
        return root;
    }

    /** Stops the HTTP subscription before buffering more than the bounded response size. */
    static HttpResponse.BodySubscriber<String> limitedBody()
    {
        return new HttpResponse.BodySubscriber<String>()
        {
            private final CompletableFuture<String> body = new CompletableFuture<String>();
            private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            private Flow.Subscription subscription;
            @Override public CompletionStage<String> getBody() { return body; }
            @Override public void onSubscribe(Flow.Subscription value)
            { subscription = value; value.request(1); }
            @Override public void onNext(List<ByteBuffer> buffers)
            {
                if (body.isDone()) { return; }
                for (ByteBuffer buffer : buffers)
                {
                    if (buffer.remaining() > BODY_LIMIT - bytes.size())
                    {
                        subscription.cancel();
                        body.completeExceptionally(new IOException("Price response is too large."));
                        return;
                    }
                    byte[] chunk = new byte[buffer.remaining()];
                    buffer.get(chunk); bytes.write(chunk, 0, chunk.length);
                }
                subscription.request(1);
            }
            @Override public void onError(Throwable error) { body.completeExceptionally(error); }
            @Override public void onComplete()
            { body.complete(new String(bytes.toByteArray(), StandardCharsets.UTF_8)); }
        };
    }

    /** Decodes one response, retaining uncertain package data as display-only candidates. */
    static SearchResult parseResponse(Map<?, ?> root, Ingredient ingredient, String storeId, Instant now)
        throws IOException
    {
        Map<?, ?> metadata = object(root.get("search_metadata"));
        if (!"Success".equals(metadata.get("status"))) { throw new IOException("Price search did not finish successfully."); }
        Map<?, ?> location = object(object(root.get("search_information")).get("location"));
        if (!storeId.equals(text(location.get("store_id"))))
        { throw new IOException("Walmart returned a different or unknown reference store. Estimate stopped."); }
        String city = text(location.get("city"));
        String state = text(location.get("province_code"));
        String label = city.isEmpty() ? "Store " + storeId : city + (state.isEmpty() ? "" : ", " + state);
        Instant source = sourceTime(text(metadata.get("created_at")));
        Instant expires = now.plus(CACHE_LIFE);
        if (source != null)
        {
            if (source.isAfter(now.plusSeconds(300)) || !now.isBefore(source.plusSeconds(3600)))
            { throw new IOException("Provider prices are stale or have an invalid timestamp. Retry later."); }
            if (source.plusSeconds(3600).isBefore(expires)) { expires = source.plusSeconds(3600); }
        }
        Object raw = root.get("organic_results");
        if (!(raw instanceof List<?>))
        {
            if ("Fully empty".equals(object(root.get("search_information")).get("organic_results_state")))
            { raw = Collections.emptyList(); }
            else { throw new IOException("Price response is missing its product list."); }
        }
        List<StoreOffer> offers = new ArrayList<StoreOffer>();
        java.util.Set<String> seen = new java.util.HashSet<String>();
        int excluded = 0;
        for (Object value : (List<?>)raw)
        {
            if (offers.size() >= 100) { break; }
            Map<?, ?> row = object(value);
            String seller = text(row.get("seller_name")).toLowerCase(Locale.ROOT);
            if (!(seller.equals("walmart.com") || seller.equals("walmart"))) { excluded++; continue; }
            String title = text(row.get("title"));
            String id = text(row.get("product_id"));
            if (id.isEmpty()) { id = text(row.get("us_item_id")); }
            Map<?, ?> price = object(row.get("primary_offer"));
            Object amount = price.get("offer_price");
            if (id.isEmpty() || title.isEmpty() || !(amount instanceof BigDecimal)
                || !"USD".equals(price.get("currency")) || ((BigDecimal)amount).signum() <= 0
                || ((BigDecimal)amount).compareTo(new BigDecimal("1000000")) > 0
                || !seen.add(id + ":" + text(price.get("offer_id")))) { excluded++; continue; }
            StoreOffer.Availability stock = Boolean.FALSE.equals(row.get("out_of_stock"))
                ? StoreOffer.Availability.IN_STOCK : Boolean.TRUE.equals(row.get("out_of_stock"))
                ? StoreOffer.Availability.OUT_OF_STOCK : StoreOffer.Availability.UNKNOWN;
            Map<?, ?> perUnit = object(row.get("price_per_unit"));
            StoreOffer.PriceBasis basis;
            try { basis = StoreOffer.PriceBasis.valueOf(text(perUnit.get("unit")).toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException exception) { basis = StoreOffer.PriceBasis.UNKNOWN; }
            double[] parsed = packageSize(title, ingredient.getUnit());
            Double quantity = parsed == null ? null : parsed[0];
            Unit unit = parsed == null ? null : Unit.values()[(int)parsed[1]];
            String reason = matchWarning(ingredient.getName(), title);
            if (reason.isEmpty() && conditional(row, price))
            { reason = "Promotion, price range, or package variant needs review."; }
            URI link = null;
            try
            {
                String candidate = text(row.get("product_page_url"));
                if (!candidate.isBlank())
                {
                    URI uri = URI.create(candidate);
                    if ("https".equals(uri.getScheme()) && ("www.walmart.com".equals(uri.getHost())
                        || "walmart.com".equals(uri.getHost())) && uri.getPath().startsWith("/ip/")
                        && uri.getUserInfo() == null && uri.getPort() == -1)
                    { link = uri; }
                }
            }
            catch (IllegalArgumentException exception) { /* Unsafe links remain absent. */ }
            offers.add(new StoreOffer(id, title, ingredient.getName(), storeId, label, link,
                (BigDecimal)amount, quantity, unit, basis, stock, now, source,
                text(perUnit.get("amount")), reason));
        }
        return new SearchResult(offers, storeId, label, now, expires, excluded);
    }

    private static boolean conditional(Map<?, ?> row, Map<?, ?> price)
    {
        Object low = price.get("min_price");
        Object high = price.get("max_price");
        return (low instanceof BigDecimal && high instanceof BigDecimal
            && ((BigDecimal)low).compareTo((BigDecimal)high) != 0)
            || Boolean.TRUE.equals(row.get("is_subscription"))
            || text(row.get("title")).toLowerCase(Locale.ROOT).contains("subscribe")
            || text(row.get("special_offer_text")).toLowerCase(Locale.ROOT).matches(".*(coupon|member|buy.*get|subscription).*");
    }

    private static Map<?, ?> object(Object value)
    { return value instanceof Map<?, ?> ? (Map<?, ?>)value : Collections.emptyMap(); }
    private static String text(Object value)
    { return value instanceof String ? ((String)value).strip() : value instanceof BigDecimal ? value.toString() : ""; }
    private static Instant sourceTime(String value)
    {
        try { return LocalDateTime.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'"))
                .toInstant(ZoneOffset.UTC); }
        catch (DateTimeParseException exception) { return null; }
    }
    private static void validateStore(String value)
    { if (value == null || !value.matches("[0-9]{1,10}")) { throw new IllegalArgumentException("A numeric Walmart reference store is required."); } }

    /** Conservative size parser: contradictions, ranges and unclear multipacks remain unknown. */
    static double[] packageSize(String title, Unit requiredUnit)
    {
        String value = title.toLowerCase(Locale.ROOT).replace("half gallon", "0.5 gallon");
        if (value.matches(".*\\d(?:\\.\\d+)?\\s*[-–]\\s*\\d.*")
            || value.matches(".*\\b\\d+\\s*[x×]\\s*\\d.*")
            || value.matches(".*\\b(?:pack of|case of|set of)\\s*\\d.*")
            || value.matches(".*\\b\\d+\\s*[- ]?packs?\\b.*")) { return null; }
        if (!value.matches(".*\\d.*") && value.matches(".*\\bgallon\\b.*")) { value += " 1 gallon"; }
        else if (value.matches(".*(?:,| )gallon\\b.*") && !value.matches(".*\\d+\\s*gallon.*")) { value += " 1 gallon"; }
        if (value.contains("dozen")) { value += " 12 count"; }
        Matcher matcher = SIZE.matcher(value);
        List<double[]> measures = new ArrayList<double[]>();
        while (matcher.find())
        {
            double n;
            try { n = Double.parseDouble(matcher.group(1)); }
            catch (NumberFormatException exception) { return null; }
            String symbol = matcher.group(2).replace(".", "").replaceAll("\\s+", "");
            Unit unit;
            if (symbol.startsWith("fl") || symbol.startsWith("fluid")) { unit = Unit.FLUID_OUNCE; }
            else if (symbol.equals("kg") || symbol.startsWith("kilo")) { unit = Unit.KILOGRAM; }
            else if (symbol.equals("g") || symbol.startsWith("gram")) { unit = Unit.GRAM; }
            else if (symbol.equals("ml") || symbol.startsWith("millil")) { unit = Unit.MILLILITER; }
            else if (symbol.equals("l") || symbol.startsWith("lit")) { unit = Unit.LITER; }
            else if (symbol.startsWith("lb") || symbol.startsWith("pound")) { unit = Unit.POUND; }
            else if (symbol.equals("oz") || symbol.startsWith("ounce")) { unit = Unit.OUNCE; }
            else if (symbol.startsWith("gallon")) { unit = Unit.FLUID_OUNCE; n *= 128; }
            else if (symbol.startsWith("quart")) { unit = Unit.FLUID_OUNCE; n *= 32; }
            else if (symbol.startsWith("pint")) { unit = Unit.FLUID_OUNCE; n *= 16; }
            else { unit = Unit.COUNT; }
            if (!Double.isFinite(n) || n <= 0) { return null; }
            // A liquid's bare ounces do not establish mass or fluid volume.
            if (unit == Unit.OUNCE && value.matches(".*\\b(milk|oil|juice|water)\\b.*")) { return null; }
            if (unit == Unit.COUNT && requiredUnit != Unit.COUNT) { continue; }
            if (unit != Unit.COUNT && requiredUnit == Unit.COUNT
                && value.matches(".*\\b(eggs?|tomatoes|onions?|potatoes)\\b.*")) { continue; }
            measures.add(new double[] {n, unit.ordinal()});
        }
        if (measures.isEmpty()) { return null; }
        double[] first = measures.get(0);
        IngredientMatcher converter = new IngredientMatcher();
        for (double[] measure : measures)
        {
            Unit from = Unit.values()[(int)measure[1]];
            Unit to = Unit.values()[(int)first[1]];
            if (!converter.areUnitsCompatible(from, to)) { return null; }
            double converted = converter.convertQuantity(measure[0], from, to);
            if (Math.abs(converted - first[0]) > first[0] * 0.015) { return null; }
        }
        return first;
    }

    /** Obvious food-form conflicts are not repaired by selecting the listing. */
    static String matchWarning(String ingredient, String title)
    {
        String wanted = ingredient.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9 ]", " ");
        String candidate = title.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9 ]", " ");
        for (String token : wanted.split("\\s+"))
        {
            if (token.isEmpty() || token.matches("fresh|raw|of|and|the")) { continue; }
            String singular = token.endsWith("s") ? token.substring(0, token.length() - 1) : token;
            if (!Pattern.compile("\\b" + Pattern.quote(singular) + "(?:s|es)?\\b").matcher(candidate).find())
            { return "Ingredient name needs a different product search."; }
        }
        String[] conflicts = {"cooked", "ketchup", "soup", "sauce", "seasoning", "chocolate",
            "yogurt", "sweetener", "stevia", "plant", "almond", "coconut", "oatmilk",
            "powder", "dried", "canned", "unsalted", "slices", "shredded", "breaded",
            "nuggets", "bites", "substitute", "syrup", "mix", "evaporated", "condensed"};
        for (String conflict : conflicts)
        {
            if (Pattern.compile("\\b" + conflict + "\\b").matcher(candidate).find()
                && !Pattern.compile("\\b" + conflict + "\\b").matcher(wanted).find())
            { return "Food form differs from the requested ingredient."; }
        }
        return "";
    }

    /** Whole packages, with only representational roundoff removed at integer boundaries. */
    public int calculateRequiredPackages(Ingredient ingredient, StoreOffer offer)
    {
        requireComparable(ingredient, offer);
        double required = ingredientMatcher.convertQuantity(ingredient.getQuantity(),
            ingredient.getUnit(), offer.getUnit());
        double ratio = required / offer.getPackageQuantity();
        double nearest = Math.rint(ratio);
        if (Math.abs(ratio - nearest) <= 4 * Math.ulp(ratio)) { ratio = nearest; }
        if (!Double.isFinite(ratio) || ratio > Integer.MAX_VALUE || ratio <= 0)
        { throw new IllegalArgumentException("Required package count cannot be represented."); }
        return (int)Math.ceil(ratio);
    }

    public BigDecimal calculatePurchaseCost(Ingredient ingredient, StoreOffer offer)
    {
        int packages = calculateRequiredPackages(ingredient, offer);
        return offer.getPackagePrice().multiply(BigDecimal.valueOf(packages))
            .setScale(2, RoundingMode.HALF_UP);
    }

    private void requireComparable(Ingredient ingredient, StoreOffer offer)
    {
        if (ingredient == null || offer == null || !offer.hasComparablePackage()
            || !ingredientMatcher.namesMatch(ingredient.getName(), offer.getIngredientName())
            || !referenceStore.equals(offer.getStoreId())
            || !clock.instant().isBefore(offer.getCheckedAt().plus(CACHE_LIFE))
            || (offer.getSourceTime() != null && !clock.instant().isBefore(offer.getSourceTime().plusSeconds(3600)))
            || !ingredientMatcher.areUnitsCompatible(ingredient.getUnit(), offer.getUnit()))
        { throw new IllegalArgumentException("Listing cannot price this shortage; check units, store, package, availability and freshness."); }
    }

    /** Callers supply only food-form-reviewed candidates, all for the same ingredient/store. */
    public StoreOffer findCheapestOffer(Ingredient ingredient, StoreOffer[] offers)
    {
        if (ingredient == null || offers == null) { throw new IllegalArgumentException("Ingredient and offers are required."); }
        StoreOffer best = null;
        BigDecimal bestCost = null;
        for (StoreOffer offer : offers)
        {
            try
            {
                BigDecimal cost = calculatePurchaseCost(ingredient, offer);
                if (bestCost == null || cost.compareTo(bestCost) < 0) { best = offer; bestCost = cost; }
            }
            catch (IllegalArgumentException exception) { /* Unpriceable listings are not zero-cost offers. */ }
        }
        return best;
    }
}
