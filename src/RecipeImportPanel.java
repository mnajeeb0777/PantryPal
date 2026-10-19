import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import javax.swing.DefaultCellEditor;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;

/**
 * Searches in the background and reviews every ingredient before importing.
 * @author PantryPal team (GenAI-assisted implementation)
 * @version 2026.10.18
 */
public class RecipeImportPanel extends JPanel
{
    private final PantryPalController controller;
    private final Consumer<Recipe> importRecipe;
    private final JTextField query = new JTextField(22);
    private final JButton search = new JButton("Search TheMealDB");
    private final JButton cancel = new JButton("Cancel search");
    private final JButton save = new JButton("Import reviewed recipe");
    private final JLabel message = new JLabel("Search by recipe name.");
    private final JTextField prep = new JTextField(4);
    private final JTextField cook = new JTextField(4);
    private final JComboBox<Effort> effort = new JComboBox<Effort>(Effort.values());
    private final DefaultTableModel resultsModel = new DefaultTableModel(
        new String[] { "Recipe", "TheMealDB ID" }, 0)
    {
        @Override
        public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable results = new JTable(resultsModel);
    private final DefaultTableModel reviewModel = new DefaultTableModel(
        new String[] { "Ingredient", "Original measurement", "Quantity", "Unit" }, 0)
    {
        @Override
        public boolean isCellEditable(int row, int column) { return column != 1; }
    };
    private final JTable review = new JTable(reviewModel);
    private final JTextArea instructions = new JTextArea(7, 50);
    private RecipeImportDraft[] drafts = new RecipeImportDraft[0];
    private RecipeImportDraft selected;
    private SwingWorker<RecipeImportDraft[], Void> active;

    /**
     * Creates controls on the Swing EDT; the callback owns saving and feedback.
     * @param controller application services
     * @param importRecipe callback for a completely validated recipe
     */
    public RecipeImportPanel(PantryPalController controller,
        Consumer<Recipe> importRecipe)
    {
        this.controller = controller;
        this.importRecipe = importRecipe;
        setLayout(new BorderLayout(8, 8));
        query.setName("externalQuery");
        search.setName("externalSearch");
        cancel.setName("externalCancel");
        save.setName("externalImport");
        message.setName("externalStatus");
        results.setName("externalResults");
        review.setName("importIngredients");
        instructions.setName("importInstructions");
        prep.setName("importPrep");
        cook.setName("importCook");
        effort.setName("importEffort");
        JPanel top = new JPanel(new BorderLayout());
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
        actions.add(query); actions.add(search); actions.add(cancel);
        top.add(actions, BorderLayout.NORTH);
        top.add(message, BorderLayout.SOUTH);
        add(top, BorderLayout.NORTH);
        results.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        results.setAutoCreateRowSorter(true);
        review.setRowHeight(27);
        review.getTableHeader().setReorderingAllowed(false);
        review.getColumnModel().getColumn(3).setCellEditor(
            new DefaultCellEditor(new JComboBox<Unit>(Unit.values())));
        JPanel editor = new JPanel(new BorderLayout(6, 6));
        editor.add(new JLabel("Review every row. Blank quantity/unit needs correction; "
            + "count means whole items, not cloves or package sizes."), BorderLayout.NORTH);
        editor.add(new JScrollPane(review), BorderLayout.CENTER);
        instructions.setEditable(false);
        instructions.setLineWrap(true);
        instructions.setWrapStyleWord(true);
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(new JScrollPane(instructions), BorderLayout.CENTER);
        JPanel metadata = new JPanel(new FlowLayout(FlowLayout.LEFT));
        metadata.add(new JLabel("Prep minutes (optional)")); metadata.add(prep);
        metadata.add(new JLabel("Cook minutes (optional)")); metadata.add(cook);
        metadata.add(effort); metadata.add(save);
        bottom.add(metadata, BorderLayout.SOUTH);
        editor.add(bottom, BorderLayout.SOUTH);
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
            new JScrollPane(results), editor);
        split.setResizeWeight(0.3);
        add(split, BorderLayout.CENTER);
        cancel.setEnabled(false);
        save.setEnabled(false);
        effort.setSelectedItem(Effort.UNKNOWN);
        search.addActionListener(event -> startSearch());
        query.addActionListener(event -> { if (active == null) { startSearch(); } });
        cancel.addActionListener(event -> cancelSearch());
        save.addActionListener(event -> saveReviewed());
        results.getSelectionModel().addListSelectionListener(event ->
        {
            if (!event.getValueIsAdjusting()) { selectDraft(); }
        });
    }

