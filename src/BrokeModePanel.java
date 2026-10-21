import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.FlowLayout;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

/** Reviews Walmart products for a snapshot of one recipe's known pantry shortages. */
public final class BrokeModePanel extends JPanel
{
    private final PantryPalController controller;
    private final JLabel heading = new JLabel("Select a saved recipe and click Estimate Walmart cost.");
    private final JLabel message = new JLabel();
    private final JLabel total = new JLabel("No estimate.");
    private final JTable shortages = table("priceShortages", "Ingredient", "Missing", "Unit",
        "Selected product", "Packages", "Purchase cost", "Status");
    private final JTable products = table("priceProducts", "Product", "Package", "Offer price",
        "Unit price", "Availability", "Estimate eligibility");
    private final List<Row> rows = new ArrayList<Row>();
    private final JButton search = button("priceSearch", "Search missing ingredients", () -> search(false));
    private final JButton retry = button("priceRetry", "Search selected ingredient", () -> search(true));
    private final JButton use = button("priceUse", "Use selected product", this::useProduct);
    private final JButton skip = button("priceSkip", "Skip for now", this::skipRow);
    private final JButton reload = button("priceReload", "Reload shortages", this::reloadRecipe);
    private final JButton cancel = button("priceCancel", "Cancel searches", this::cancelSearch);
    private final JButton open = button("priceOpen", "Open Walmart listing", this::openListing);
    private final Timer validity = new Timer(1000, event -> refreshValidity());
    private Recipe recipe;
    private long revision;
    private boolean stale = true;
    private boolean updating;
    private SwingWorker<List<Update>, Update> active;

    private static final class Row
    {
        private final Ingredient shortage;
        private final boolean unknown;
        private PriceFinder.SearchResult result;
        private StoreOffer selected;
        private String status;
        Row(Ingredient shortage, boolean unknown)
        { this.shortage = new Ingredient(shortage); this.unknown = unknown;
          status = unknown ? "Pantry quantity cannot be compared." : "Not searched."; }
    }
    private static final class Update
    {
        private final int index;
        private final PriceFinder.SearchResult result;
        private final String error;
        Update(int index, PriceFinder.SearchResult result, String error)
        { this.index = index; this.result = result; this.error = error; }
    }

    /** Construction does not request prices. Must run on Swing's event thread. */
    public BrokeModePanel(PantryPalController controller)
    {
        if (controller == null || !javax.swing.SwingUtilities.isEventDispatchThread())
        { throw new IllegalArgumentException("Create pricing UI on the Swing event thread with a controller."); }
        this.controller = controller;
        setLayout(new BorderLayout(6, 6));
        setName("brokeModePanel");
        heading.setName("priceHeading"); message.setName("priceStatus"); total.setName("priceTotal");
        JPanel top = new JPanel(new BorderLayout());
        top.add(heading, BorderLayout.NORTH);
        top.add(new JLabel("Review food form before selecting. Skip unknown sizes. Up to 15 searches per run."), BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
        for (JButton b : new JButton[] {search, retry, reload, cancel}) { actions.add(b); }
        top.add(actions, BorderLayout.SOUTH);
        add(top, BorderLayout.NORTH);
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
            scroll(shortages, "Missing recipe ingredients"), scroll(products, "Walmart product candidates"));
        split.setResizeWeight(0.5); split.setDividerLocation(180);
        add(split, BorderLayout.CENTER);
        JPanel bottom = new JPanel(new BorderLayout(4, 4));
        JPanel choices = new JPanel(new FlowLayout(FlowLayout.LEFT));
        choices.add(use); choices.add(skip); choices.add(open);
        bottom.add(choices, BorderLayout.NORTH);
        JPanel labels = new JPanel(new java.awt.GridLayout(0, 1));
        labels.add(total); labels.add(message);
        labels.add(new JLabel("USD estimates exclude taxes, shipping and delivery fees. Stock is provider-reported."));
        bottom.add(labels, BorderLayout.SOUTH); add(bottom, BorderLayout.SOUTH);
        shortages.getSelectionModel().addListSelectionListener(event ->
        { if (!updating && !event.getValueIsAdjusting()) { showProducts(); updateButtons(); } });
        message.setText(controller.isPricingAvailable() ? "Ready when you select a recipe."
            : "Set SERPAPI_API_KEY in your environment or .env.local, then restart PantryPal.");
        updateButtons();
    }

