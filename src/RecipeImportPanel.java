import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.io.IOException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.function.BiConsumer;
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
    private final BiConsumer<String, Boolean> feedback;
    private final JButton saveLater = new JButton("Save for later");
    private final JButton savedLater = new JButton("Saved for later (0)");
    private final JButton removeDraft = new JButton("Remove saved draft");
    private final JTextField query = new JTextField(22);
    private final JButton search = new JButton("Search TheMealDB");
    private final JButton cancel = new JButton("Cancel search");
    private final JButton save = new JButton("Import reviewed recipe");
    private final JLabel message = new JLabel("Search by recipe name.");
    private final JTextField prep = new JTextField(4);
    private final JTextField cook = new JTextField(4);
    private final JComboBox<Effort> effort = new JComboBox<Effort>(Effort.values());
    private final DefaultTableModel resultsModel = new DefaultTableModel(
        new String[] { "Recipe", "TheMealDB ID", "Status" }, 0)
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
     * @param feedback callback updating application save status
     */
    public RecipeImportPanel(PantryPalController controller,
        Consumer<Recipe> importRecipe, BiConsumer<String, Boolean> feedback)
    {
        this.controller = controller;
        this.importRecipe = importRecipe;
        this.feedback = feedback;
        setLayout(new BorderLayout(8, 8));
        query.setName("externalQuery");
        search.setName("externalSearch");
        cancel.setName("externalCancel");
        save.setName("externalImport");
        saveLater.setName("externalSaveLater");
        savedLater.setName("externalSavedDrafts");
        removeDraft.setName("externalRemoveDraft");
        message.setName("externalStatus");
        results.setName("externalResults");
        review.setName("importIngredients");
        instructions.setName("importInstructions");
        prep.setName("importPrep");
        cook.setName("importCook");
        effort.setName("importEffort");
        JPanel top = new JPanel(new BorderLayout());
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
        actions.add(query); actions.add(search); actions.add(cancel); actions.add(savedLater);
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
            + "use Save for later if you do not know an amount."), BorderLayout.NORTH);
        editor.add(new JScrollPane(review), BorderLayout.CENTER);
        instructions.setEditable(false);
        instructions.setLineWrap(true);
        instructions.setWrapStyleWord(true);
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(new JScrollPane(instructions), BorderLayout.CENTER);
        JPanel metadata = new JPanel(new FlowLayout(FlowLayout.LEFT));
        metadata.add(new JLabel("Prep minutes (optional)")); metadata.add(prep);
        metadata.add(new JLabel("Cook minutes (optional)")); metadata.add(cook);
        metadata.add(effort);
        JPanel reviewActions = new JPanel(new FlowLayout(FlowLayout.LEFT));
        reviewActions.add(save); reviewActions.add(saveLater); reviewActions.add(removeDraft);
        JPanel footer = new JPanel(new BorderLayout());
        footer.add(metadata, BorderLayout.NORTH);
        footer.add(reviewActions, BorderLayout.SOUTH);
        bottom.add(footer, BorderLayout.SOUTH);
        editor.add(bottom, BorderLayout.SOUTH);
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
            new JScrollPane(results), editor);
        split.setResizeWeight(0.3);
        add(split, BorderLayout.CENTER);
        cancel.setEnabled(false);
        save.setEnabled(false);
        saveLater.setEnabled(false);
        removeDraft.setEnabled(false);
        refreshSavedCount();
        effort.setSelectedItem(Effort.UNKNOWN);
        search.addActionListener(event -> startSearch());
        query.addActionListener(event -> { if (active == null) { startSearch(); } });
        cancel.addActionListener(event -> cancelSearch());
        save.addActionListener(event -> saveReviewed());
        saveLater.addActionListener(event -> saveForLater());
        savedLater.addActionListener(event -> showSavedDrafts());
        removeDraft.addActionListener(event -> removeSavedDraft());
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
        clearResults();
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
                            draft.getName(), draft.getExternalId(),
                            resultStatus(draft.getExternalId()) });
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
        saveLater.setEnabled(false);
        removeDraft.setEnabled(false);
        int row = results.getSelectedRow();
        if (row < 0) { return; }
        selected = drafts[results.convertRowIndexToModel(row)];
        RecipeImportDraft saved = savedDraft(selected.getExternalId());
        if (saved != null) { selected = saved; }
        String[] measures = selected.getMeasurements();
        String[][] rows = selected.getReviewRows();
        int unresolved = 0;
        for (int i = 0; i < rows.length; i++)
        {
            if (rows[i][1].isBlank() || rows[i][2].isBlank()) { unresolved++; }
            reviewModel.addRow(new Object[] { rows[i][0], measures[i], rows[i][1],
                rows[i][2].isEmpty() ? null : Unit.valueOf(rows[i][2]) });
        }
        prep.setText(selected.getPrepText());
        cook.setText(selected.getCookText());
        effort.setSelectedItem(selected.getEffort());
        instructions.setText((selected.getInstructions().isBlank()
            ? "No cooking instructions supplied." : selected.getInstructions())
            + "\n\nRecipe data: TheMealDB - " + selected.getProviderUrl()
            + (selected.getOriginalSource().isBlank() ? ""
                : "\nOriginal source: " + selected.getOriginalSource()));
        instructions.setCaretPosition(0);
        message.setText(unresolved + " measurement(s) need correction. "
            + "Review before importing, or Save for later. Times may stay blank.");
        save.setEnabled(true);
        saveLater.setEnabled(true);
        removeDraft.setEnabled(saved != null);
    }

    /** Updates the saved-draft count after application loading or mutations. */
    public void refreshSavedCount()
    {
        savedLater.setText("Saved for later (" + controller.getSavedDrafts().length + ")");
        for (int i = 0; i < resultsModel.getRowCount(); i++)
        {
            String id = resultsModel.getValueAt(i, 1).toString();
            resultsModel.setValueAt(resultStatus(id), i, 2);
        }
    }

    private String resultStatus(String id)
    {
        if (savedDraft(id) != null) { return "Needs review"; }
        for (Recipe recipe : controller.getSavedRecipes())
        {
            if (recipe.getExternalRecipeId().equals("themealdb:" + id))
            {
                return "In collection";
            }
        }
        return "Not saved";
    }

    private void clearResults()
    {
        results.clearSelection();
        results.getSelectionModel().setAnchorSelectionIndex(-1);
        results.getSelectionModel().setLeadSelectionIndex(-1);
        resultsModel.setRowCount(0);
    }

    private RecipeImportDraft savedDraft(String id)
    {
        for (RecipeImportDraft draft : controller.getSavedDrafts())
        {
            if (draft.getExternalId().equals(id)) { return draft; }
        }
        return null;
    }

    private void showSavedDrafts()
    {
        cancelSearch();
        clearResults();
        drafts = controller.getSavedDrafts();
        for (RecipeImportDraft draft : drafts)
        {
            resultsModel.addRow(new Object[] { draft.getName(), draft.getExternalId(), "Needs review" });
        }
        refreshSavedCount();
        message.setText(drafts.length == 0 ? "No recipes saved for later. Search to find one."
            : "Saved for later: select a recipe to continue review. These recipes cannot be cooked yet.");
    }

    private RecipeImportDraft captureReview()
    {
        if (review.isEditing() && !review.getCellEditor().stopCellEditing())
        {
            throw new IllegalArgumentException("Finish the active edit before saving.");
        }
        String[][] rows = new String[reviewModel.getRowCount()][3];
        for (int i = 0; i < rows.length; i++)
        {
            Object name = reviewModel.getValueAt(i, 0);
            Object quantity = reviewModel.getValueAt(i, 2);
            Object unit = reviewModel.getValueAt(i, 3);
            rows[i][0] = name == null ? "" : name.toString();
            rows[i][1] = quantity == null ? "" : quantity.toString();
            rows[i][2] = unit instanceof Unit ? ((Unit)unit).name() : "";
        }
        return selected.withReview(rows, prep.getText(), cook.getText(),
            (Effort)effort.getSelectedItem());
    }

    private void saveForLater()
    {
        if (selected == null) { return; }
        try
        {
            RecipeImportDraft snapshot = captureReview();
            controller.saveRecipeDraft(snapshot);
            selected = snapshot;
            removeDraft.setEnabled(true);
            refreshSavedCount();
            String notice = "Saved for later" + (controller.isPersistenceEnabled()
                ? "." : " in this session.")
                + " Your edits were kept. Open Saved for later to resume review.";
            message.setText(notice);
            feedback.accept(notice, false);
        }
        catch (IOException exception) { saveFailure(exception); }
        catch (IllegalArgumentException | IllegalStateException exception)
        {
            message.setText(exception.getMessage());
        }
    }

    private void removeSavedDraft()
    {
        if (selected == null) { return; }
        try
        {
            controller.removeRecipeDraft(selected.getExternalId());
            showSavedDrafts();
            feedback.accept("Saved draft removed.", false);
        }
        catch (IOException exception)
        {
            showSavedDrafts();
            saveFailure(exception);
        }
        catch (IllegalStateException exception) { message.setText(exception.getMessage()); }
    }

    private void saveFailure(IOException exception)
    {
        refreshSavedCount();
        removeDraft.setEnabled(selected != null && savedDraft(selected.getExternalId()) != null);
        String notice = "Could not save: " + exception.getMessage()
            + " Changes remain in this session. Click Save now to retry before closing.";
        message.setText(notice);
        feedback.accept(notice, true);
    }

    private void saveReviewed()
    {
        if (selected == null) { return; }
        try
        {
            importRecipe.accept(captureReview().toRecipe());
            refreshSavedCount();
            removeDraft.setEnabled(savedDraft(selected.getExternalId()) != null);
        }
        catch (IllegalArgumentException exception) { message.setText(exception.getMessage()); }
    }
}