    private void startSearch()
    {
        String text = query.getText().strip();
        if (text.isEmpty() || text.length() > 200)
        {
            message.setText("Enter a recipe name (1-200 characters).");
            return;
        }
        cancelSearch();
        results.clearSelection();
        resultsModel.setRowCount(0);
        drafts = new RecipeImportDraft[0];
        message.setText("Searching TheMealDB...");
        search.setEnabled(false);
        cancel.setEnabled(true);
        active = new SwingWorker<RecipeImportDraft[], Void>()
        {
            @Override
            protected RecipeImportDraft[] doInBackground()
                throws IOException, InterruptedException
            {
                return controller.searchExternalRecipes(text);
            }

            @Override
            protected void done()
            {
                if (active != this) { return; }
                active = null;
                search.setEnabled(true);
                cancel.setEnabled(false);
                try
                {
                    drafts = get();
                    for (RecipeImportDraft draft : drafts)
                    {
                        resultsModel.addRow(new Object[] {
                            draft.getName(), draft.getExternalId() });
                    }
                    message.setText(drafts.length == 0 ? "No recipes found. Try another name."
                        : "Select a recipe to review its measurements.");
                }
                catch (CancellationException exception)
                {
                    message.setText("Search cancelled.");
                }
                catch (InterruptedException exception)
                {
                    Thread.currentThread().interrupt();
                    message.setText("Search interrupted. Try again.");
                }
                catch (ExecutionException exception)
                {
                    message.setText("Search failed: " + exception.getCause().getMessage()
                        + " Click Search to retry.");
                }
            }
        };
        active.execute();
    }

    /** Cancels pending work and prevents a late response from updating the view. */
    public void cancelSearch()
    {
        SwingWorker<RecipeImportDraft[], Void> previous = active;
        active = null;
        if (previous != null) { previous.cancel(true); }
        search.setEnabled(true);
        cancel.setEnabled(false);
        if (previous != null) { message.setText("Search cancelled."); }
    }

    @Override
    public void removeNotify()
    {
        cancelSearch();
        super.removeNotify();
    }

    private void selectDraft()
    {
        if (review.isEditing()) { review.getCellEditor().cancelCellEditing(); }
        selected = null;
        reviewModel.setRowCount(0);
        instructions.setText("");
        prep.setText(""); cook.setText("");
        effort.setSelectedItem(Effort.UNKNOWN);
        save.setEnabled(false);
        int row = results.getSelectedRow();
        if (row < 0) { return; }
        selected = drafts[results.convertRowIndexToModel(row)];
        String[] names = selected.getIngredientNames();
        String[] measures = selected.getMeasurements();
        int unresolved = 0;
        for (int i = 0; i < names.length; i++)
        {
            Ingredient suggestion = RecipeImportDraft.parseMeasurement(names[i], measures[i]);
            if (suggestion == null) { unresolved++; }
            reviewModel.addRow(new Object[] { names[i], measures[i],
                suggestion == null ? "" : Double.toString(suggestion.getQuantity()),
                suggestion == null ? null : suggestion.getUnit() });
        }
        instructions.setText((selected.getInstructions().isBlank()
            ? "No cooking instructions supplied." : selected.getInstructions())
            + "\n\nRecipe data: TheMealDB - " + selected.getProviderUrl()
            + (selected.getOriginalSource().isBlank() ? ""
                : "\nOriginal source: " + selected.getOriginalSource()));
        instructions.setCaretPosition(0);
        message.setText(unresolved + " measurement(s) need correction. "
            + "Review names and amounts before importing. Times may stay blank.");
        save.setEnabled(true);
    }

    private void saveReviewed()
    {
        if (selected == null) { return; }
        if (review.isEditing() && !review.getCellEditor().stopCellEditing()) { return; }
        try
        {
            Ingredient[] ingredients = new Ingredient[reviewModel.getRowCount()];
            for (int i = 0; i < ingredients.length; i++)
            {
                try
                {
                    Object unit = reviewModel.getValueAt(i, 3);
                    ingredients[i] = new Ingredient(UUID.randomUUID().toString(),
                        String.valueOf(reviewModel.getValueAt(i, 0)).strip(),
                        Double.parseDouble(String.valueOf(reviewModel.getValueAt(i, 2)).strip()),
                        unit instanceof Unit ? (Unit)unit : null);
                }
                catch (IllegalArgumentException exception)
                {
                    throw new IllegalArgumentException("Row " + (i + 1)
                        + ": enter a name, positive quantity, and unit.", exception);
                }
            }
            Recipe recipe = selected.toRecipe(ingredients, minutes(prep), minutes(cook),
                (Effort)effort.getSelectedItem());
            importRecipe.accept(recipe);
        }
        catch (IllegalArgumentException exception)
        {
            message.setText(exception.getMessage());
        }
    }

    private static Integer minutes(JTextField field)
    {
        if (field.getText().isBlank()) { return null; }
        try
        {
            int value = Integer.parseInt(field.getText().strip());
            if (value < 0) { throw new NumberFormatException(); }
            return value;
        }
        catch (NumberFormatException exception)
        {
            throw new IllegalArgumentException("Times must be nonnegative whole minutes or blank.",
                exception);
        }
    }
}
