import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Owns pantry entries and applies all-or-nothing cooking deductions.
 *
 * @author PantryPal team (GenAI-assisted implementation)
 * @version 2026.10.14
 */
public class Pantry
{
    private LinkedChain<Ingredient> ingredients;


    /**
     * Creates an empty pantry.
     */
    public Pantry()
    {
        ingredients = new LinkedChain<Ingredient>();
    }


    /**
     * Creates a pantry from independent copies of unique entries.
     * @param initialIngredients initial pantry entries to copy
     * @throws IllegalArgumentException if entries are null or IDs repeat
     */
    public Pantry(Ingredient[] initialIngredients)
    {
        this();
        if (initialIngredients == null)
        {
            throw new IllegalArgumentException("Initial entries are null.");
        }
        Set<String> ids = new HashSet<String>();
        for (int i = initialIngredients.length - 1; i >= 0; i--)
        {
            Ingredient entry = initialIngredients[i];
            if (entry == null || !ids.add(entry.getId()))
            {
                throw new IllegalArgumentException(
                    "Initial entries must have unique IDs.");
            }
            ingredients.add(new Ingredient(entry));
        }
    }


    /**
     * Stores a copy unless this entry ID already exists.
     * @param ingredient ingredient entry to copy
     * @return false for a duplicate ID
     * @throws IllegalArgumentException if ingredient is null
     */
    public boolean addIngredient(Ingredient ingredient)
    {
        if (ingredient == null)
        {
            throw new IllegalArgumentException("Ingredient must not be null.");
        }
        if (ingredients.contains(ingredient))
        {
            return false;
        }
        return ingredients.add(new Ingredient(ingredient));
    }


    /**
     * Returns independent snapshots of all entries.
     * @return snapshots, or an empty array
     */
    public Ingredient[] getAllIngredients()
    {
        Ingredient[] result = entries();
        for (int i = 0; i < result.length; i++)
        {
            result[i] = new Ingredient(result[i]);
        }
        return result;
    }


    /**
     * Finds an entry by its exact ID without exposing stored data.
     * @param id stable entry identifier
     * @return a snapshot, or null if absent
     * @throws IllegalArgumentException if ID is null or blank
     */
    public Ingredient findIngredientById(String id)
    {
        Ingredient entry = findStored(id);
        return entry == null ? null : new Ingredient(entry);
    }


    /**
     * Finds entries by case-insensitive name substring.
     * @param query case-insensitive name substring; blank matches all
     * @return matching snapshots; blank query returns all
     * @throws IllegalArgumentException if query is null
     */
    public Ingredient[] searchIngredients(String query)
    {
        if (query == null)
        {
            throw new IllegalArgumentException("Query must not be null.");
        }
        String search = query.strip().toLowerCase(Locale.ROOT);
        Ingredient[] result = new Ingredient[ingredients.size()];
        int count = 0;
        for (Ingredient entry : entries())
        {
            if (entry.getName().toLowerCase(Locale.ROOT).contains(search))
            {
                result[count++] = new Ingredient(entry);
            }
        }
        return Arrays.copyOf(result, count);
    }


    /**
     * Updates a selected entry atomically after validating its details.
     * @param id stable entry identifier
     * @param name display name
     * @param quantity amount in the specified unit
     * @param unit measurement unit
     * @return false if no entry has the ID
     * @throws IllegalArgumentException if ID or new details are invalid
     */
    public boolean updateIngredient(
        String id,
        String name,
        double quantity,
        Unit unit)
    {
        Ingredient entry = findStored(id);
        if (entry == null)
        {
            return false;
        }
        entry.updateDetails(name, quantity, unit);
        return true;
    }


    /**
     * Removes only the entry with the selected ID.
     * @param id stable entry identifier
     * @return false if absent, including an empty pantry
     * @throws IllegalArgumentException if ID is null or blank
     */
    public boolean removeIngredient(String id)
    {
        Ingredient entry = findStored(id);
        return entry != null && ingredients.remove(entry);
    }


    /**
     * Allocates every requirement before committing any stock change.
     * @param requiredIngredients amounts to deduct together
     * @param matcher name and unit matching service
     * @return false on shortage; true after a complete deduction
     * @throws IllegalArgumentException if a requirement or matcher is invalid
     */
    public boolean deductIngredients(
        Ingredient[] requiredIngredients,
        IngredientMatcher matcher)
    {
        if (requiredIngredients == null || matcher == null)
        {
            throw new IllegalArgumentException(
                "Requirements and matcher must not be null.");
        }
        for (Ingredient required : requiredIngredients)
        {
            if (required == null)
            {
                throw new IllegalArgumentException(
                    "Requirements must not contain null.");
            }
        }
        Ingredient[] stock = getAllIngredients();
        double[] remaining = new double[stock.length];
        for (int i = 0; i < stock.length; i++)
        {
            remaining[i] = stock[i].getQuantity();
        }
        for (Ingredient required : requiredIngredients)
        {
            double needed = required.getQuantity();
            for (int i = 0; i < stock.length && needed > 0; i++)
            {
                if (remaining[i] == 0
                    || !matcher.namesMatch(
                        stock[i].getName(), required.getName())
                    || !matcher.areUnitsCompatible(
                        stock[i].getUnit(), required.getUnit()))
                {
                    continue;
                }
                double available = matcher.convertQuantity(
                    remaining[i], stock[i].getUnit(), required.getUnit());
                if (available <= needed)
                {
                    remaining[i] = 0;
                    double difference = needed - available;
                    needed = isRoundoff(difference, needed, available)
                        ? 0 : difference;
                }
                else
                {
                    double used = matcher.convertQuantity(
                        needed, required.getUnit(), stock[i].getUnit());
                    double difference = remaining[i] - used;
                    remaining[i] = isRoundoff(
                        difference, remaining[i], used) ? 0 : difference;
                    needed = 0;
                }
            }
            if (needed > 0)
            {
                return false;
            }
        }
        LinkedChain<Ingredient> replacement = new LinkedChain<Ingredient>();
        for (int i = stock.length - 1; i >= 0; i--)
        {
            if (remaining[i] > 0)
            {
                stock[i].setQuantity(remaining[i]);
                replacement.add(stock[i]);
            }
        }
        ingredients = replacement;
        return true;
    }


    /**
     * Counts separate entries, not their combined quantities.
     * @return the entry count
     */
    public int size()
    {
        return ingredients.size();
    }


    /**
     * Checks whether the pantry contains any entries.
     * @return true when no entries remain
     */
    public boolean isEmpty()
    {
        return ingredients.isEmpty();
    }


    /**
     * Takes a private traversal snapshot of stored object references.
     * @return the chain entries in current order
     */
    private Ingredient[] entries()
    {
        return ingredients.toArray(new Ingredient[ingredients.size()]);
    }


    /**
     * Finds an internal entry for an update or snapshot.
     * @param id selected ID
     * @return stored entry, or null
     */
    private Ingredient findStored(String id)
    {
        if (id == null || id.isBlank())
        {
            throw new IllegalArgumentException("ID must not be blank.");
        }
        for (Ingredient entry : entries())
        {
            if (entry.getId().equals(id))
            {
                return entry;
            }
        }
        return null;
    }


    /**
     * Allows only a few floating-point rounding steps, not a fixed epsilon.
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
