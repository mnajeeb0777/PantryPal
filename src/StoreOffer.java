import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;

/** Immutable Walmart listing with explicit price basis and unknown measurements. */
public final class StoreOffer
{
    /** How the provider prices this listing. */
    public enum PriceBasis { EACH, PACK_WEIGHT, EACH_WEIGHT, UNKNOWN }
    /** Provider-reported stock, not an inventory guarantee. */
    public enum Availability { IN_STOCK, OUT_OF_STOCK, UNKNOWN }

    private final String productId;
    private final String productName;
    private final String ingredientName;
    private final String storeId;
    private final String storeLocation;
    private final URI productUrl;
    private final BigDecimal packagePrice;
    private final Double packageQuantity;
    private final Unit unit;
    private final PriceBasis priceBasis;
    private final Availability availability;
    private final Instant checkedAt;
    private final Instant sourceTime;
    private final String unitPriceText;
    private final String exclusionReason;

    /**
     * Copies a validated listing. Null quantity/unit together mean unknown.
     * @param productId provider product identifier
     * @param productName original listing title
     * @param ingredientName ingredient whose search produced this candidate
     * @param storeId verified reference store
     * @param storeLocation returned city/state
     * @param productUrl HTTPS Walmart product link, or null
     * @param packagePrice positive USD listing price
     * @param packageQuantity optional finite positive package amount
     * @param unit optional measurement unit
     * @param priceBasis provider price basis
     * @param availability provider stock status
     * @param checkedAt retrieval time
     * @param sourceTime optional provider search timestamp
     * @param unitPriceText original unit-price display
     * @param exclusionReason reason to exclude from fixed-package totals, or blank
     */
    public StoreOffer(String productId, String productName, String ingredientName,
        String storeId, String storeLocation, URI productUrl,
        BigDecimal packagePrice, Double packageQuantity, Unit unit,
        PriceBasis priceBasis, Availability availability, Instant checkedAt,
        Instant sourceTime, String unitPriceText, String exclusionReason)
    {
        if (productId == null || productId.isBlank() || productName == null
            || productName.isBlank() || ingredientName == null || ingredientName.isBlank()
            || storeId == null || !storeId.matches("[0-9]{1,10}")
            || storeLocation == null || storeLocation.isBlank()
            || packagePrice == null || packagePrice.signum() <= 0
            || packagePrice.compareTo(new BigDecimal("1000000")) > 0
            || priceBasis == null || availability == null || checkedAt == null
            || unitPriceText == null || exclusionReason == null
            || (packageQuantity == null) != (unit == null)
            || (packageQuantity != null && (!Double.isFinite(packageQuantity)
                || packageQuantity <= 0)))
        {
            throw new IllegalArgumentException("Invalid Walmart listing.");
        }
        if (productUrl != null && (!"https".equals(productUrl.getScheme())
            || !("www.walmart.com".equals(productUrl.getHost())
                || "walmart.com".equals(productUrl.getHost()))
            || !productUrl.getPath().startsWith("/ip/")
            || productUrl.getUserInfo() != null || productUrl.getPort() != -1))
        {
            throw new IllegalArgumentException("Invalid Walmart product link.");
        }
        this.productId = productId;
        this.productName = productName;
        this.ingredientName = ingredientName;
        this.storeId = storeId;
        this.storeLocation = storeLocation;
        this.productUrl = productUrl;
        this.packagePrice = packagePrice;
        this.packageQuantity = packageQuantity;
        this.unit = unit;
        this.priceBasis = priceBasis;
        this.availability = availability;
        this.checkedAt = checkedAt;
        this.sourceTime = sourceTime;
        this.unitPriceText = unitPriceText;
        this.exclusionReason = exclusionReason;
    }

    public String getProductId() { return productId; }
    public String getProductName() { return productName; }
    public String getIngredientName() { return ingredientName; }
    public String getStoreName() { return "Walmart"; }
    public String getStoreId() { return storeId; }
    public String getStoreLocation() { return storeLocation; }
    public URI getProductUrl() { return productUrl; }
    public BigDecimal getPackagePrice() { return packagePrice; }
    public Double getPackageQuantity() { return packageQuantity; }
    public Unit getUnit() { return unit; }
    public PriceBasis getPriceBasis() { return priceBasis; }
    public Availability getAvailability() { return availability; }
    public boolean isInStock() { return availability == Availability.IN_STOCK; }
    public Instant getCheckedAt() { return checkedAt; }
    public Instant getSourceTime() { return sourceTime; }
    public String getUnitPriceText() { return unitPriceText; }

    /** Why this listing cannot be used for a fixed-package estimate. */
    public String getExclusionReason()
    {
        if (!exclusionReason.isEmpty()) { return exclusionReason; }
        if (!isInStock()) { return "Availability is not confirmed."; }
        if (priceBasis != PriceBasis.EACH) { return "Variable or unknown price basis."; }
        if (packageQuantity == null) { return "Package size needs review."; }
        return "";
    }

    /** A usable package still requires the user's food-form confirmation. */
    public boolean hasComparablePackage() { return getExclusionReason().isEmpty(); }
}