    @Override public void addNotify() { super.addNotify(); validity.start(); }
    @Override public void removeNotify() { validity.stop(); cancelSearch(); super.removeNotify(); }

    /** Starts a fresh shortage snapshot, without automatically searching. */
    public void showRecipe(Recipe selected)
    {
        cancelSearch();
        recipe = new Recipe(selected);
        revision = controller.getDataRevision(); stale = false;
        rows.clear();
        controller.setBrokeModeEnabled(true);
        for (Ingredient missing : controller.getPricingShortages(recipe)) { rows.add(new Row(missing, false)); }
        for (Ingredient unknown : controller.compareRecipeWithPantry(recipe).getUncomparableIngredients())
        { rows.add(new Row(unknown, true)); }
        heading.setText("Walmart estimate for " + recipe.getName() + (controller.isPricingAvailable()
            ? " — reference store " + controller.getReferenceStore() : " — pricing is not configured"));
        message.setText(!controller.isPricingAvailable()
            ? "Set SERPAPI_API_KEY in your environment or .env.local, then restart PantryPal."
            : rows.isEmpty() ? "No missing ingredients. Your pantry already covers this recipe."
            : "Click Search, then review and select one product for each known shortage.");
        render();
        if (!rows.isEmpty()) { shortages.setRowSelectionInterval(0, 0); }
    }

    private void reloadRecipe()
    {
        if (recipe == null) { return; }
        Recipe current = controller.findSavedRecipeById(recipe.getId());
        if (current == null)
        { cancelSearch(); stale = true; total.setText("Estimate unavailable — recipe was removed.");
          message.setText("Select another saved recipe."); updateButtons(); return; }
        try { showRecipe(current); }
        catch (IllegalArgumentException exception) { message.setText("Shortages could not be combined for this recipe."); }
    }

    private void search(boolean selectedOnly)
    {
        refreshValidity();
        if (stale || recipe == null || active != null || !controller.isPricingAvailable()) { return; }
        List<Integer> indices = new ArrayList<Integer>();
        int selected = shortages.getSelectedRow();
        for (int i = 0; i < rows.size() && indices.size() < 15; i++)
        {
            Row row = rows.get(i);
            if (!row.unknown && (!selectedOnly || i == selected)
                && (selectedOnly || (!row.status.equals("Skipped.")
                    && (row.result == null || !controller.isPriceSearchCurrent(row.result)))))
            { indices.add(i); row.selected = null; row.status = "Searching..."; }
        }
        if (indices.isEmpty()) { message.setText("No remaining searchable rows. Select a row to retry it."); return; }
        message.setText("Searching Walmart. Up to 15 requests; choose products after results arrive.");
        render();
        List<Ingredient> requests = new ArrayList<Ingredient>();
        for (int index : indices) { requests.add(new Ingredient(rows.get(index).shortage)); }
        SwingWorker<List<Update>, Update> worker = new SwingWorker<List<Update>, Update>()
        {
            @Override protected List<Update> doInBackground() throws Exception
            {
                List<Update> updates = new ArrayList<Update>();
                for (int position = 0; position < indices.size(); position++)
                {
                    if (isCancelled()) { break; }
                    int index = indices.get(position);
                    Update update;
                    try { update = new Update(index, controller.findOffers(requests.get(position)), null); }
                    catch (java.io.IOException exception) { update = new Update(index, null, exception.getMessage()); }
                    updates.add(update); publish(update);
                    // Provider errors require an explicit retry, not more quota-consuming work.
                    if (update.error != null) { break; }
                }
                return updates;
            }
            @Override protected void process(List<Update> updates)
            {
                if (active != this || stale || revision != controller.getDataRevision()) { return; }
                for (Update update : updates) { apply(update); }
                render();
            }
            @Override protected void done()
            {
                if (active != this) { return; }
                refreshValidity();
                if (active != this) { return; }
                try
                {
                    String error = null;
                    int candidates = 0;
                    for (Update update : get())
                    {
                        apply(update);
                        if (update.error != null) { error = update.error; }
                        if (update.result != null) { candidates += update.result.getOffers().length; }
                    }
                    message.setText(error != null ? "Search stopped: " + error
                        + " Select the affected ingredient to retry. Completed results are kept."
                        : candidates == 0 ? "No Walmart candidates found. Try another ingredient or skip this requirement."
                        : "Search finished. Select an ingredient, review a listing, then click Use selected product.");
                }
                catch (CancellationException exception) { message.setText("Search cancelled. Completed results are kept."); }
                catch (InterruptedException exception) { Thread.currentThread().interrupt(); message.setText("Search interrupted."); }
                catch (ExecutionException exception) { message.setText("Price search failed. Select the affected row and retry."); }
                active = null;
                for (Row row : rows) { if (row.status.equals("Searching...")) { row.status = "Not searched; retry explicitly."; } }
                render();
            }
        };
        active = worker; updateButtons(); worker.execute();
    }

