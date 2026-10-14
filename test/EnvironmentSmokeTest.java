import student.TestCase;

/**
 * Verifies the course testing environment and the supplied chain's public API.
 *
 * @author PantryPal team (GenAI-assisted environment setup)
 * @version 2026.10.14
 */
public class EnvironmentSmokeTest extends TestCase
{
    /**
     * Stores and retrieves an entry under the required Java runtime.
     */
    public void testStorageAndRuntime()
    {
        assertEquals("17", System.getProperty("java.specification.version"));
        LinkedChain<String> entries = new LinkedChain<String>();
        assertTrue(entries.add("environment check"));
        assertEquals(1, entries.size());
        assertEquals("environment check", entries.get(0));
    }


    /**
     * A rejected null entry leaves storage unchanged.
     */
    public void testNullEntryRejected()
    {
        LinkedChain<String> entries = new LinkedChain<String>();
        try
        {
            entries.add(null);
            fail("Null entries must be rejected.");
        }
        catch (IllegalArgumentException exception)
        {
            assertTrue(entries.isEmpty());
            assertEquals(0, entries.size());
        }
    }
}
