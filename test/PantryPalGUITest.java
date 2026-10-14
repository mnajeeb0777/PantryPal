import java.awt.Component;
import java.awt.Container;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
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


    /**
     * Creates an independent session and its Swing components for each test.
     * @throws Exception if the test operation fails unexpectedly
     */
    @Override
    public void setUp()
        throws Exception
    {
        controller = new PantryPalController();
        SwingUtilities.invokeAndWait(() -> gui = new PantryPalGUI(controller));
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
