import java.awt.Component;
import java.awt.Container;
import java.awt.Frame;
import java.awt.GraphicsEnvironment;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import org.junit.Assume;
import student.TestCase;

/**
 * Exercises actual Swing controls on the EDT without opening desktop windows.
 *
 * @author PantryPal team (GenAI-assisted tests)
 * @version 2026.10.14
 */
public class PantryPalGUITest extends TestCase
{
    private PantryPalController controller;
    private PantryPalGUI gui;
    private Path directory;
    private Path saveFile;
    private String previousDataFile;
    private String previousPricingDisabled;
    private JFrame desktopWindow;


    /**
     * Creates an independent session and its Swing components for each test.
     * @throws Exception if the test operation fails unexpectedly
     */
    @Override
    public void setUp()
        throws Exception
    {
        directory = TestFixtures.temporaryDirectory();
        saveFile = directory.resolve("data.properties");
        previousPricingDisabled = System.getProperty("pantrypal.pricingDisabled");
        System.setProperty("pantrypal.pricingDisabled", "true");
        previousDataFile = System.getProperty("pantrypal.dataFile");
        System.setProperty("pantrypal.dataFile", saveFile.toString());
        controller = new PantryPalController();
        SwingUtilities.invokeAndWait(() -> gui = new PantryPalGUI(controller));
    }


    /**
     * Restores launch configuration and removes only isolated test data.
     * @throws Exception if Swing or file cleanup fails
     */
    @Override
    public void tearDown() throws Exception
    {
        try
        {
            SwingUtilities.invokeAndWait(() ->
            {
                if (desktopWindow != null)
                {
                    desktopWindow.dispose();
                }
            });
        }
        finally
        {
            if (previousDataFile == null)
            {
                System.clearProperty("pantrypal.dataFile");
            }
            else
            {
                System.setProperty("pantrypal.dataFile", previousDataFile);
            }
            if (previousPricingDisabled == null) { System.clearProperty("pantrypal.pricingDisabled"); }
            else { System.setProperty("pantrypal.pricingDisabled", previousPricingDisabled); }
            TestFixtures.deleteDirectory(directory);
        }
    }


    /**
     * Demo controls load useful comparisons and support cooking and roulette.
     * @throws Exception if the test operation fails unexpectedly
     */
    public void testDemoWorkflow()
        throws Exception
    {
        SwingUtilities.invokeAndWait(() ->
        {
            set("pantryQuery", "nothing");
            set("recipeQuery", "nothing");
            set("maxCookTime", "bad");
            click("loadDemo");
            assertEquals(8, table("pantryTable").getRowCount());
            assertEquals(5, table("recipeTable").getRowCount());
            assertTrue(controller.hasUnsavedChanges());
            assertTrue(message().contains("Demo loaded"));
            int cookable = 0;
            for (Recipe recipe : controller.getSavedRecipes())
            {
                RecipeMatchResult result =
                    controller.compareRecipeWithPantry(recipe);
                if (result.canCook())
                {
                    cookable++;
                }
                if (recipe.getName().equals("Big Batch Pancakes"))
                {
                    assertFalse(result.canCook());
                    assertEquals(2, result.getMissingIngredients().length);
                }
                if (recipe.getName().equals("Tomato Pasta"))
                {
                    assertFalse(result.canCook());
                    assertEquals("Tomatoes",
                        result.getMissingIngredients()[0].getName());
                }
            }
            assertEquals(3, cookable);
            click("mealRoulette");
            assertTrue(message().contains("Meal Roulette picked:"));
            JTable recipes = table("recipeTable");
            for (int row = 0; row < recipes.getRowCount(); row++)
            {
                if (recipes.getValueAt(row, 0).equals("Cheesy Pasta"))
                {
                    recipes.setRowSelectionInterval(row, row);
                    break;
                }
            }
            click("recipeCook");
            assertTrue(message().contains("Cooked!"));
            assertEquals(300.0, controller.searchPantryIngredients("Pasta")
                [0].getQuantity(), 0.0);
            assertEquals(0.9, controller.searchPantryIngredients("Milk")
                [0].getQuantity(), 0.000001);
            click("loadDemo");
            assertTrue(message().contains("requires an empty session"));
            assertEquals(8, controller.getPantryIngredients().length);
            assertEquals(5, controller.getSavedRecipes().length);
            assertEquals(300.0, controller.searchPantryIngredients("Pasta")
                [0].getQuantity(), 0.0);
        });
    }


