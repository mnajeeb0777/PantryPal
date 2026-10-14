/**
 * An immutable comparison for one ingredient requirement.
 * Available and missing quantities use the requirement's unit.
 *
 * @author PantryPal team (GenAI-assisted implementation)
 * @version 2026.10.14
 */
public class IngredientMatch
{
    private final Ingredient requiredIngredient;
    private final Double availableQuantity;


    /**
     * Copies the requirement and validates the available amount.
     * @param requiredIngredient requirement to snapshot
     * @param availableQuantity finite nonnegative amount, or null if unknown
     * @throws IllegalArgumentException if a supplied value is invalid
     */
    public IngredientMatch(
        Ingredient requiredIngredient,
        Double availableQuantity)
    {
        if (requiredIngredient == null)
        {
            throw new IllegalArgumentException(
                "Required ingredient must not be null.");
        }
        if (availableQuantity != null
            && (!Double.isFinite(availableQuantity) || availableQuantity < 0))
        {
            throw new IllegalArgumentException(
                "Available quantity must be finite and nonnegative.");
        }
        this.requiredIngredient = new Ingredient(requiredIngredient);
        this.availableQuantity = availableQuantity;
    }


    /**
     * Returns an independent snapshot of the requirement.
     * @return the required ingredient, retaining its ID, amount, and unit
     */
    public Ingredient getRequiredIngredient()
    {
        return new Ingredient(requiredIngredient);
    }


    /**
     * Returns the available amount in the requirement's unit.
     * @return a nonnegative amount, or null if comparison is impossible
     */
    public Double getAvailableQuantity()
    {
        return availableQuantity;
    }


    /**
     * Calculates a shortage, treating conversion roundoff as zero.
     * @return the positive shortage, zero if sufficient, or null if unknown
     */
    public Double getMissingQuantity()
    {
        if (!canCompareQuantities())
        {
            return null;
        }
        double required = requiredIngredient.getQuantity();
        double missing = Math.max(required - availableQuantity, 0);
        return isRoundoff(missing, required, availableQuantity) ? 0 : missing;
    }


    /**
     * Distinguishes a known amount (including zero) from an unknown amount.
     * @return whether quantities can be compared
     */
    public boolean canCompareQuantities()
    {
        return availableQuantity != null;
    }


    /**
     * Checks availability using the same roundoff policy as pantry deductions.
     * @return true only if the amount is comparable and has no shortage
     */
    public boolean isSufficient()
    {
        Double missing = getMissingQuantity();
        return missing != null && missing == 0;
    }


    /**
     * Matches Pantry's four-ULP and relative-difference roundoff limits.
     * @param difference residual amount
     * @param first first operand
     * @param second second operand
     * @return whether the residual is representational rounding
     */
    private static boolean isRoundoff(
        double difference,
        double first,
        double second)
    {
        double scale = Math.max(Math.abs(first), Math.abs(second));
        return difference == 0 || (Math.abs(difference) / scale <= 1e-12
            && Math.abs(difference) <= 4 * Math.ulp(scale));
    }
}
