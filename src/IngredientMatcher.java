import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Matches ingredient names and converts units within a physical dimension.
 *
 * @author PantryPal team (GenAI-assisted implementation)
 * @version 2026.10.14
 */
public class IngredientMatcher
{
    private Map<String, String> aliases;


    /**
     * Creates the matcher with a small documented alias set.
     */
    public IngredientMatcher()
    {
        this(defaultAliases());
    }


    /**
     * Copies, normalizes, and validates the supplied alias map.
     * @param aliases mapping of alternative names to canonical names
     * @throws IllegalArgumentException if aliases are invalid or cyclic
     */
    public IngredientMatcher(Map<String, String> aliases)
    {
        if (aliases == null)
        {
            throw new IllegalArgumentException("Aliases must not be null.");
        }
        this.aliases = new HashMap<String, String>();
        for (Map.Entry<String, String> entry : aliases.entrySet())
        {
            String key = cleanName(entry.getKey());
            String value = cleanName(entry.getValue());
            String existing = this.aliases.putIfAbsent(key, value);
            if (existing != null && !existing.equals(value))
            {
                throw new IllegalArgumentException(
                    "Conflicting aliases for " + key);
            }
        }
        Map<String, String> resolved = new HashMap<String, String>();
        for (String key : this.aliases.keySet())
        {
            resolved.put(key, resolveAlias(key));
        }
        this.aliases = resolved;
    }


    /**
     * Normalizes case and whitespace, then resolves a known alias.
     * @param name display name
     * @return the canonical ingredient name
     * @throws IllegalArgumentException if name is null or blank
     */
    public String normalizeName(String name)
    {
        String normalized = cleanName(name);
        return aliases.getOrDefault(normalized, normalized);
    }


    /**
     * Compares normalized ingredient names without food substitutions.
     * @param firstName first ingredient name
     * @param secondName second ingredient name
     * @return whether both names resolve to the same name
     * @throws IllegalArgumentException if a name is null or blank
     */
    public boolean namesMatch(String firstName, String secondName)
    {
        return normalizeName(firstName).equals(normalizeName(secondName));
    }


    /**
     * Allows conversion only within mass, volume, or count.
     * @param firstUnit first measurement unit
     * @param secondUnit second measurement unit
     * @return whether the units share a measurement category
     * @throws IllegalArgumentException if either unit is null
     */
    public boolean areUnitsCompatible(Unit firstUnit, Unit secondUnit)
    {
        return category(firstUnit) == category(secondUnit);
    }


    /**
     * Converts compatible units using grams or milliliters as base units.
     * @param quantity amount in the specified unit
     * @param fromUnit original unit
     * @param toUnit target unit
     * @return the amount in the target unit
     * @throws IllegalArgumentException if units or quantity cannot be converted
     */
    public double convertQuantity(double quantity, Unit fromUnit, Unit toUnit)
    {
        if (!Double.isFinite(quantity) || quantity < 0)
        {
            throw new IllegalArgumentException(
                "Conversion quantity must be finite and nonnegative.");
        }
        if (!areUnitsCompatible(fromUnit, toUnit))
        {
            throw new IllegalArgumentException("Units are incompatible.");
        }
        double converted = quantity * (factor(fromUnit) / factor(toUnit));
        if (!Double.isFinite(converted) || (quantity > 0 && converted == 0))
        {
            throw new IllegalArgumentException(
                "Converted quantity cannot be represented.");
        }
        return converted;
    }


    /**
     * Supplies the aliases named in the specification's matching example.
     * @return a small alias map
     */
    private static Map<String, String> defaultAliases()
    {
        Map<String, String> result = new HashMap<String, String>();
        result.put("scallion", "green onion");
        result.put("scallions", "green onion");
        result.put("green onions", "green onion");
        return result;
    }


    /**
     * Normalizes whitespace and case without resolving aliases.
     * @param name input name
     * @return normalized text
     */
    private static String cleanName(String name)
    {
        if (name == null || name.isBlank())
        {
            throw new IllegalArgumentException("Name must not be blank.");
        }
        return name.strip().replaceAll("\\s+", " ")
            .toLowerCase(Locale.ROOT);
    }


    /**
     * Resolves alias chains and rejects cycles.
     * @param name normalized key
     * @return final canonical name
     */
    private String resolveAlias(String name)
    {
        Set<String> visited = new HashSet<String>();
        String current = name;
        while (aliases.containsKey(current))
        {
            if (!visited.add(current))
            {
                throw new IllegalArgumentException("Alias cycle detected.");
            }
            String next = aliases.get(current);
            if (next.equals(current))
            {
                return current;
            }
            current = next;
        }
        return current;
    }


    /**
     * Identifies the physical dimension of a unit.
     * @param unit measurement unit
     * @return 0 for mass, 1 for volume, or 2 for count
     */
    private static int category(Unit unit)
    {
        if (unit == null)
        {
            throw new IllegalArgumentException("Unit must not be null.");
        }
        switch (unit)
        {
            case GRAM:
            case KILOGRAM:
            case OUNCE:
            case POUND:
                return 0;
            case MILLILITER:
            case LITER:
            case TEASPOON:
            case TABLESPOON:
            case CUP:
            case FLUID_OUNCE:
                return 1;
            case COUNT:
                return 2;
            default:
                throw new IllegalArgumentException("Unsupported unit.");
        }
    }


    /**
     * Returns grams, milliliters, or items per unit; volume measures are US.
     * @param unit supported measurement unit
     * @return positive conversion factor
     */
    private static double factor(Unit unit)
    {
        switch (unit)
        {
            case GRAM:
            case MILLILITER:
            case COUNT:
                return 1;
            case KILOGRAM:
            case LITER:
                return 1000;
            case OUNCE:
                return 28.349523125;
            case POUND:
                return 453.59237;
            case TEASPOON:
                return 4.92892159375;
            case TABLESPOON:
                return 14.78676478125;
            case CUP:
                return 236.5882365;
            case FLUID_OUNCE:
                return 29.5735295625;
            default:
                throw new IllegalArgumentException("Unsupported unit.");
        }
    }
}