    /**
     * Existing pantry data prevents loading without changing data or filters.
     * @throws Exception if the test operation fails unexpectedly
     */
    public void testDemoPreservesExistingPantry()
        throws Exception
    {
        SwingUtilities.invokeAndWait(() ->
        {
            addPantry("Rice", "3", "cup");
            set("pantryQuery", "Rice");
            click("loadDemo");
            assertTrue(message().contains("requires an empty session"));
            assertEquals("Rice", field("pantryQuery").getText());
            assertEquals(1, controller.getPantryIngredients().length);
            assertEquals(3.0, controller.getPantryIngredients()[0]
                .getQuantity(), 0.0);
            assertEquals(0, controller.getSavedRecipes().length);
        });
    }


    /**
     * An existing recipe also prevents loading when the pantry is empty.
     * @throws Exception if the test operation fails unexpectedly
     */
    public void testDemoPreservesExistingRecipes()
        throws Exception
    {
        SwingUtilities.invokeAndWait(() ->
        {
            createRiceRecipe("My Rice", "1", "20");
            click("loadDemo");
            assertTrue(message().contains("requires an empty session"));
            assertEquals(0, controller.getPantryIngredients().length);
            assertEquals(1, controller.getSavedRecipes().length);
            assertEquals("My Rice", controller.getSavedRecipes()[0]
                .getName());
        });
    }


    /**
     * Construction rejects missing services and work outside the Swing EDT.
     * @throws Exception if the test operation fails unexpectedly
     */
    public void testConstructionAndMainArguments()
        throws Exception
    {
        SwingUtilities.invokeAndWait(() ->
        {
            TestFixtures.invalid(() -> new PantryPalGUI(null));
            assertEquals(0, table("pantryTable").getRowCount());
            assertEquals(0, table("recipeTable").getRowCount());
        });
        try
        {
            assertNotNull(new PantryPalGUI(controller));
            fail("GUI construction must use the EDT.");
        }
        catch (IllegalStateException expected)
        {
            assertTrue(expected.getMessage().contains("EDT"));
        }
        TestFixtures.invalid(() -> PantryPalGUI.main(null));
        TestFixtures.invalid(() -> PantryPalGUI.main(new String[] { "bad" }));
    }


    /**
     * Pantry form actions update real model state, including selected rows.
     * @throws Exception if the test operation fails unexpectedly
     */
    public void testPantryWorkflow()
        throws Exception
    {
        SwingUtilities.invokeAndWait(() ->
        {
            addPantry("Rice", "3", "cup");
            assertEquals(1, table("pantryTable").getRowCount());
            table("pantryTable").setRowSelectionInterval(0, 0);
            set("pantryQuantity", "2");
            click("pantryUpdate");
            assertEquals(2.0,
                controller.getPantryIngredients()[0].getQuantity(), 0.0);
            set("pantryQuery", "missing");
            click("pantrySearch");
            assertEquals(0, table("pantryTable").getRowCount());
            assertTrue(message().contains("No ingredients found."));
            click("pantryShowAll");
            table("pantryTable").setRowSelectionInterval(0, 0);
            click("pantryRemove");
            assertEquals(0, controller.getPantryIngredients().length);
            click("pantryRemove");
            assertTrue(message().contains("Your pantry is empty."));
            click("pantryUpdate");
            assertTrue(message().contains("Your pantry is empty."));
        });
    }


