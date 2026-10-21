import java.math.BigDecimal;
import java.net.URI;
import student.TestCase;

/** Explicit unknown values, validation and immutable offer identity. */
public class StoreOfferTest extends TestCase
{
    public void testFixedPackage()
    {
        StoreOffer offer = PriceFixtures.offer("rice", 400, Unit.GRAM, "2.50");
        assertEquals("Walmart", offer.getStoreName());
        assertEquals("1852", offer.getStoreId());
        assertEquals("Lake Ridge, VA", offer.getStoreLocation());
        assertEquals("Rice", offer.getIngredientName());
        assertEquals("Rice package", offer.getProductName());
        assertEquals(400.0, offer.getPackageQuantity().doubleValue(), 0.0);
        assertEquals(Unit.GRAM, offer.getUnit());
        assertEquals(PriceFixtures.NOW, offer.getCheckedAt());
        assertEquals(PriceFixtures.NOW, offer.getSourceTime());
        assertTrue(offer.isInStock()); assertTrue(offer.hasComparablePackage());
    }
    public void testUnknownAndVariablePackages()
    {
        StoreOffer offer = unknown(StoreOffer.PriceBasis.UNKNOWN, StoreOffer.Availability.UNKNOWN, "");
        assertNull(offer.getPackageQuantity()); assertNull(offer.getUnit());
        assertFalse(offer.isInStock()); assertFalse(offer.hasComparablePackage());
        assertFalse(unknown(StoreOffer.PriceBasis.PACK_WEIGHT, StoreOffer.Availability.IN_STOCK, "").hasComparablePackage());
        assertEquals("Review promotion", unknown(StoreOffer.PriceBasis.EACH,
            StoreOffer.Availability.IN_STOCK, "Review promotion").getExclusionReason());
    }
    public void testInvalidPriceAndLinks()
    {
        TestFixtures.invalid(() -> PriceFixtures.offer("a", 1, Unit.GRAM, "0"));
        TestFixtures.invalid(() -> PriceFixtures.offer("a", Double.NaN, Unit.GRAM, "2"));
        TestFixtures.invalid(() -> new StoreOffer("a", "Rice", "Rice", "1852", "VA",
            URI.create("https://evil.test/ip/a"), BigDecimal.ONE, 1.0, Unit.GRAM,
            StoreOffer.PriceBasis.EACH, StoreOffer.Availability.IN_STOCK, PriceFixtures.NOW,
            null, "", ""));
        TestFixtures.invalid(() -> new StoreOffer("a", "Rice", "Rice", "1852", "VA",
            null, BigDecimal.ONE, null, Unit.GRAM, StoreOffer.PriceBasis.EACH,
            StoreOffer.Availability.IN_STOCK, PriceFixtures.NOW, null, "", ""));
    }
    private StoreOffer unknown(StoreOffer.PriceBasis basis, StoreOffer.Availability stock, String reason)
    {
        return new StoreOffer("a", "Rice", "Rice", "1852", "VA", null, BigDecimal.ONE,
            null, null, basis, stock, PriceFixtures.NOW, null, "$1/lb", reason);
    }
}
