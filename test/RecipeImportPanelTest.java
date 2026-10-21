import java.awt.Component;
import java.awt.Container;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import student.TestCase;

/**
 * Exercises real Swing import controls, including cancellation and persistence.
 * @author PantryPal team (GenAI-assisted tests)
 * @version 2026.10.18
 */
public class RecipeImportPanelTest extends TestCase
{
    private PantryPalGUI gui;
    private PantryPalController controller;
    private RecipeHttpStub transport;
    private Path directory;
    private boolean failSave;

    @Override
    public void setUp() throws Exception
    {
        directory = TestFixtures.temporaryDirectory();
        transport = new RecipeHttpStub();
        transport.body = RecipeImporterTest.MEAL;
        DataStore store = new DataStore(directory.resolve("data.properties"))
        {
            @Override
            public void save(PantryPalData data) throws IOException
            {
                if (failSave) { throw new IOException("Test save failure"); }
                super.save(data);
            }
        };
        controller = new PantryPalController(store, new IngredientMatcher(),
            new RecipeImporter(URI.create("https://example.org/api/"), "1", transport), null);
        SwingUtilities.invokeAndWait(() -> gui = new PantryPalGUI(controller));
    }

    @Override
    public void tearDown() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> click("externalCancel"));
        TestFixtures.deleteDirectory(directory);
    }

    /** @throws Exception for unexpected failures */
    public void testReviewRequiredAndSuccessfulImport() throws Exception
    {
        search();
        SwingUtilities.invokeAndWait(() ->
        {
            select();
            assertEquals("1 tin", table().getValueAt(1, 1));
            assertEquals("1.5", table().getValueAt(0, 2));
            click("externalImport");
            assertTrue(status().contains("Row 2"));
            assertEquals(0, controller.getSavedRecipes().length);
            table().setValueAt("200", 1, 2);
            table().setValueAt(Unit.GRAM, 1, 3);
            field("importCook").setText("bad");
            click("externalImport");
            assertTrue(status().contains("Times"));
            field("importCook").setText("");
            // Commit an in-progress editor before saving.
            assertTrue(table().editCellAt(1, 2));
            ((JTextField)table().getEditorComponent()).setText("250");
            click("externalImport");
            assertEquals(1, controller.getSavedRecipes().length);
            assertEquals(250.0, controller.getSavedRecipes()[0]
                .getRequiredIngredients()[1].getQuantity(), 0.001);
            assertNull(controller.getSavedRecipes()[0].getCookTimeMinutes());
            assertFalse(controller.hasUnsavedChanges());
            assertEquals(1, ((JTabbedPane)find(gui, "tabs")).getSelectedIndex());
            click("externalImport");
            assertEquals(1, controller.getSavedRecipes().length);
            assertTrue(((JTextArea)find(gui, "status")).getText().contains("already saved"));
        });
    }

    /** @throws Exception for unexpected failures */
    public void testSearchErrorEmptyAndRetry() throws Exception
    {
        transport.status = 503;
        search();
        SwingUtilities.invokeAndWait(() -> assertTrue(status().contains("503")));
        transport.status = 200;
        transport.body = "{\"meals\":null}";
        search();
        SwingUtilities.invokeAndWait(() ->
        {
            assertTrue(status().contains("No recipes"));
            assertFalse(((JButton)find(gui, "externalImport")).isEnabled());
        });
        transport.body = RecipeImporterTest.MEAL;
        search();
        SwingUtilities.invokeAndWait(() ->
            assertEquals(1, ((JTable)find(gui, "externalResults")).getRowCount()));
    }

    /** @throws Exception for unexpected failures */
    public void testSaveFailureKeepsImportedRecipeAndRetry() throws Exception
    {
        failSave = true;
        search();
        SwingUtilities.invokeAndWait(() ->
        {
            select();
            table().setValueAt("200", 1, 2);
            table().setValueAt(Unit.GRAM, 1, 3);
            click("externalImport");
            assertEquals(1, controller.getSavedRecipes().length);
            assertTrue(controller.hasUnsavedChanges());
            assertTrue(((JTextArea)find(gui, "status")).getText().contains("Could not save"));
            failSave = false;
            click("saveNow");
            assertFalse(controller.hasUnsavedChanges());
            click("externalImport");
            assertEquals(1, controller.getSavedRecipes().length);
        });
    }

    /** @throws Exception for unexpected failures */
    public void testCancellationDiscardsLateResponseAndDoesNotFreezeEdt() throws Exception
    {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);
        RecipeImportDraft[] late = RecipeImporter.parseResponse(RecipeImporterTest.MEAL);
        PantryPalController slow = new PantryPalController()
        {
            @Override
            public RecipeImportDraft[] searchExternalRecipes(String query)
            {
                entered.countDown();
                try { release.await(3, TimeUnit.SECONDS); }
                catch (InterruptedException ignored) { /* Simulate a late provider reply. */ }
                finished.countDown();
                return late;
            }
        };
        SwingUtilities.invokeAndWait(() ->
        {
            gui = new PantryPalGUI(slow);
            field("externalQuery").setText("rice");
            click("externalSearch");
        });
        assertTrue(entered.await(3, TimeUnit.SECONDS));
        SwingUtilities.invokeAndWait(() ->
        {
            assertFalse(((JButton)find(gui, "externalSearch")).isEnabled());
            click("externalCancel");
            assertTrue(status().contains("cancelled"));
            assertTrue(((JButton)find(gui, "externalSearch")).isEnabled());
        });
        release.countDown();
        assertTrue(finished.await(3, TimeUnit.SECONDS));
        Thread.sleep(100); // SwingWorker batches completion callbacks.
        SwingUtilities.invokeAndWait(() ->
        {
            assertEquals(0, ((JTable)find(gui, "externalResults")).getRowCount());
            assertTrue(status().contains("cancelled"));
        });
    }

    /** @throws Exception for unexpected failures */
    public void testBlankSearchDoesNotCallProvider() throws Exception
    {
        SwingUtilities.invokeAndWait(() ->
        {
            field("externalQuery").setText(" ");
            click("externalSearch");
            assertTrue(status().contains("Enter a recipe"));
            assertNull(transport.lastRequest);
        });
    }

    /**
     * Partial edits survive reopening offline and cannot cook until promoted.
     * @throws Exception for unexpected UI or persistence errors
     */
    public void testSaveForLaterAndResumeOffline() throws Exception
    {
        search();
        SwingUtilities.invokeAndWait(() ->
        {
            select();
            table().setValueAt("White rice", 0, 0);
            assertTrue(table().editCellAt(0, 2));
            ((JTextField)table().getEditorComponent()).setText("1.75");
            field("importPrep").setText("not sure yet");
            click("externalSaveLater");
            assertEquals(1, controller.getSavedDrafts().length);
            assertEquals(0, controller.getSavedRecipes().length);
            assertNull(controller.selectMealRouletteRecipe());
            assertFalse(controller.hasUnsavedChanges());
            assertTrue(status().contains("Saved for later"));
        });
        DataStore disk = new DataStore(directory.resolve("data.properties"));
        PantryPalController reopened = new PantryPalController(disk,
            new IngredientMatcher(), null, null);
        reopened.loadData();
        transport.lastRequest = null;
        SwingUtilities.invokeAndWait(() ->
        {
            controller = reopened;
            gui = new PantryPalGUI(reopened);
            assertTrue(((JButton)find(gui, "externalSavedDrafts")).getText().contains("(1)"));
            click("externalSavedDrafts");
            assertEquals("Needs review", ((JTable)find(gui, "externalResults")).getValueAt(0, 2));
            select();
            assertEquals("White rice", table().getValueAt(0, 0));
            assertEquals("1 1/2 cups", table().getValueAt(0, 1));
            assertEquals("1.75", table().getValueAt(0, 2));
            assertEquals("", table().getValueAt(1, 2));
            assertEquals("not sure yet", field("importPrep").getText());
            click("externalImport");
            assertTrue(status().contains("Row 2"));
            table().setValueAt("200", 1, 2);
            table().setValueAt(Unit.GRAM, 1, 3);
            field("importPrep").setText("");
            click("externalImport");
            assertEquals(1, controller.getSavedRecipes().length);
            assertEquals(0, controller.getSavedDrafts().length);
            assertTrue(((JButton)find(gui, "externalSavedDrafts")).getText().contains("(0)"));
            assertNull(transport.lastRequest);
        });
        assertEquals(0, disk.load().getSavedDrafts().length);
        assertEquals(1, disk.load().getSavedRecipes().length);
    }

    /**
     * Saving repeatedly updates one draft, and searching again resumes its edits.
     * @throws Exception for unexpected UI errors
     */
    public void testSaveForLaterUpdatesAndSearchResumesEdits() throws Exception
    {
        search();
        SwingUtilities.invokeAndWait(() ->
        {
            select();
            table().setValueAt("???", 1, 2);
            click("externalSaveLater");
            table().setValueAt("400", 1, 2);
            table().setValueAt(Unit.GRAM, 1, 3);
            click("externalSaveLater");
            assertEquals(1, controller.getSavedDrafts().length);
        });
        search();
        SwingUtilities.invokeAndWait(() ->
        {
            select();
            assertEquals("400", table().getValueAt(1, 2));
            assertEquals(Unit.GRAM, table().getValueAt(1, 3));
        });
    }

    /**
     * Failed draft saves retain progress, and deletion can also be retried.
     * @throws Exception for unexpected UI or persistence errors
     */
    public void testSaveForLaterFailureRetryAndRemoval() throws Exception
    {
        search();
        SwingUtilities.invokeAndWait(() ->
        {
            select();
            failSave = true;
            click("externalSaveLater");
            assertTrue(status().contains("Could not save"));
            assertTrue(controller.hasUnsavedChanges());
            assertEquals(1, controller.getSavedDrafts().length);
            failSave = false;
            click("saveNow");
            assertFalse(controller.hasUnsavedChanges());
            click("externalSavedDrafts");
            select();
            failSave = true;
            click("externalRemoveDraft");
            assertTrue(status().contains("Could not save"));
            assertTrue(controller.hasUnsavedChanges());
            assertEquals(0, controller.getSavedDrafts().length);
            failSave = false;
            click("saveNow");
            assertFalse(controller.hasUnsavedChanges());
            click("externalSavedDrafts");
            assertTrue(status().contains("No recipes saved"));
            assertFalse(((JButton)find(gui, "externalSaveLater")).isEnabled());
        });
        assertEquals(0, new DataStore(directory.resolve("data.properties")).load()
            .getSavedDrafts().length);
    }

    private void search() throws Exception
    {
        SwingUtilities.invokeAndWait(() ->
        {
            field("externalQuery").setText("rice");
            click("externalSearch");
        });
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(4);
        AtomicBoolean done = new AtomicBoolean();
        do
        {
            SwingUtilities.invokeAndWait(() -> done.set(
                ((JButton)find(gui, "externalSearch")).isEnabled()));
            if (done.get()) { return; }
            Thread.sleep(20);
        }
        while (System.nanoTime() < deadline);
        fail("Search did not finish");
    }

    private void select()
    {
        ((JTable)find(gui, "externalResults")).setRowSelectionInterval(0, 0);
    }
    private JTable table() { return (JTable)find(gui, "importIngredients"); }
    private JTextField field(String name) { return (JTextField)find(gui, name); }
    private void click(String name) { ((JButton)find(gui, name)).doClick(0); }
    private String status() { return ((JLabel)find(gui, "externalStatus")).getText(); }
    private Component find(Component component, String name)
    {
        if (name.equals(component.getName())) { return component; }
        if (component instanceof Container)
        {
            for (Component child : ((Container)component).getComponents())
            {
                Component result = find(child, name);
                if (result != null) { return result; }
            }
        }
        return null;
    }
}