    /**
     * Bad numeric and name inputs are explained without discarding the form.
     * @throws Exception if the test operation fails unexpectedly
     */
    public void testBadPantryInputRecovery()
        throws Exception
    {
        SwingUtilities.invokeAndWait(() ->
        {
            for (String invalid : new String[] {
                "abc", "", "-2", "0", "NaN", "Infinity", "1000001", "1e999" })
            {
                addPantry("Rice", invalid, "cup");
                assertEquals(0, controller.getPantryIngredients().length);
                assertTrue(message().startsWith("Please try again:"));
                assertEquals("Rice", field("pantryName").getText());
            }
            addPantry("123", "2", "cup");
            assertEquals(0, controller.getPantryIngredients().length);
            assertTrue(message().contains("valid ingredient name"));
            addPantry("a".repeat(101), "2", "cup");
            assertTrue(message().contains("100 characters"));
            addPantry("Rice", "2", "cup");
            assertEquals(1, controller.getPantryIngredients().length);
            assertFalse(message().startsWith("Please try again:"));
        });
    }


    /**
     * A rejected edit leaves the old entry intact and permits correction.
     * @throws Exception if the test operation fails unexpectedly
     */
    public void testInvalidUpdateAndMissingSelection()
        throws Exception
    {
        SwingUtilities.invokeAndWait(() ->
        {
            addPantry("Rice", "2", "cup");
            click("pantryUpdate");
            assertTrue(message().contains("Select a pantry ingredient"));
            table("pantryTable").setRowSelectionInterval(0, 0);
            set("pantryQuantity", "-1");
            click("pantryUpdate");
            assertEquals(2.0,
                controller.getPantryIngredients()[0].getQuantity(), 0.0);
            assertEquals("-1", field("pantryQuantity").getText());
            set("pantryQuantity", "1");
            click("pantryUpdate");
            assertEquals(1.0,
                controller.getPantryIngredients()[0].getQuantity(), 0.0);
            click("pantryClear");
            assertEquals("", field("pantryName").getText());
        });
    }


    /**
     * Sorting never causes an action to target the wrong underlying entry.
     * @throws Exception if the test operation fails unexpectedly
     */
    public void testSortedSelectionIdentity()
        throws Exception
    {
        SwingUtilities.invokeAndWait(() ->
        {
            addPantry("Zucchini", "3", "count");
            addPantry("Apples", "2", "count");
            JTable table = table("pantryTable");
            table.getRowSorter().toggleSortOrder(0);
            assertEquals("Apples", table.getValueAt(0, 0));
            table.setRowSelectionInterval(0, 0);
            click("pantryRemove");
            assertEquals("Zucchini",
                controller.getPantryIngredients()[0].getName());
        });
    }


    /**
     * A recipe can be entered, compared, cooked, and removed through buttons.
     * @throws Exception if the test operation fails unexpectedly
     */
    public void testRecipeCookingWorkflow()
        throws Exception
    {
        SwingUtilities.invokeAndWait(() ->
        {
            addPantry("Rice", "2", "cup");
            createRiceRecipe("Rice bowl", "2", "10");
            assertEquals(1, controller.getSavedRecipes().length);
            assertEquals(1, table("comparisonTable").getRowCount());
            assertEquals("Ready", table("comparisonTable").getValueAt(0, 5));
            assertTrue(area("recipeDetails").getText().contains("Cook rice."));
            click("recipeCompare");
            assertEquals(2.0,
                controller.getPantryIngredients()[0].getQuantity(), 0.0);
            click("recipeCook");
            assertEquals(0, controller.getPantryIngredients().length);
            assertTrue(message().contains("Cooked!"));
            assertEquals("Missing",
                table("comparisonTable").getValueAt(0, 5));
            click("recipeCook");
            assertTrue(message().contains("Cannot cook"));
            click("recipeRemove");
            assertEquals(0, controller.getSavedRecipes().length);
            assertEquals(0, table("comparisonTable").getRowCount());
            click("recipeCompare");
            assertTrue(message().contains("No recipes found"));
        });
    }


