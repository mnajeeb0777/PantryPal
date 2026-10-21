import java.awt.Component;
import java.awt.Container;
import java.net.URI;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import student.TestCase;

/** Real Swing actions use offline injected responses and never access the user's key. */
public class BrokeModePanelTest extends TestCase
{
    private RecipeHttpStub http;
    private PantryPalController controller;
    private BrokeModePanel panel;
    private Recipe recipe;
    @Override public void setUp() throws Exception
    {
        http=new RecipeHttpStub();
        PriceFinder finder=PriceFixtures.finder(http);
        http.body=PriceFixtures.response(PriceFixtures.product("a","White Rice, 400 g","2.50"));
        controller=new PantryPalController(null,new IngredientMatcher(),null,finder);
        recipe=controller.addCustomRecipe("Rice dinner",new Ingredient[]{TestFixtures.ingredient(1,"Rice",600,Unit.GRAM)},
            new String[]{"Cook rice"},1,1,Effort.EASY);
        SwingUtilities.invokeAndWait(()->{panel=new BrokeModePanel(controller);panel.showRecipe(recipe);});
    }
    @Override public void tearDown() throws Exception
    {SwingUtilities.invokeAndWait(()->panel.cancelSearch());}

    public void testReviewCompleteEstimateAndNoPantryMutation() throws Exception
    {
        search();
        SwingUtilities.invokeAndWait(()->{
            assertEquals(1,table("priceProducts").getRowCount());
            assertTrue(label("priceTotal").contains("Partial subtotal"));
            choose();assertTrue(label("priceTotal").contains("$5.00"));
            assertTrue(label("priceTotal").contains("Estimated Walmart purchase cost"));
            assertEquals("2",table("priceShortages").getValueAt(0,4));
            assertTrue(label("priceHeading").contains("Lake Ridge, VA"));
            assertEquals(0,controller.getPantryIngredients().length);
        });
        assertFalse(http.calledOnEdt);assertEquals(1,http.searchCalls);
    }
    public void testKnownShortageAndChangedPantryInvalidates() throws Exception
    {
        controller.addPantryIngredient("Rice",200,Unit.GRAM);
        SwingUtilities.invokeAndWait(()->panel.showRecipe(recipe));
        search();
        SwingUtilities.invokeAndWait(()->{choose();assertTrue(label("priceTotal").contains("$2.50"));});
        controller.addPantryIngredient("Rice",100,Unit.GRAM);
        SwingUtilities.invokeAndWait(()->{
            panel.refreshValidity();assertTrue(label("priceTotal").contains("unavailable"));
            assertFalse(button("priceSearch").isEnabled());click("priceReload");
            assertTrue(button("priceSearch").isEnabled());
        });
        search();assertEquals(1,http.searchCalls); // Current product cache, newly calculated shortage.
        SwingUtilities.invokeAndWait(()->{choose();assertTrue(label("priceTotal").contains("$2.50"));});
    }
    public void testUnknownRequirementStaysInPartialCoverage() throws Exception
    {
        controller.addPantryIngredient("Milk",100,Unit.GRAM);
        recipe=controller.addCustomRecipe("Unknown units",new Ingredient[]{
            TestFixtures.ingredient(1,"Rice",600,Unit.GRAM),TestFixtures.ingredient(2,"Milk",1,Unit.LITER)},
            new String[]{"Cook"},1,1,Effort.EASY);
        SwingUtilities.invokeAndWait(()->panel.showRecipe(recipe));search();
        SwingUtilities.invokeAndWait(()->{choose();assertTrue(label("priceTotal").contains("1 of 2"));
            assertTrue(label("priceTotal").contains("Partial subtotal"));});
        assertEquals(1,http.searchCalls);
    }
    public void testMissingPackageCanBeSkipped() throws Exception
    {
        http.body=PriceFixtures.response(PriceFixtures.product("a","White Rice","2.50"));search();
        SwingUtilities.invokeAndWait(()->{
            choose();assertTrue(label("priceStatus").contains("Package size"));
            click("priceSkip");assertTrue(label("priceTotal").contains("0 of 1"));
            assertEquals("Skipped.",table("priceShortages").getValueAt(0,6));
            assertFalse(table("priceProducts").isCellEditable(0,1));
        });
    }
    public void testEmptyErrorAndRetry() throws Exception
    {
        http.body=PriceFixtures.response("");search();
        SwingUtilities.invokeAndWait(()->{
            assertEquals("No Walmart candidates.",table("priceShortages").getValueAt(0,6));
            assertTrue(label("priceStatus").contains("No Walmart candidates found"));
        });
        // Another ingredient bypasses the successful-empty cache.
        recipe=controller.addCustomRecipe("Flour",new Ingredient[]{TestFixtures.ingredient(2,"Flour",100,Unit.GRAM)},
            new String[]{"Cook"},1,1,Effort.EASY);
        http.body="{\"error\":\"private provider details\"}";
        SwingUtilities.invokeAndWait(()->panel.showRecipe(recipe));search();
        SwingUtilities.invokeAndWait(()->{
            assertTrue(table("priceShortages").getValueAt(0,6).toString().contains("provider"));
            assertTrue(label("priceStatus").contains("Search stopped"));
            assertTrue(label("priceStatus").contains("provider"));
        });
        http.body=PriceFixtures.response(PriceFixtures.product("flour","Flour, 400 g","2.50"));
        SwingUtilities.invokeAndWait(()->click("priceRetry"));waitDone();
        SwingUtilities.invokeAndWait(()->{choose();assertTrue(label("priceTotal").contains("$2.50"));});
    }
    public void testDuplicateRequirementsShareOnePackage() throws Exception
    {
        recipe=controller.addCustomRecipe("Shared rice",new Ingredient[]{
            TestFixtures.ingredient(1,"Rice",200,Unit.GRAM),TestFixtures.ingredient(2,"Rice",0.1,Unit.KILOGRAM)},
            new String[]{"Cook"},1,1,Effort.EASY);
        SwingUtilities.invokeAndWait(()->panel.showRecipe(recipe));search();
        SwingUtilities.invokeAndWait(()->{assertEquals(1,table("priceShortages").getRowCount());
            choose();assertTrue(label("priceTotal").contains("$2.50"));});
    }
    public void testNoShortagesNeedsNoRequests() throws Exception
    {
        controller.addPantryIngredient("Rice",1000,Unit.GRAM);
        SwingUtilities.invokeAndWait(()->{
            panel.showRecipe(recipe);assertEquals(0,table("priceShortages").getRowCount());
            assertTrue(label("priceTotal").contains("$0.00"));assertFalse(button("priceSearch").isEnabled());
        });
        assertEquals(0,http.calls);
    }
    public void testRequestCapAndSearchRemaining() throws Exception
    {
        Ingredient[] ingredients=new Ingredient[16];
        for(int i=0;i<16;i++){ingredients[i]=TestFixtures.ingredient(i+1,"Rice "+i,10,Unit.GRAM);}
        recipe=controller.addCustomRecipe("Large recipe",ingredients,new String[]{"Cook"},1,1,Effort.EASY);
        SwingUtilities.invokeAndWait(()->panel.showRecipe(recipe));search();assertEquals(15,http.searchCalls);
        search();assertEquals(16,http.searchCalls);
    }
    public void testCancellationIgnoresLateResponse() throws Exception
    {
        CountDownLatch started=new CountDownLatch(1);CountDownLatch release=new CountDownLatch(1);
        PriceFinder delegate=PriceFixtures.finder(http);
        PriceFinder blocking=new PriceFinder(URI.create("https://example.test/"),"test-api-key",http,
            new IngredientMatcher(),"1852",Clock.fixed(PriceFixtures.NOW,ZoneOffset.UTC))
        {
            @Override public SearchResult findOffers(Ingredient ingredient) throws java.io.IOException,InterruptedException
            {
                started.countDown();
                while(release.getCount()>0){try{release.await();}catch(InterruptedException ignored){/* Simulated late provider. */}}
                return delegate.findOffers(ingredient);
            }
        };
        controller=new PantryPalController(null,new IngredientMatcher(),null,blocking);
        SwingUtilities.invokeAndWait(()->{panel=new BrokeModePanel(controller);panel.showRecipe(recipe);click("priceSearch");});
        assertTrue(started.await(2,TimeUnit.SECONDS));
        try
        {
            SwingUtilities.invokeAndWait(()->{click("priceCancel");assertTrue(button("priceSearch").isEnabled());});
        }
        finally{release.countDown();}
        Thread.sleep(100);
        SwingUtilities.invokeAndWait(()->{assertEquals(0,table("priceProducts").getRowCount());
            assertTrue(table("priceShortages").getValueAt(0,6).toString().contains("Cancelled"));});
    }
    public void testMissingConfigurationIsAnOptionalFeature() throws Exception
    {
        SwingUtilities.invokeAndWait(()->{
            panel=new BrokeModePanel(new PantryPalController());panel.showRecipe(recipe);
            assertFalse(button("priceSearch").isEnabled());assertTrue(label("priceStatus").contains("SERPAPI_API_KEY"));
        });
    }
    public void testRecipeActionOpensBrokeModeAndLeavingDisablesIt() throws Exception
    {
        SwingUtilities.invokeAndWait(()->{
            PantryPalGUI gui=new PantryPalGUI(controller);
            ((JTable)find(gui,"recipeTable")).setRowSelectionInterval(0,0);
            ((JButton)find(gui,"recipeEstimate")).doClick(0);
            javax.swing.JTabbedPane tabs=(javax.swing.JTabbedPane)find(gui,"tabs");
            assertEquals("Broke Mode",tabs.getTitleAt(tabs.getSelectedIndex()));
            assertTrue(controller.isBrokeModeEnabled());
            tabs.setSelectedIndex(0);assertFalse(controller.isBrokeModeEnabled());
        });
        assertEquals(0,http.calls);
    }
    private void search() throws Exception
    {SwingUtilities.invokeAndWait(()->click("priceSearch"));waitDone();}
    private void waitDone() throws Exception
    {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);AtomicBoolean done=new AtomicBoolean();
        while(System.nanoTime()<deadline)
        {
            SwingUtilities.invokeAndWait(()->done.set(!button("priceCancel").isEnabled()));
            if(done.get()){return;}Thread.sleep(20);
        }
        fail("Offline search did not finish");
    }
    private void choose()
    {table("priceShortages").setRowSelectionInterval(0,0);table("priceProducts").setRowSelectionInterval(0,0);click("priceUse");}
    private void click(String name){button(name).doClick(0);}
    private JButton button(String name){return (JButton)find(panel,name);}
    private JTable table(String name){return (JTable)find(panel,name);}
    private String label(String name){return ((JLabel)find(panel,name)).getText();}
    private Component find(Component component,String name)
    {
        if(name.equals(component.getName())){return component;}
        if(component instanceof Container){for(Component child:((Container)component).getComponents())
            {Component result=find(child,name);if(result!=null){return result;}}}
        return null;
    }
}
