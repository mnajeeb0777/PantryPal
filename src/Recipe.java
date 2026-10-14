import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * A recipe owning its requirements and instructions, with no setters.
 *
 * @author PantryPal team (GenAI-assisted implementation)
 * @version 2026.10.14
 */
public class Recipe
{
    private final String id;
    private final String name;
    private final LinkedChain<Ingredient> requiredIngredients;
    private final String[] instructions;
    private final Integer prepTimeMinutes;
    private final Integer cookTimeMinutes;
    private final Effort effort;
    private final String sourceUrl;
    private final String externalRecipeId;


    /**
     * Creates a validated recipe owning independent ingredient and step data.
     * @param id stable entry identifier
     * @param name display name
     * @param ingredients recipe ingredient requirements
     * @param instructions ordered cooking steps
     * @param prepTimeMinutes preparation time, or null if unknown
     * @param cookTimeMinutes cooking time, or null if unknown
     * @param effort non-null effort category
     * @param sourceUrl source link, or an empty string for custom recipes
     * @param externalRecipeId provider identifier, or empty for custom recipes
     * @throws IllegalArgumentException if recipe data is invalid
     */
    public Recipe(
        String id,
        String name,
        Ingredient[] ingredients,
        String[] instructions,
        Integer prepTimeMinutes,
        Integer cookTimeMinutes,
        Effort effort,
        String sourceUrl,
        String externalRecipeId)
    {
        validateId(id);
        if (name == null || name.isBlank()
            || name.codePoints().anyMatch(Character::isISOControl))
        {
            throw new IllegalArgumentException("Recipe name is invalid.");
        }
        if (ingredients == null || ingredients.length == 0)
        {
            throw new IllegalArgumentException(
                "A recipe must contain at least one ingredient.");
        }
        if (instructions == null || effort == null || sourceUrl == null
            || externalRecipeId == null)
        {
            throw new IllegalArgumentException(
                "Recipe details must not be null.");
        }
        validateTime(prepTimeMinutes);
        validateTime(cookTimeMinutes);
        this.instructions = instructions.clone();
        for (String step : this.instructions)
        {
            if (step == null || step.isBlank())
            {
                throw new IllegalArgumentException(
                    "Instruction steps must not be blank.");
            }
        }
        requiredIngredients = new LinkedChain<Ingredient>();
        Set<String> ids = new HashSet<String>();
        for (int i = ingredients.length - 1; i >= 0; i--)
        {
            Ingredient entry = ingredients[i];
            if (entry == null || !ids.add(entry.getId()))
            {
                throw new IllegalArgumentException(
                    "Recipe ingredients must have unique IDs.");
            }
            requiredIngredients.add(new Ingredient(entry));
        }
        this.id = id;
        this.name = name;
        this.prepTimeMinutes = prepTimeMinutes;
        this.cookTimeMinutes = cookTimeMinutes;
        this.effort = effort;
        this.sourceUrl = sourceUrl;
        this.externalRecipeId = externalRecipeId;
    }


    /**
     * Creates an independent copy preserving local and external identifiers.
     * @param other object to copy or compare
     * @throws IllegalArgumentException if other is null
     */
    public Recipe(Recipe other)
    {
        this(requireRecipe(other).id, other.name,
            other.getRequiredIngredients(), other.instructions,
            other.prepTimeMinutes, other.cookTimeMinutes, other.effort,
            other.sourceUrl, other.externalRecipeId);
    }


    /**
     * Returns the recipe's id.
     * @return the stored value
     */
    public String getId()
    {
        return id;
    }


    /**
     * Returns the recipe's name.
     * @return the stored value
     */
    public String getName()
    {
        return name;
    }


    /**
     * Returns independent copies of the ingredient requirements.
     * @return ingredient snapshots in the original order
     */
    public Ingredient[] getRequiredIngredients()
    {
        Ingredient[] result = requiredIngredients.toArray(
            new Ingredient[requiredIngredients.size()]);
        for (int i = 0; i < result.length; i++)
        {
            result[i] = new Ingredient(result[i]);
        }
        return result;
    }


    /**
     * Returns a copy of the ordered cooking steps.
     * @return an independent instruction array
     */
    public String[] getInstructions()
    {
        return instructions.clone();
    }


    /**
     * Returns the recipe's prep time minutes.
     * @return minutes, or null if unknown
     */
    public Integer getPrepTimeMinutes()
    {
        return prepTimeMinutes;
    }


    /**
     * Returns the recipe's cook time minutes.
     * @return minutes, or null if unknown
     */
    public Integer getCookTimeMinutes()
    {
        return cookTimeMinutes;
    }


    /**
     * Returns the recipe's effort.
     * @return the stored value
     */
    public Effort getEffort()
    {
        return effort;
    }


    /**
     * Returns the recipe's source url.
     * @return the stored value
     */
    public String getSourceUrl()
    {
        return sourceUrl;
    }


    /**
     * Returns the recipe's external recipe id.
     * @return the stored value
     */
    public String getExternalRecipeId()
    {
        return externalRecipeId;
    }


    /**
     * Compares recipes by local ID, not title or external source.
     * @param other object to copy or compare
     * @return true for the same local identity
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
        Recipe recipe = (Recipe)other;
        return id.equals(recipe.id);
    }


    /**
     * Returns a hash consistent with local ID equality.
     * @return the ID hash
     */
    @Override
    public int hashCode()
    {
        return id.hashCode();
    }


    /**
     * Rejects a missing source before invoking the copying constructor.
     * @param other source recipe
     * @return the non-null source
     */
    private static Recipe requireRecipe(Recipe other)
    {
        if (other == null)
        {
            throw new IllegalArgumentException("Recipe must not be null.");
        }
        return other;
    }


    /**
     * Validates a UUID while retaining its original spelling.
     * @param value local identifier
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
     * Accepts unknown times and nonnegative known times.
     * @param minutes proposed time
     */
    private static void validateTime(Integer minutes)
    {
        if (minutes != null && minutes < 0)
        {
            throw new IllegalArgumentException(
                "Recipe times must not be negative.");
        }
    }
}