    /**
     * Mixed units show known shortages; incompatible-only stock is unknown.
     * @throws Exception if the test operation fails unexpectedly
     */
    public void testComparisonStatuses()
        throws Exception
    {
        SwingUtilities.invokeAndWait(() ->
        {
            addPantry("Rice", "100", "gram");
            createRiceRecipe("Rice bowl", "2", "");
            assertEquals("Unknown",
                table("comparisonTable").getValueAt(0, 2));
            click("recipeCook");
            assertEquals(100.0,
                controller.getPantryIngredients()[0].getQuantity(), 0.0);
            addPantry("Rice", "1", "cup");
            assertEquals("1.0", table("comparisonTable").getValueAt(0, 2));
            assertEquals("1.0", table("comparisonTable").getValueAt(0, 3));
            click("recipeCook");
            assertEquals(2, controller.getPantryIngredients().length);
        });
    }


    /**
     * Recipe filters combine name, cook time, and effort, with recovery.
     * @throws Exception if the test operation fails unexpectedly
     */
    public void testRecipeSearchAndFilters()
        throws Exception
    {
        SwingUtilities.invokeAndWait(() ->
        {
            createRiceRecipe("Quick rice", "1", "10");
            createRiceRecipe("Slow rice", "1", "40");
            set("maxCookTime", "15");
            click("recipeSearch");
            assertEquals(1, table("recipeTable").getRowCount());
            assertEquals("Quick rice", table("recipeTable").getValueAt(0, 0));
            set("recipeQuery", "beans");
            click("recipeSearch");
            assertTrue(message().contains("No recipes found."));
            set("maxCookTime", "wrong");
            click("recipeSearch");
            assertTrue(message().contains("whole minutes"));
            click("recipeShowAll");
            combo("effortFilter").setSelectedItem("HARD");
            click("recipeSearch");
            assertEquals(0, table("recipeTable").getRowCount());
            click("recipeShowAll");
            assertEquals(2, table("recipeTable").getRowCount());
        });
    }


    /**
     * Invalid recipe metadata preserves the draft until it can be corrected.
     * @throws Exception if the test operation fails unexpectedly
     */
    public void testRecipeDraftRecovery()
        throws Exception
    {
        SwingUtilities.invokeAndWait(() ->
        {
            click("recipeCreate");
            assertEquals(0, controller.getSavedRecipes().length);
            set("recipeName", "Rice bowl");
            click("recipeCreate");
            assertTrue(message().startsWith("Please try again:"));
            addRequirement("Rice", "bad", "cup");
            assertEquals(0, table("requirementTable").getRowCount());
            addRequirement("Rice", "2", "cup");
            set("prepTime", "-1");
            click("recipeCreate");
            assertTrue(message().contains("whole minutes"));
            assertEquals(1, table("requirementTable").getRowCount());
            set("prepTime", "");
            set("cookTime", "2147483648");
            click("recipeCreate");
            assertTrue(message().contains("whole minutes"));
            set("cookTime", "");
            area("instructions").setText("First step.\n\nLast step.");
            click("recipeCreate");
            assertEquals(0, controller.getSavedRecipes().length);
            area("instructions").setText("Cook rice.");
            click("recipeCreate");
            assertEquals(1, controller.getSavedRecipes().length);
            assertNull(controller.getSavedRecipes()[0].getCookTimeMinutes());
            assertEquals(0, table("requirementTable").getRowCount());
        });
    }


    /**
     * Removing a draft ingredient follows its model index after sorting.
     * @throws Exception if the test operation fails unexpectedly
     */
    public void testDraftRequirementRemoval()
        throws Exception
    {
        SwingUtilities.invokeAndWait(() ->
        {
            click("requirementRemove");
            assertTrue(message().contains("Select a recipe ingredient"));
            addRequirement("Zucchini", "2", "count");
            addRequirement("Apples", "1", "count");
            JTable table = table("requirementTable");
            table.getRowSorter().toggleSortOrder(0);
            table.setRowSelectionInterval(0, 0);
            click("requirementRemove");
            set("recipeName", "Zucchini");
            click("recipeCreate");
            assertEquals("Zucchini", controller.getSavedRecipes()[0]
                .getRequiredIngredients()[0].getName());
        });
    }


