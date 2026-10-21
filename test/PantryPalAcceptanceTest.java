import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import student.TestCase;

/** A continuous user journey through real controls, isolated saves and offline providers. */
public class PantryPalAcceptanceTest extends TestCase
{
    private Path directory;
    private DataStore store;
    private PantryPalController controller;
    private PantryPalGUI gui;
    private RecipeHttpStub recipes;
    private RecipeHttpStub prices;

    @Override public void setUp() throws Exception
    {
        directory = TestFixtures.temporaryDirectory();
        store = new DataStore(directory.resolve("acceptance.properties"));
        recipes = new RecipeHttpStub();
        recipes.body = RecipeImporterTest.MEAL;
        reopen(true);
    }

    @Override public void tearDown() throws Exception
    {
        SwingUtilities.invokeAndWait(this::cancelWorkers);
        TestFixtures.deleteDirectory(directory);
    }

    /** Cross-feature state remains consistent across imports, pricing, cooking and restarts. */
    public void testCompleteUserJourney() throws Exception
    {
        SwingUtilities.invokeAndWait(() ->
        {
            assertEquals(0, table("pantryTable").getRowCount());
            assertEquals(0, table("recipeTable").getRowCount());
            assertEquals(5, tabs().getTabCount());
            click("mealRoulette");
            assertTrue(message().contains("No cookable"));
            addPantry("Rice", "0");
            assertEquals(0, controller.getPantryIngredients().length);
            assertTrue(message().contains("greater than 0"));
            addPantry("Rice", "200");
            addPantry("Flour", "25");
            selectNamed("pantryTable", "Flour");
            set("pantryQuantity", "30"); click("pantryUpdate");
            assertEquals(30.0, controller.searchPantryIngredients("Flour")[0].getQuantity(), 0.0);
            selectNamed("pantryTable", "Flour"); click("pantryRemove");
            set("pantryQuery", "absent"); click("pantrySearch");
            assertEquals(0, table("pantryTable").getRowCount());
            click("pantryShowAll");
            tabs().setSelectedIndex(2);
            set("recipeName", "Rice supper"); set("cookTime", "10");
            set("requirementName", "Rice"); set("requirementQuantity", "800");
            combo("requirementUnit").setSelectedItem("gram"); click("requirementAdd");
            ((JTextArea)find("instructions")).setText("Boil the rice.\nServe.");
            click("recipeCreate");
            assertEquals(1, controller.getSavedRecipes().length);
            assertTrue(message().contains("Recipe added"));
            click("recipeCompare");
            assertEquals(600.0, controller.compareRecipeWithPantry(recipe("Rice supper"))
                .getMissingIngredients()[0].getQuantity(), 0.0);
            tabs().setSelectedIndex(3); set("externalQuery", "rice"); click("externalSearch");
        });
        awaitIdle("externalCancel");
        SwingUtilities.invokeAndWait(() ->
        {
            table("externalResults").setRowSelectionInterval(0, 0);
            assertEquals("1 tin", table("importIngredients").getValueAt(1, 1));
            table("importIngredients").setValueAt("600", 0, 2);
            table("importIngredients").setValueAt(Unit.GRAM, 0, 3);
            set("importPrep", "not sure yet"); click("externalSaveLater");
            assertEquals(1, controller.getSavedDrafts().length);
            assertEquals(1, controller.getSavedRecipes().length);
            assertNull(controller.selectMealRouletteRecipe());
            assertFalse(controller.hasUnsavedChanges());
        });
        capture("01-saved-draft");
        assertEquals(1, store.load().getSavedDrafts().length);

        // A fresh controller and view have no recipe network service at all.
        reopen(false);
        SwingUtilities.invokeAndWait(() ->
        {
            assertEquals(1, table("pantryTable").getRowCount());
            assertEquals(1, table("recipeTable").getRowCount());
            tabs().setSelectedIndex(3); click("externalSavedDrafts");
            table("externalResults").setRowSelectionInterval(0, 0);
            assertEquals("600", table("importIngredients").getValueAt(0, 2));
            assertEquals(Unit.GRAM, table("importIngredients").getValueAt(0, 3));
            assertEquals("not sure yet", field("importPrep").getText());
            click("externalImport");
            assertTrue(label("externalStatus").contains("Row 2"));
            table("importIngredients").setValueAt("200", 1, 2);
            table("importIngredients").setValueAt(Unit.GRAM, 1, 3);
            set("importPrep", ""); click("externalImport");
            assertEquals(2, controller.getSavedRecipes().length);
            assertEquals(0, controller.getSavedDrafts().length);
            assertTrue(((JTextArea)find("recipeDetails")).getText().contains("themealdb"));
            set("maxCookTime", "20"); click("recipeSearch");
            assertEquals(1, table("recipeTable").getRowCount());
            assertEquals("Rice supper", table("recipeTable").getValueAt(0, 0));
            click("recipeShowAll"); selectNamed("recipeTable", "Rice supper");
            click("recipeEstimate"); click("priceSearch");
        });
        byte[] beforePricing = Files.readAllBytes(directory.resolve("acceptance.properties"));
        awaitIdle("priceCancel");
        SwingUtilities.invokeAndWait(() ->
        {
            table("priceProducts").setRowSelectionInterval(0, 0); click("priceUse");
            assertTrue(label("priceTotal").contains("$5.00"));
            assertEquals("2", table("priceShortages").getValueAt(0, 4));
            assertTrue(label("priceHeading").contains("Lake Ridge, VA"));
            assertEquals(200.0, controller.searchPantryIngredients("Rice")[0].getQuantity(), 0.0);
            assertFalse(controller.compareRecipeWithPantry(recipe("Rice supper")).canCook());
        });
        assertTrue(Arrays.equals(beforePricing, Files.readAllBytes(directory.resolve("acceptance.properties"))));
        capture("02-walmart-estimate");
        SwingUtilities.invokeAndWait(() ->
        {
            tabs().setSelectedIndex(1); selectNamed("recipeTable", "Rice supper");
            click("recipeCook"); assertTrue(message().contains("Cannot cook"));
            assertEquals(200.0, controller.searchPantryIngredients("Rice")[0].getQuantity(), 0.0);
            tabs().setSelectedIndex(0); selectNamed("pantryTable", "Rice");
            set("pantryQuantity", "800"); click("pantryUpdate");
            tabs().setSelectedIndex(4);
            assertTrue(label("priceTotal").contains("unavailable"));
            click("priceReload"); assertTrue(label("priceTotal").contains("$0.00"));
            assertEquals(0, table("priceShortages").getRowCount());
            tabs().setSelectedIndex(1); selectNamed("recipeTable", "Rice supper");
            click("recipeCompare"); click("mealRoulette");
            assertTrue(message().contains("Rice supper"));
            click("recipeCook"); assertTrue(message().contains("Cooked!"));
            click("mealRoulette"); assertTrue(message().contains("No cookable"));
            tabs().setSelectedIndex(0); addPantry("Rice", "800"); addPantry("Beans", "200");
            tabs().setSelectedIndex(1); selectNamed("recipeTable", "Rice & beans");
            click("recipeCook"); assertTrue(message().contains("Cooked!"));
            assertEquals(200.0, controller.searchPantryIngredients("Rice")[0].getQuantity(), 0.0);
            assertFalse(controller.hasUnsavedChanges());
        });
        capture("03-after-cooking");
        assertEquals(1, recipes.calls);
        assertEquals(1, prices.searchCalls);
        assertFalse(recipes.calledOnEdt); assertFalse(prices.calledOnEdt);

        reopen(false);
        SwingUtilities.invokeAndWait(() ->
        {
            assertEquals(2, controller.getSavedRecipes().length);
            assertEquals(0, controller.getSavedDrafts().length);
            assertEquals(200.0, controller.searchPantryIngredients("Rice")[0].getQuantity(), 0.0);
            assertFalse(controller.compareRecipeWithPantry(recipe("Rice & beans")).canCook());
            assertTrue(label("priceTotal").contains("No estimate"));
            assertEquals(0, prices.calls);
            tabs().setSelectedIndex(1); selectNamed("recipeTable", "Rice supper"); click("recipeRemove");
            assertEquals(1, controller.getSavedRecipes().length);
        });
        assertEquals(1, store.load().getSavedRecipes().length);
    }

