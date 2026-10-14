import java.util.UUID;

/**
 * One validated ingredient entry with identity independent of quantity.
 *
 * @author PantryPal team (GenAI-assisted implementation)
 * @version 2026.10.14
 */
public class Ingredient
{
    private final String id;
    private String name;
    private double quantity;
    private Unit unit;


    /**
     * Creates a validated ingredient with a stable UUID.
     * @param id stable entry identifier
     * @param name display name
     * @param quantity amount in the specified unit
     * @param unit measurement unit
     * @throws IllegalArgumentException if any ingredient value is invalid
     */
    public Ingredient(String id, String name, double quantity, Unit unit)
    {
        validateId(id);
        validateDetails(name, quantity, unit);
        this.id = id;
        this.name = name;
        this.quantity = quantity;
        this.unit = unit;
    }


    /**
     * Creates an independent copy retaining the original ID.
     * @param other object to copy or compare
     * @throws IllegalArgumentException if other is null
     */
    public Ingredient(Ingredient other)
    {
        if (other == null)
        {
            throw new IllegalArgumentException("Ingredient must not be null.");
        }
        id = other.id;
        name = other.name;
        quantity = other.quantity;
        unit = other.unit;
    }


    /**
     * Returns the stable identifier.
     * @return the ingredient ID
     */
    public String getId()
    {
        return id;
    }


    /**
     * Returns the ingredient name.
     * @return the display name
     */
    public String getName()
    {
        return name;
    }


    /**
     * Returns the amount in the ingredient's unit.
     * @return the positive quantity
     */
    public double getQuantity()
    {
        return quantity;
    }


    /**
     * Returns the measurement unit.
     * @return the unit
     */
    public Unit getUnit()
    {
        return unit;
    }


    /**
     * Validates all changes before updating any field.
     * @param newName new display name
     * @param newQuantity new amount in the specified unit
     * @param newUnit new measurement unit
     * @throws IllegalArgumentException if any new detail is invalid
     */
    public void updateDetails(
        String newName,
        double newQuantity,
        Unit newUnit)
    {
        validateDetails(newName, newQuantity, newUnit);
        name = newName;
        quantity = newQuantity;
        unit = newUnit;
    }


    /**
     * Changes the quantity after validating its range.
     * @param quantity amount in the specified unit
     * @throws IllegalArgumentException if the quantity is invalid
     */
    public void setQuantity(double quantity)
    {
        validateQuantity(quantity);
        this.quantity = quantity;
    }


    /**
     * Compares identity by ID, independent of mutable details.
     * @param other object to copy or compare
     * @return true for an ingredient with the same ID
     */
    @Override
    public boolean equals(Object other)
    {
        if (this == other)
        {
            return true;
        }
        if (other == null || getClass() != other.getClass())
        {
            return false;
        }
        Ingredient ingredient = (Ingredient)other;
        return id.equals(ingredient.id);
    }


    /**
     * Returns a hash consistent with ID-based equality.
     * @return the ID hash
     */
    @Override
    public int hashCode()
    {
        return id.hashCode();
    }


    /**
     * Validates UUID spelling without replacing the supplied identifier.
     * @param value identifier to validate
     */
    private static void validateId(String value)
    {
        if (value == null)
        {
            throw new IllegalArgumentException("ID must not be null.");
        }
        try
        {
            if (!UUID.fromString(value).toString().equalsIgnoreCase(value))
            {
                throw new IllegalArgumentException("ID must be a UUID.");
            }
        }
        catch (IllegalArgumentException exception)
        {
            throw new IllegalArgumentException("ID must be a UUID.",
                exception);
        }
    }


    /**
     * Checks all mutable values before allowing an update.
     * @param name proposed name
     * @param quantity proposed amount
     * @param unit proposed unit
     */
    private static void validateDetails(
        String name,
        double quantity,
        Unit unit)
    {
        if (name == null || name.isBlank()
            || !name.codePoints().anyMatch(Character::isLetter)
            || name.codePoints().anyMatch(Character::isISOControl))
        {
            throw new IllegalArgumentException(
                "Enter a valid ingredient name.");
        }
        if (name.length() > 100)
        {
            throw new IllegalArgumentException(
                "Ingredient names cannot exceed 100 characters.");
        }
        validateQuantity(quantity);
        if (unit == null)
        {
            throw new IllegalArgumentException("Unit must not be null.");
        }
    }


    /**
     * Checks the quantity range from the project specification.
     * @param quantity proposed amount
     */
    private static void validateQuantity(double quantity)
    {
        if (!Double.isFinite(quantity) || quantity > 1000000)
        {
            throw new IllegalArgumentException(
                "Enter a finite quantity greater than 0 "
                    + "and no greater than 1,000,000.");
        }
        if (quantity <= 0)
        {
            throw new IllegalArgumentException(
                "Quantity must be greater than 0.");
        }
    }
}