    private void apply(Update update)
    {
        Row row = rows.get(update.index); row.result = update.result;
        row.status = update.error != null ? update.error
            : update.result.getOffers().length == 0 ? "No Walmart candidates."
            : "Choose a product; confirm its food form.";
        if (update.result != null)
        { heading.setText("Walmart estimate for " + recipe.getName() + " — " + update.result.getLocation()
            + " (store " + update.result.getStoreId() + ") — checked " + update.result.getCheckedAt()); }
    }

    /** Cancels in-flight work and ignores late responses; completed selections remain. */
    public void cancelSearch()
    {
        SwingWorker<List<Update>, Update> previous = active; active = null;
        if (previous != null)
        {
            previous.cancel(true);
            for (Row row : rows) { if (row.status.equals("Searching...")) { row.status = "Cancelled; retry explicitly."; } }
            message.setText("Search cancelled. Completed results are kept.");
            render();
        }
    }

    /** Checks mutations and expiry; used by the display timer and every editing action. */
    void refreshValidity()
    {
        if (recipe == null || stale) { return; }
        if (revision != controller.getDataRevision())
        {
            stale = true; cancelSearch();
            for (Row row : rows) { row.selected = null; row.result = null; row.status = "Snapshot changed — reload shortages."; }
            message.setText("Pantry or recipes changed. Click Reload shortages before estimating again.");
            render(); return;
        }
        boolean expired = false;
        for (Row row : rows)
        {
            if (row.result != null && !controller.isPriceSearchCurrent(row.result))
            { row.result = null; row.selected = null; row.status = "Price expired — search again."; expired = true; }
        }
        if (expired) { message.setText("Some prices expired. Recheck them before using the estimate."); render(); }
    }

    private void useProduct()
    {
        refreshValidity();
        Row row = selectedRow(); StoreOffer offer = selectedProduct();
        if (stale || row == null || offer == null || row.unknown) { return; }
        try
        {
            controller.calculatePurchaseCost(row.shortage, offer);
            row.selected = offer; row.status = "Product reviewed.";
            message.setText("Product selected. Check that this food form and package meet your requirement."); render();
        }
        catch (IllegalArgumentException exception)
        { message.setText(offer.getExclusionReason().isEmpty()
            ? "Units or freshness do not support a package estimate. Choose another listing or skip."
            : offer.getExclusionReason() + " Choose another listing or skip."); }
    }

    private void skipRow()
    {
        refreshValidity(); Row row = selectedRow();
        if (stale || row == null) { return; }
        row.selected = null; row.status = "Skipped."; render();
    }

    private void openListing()
    {
        StoreOffer offer = selectedProduct();
        if (offer == null || offer.getProductUrl() == null) { message.setText("No validated Walmart link for this listing."); return; }
        try
        {
            if (!Desktop.isDesktopSupported()) { throw new java.io.IOException(); }
            Desktop.getDesktop().browse(offer.getProductUrl());
        }
        catch (java.io.IOException | UnsupportedOperationException | SecurityException exception)
        { message.setText("Could not open the Walmart listing in your browser."); }
    }