    private void reopen(boolean withImporter) throws Exception
    {
        if (gui != null) { SwingUtilities.invokeAndWait(this::cancelWorkers); }
        prices = new RecipeHttpStub();
        PriceFinder finder = PriceFixtures.finder(prices);
        prices.body = PriceFixtures.response(PriceFixtures.product("rice", "White Rice, 400 g", "2.50"));
        controller = new PantryPalController(store, new IngredientMatcher(), withImporter
            ? new RecipeImporter(URI.create("https://example.test/recipes/"), "1", recipes) : null, finder);
        controller.loadData();
        SwingUtilities.invokeAndWait(() -> gui = new PantryPalGUI(controller));
    }

    private void cancelWorkers()
    {
        ((BrokeModePanel)find("brokeModePanel")).cancelSearch();
        click("externalCancel");
    }
    private void addPantry(String name, String quantity)
    {
        set("pantryName", name); set("pantryQuantity", quantity);
        combo("pantryUnit").setSelectedItem("gram"); click("pantryAdd");
    }
    private Recipe recipe(String name)
    {
        for (Recipe recipe : controller.getSavedRecipes())
        { if (recipe.getName().equals(name)) { return recipe; } }
        throw new AssertionError("Missing saved recipe " + name);
    }
    private void selectNamed(String name, String value)
    {
        JTable target = table(name);
        for (int row = 0; row < target.getRowCount(); row++)
        { if (value.equals(target.getValueAt(row, 0))) { target.setRowSelectionInterval(row, row); return; } }
        throw new AssertionError("Missing table row " + value);
    }
    private void awaitIdle(String cancelName) throws Exception
    {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        AtomicBoolean idle = new AtomicBoolean();
        while (System.nanoTime() < deadline)
        {
            SwingUtilities.invokeAndWait(() -> idle.set(!((JButton)find(cancelName)).isEnabled()));
            if (idle.get()) { return; } Thread.sleep(20);
        }
        fail("Offline worker did not finish: " + cancelName);
    }
    private void capture(String name) throws Exception
    {
        String output = System.getProperty("pantrypal.acceptanceScreenshots");
        if (output == null) { return; }
        Path folder = Path.of(output); Files.createDirectories(folder);
        SwingUtilities.invokeAndWait(() ->
        {
            try
            {
                for (int[] size : new int[][] {{1100, 820}, {920, 720}})
                {
                    gui.setSize(size[0], size[1]); layout(gui); layout(gui);
                    BufferedImage image = new BufferedImage(size[0], size[1], BufferedImage.TYPE_INT_RGB);
                    Graphics2D graphics = image.createGraphics(); gui.printAll(graphics); graphics.dispose();
                    ImageIO.write(image, "png", folder.resolve(name + "-" + size[0] + ".png").toFile());
                }
            }
            catch (java.io.IOException exception) { throw new IllegalStateException(exception); }
        });
    }
    private static void layout(Container parent)
    {
        // Match JTable's display-time header setup without opening a native window.
        if (parent instanceof javax.swing.JScrollPane)
        {
            javax.swing.JScrollPane scroll = (javax.swing.JScrollPane)parent;
            if (scroll.getViewport().getView() instanceof JTable)
            { scroll.setColumnHeaderView(((JTable)scroll.getViewport().getView()).getTableHeader()); }
        }
        parent.doLayout(); for (Component child : parent.getComponents())
      { if (child instanceof Container) { layout((Container)child); } } }
    private String message() { return ((JTextArea)find("status")).getText(); }
    private String label(String name) { return ((JLabel)find(name)).getText(); }
    private JTabbedPane tabs() { return (JTabbedPane)find("tabs"); }
    private JTable table(String name) { return (JTable)find(name); }
    private JTextField field(String name) { return (JTextField)find(name); }
    private void set(String name, String value) { field(name).setText(value); }
    private void click(String name) { ((JButton)find(name)).doClick(0); }
    private JComboBox<?> combo(String name) { return (JComboBox<?>)find(name); }
    private Component find(String name)
    {
        Component result = find(gui, name);
        if (result == null) { throw new AssertionError("Missing control " + name); }
        return result;
    }
    private static Component find(Component parent, String name)
    {
        if (name.equals(parent.getName())) { return parent; }
        if (parent instanceof Container)
        { for (Component child : ((Container)parent).getComponents())
          { Component result = find(child, name); if (result != null) { return result; } } }
        return null;
    }
}