    /**
     * Roulette explains empty/ineligible collections and survives another try.
     * @throws Exception if the EDT operation fails unexpectedly
     */
    public void testRouletteNoEligibleRecipes()
        throws Exception
    {
        SwingUtilities.invokeAndWait(() ->
        {
            click("mealRoulette");
            assertTrue(message().contains("No cookable saved recipes."));
            assertFalse(controller.hasUnsavedChanges());
            createRiceRecipe("Rice bowl", "2", "10");
            addPantry("Rice", "1", "cup");
            click("mealRoulette");
            assertTrue(message().contains("No cookable saved recipes."));
            assertEquals(1.0,
                controller.getPantryIngredients()[0].getQuantity(), 0.0);
            addPantry("Rice", "1", "cup");
            click("mealRoulette");
            assertTrue(message().contains("Meal Roulette picked: Rice bowl"));
            assertEquals("Ready",
                table("comparisonTable").getValueAt(0, 5));
            assertEquals(2, controller.getPantryIngredients().length);
        });
    }


    /**
     * Roulette reveals its result despite filters/sorting and never cooks it.
     * @throws Exception if the EDT operation fails unexpectedly
     */
    public void testRouletteSelectionAndCooking()
        throws Exception
    {
        SwingUtilities.invokeAndWait(() ->
        {
            addPantry("Rice", "2", "cup");
            createRiceRecipe("Zucchini-style rice", "2", "10");
            createRiceRecipe("Abundant rice", "20", "40");
            JTable recipes = table("recipeTable");
            recipes.getRowSorter().toggleSortOrder(0);
            set("recipeQuery", "Abundant");
            set("maxCookTime", "50");
            click("recipeSearch");
            assertEquals(1, recipes.getRowCount());
            // Even an unsubmitted invalid filter cannot block roulette.
            set("maxCookTime", "invalid");
            combo("effortFilter").setSelectedItem("HARD");
            click("mealRoulette");
            assertTrue(message().contains(
                "Meal Roulette picked: Zucchini-style rice"));
            assertEquals("", field("recipeQuery").getText());
            assertEquals("", field("maxCookTime").getText());
            assertEquals(0, combo("effortFilter").getSelectedIndex());
            assertEquals("Zucchini-style rice",
                recipes.getValueAt(recipes.getSelectedRow(), 0));
            assertEquals("Ready",
                table("comparisonTable").getValueAt(0, 5));
            assertEquals(2.0,
                controller.getPantryIngredients()[0].getQuantity(), 0.0);
            click("recipeCook");
            assertEquals(0, controller.getPantryIngredients().length);
            click("mealRoulette");
            assertTrue(message().contains("No cookable saved recipes."));
            assertEquals("Missing",
                table("comparisonTable").getValueAt(0, 5));
        });
    }


    /**
     * Unknown units and a conversion error stay recoverable in the GUI.
     * @throws Exception if the EDT operation fails unexpectedly
     */
    public void testRouletteUnknownAndConversionRecovery()
        throws Exception
    {
        SwingUtilities.invokeAndWait(() ->
        {
            createRiceRecipe("Rice bowl", "2", "10");
            addPantry("Rice", "100", "gram");
            click("mealRoulette");
            assertTrue(message().contains("No cookable saved recipes."));
            table("pantryTable").setRowSelectionInterval(0, 0);
            set("pantryQuantity", Double.toString(Double.MIN_VALUE));
            combo("pantryUnit").setSelectedItem("milliliter");
            click("pantryUpdate");
            click("mealRoulette");
            assertTrue(message().startsWith("Please try again:"));
            assertEquals(Double.MIN_VALUE,
                controller.getPantryIngredients()[0].getQuantity(), 0.0);
            table("pantryTable").setRowSelectionInterval(0, 0);
            set("pantryQuantity", "2");
            combo("pantryUnit").setSelectedItem("cup");
            click("pantryUpdate");
            click("mealRoulette");
            assertTrue(message().contains("Meal Roulette picked: Rice bowl"));
            assertEquals("Ready",
                table("comparisonTable").getValueAt(0, 5));
        });
    }