    private Row selectedRow()
    { int index = shortages.getSelectedRow(); return index < 0 || index >= rows.size() ? null : rows.get(index); }
    private StoreOffer selectedProduct()
    {
        Row row = selectedRow(); int index = products.getSelectedRow();
        return row == null || row.result == null || index < 0 || index >= row.result.getOffers().length
            ? null : row.result.getOffers()[index];
    }
    private void showProducts()
    {
        model(products).setRowCount(0); Row row = selectedRow();
        if (row == null || row.result == null) { return; }
        for (StoreOffer offer : row.result.getOffers())
        { model(products).addRow(new Object[] {offer.getProductName(),
            offer.getPackageQuantity() == null ? "Unknown / review title" : number(offer.getPackageQuantity()) + " " + unitText(offer.getUnit()),
            money(offer.getPackagePrice()), offer.getUnitPriceText(),
            offer.getAvailability() == StoreOffer.Availability.IN_STOCK ? "Reported available"
                : offer.getAvailability() == StoreOffer.Availability.OUT_OF_STOCK ? "Reported unavailable" : "Unknown",
            offer.getExclusionReason().isEmpty() ? "Confirm food form and unit compatibility" : offer.getExclusionReason()}); }
    }

    private void render()
    {
        int index = shortages.getSelectedRow(); updating = true; model(shortages).setRowCount(0);
        BigDecimal subtotal = BigDecimal.ZERO; int priced = 0;
        for (Row row : rows)
        {
            String packages = ""; String costText = "";
            if (row.selected != null && !stale)
            {
                try
                {
                    BigDecimal cost = controller.calculatePurchaseCost(row.shortage, row.selected);
                    packages = Integer.toString(controller.calculateRequiredPackages(row.shortage, row.selected));
                    costText = money(cost); subtotal = subtotal.add(cost); priced++;
                }
                catch (IllegalArgumentException exception) { row.selected = null; row.status = "Listing no longer supports an estimate."; }
            }
            model(shortages).addRow(new Object[] {row.shortage.getName(), row.unknown ? "Unknown"
                : number(row.shortage.getQuantity()), unitText(row.shortage.getUnit()),
                row.selected == null ? "" : row.selected.getProductName(), packages, costText, row.status});
        }
        if (index >= 0 && index < rows.size()) { shortages.setRowSelectionInterval(index, index); }
        updating = false; showProducts();
        total.setText(stale ? "Estimate unavailable — reload changed shortages."
            : priced == rows.size() ? "Estimated Walmart purchase cost: " + money(subtotal)
            : "Partial subtotal: " + money(subtotal) + " — " + priced + " of " + rows.size() + " requirements priced");
        updateButtons();
    }
    private void updateButtons()
    {
        boolean ready = !stale && recipe != null && controller.isPricingAvailable();
        search.setEnabled(ready && active == null && !rows.isEmpty());
        retry.setEnabled(ready && active == null && selectedRow() != null && !selectedRow().unknown);
        use.setEnabled(ready && active == null && selectedRow() != null);
        skip.setEnabled(ready && active == null && selectedRow() != null);
        reload.setEnabled(recipe != null && active == null); cancel.setEnabled(active != null);
        open.setEnabled(selectedRow() != null);
    }
    private static String number(double value) { return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString(); }
    private static String unitText(Unit unit) { return unit.name().toLowerCase(Locale.ROOT).replace('_', ' '); }
    private static JScrollPane scroll(JTable table, String title)
    {
        JScrollPane scroll = new JScrollPane(table);
        scroll.setColumnHeaderView(table.getTableHeader());
        scroll.setBorder(javax.swing.BorderFactory.createTitledBorder(title));
        return scroll;
    }
    private static String money(BigDecimal value) { return "$" + value.setScale(2, java.math.RoundingMode.HALF_UP); }
    private static DefaultTableModel model(JTable table) { return (DefaultTableModel)table.getModel(); }
    private static JTable table(String name, String... columns)
    {
        JTable table = new JTable(new DefaultTableModel(columns, 0)
        { @Override public boolean isCellEditable(int row, int column) { return false; } });
        table.setName(name); table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        DefaultTableCellRenderer renderer = new DefaultTableCellRenderer()
        {
            @Override public java.awt.Component getTableCellRendererComponent(JTable source,
                Object value, boolean selected, boolean focus, int row, int column)
            {
                super.getTableCellRendererComponent(source, value, selected, focus, row, column);
                setToolTipText(value == null ? null : "Details: " + value);
                return this;
            }
        };
        renderer.putClientProperty("html.disable", Boolean.TRUE); table.setDefaultRenderer(Object.class, renderer);
        return table;
    }
    private static JButton button(String name, String label, Runnable action)
    { JButton button = new JButton(label); button.setName(name); button.addActionListener(event -> action.run()); return button; }
}
