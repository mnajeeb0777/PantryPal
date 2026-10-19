import java.util.Locale;
import junit.framework.AssertionFailedError;

/**
 * Deterministic sample data and exception assertions shared by core tests.
 *
 * @author PantryPal team (GenAI-assisted tests)
 * @version 2026.10.14
 */
final class TestFixtures
{
    private TestFixtures()
    {
        // Utility class.
    }


    /**
     * Creates an isolated directory under the ignored build output.
     * @return a newly created directory owned by one test
     * @throws java.io.IOException if it cannot be created
     */
    static java.nio.file.Path temporaryDirectory()
        throws java.io.IOException
    {
        java.nio.file.Path root = java.nio.file.Path.of("build", "test-data")
            .toAbsolutePath().normalize();
        java.nio.file.Files.createDirectories(root);
        return java.nio.file.Files.createTempDirectory(root, "persistence-");
    }


    /**
     * Removes only a test-owned directory beneath build/test-data.
     * @param directory directory returned by temporaryDirectory
     * @throws java.io.IOException if cleanup fails
     */
    static void deleteDirectory(java.nio.file.Path directory)
        throws java.io.IOException
    {
        java.nio.file.Path root = java.nio.file.Path.of("build", "test-data")
            .toAbsolutePath().normalize();
        java.nio.file.Path target = directory.toAbsolutePath().normalize();
        if (!target.startsWith(root) || target.equals(root))
        {
            throw new IllegalArgumentException("Not a test directory.");
        }
        try (java.util.stream.Stream<java.nio.file.Path> paths =
            java.nio.file.Files.walk(target))
        {
            java.nio.file.Path[] ordered = paths.sorted(
                java.util.Comparator.reverseOrder())
                .toArray(java.nio.file.Path[]::new);
            for (java.nio.file.Path path : ordered)
            {
                java.nio.file.Files.delete(path);
            }
        }
    }


    /**
     * Returns a deterministic UUID-shaped identifier.
     * @param value nonnegative fixture number
     * @return the fixture UUID
     */
    static String id(int value)
    {
        return String.format(Locale.ROOT,
            "00000000-0000-0000-0000-%012d", value);
    }


    /**
     * Creates an ingredient fixture.
     * @param id fixture number
     * @param name ingredient name
     * @param quantity amount to store
     * @param unit measurement unit
     * @return a new ingredient
     */
    static Ingredient ingredient(
        int id,
        String name,
        double quantity,
        Unit unit)
    {
        return new Ingredient(id(id), name, quantity, unit);
    }


    /**
     * Creates a simple recipe with one ingredient and one step.
     * @param id fixture number
     * @param name recipe title
     * @param time cook time, or null if unknown
     * @param effort effort category
     * @return a new recipe
     */
    static Recipe recipe(int id, String name, Integer time, Effort effort)
    {
        return new Recipe(id(id), name,
            new Ingredient[] { ingredient(100, "Rice", 2, Unit.CUP) },
            new String[] { "Cook the rice." }, 5, time, effort, "", "");
    }


    /**
     * Requires the operation to reject its input with the model contract.
     * @param operation action expected to throw IllegalArgumentException
     */
    static void invalid(Runnable operation)
    {
        try
        {
            operation.run();
        }
        catch (IllegalArgumentException exception)
        {
            return;
        }
        throw new AssertionFailedError("Expected IllegalArgumentException.");
    }
}