    /**
     * Scoped quantity/name errors display their specified recovery messages.
     * @throws Exception if the EDT operation fails unexpectedly
     */
    public void testExactSpecificationMessages()
        throws Exception
    {
        SwingUtilities.invokeAndWait(() ->
        {
            String finiteMessage = "Enter a finite quantity greater than 0 "
                + "and no greater than 1,000,000.";
            String[][] cases = {
                { "Rice", "abc", "Enter a valid number." },
                { "Rice", "0", "Quantity must be greater than 0." },
                { "Rice", "-1", "Quantity must be greater than 0." },
                { "Rice", "1000001", finiteMessage },
                { "Rice", "NaN", finiteMessage },
                { "Rice", "Infinity", finiteMessage },
                { "Rice", "-Infinity", finiteMessage },
                { "Rice", "9".repeat(1000), finiteMessage },
                { "", "1", "Enter a valid ingredient name." },
                { "123!@", "1", "Enter a valid ingredient name." },
                { "Rice" + (char)1, "1", "Enter a valid ingredient name." },
                { "x".repeat(101), "1",
                    "Ingredient names cannot exceed 100 characters." } };
            for (String[] values : cases)
            {
                addPantry(values[0], values[1], "cup");
                assertEquals("Please try again: " + values[2], message());
                assertEquals(0, controller.getPantryIngredients().length);
                assertFalse(controller.hasUnsavedChanges());
                assertEquals(values[0], field("pantryName").getText());
                assertEquals(values[1], field("pantryQuantity").getText());
            }
            addPantry("Rice", "2", "cup");
            assertEquals(1, controller.getPantryIngredients().length);
            set("pantryQuery", "absent");
            click("pantrySearch");
            assertEquals("No ingredients found.", message());
        });
    }


    /**
     * An entry removed since display gets the specified not-found response.
     * @throws Exception if the test operation fails unexpectedly
     */
    public void testStalePantryActions()
        throws Exception
    {
        SwingUtilities.invokeAndWait(() -> addPantry("Rice", "2", "cup"));
        String removedId = controller.getPantryIngredients()[0].getId();
        SwingUtilities.invokeAndWait(() ->
            table("pantryTable").setRowSelectionInterval(0, 0));
        controller.removePantryIngredient(removedId);
        controller.addPantryIngredient("Salt", 1, Unit.GRAM);
        SwingUtilities.invokeAndWait(() ->
        {
            click("pantryRemove");
            assertEquals("Please try again: Ingredient not found.", message());
            assertEquals("Salt",
                controller.getPantryIngredients()[0].getName());
            table("pantryTable").setRowSelectionInterval(0, 0);
        });
        String saltId = controller.getPantryIngredients()[0].getId();
        controller.removePantryIngredient(saltId);
        controller.addPantryIngredient("Beans", 3, Unit.CUP);
        SwingUtilities.invokeAndWait(() ->
        {
            set("pantryQuantity", "9");
            click("pantryUpdate");
            assertEquals("Please try again: Ingredient not found.", message());
            assertEquals(3.0,
                controller.getPantryIngredients()[0].getQuantity(), 0.0);
            assertEquals("Beans",
                controller.getPantryIngredients()[0].getName());
        });
    }


    /**
     * The real entry point opens a window, and an unchanged session can close.
     * @throws Exception if Swing startup or disposal fails
     */
    public void testDesktopEntryPoint()
        throws Exception
    {
        Assume.assumeFalse("A desktop display is required for this test.",
            GraphicsEnvironment.isHeadless());
        Set<Frame> existing = new HashSet<Frame>(
            Arrays.asList(Frame.getFrames()));
        PantryPalGUI.main(new String[0]);
        SwingUtilities.invokeAndWait(() ->
        {
            JFrame opened = null;
            try
            {
                for (Frame candidate : Frame.getFrames())
                {
                    if (!existing.contains(candidate)
                        && candidate instanceof JFrame
                        && ((JFrame)candidate).getContentPane()
                            instanceof PantryPalGUI)
                    {
                        opened = (JFrame)candidate;
                        break;
                    }
                }
                if (opened == null)
                {
                    throw new AssertionError(
                        "main must open the desktop application.");
                }
                assertTrue(opened.isShowing());
                assertEquals("PantryPal", opened.getTitle());
                assertEquals(0, ((JTable)find(opened, "pantryTable"))
                    .getRowCount());
                opened.dispatchEvent(new WindowEvent(opened,
                    WindowEvent.WINDOW_CLOSING));
                assertFalse(opened.isDisplayable());
            }
            finally
            {
                if (opened != null)
                {
                    opened.dispose();
                }
            }
        });
    }


    /**
     * Startup restores both collections, and saved changes survive reopening.
     * @throws Exception if setup or Swing operations fail
     */
    public void testDesktopPersistence() throws Exception
    {
        DataStore store = new DataStore(saveFile);
        store.save(new PantryPalData(new Ingredient[] {
            TestFixtures.ingredient(1, "Rice", 3, Unit.CUP) },
            new Recipe[] { TestFixtures.recipe(2, "Rice bowl", 10, Effort.EASY) }));
        openApplicationWindow();
        SwingUtilities.invokeAndWait(() ->
        {
            assertEquals(1, table("pantryTable").getRowCount());
            assertEquals(1, table("recipeTable").getRowCount());
            assertTrue(((JLabel)find(gui, "storageStatus")).getText()
                .contains("Autosave is on"));
            assertFalse(find(gui, "retryLoad").isVisible());
            addPantry("Egg", "2", "count");
            desktopWindow.dispatchEvent(new WindowEvent(desktopWindow,
                WindowEvent.WINDOW_CLOSING));
            assertFalse(desktopWindow.isDisplayable());
        });
        assertEquals(2, store.load().getPantryIngredients().length);
        openApplicationWindow();
        SwingUtilities.invokeAndWait(() ->
            assertEquals(2, table("pantryTable").getRowCount()));
    }


    /**
     * A corrupt startup file is protected until the user repairs and reloads it.
     * @throws Exception if setup or Swing operations fail
     */
    public void testDesktopLoadFailureAndRetry() throws Exception
    {
        Files.writeString(saveFile, "version=99");
        openApplicationWindow();
        SwingUtilities.invokeAndWait(() ->
        {
            assertTrue(message().contains("Could not load"));
            assertTrue(find(gui, "retryLoad").isVisible());
            assertTrue(((JLabel)find(gui, "storageStatus")).getText()
                .contains("Load failed"));
            addPantry("Rice", "3", "cup");
            assertEquals(0, table("pantryTable").getRowCount());
            click("saveNow");
            assertTrue(message().contains("retry loading"));
        });
        assertEquals("version=99", Files.readString(saveFile));
        new DataStore(saveFile).save(new PantryPalData(new Ingredient[] {
            TestFixtures.ingredient(1, "Rice", 3, Unit.CUP) }, new Recipe[0]));
        SwingUtilities.invokeAndWait(() ->
        {
            click("retryLoad");
            assertEquals(1, table("pantryTable").getRowCount());
            assertFalse(find(gui, "retryLoad").isVisible());
            assertTrue(message().contains("loaded"));
            addPantry("Egg", "2", "count");
        });
        assertEquals(2, new DataStore(saveFile).load().getPantryIngredients().length);
    }


    /**
     * Failed autosave displays changed memory and can retry without duplication.
     * @throws Exception if setup or Swing operations fail
     */
    public void testAutosaveFailureRefreshAndRetry() throws Exception
    {
        controller = new PantryPalController(new DataStore(saveFile),
            new IngredientMatcher(), null, null);
        controller.loadData();
        SwingUtilities.invokeAndWait(() ->
        {
            gui = new PantryPalGUI(controller);
            addPantry("Rice", "5", "cup");
            createRiceRecipe("Rice bowl", "2", "10");
        });
        Path backup = directory.resolve("previous.properties");
        Files.move(saveFile, backup);
        Files.createDirectory(saveFile);
        Files.writeString(saveFile.resolve("block"), "keep");
        SwingUtilities.invokeAndWait(() ->
        {
            click("recipeCook");
            assertTrue(controller.hasUnsavedChanges());
            assertTrue(message().contains("Could not save"));
            assertTrue(message().contains("do not repeat"));
            assertEquals("3.0", table("pantryTable").getValueAt(0, 1));
            assertTrue(((JLabel)find(gui, "storageStatus")).getText()
                .contains("Unsaved changes"));
        });
        Files.delete(saveFile.resolve("block"));
        Files.delete(saveFile);
        Files.move(backup, saveFile);
        SwingUtilities.invokeAndWait(() ->
        {
            click("saveNow");
            assertFalse(controller.hasUnsavedChanges());
            assertTrue(message().contains("changes are saved"));
        });
        assertEquals(3.0, new DataStore(saveFile).load().getPantryIngredients()[0]
            .getQuantity(), 0.0);
    }


    private void openApplicationWindow() throws Exception
    {
        Assume.assumeFalse("A desktop display is required for this test.",
            GraphicsEnvironment.isHeadless());
        Set<Frame> existing = new HashSet<Frame>(Arrays.asList(Frame.getFrames()));
        PantryPalGUI.main(new String[0]);
        SwingUtilities.invokeAndWait(() ->
        {
            for (Frame candidate : Frame.getFrames())
            {
                if (!existing.contains(candidate) && candidate instanceof JFrame
                    && ((JFrame)candidate).getContentPane() instanceof PantryPalGUI)
                {
                    desktopWindow = (JFrame)candidate;
                    gui = (PantryPalGUI)desktopWindow.getContentPane();
                    return;
                }
            }
            throw new AssertionError("main must open the application.");
        });
    }


    private void addPantry(String name, String quantity, String unit)
    {
        set("pantryName", name);
        set("pantryQuantity", quantity);
        combo("pantryUnit").setSelectedItem(unit);
        click("pantryAdd");
    }


    private void addRequirement(String name, String quantity, String unit)
    {
        set("requirementName", name);
        set("requirementQuantity", quantity);
        combo("requirementUnit").setSelectedItem(unit);
        click("requirementAdd");
    }


    private void createRiceRecipe(String name, String quantity, String time)
    {
        ((JTabbedPane)find(gui, "tabs")).setSelectedIndex(2);
        set("recipeName", name);
        set("cookTime", time);
        addRequirement("Rice", quantity, "cup");
        area("instructions").setText("Cook rice.");
        click("recipeCreate");
    }


    private void set(String name, String value)
    {
        field(name).setText(value);
    }


    private void click(String name)
    {
        ((JButton)find(gui, name)).doClick(0);
    }


    private JTextField field(String name)
    {
        return (JTextField)find(gui, name);
    }


    private JTextArea area(String name)
    {
        return (JTextArea)find(gui, name);
    }


    private JTable table(String name)
    {
        return (JTable)find(gui, name);
    }


    private JComboBox<?> combo(String name)
    {
        return (JComboBox<?>)find(gui, name);
    }


    private String message()
    {
        return area("status").getText();
    }


    private Component find(Component component, String name)
    {
        if (name.equals(component.getName()))
        {
            return component;
        }
        if (component instanceof Container)
        {
            for (Component child : ((Container)component).getComponents())
            {
                Component result = find(child, name);
                if (result != null)
                {
                    return result;
                }
            }
        }
        return null;
    }
}
