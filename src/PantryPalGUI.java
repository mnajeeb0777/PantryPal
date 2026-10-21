import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;

/**
 * A Swing view for pantry and recipe management with optional local saving.
 * Parses input and displays feedback through an application controller.
 *
 * @author PantryPal team (GenAI-assisted implementation)
 * @version 2026.10.14
 */
public class PantryPalGUI extends JPanel
{
    private final PantryPalController controller;
    private final JTextArea status = textArea("status", 2);
    private final JLabel storageStatus = new JLabel();
    private boolean loadFailed;
    private JButton retryLoad;
    private final JTabbedPane tabs = new JTabbedPane();
    private RecipeImportPanel importPanel;
    private BrokeModePanel brokePanel;
    private final JTextField pantryQuery = field("pantryQuery");
    private final JTextField pantryName = field("pantryName");
    private final JTextField pantryQuantity = field("pantryQuantity");
    private final JComboBox<String> pantryUnit = units("pantryUnit");
    private final JTable pantryTable = table("pantryTable",
        "Ingredient", "Quantity", "Unit");
    private Ingredient[] shownPantry = new Ingredient[0];
    private String selectedPantryId;
    private boolean refreshingPantry;

    private final JTextField recipeQuery = field("recipeQuery");
    private final JTextField maxCookTime = field("maxCookTime");
    private final JComboBox<String> effortFilter =
        choices("effortFilter", "Any effort", "EASY", "MEDIUM", "HARD",
            "UNKNOWN");
    private final JTable recipeTable = table("recipeTable",
        "Recipe", "Cook minutes", "Effort");
    private Recipe[] shownRecipes = new Recipe[0];
    private final JTextArea details = textArea("recipeDetails", 4);
    private final JLabel comparisonSummary = new JLabel("Select a recipe.");
    private final JTable comparisonTable = table("comparisonTable",
        "Ingredient", "Required", "Allocated", "Missing", "Unit", "Status");

    private final JTextField recipeName = field("recipeName");
    private final JTextField prepTime = field("prepTime");
    private final JTextField cookTime = field("cookTime");
    private final JComboBox<String> recipeEffort =
        choices("recipeEffort", "EASY", "MEDIUM", "HARD", "UNKNOWN");
    private final JTextArea instructions = textArea("instructions", 5);
    private final JTextField requirementName = field("requirementName");
    private final JTextField requirementQuantity =
        field("requirementQuantity");
    private final JComboBox<String> requirementUnit = units("requirementUnit");
    private final JTable requirementTable = table("requirementTable",
        "Ingredient", "Quantity", "Unit");
    private final List<Ingredient> draftRequirements =
        new ArrayList<Ingredient>();


    /**
     * Builds the view on Swing's event-dispatch thread.
     * @param controller application operations for this session
     * @throws IllegalArgumentException if the controller is null
     * @throws IllegalStateException if called outside Swing's event thread
     */
    public PantryPalGUI(PantryPalController controller)
    {
        if (controller == null)
        {
            throw new IllegalArgumentException("Controller is required.");
        }
        if (!SwingUtilities.isEventDispatchThread())
        {
            throw new IllegalStateException(
                "Create the GUI on the Swing EDT.");
        }
        this.controller = controller;
        setLayout(new BorderLayout(12, 12));
        setBorder(BorderFactory.createEmptyBorder(18, 20, 14, 20));
        setBackground(new Color(244, 247, 241));
        JPanel header = new JPanel(new BorderLayout(4, 6));
        header.setOpaque(false);
        JLabel title = new JLabel("PantryPal");
        title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 30));
        title.setForeground(new Color(35, 83, 61));
        header.add(title, BorderLayout.NORTH);
        header.add(new JLabel(
            "Know what you have. Make something good."), BorderLayout.CENTER);
        storageStatus.setName("storageStatus");
        storageStatus.setForeground(new Color(124, 68, 22));
        header.add(storageStatus, BorderLayout.SOUTH);
        JPanel actions = row(button("loadDemo", "Load demo data",
            () -> loadDemoData()));
        if (controller.isPersistenceEnabled())
        {
            actions.add(button("saveNow", "Save now", () ->
            {
                controller.saveData();
                feedback("All pantry and recipe changes are saved.", false);
            }));
            retryLoad = button("retryLoad", "Retry loading",
                () -> loadSavedData());
            retryLoad.setVisible(false);
            actions.add(retryLoad);
        }
        header.add(actions, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);
        tabs.setName("tabs");
        tabs.addTab("Pantry", pantryPanel());
        tabs.addTab("Recipes", recipesPanel());
        tabs.addTab("Create recipe", createRecipePanel());
        importPanel = new RecipeImportPanel(controller, recipe ->
            perform(() ->
            {
                boolean added = controller.importRecipe(recipe);
                if (added)
                {
                    showRecipe(recipe);
                }
                feedback(added ? "Recipe imported into your collection."
                    : "This recipe is already saved. Your existing recipe was kept.",
                    !added);
            }), this::feedback);
        tabs.addTab("Import recipes", importPanel);
        brokePanel = new BrokeModePanel(controller);
        tabs.addTab("Broke Mode", brokePanel);
        tabs.addChangeListener(event ->
        {
            boolean pricing = tabs.getSelectedComponent() == brokePanel;
            controller.setBrokeModeEnabled(pricing);
            if (!pricing) { brokePanel.cancelSearch(); }
            else { brokePanel.refreshValidity(); }
        });
        add(tabs, BorderLayout.CENTER);
        status.setEditable(false);
        status.setBackground(new Color(233, 239, 229));
        status.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        add(status, BorderLayout.SOUTH);
        refreshPantry();
        refreshRecipes();
        feedback("Load demo data, add pantry ingredients, or create a recipe.",
            false);
    }


    /**
     * Opens the desktop application and loads its local save.
     * The pantrypal.dataFile system property overrides the default save at
     * user.home/.pantrypal/data.properties. Uses TheMealDB educational access.
     * @param args command-line arguments (none supported)
     * @throws IllegalArgumentException if arguments are supplied or null
     */
    public static void main(String[] args)
    {
        if (args == null || args.length != 0)
        {
            throw new IllegalArgumentException("No arguments are supported.");
        }
        String configured = System.getProperty("pantrypal.dataFile");
        Path savePath = configured == null
            ? Path.of(System.getProperty("user.home"), ".pantrypal",
                "data.properties") : Path.of(configured);
        DataStore store = new DataStore(savePath);
        PriceFinder pricing = PriceFinder.configuredClient();
        SwingUtilities.invokeLater(() ->
        {
            PantryPalController session = new PantryPalController(store,
                new IngredientMatcher(), RecipeImporter.educationalClient(), pricing);
            JFrame window = new JFrame("PantryPal");
            window.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
            PantryPalGUI view = new PantryPalGUI(session);
            window.setContentPane(view);
            view.storageStatus.setToolTipText(savePath.toAbsolutePath().toString());
            view.loadSavedData();
            window.setMinimumSize(new Dimension(920, 720));
            window.setSize(1100, 820);
            window.setLocationRelativeTo(null);
            window.addWindowListener(new WindowAdapter()
            {
                @Override
                public void windowClosing(WindowEvent event)
                {
                    view.closeWindow(window);
                }
            });
            window.setVisible(true);
        });
    }


    private void loadSavedData()
    {
        try
        {
            controller.loadData();
            loadFailed = false;
            retryLoad.setVisible(false);
            refreshPantry();
            refreshRecipes();
            feedback("Saved pantry and recipes loaded. Changes save automatically.",
                false);
        }
        catch (IOException exception)
        {
            loadFailed = true;
            retryLoad.setVisible(true);
            feedback("Could not load: " + exception.getMessage()
                + " The save file was left unchanged. Restore or move it, "
                + "then click Retry loading.", true);
        }
    }


    private void closeWindow(JFrame window)
    {
        brokePanel.cancelSearch();
        importPanel.cancelSearch();
        if (!controller.hasUnsavedChanges())
        {
            window.dispose();
            return;
        }
        int choice = JOptionPane.showOptionDialog(window,
            "Some changes could not be saved. Save before closing?",
            "Close PantryPal", JOptionPane.DEFAULT_OPTION,
            JOptionPane.WARNING_MESSAGE, null,
            new String[] { "Save and close", "Close without saving", "Cancel" },
            "Save and close");
        if (choice == 0)
        {
            perform(() ->
            {
                controller.saveData();
                window.dispose();
            });
        }
        else if (choice == 1)
        {
            window.dispose();
        }
    }


    private void loadDemoData()
        throws IOException
    {
        if (controller.getPantryIngredients().length != 0
            || controller.getSavedRecipes().length != 0)
        {
            feedback("Demo data requires an empty session. "
                + "Your existing pantry and recipes were kept.", true);
            return;
        }
        controller.addPantryIngredient("Rice", 1, Unit.KILOGRAM);
        controller.addPantryIngredient("Pasta", 500, Unit.GRAM);
        controller.addPantryIngredient("Eggs", 6, Unit.COUNT);
        controller.addPantryIngredient("Milk", 1, Unit.LITER);
        controller.addPantryIngredient("Cheese", 200, Unit.GRAM);
        controller.addPantryIngredient("Butter", 100, Unit.GRAM);
        controller.addPantryIngredient("Olive oil", 8, Unit.TABLESPOON);
        controller.addPantryIngredient("Salt", 10, Unit.TEASPOON);
        controller.addPantryIngredient("Black beans", 400, Unit.GRAM);
        controller.addPantryIngredient("Soy sauce", 100, Unit.MILLILITER);
        controller.addPantryIngredient("Broccoli", 300, Unit.GRAM);
        controller.addPantryIngredient("Rolled oats", 500, Unit.GRAM);

        controller.addCustomRecipe("Cheesy Pasta", new Ingredient[] {
            demoIngredient("Pasta", 200, Unit.GRAM),
            demoIngredient("Cheese", 80, Unit.GRAM),
            demoIngredient("Milk", 100, Unit.MILLILITER),
            demoIngredient("Butter", 20, Unit.GRAM) },
            new String[] { "Boil the pasta in water until tender and drain.",
                "Warm the butter and milk, then stir in the cheese.",
                "Toss the pasta with the sauce and serve." },
            5, 15, Effort.EASY);
        controller.addCustomRecipe("Scrambled Eggs", new Ingredient[] {
            demoIngredient("Eggs", 2, Unit.COUNT),
            demoIngredient("Milk", 30, Unit.MILLILITER),
            demoIngredient("Butter", 10, Unit.GRAM),
            demoIngredient("Salt", 0.25, Unit.TEASPOON) },
            new String[] { "Beat the eggs with milk and salt.",
                "Melt the butter in a pan over medium-low heat.",
                "Add the eggs and stir gently until set." },
            3, 5, Effort.EASY);
        controller.addCustomRecipe("Simple Rice", new Ingredient[] {
            demoIngredient("Rice", 200, Unit.GRAM),
            demoIngredient("Olive oil", 1, Unit.TABLESPOON),
            demoIngredient("Salt", 0.5, Unit.TEASPOON) },
            new String[] { "Rinse the rice and add it to a saucepan.",
                "Add water according to the rice package, oil, and salt.",
                "Simmer covered until tender, then rest and fluff." },
            5, 20, Effort.EASY);
        controller.addCustomRecipe("Tomato Pasta", new Ingredient[] {
            demoIngredient("Pasta", 200, Unit.GRAM),
            demoIngredient("Diced tomatoes", 400, Unit.GRAM),
            demoIngredient("Olive oil", 2, Unit.TABLESPOON),
            demoIngredient("Salt", 0.5, Unit.TEASPOON) },
            new String[] { "Simmer the diced tomatoes in olive oil.",
                "Boil the pasta until tender and drain.",
                "Combine the pasta and tomatoes, then season with salt." },
            10, 20, Effort.MEDIUM);
        controller.addCustomRecipe("Big Batch Pancakes", new Ingredient[] {
            demoIngredient("Flour", 1000, Unit.GRAM),
            demoIngredient("Milk", 2, Unit.LITER),
            demoIngredient("Eggs", 4, Unit.COUNT),
            demoIngredient("Butter", 40, Unit.GRAM) },
            new String[] { "Whisk the flour, milk, and eggs into a batter.",
                "Heat a pan and grease it with a little butter.",
                "Cook small portions of batter on both sides until set." },
            10, 30, Effort.MEDIUM);
        controller.addCustomRecipe("Egg Fried Rice", new Ingredient[] {
            demoIngredient("Rice", 250, Unit.GRAM),
            demoIngredient("Eggs", 2, Unit.COUNT),
            demoIngredient("Soy sauce", 15, Unit.MILLILITER),
            demoIngredient("Olive oil", 1, Unit.TABLESPOON) },
            new String[] { "Cook the rice according to the package instructions.",
                "Heat the oil in a pan and scramble the eggs.",
                "Add the rice and soy sauce, then stir-fry until hot." },
            5, 20, Effort.MEDIUM);
        controller.addCustomRecipe("Broccoli Bean Rice Bowl", new Ingredient[] {
            demoIngredient("Rice", 200, Unit.GRAM),
            demoIngredient("Broccoli", 150, Unit.GRAM),
            demoIngredient("Black beans", 100, Unit.GRAM),
            demoIngredient("Soy sauce", 1, Unit.TABLESPOON),
            demoIngredient("Olive oil", 1, Unit.TABLESPOON) },
            new String[] { "Cook the rice and steam the broccoli.",
                "Heat the black beans and broccoli in olive oil.",
                "Serve over rice and stir in the soy sauce." },
            5, 20, Effort.EASY);
        controller.addCustomRecipe("Creamy Oats", new Ingredient[] {
            demoIngredient("Rolled oats", 80, Unit.GRAM),
            demoIngredient("Milk", 200, Unit.MILLILITER),
            demoIngredient("Salt", 0.125, Unit.TEASPOON) },
            new String[] { "Combine the oats, milk, and salt in a saucepan.",
                "Simmer gently, stirring until the oats are tender.",
                "Add a little water if needed and serve warm." },
            2, 5, Effort.EASY);
        controller.addCustomRecipe("Chicken Rice Bowl", new Ingredient[] {
            demoIngredient("Chicken breast", 600, Unit.GRAM),
            demoIngredient("Rice", 300, Unit.GRAM),
            demoIngredient("Broccoli", 200, Unit.GRAM),
            demoIngredient("Soy sauce", 2, Unit.TABLESPOON),
            demoIngredient("Olive oil", 1, Unit.TABLESPOON) },
            new String[] { "Cook the rice and steam the broccoli.",
                "Cut the chicken into pieces and cook thoroughly in the oil.",
                "Stir in the soy sauce and serve with the rice and broccoli." },
            10, 20, Effort.MEDIUM);
        pantryQuery.setText("");
        recipeQuery.setText("");
        maxCookTime.setText("");
        effortFilter.setSelectedIndex(0);
        refreshPantry();
        refreshRecipes();
        feedback("Demo loaded: 12 ingredients and 9 recipes; 6 are ready to cook. "
            + "Try Meal Roulette, cook Cheesy Pasta, or estimate Chicken Rice Bowl. "
            + (controller.isPersistenceEnabled() ? "Demo data was saved."
                : "Demo data lasts only for this session."), false);
    }


    private static Ingredient demoIngredient(
        String name,
        double quantity,
        Unit unit)
    {
        return new Ingredient(UUID.randomUUID().toString(),
            name, quantity, unit);
    }


    private JPanel pantryPanel()
    {
        JPanel panel = section();
        JPanel search = row(new JLabel("Name contains"), pantryQuery,
            button("pantrySearch", "Search", () ->
            {
                refreshPantry();
                feedback(shownPantry.length == 0 ? "No ingredients found."
                    : shownPantry.length + " ingredient entries found.",
                    false);
            }),
            button("pantryShowAll", "Show all", () ->
            {
                pantryQuery.setText("");
                refreshPantry();
                feedback(shownPantry.length == 0 ? "Your pantry is empty."
                    : "Showing all pantry entries.", false);
            }));
        panel.add(search, BorderLayout.NORTH);
        panel.add(tableScroll(pantryTable), BorderLayout.CENTER);
        JPanel editor = form();
        editor.setBorder(BorderFactory.createTitledBorder(
            "Add an entry, or select a row to update it"));
        formRow(editor, 0, "Name", pantryName);
        formRow(editor, 1, "Quantity", pantryQuantity);
        formRow(editor, 2, "Unit", pantryUnit);
        formRow(editor, 3, "", row(
            button("pantryAdd", "Add new", () ->
            {
                controller.addPantryIngredient(pantryName.getText(),
                    quantity(pantryQuantity), unit(pantryUnit));
                pantryQuery.setText("");
                refreshPantry();
                refreshComparison();
                clearPantryForm();
                feedback("Ingredient added.", false);
            }),
            button("pantryUpdate", "Update selected", () ->
            {
                requirePantrySelection();
                boolean updated = controller.updatePantryIngredient(
                    selectedPantryId, pantryName.getText(),
                    quantity(pantryQuantity), unit(pantryUnit));
                refreshPantry();
                refreshComparison();
                feedback(updated ? "Ingredient updated."
                    : "Ingredient not found.", !updated);
            }),
            button("pantryRemove", "Remove selected", () ->
            {
                requirePantrySelection();
                boolean removed = controller.removePantryIngredient(
                    selectedPantryId);
                refreshPantry();
                refreshComparison();
                clearPantryForm();
                feedback(removed ? "Ingredient removed."
                    : "Ingredient not found.", !removed);
            }),
            button("pantryClear", "Clear form", () ->
            {
                pantryTable.clearSelection();
                clearPantryForm();
                feedback("Enter a new ingredient.", false);
            })));
        panel.add(editor, BorderLayout.SOUTH);
        pantryTable.getSelectionModel().addListSelectionListener(event ->
        {
            if (!refreshingPantry && !event.getValueIsAdjusting())
            {
                int index = selectedRow(pantryTable);
                selectedPantryId = index < 0
                    ? null : shownPantry[index].getId();
                if (index >= 0)
                {
                    Ingredient entry = shownPantry[index];
                    pantryName.setText(entry.getName());
                    pantryQuantity.setText(number(entry.getQuantity()));
                    pantryUnit.setSelectedItem(unitText(entry.getUnit()));
                }
            }
        });
        return panel;
    }


    private JPanel recipesPanel()
    {
        JPanel panel = section();
        panel.add(row(new JLabel("Name contains"), recipeQuery,
            new JLabel("Max cook minutes"), maxCookTime, effortFilter,
            button("recipeSearch", "Apply", () ->
            {
                refreshRecipes();
                feedback(shownRecipes.length == 0 ? "No recipes found."
                    : shownRecipes.length + " recipes found.", false);
            }),
            button("recipeShowAll", "Show all", () ->
            {
                recipeQuery.setText("");
                maxCookTime.setText("");
                effortFilter.setSelectedIndex(0);
                refreshRecipes();
                feedback(shownRecipes.length == 0 ? "No saved recipes yet."
                    : "Showing all saved recipes.", false);
            })), BorderLayout.NORTH);
        details.setEditable(false);
        JPanel information = section();
        information.setBorder(
            BorderFactory.createTitledBorder("Recipe details"));
        information.add(new JScrollPane(details), BorderLayout.NORTH);
        JPanel comparison = section();
        comparison.setBorder(BorderFactory.createEmptyBorder());
        comparison.add(comparisonSummary, BorderLayout.NORTH);
        comparison.add(tableScroll(comparisonTable), BorderLayout.CENTER);
        information.add(comparison, BorderLayout.CENTER);
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
            tableScroll(recipeTable), information);
        split.setResizeWeight(0.25);
        split.setDividerLocation(120);
        panel.add(split, BorderLayout.CENTER);
        panel.add(row(
            button("mealRoulette", "Meal Roulette", () ->
            {
                Recipe choice = controller.selectMealRouletteRecipe();
                if (choice == null)
                {
                    feedback("No cookable saved recipes. "
                        + "Add ingredients or create a recipe.", false);
                    return;
                }
                showRecipe(choice);
                feedback("Meal Roulette picked: " + choice.getName()
                    + ". Searched all saved recipes; pantry unchanged.",
                    false);
            }),
            button("recipeCompare", "Compare with pantry", () ->
            {
                requireRecipe();
                refreshComparison();
                feedback("Comparison updated using current pantry stock.",
                    false);
            }),
            button("recipeEstimate", "Estimate Walmart cost", () ->
            {
                brokePanel.showRecipe(requireRecipe());
                tabs.setSelectedComponent(brokePanel);
            }),
            button("recipeCook", "Cook & deduct ingredients", () ->
            {
                boolean cooked = controller.cookRecipe(requireRecipe());
                refreshPantry();
                refreshComparison();
                feedback(cooked ? "Cooked! Pantry quantities were updated."
                    : "Cannot cook: stock is missing or cannot be compared. "
                        + "Your pantry was not changed.", !cooked);
            }),
            button("recipeRemove", "Remove selected recipe", () ->
            {
                boolean removed = controller.removeSavedRecipe(
                    requireRecipe().getId());
                refreshRecipes();
                feedback(removed ? "Recipe removed."
                    : "Recipe not found.", !removed);
            })), BorderLayout.SOUTH);
        recipeTable.getSelectionModel().addListSelectionListener(event ->
        {
            if (!event.getValueIsAdjusting())
            {
                perform(() -> refreshComparison());
            }
        });
        return panel;
    }


    private JPanel createRecipePanel()
    {
        JPanel panel = section();
        JPanel metadata = form();
        formRow(metadata, 0, "Recipe name", recipeName);
        formRow(metadata, 1, "Prep minutes (blank = unknown)", prepTime);
        formRow(metadata, 2, "Cook minutes (blank = unknown)", cookTime);
        formRow(metadata, 3, "Effort", recipeEffort);
        panel.add(metadata, BorderLayout.NORTH);
        JPanel requirements = section();
        requirements.setBorder(BorderFactory.createTitledBorder(
            "Ingredients - add at least one"));
        requirements.add(row(new JLabel("Name"), requirementName,
            new JLabel("Quantity"), requirementQuantity, requirementUnit,
            button("requirementAdd", "Add ingredient", () ->
            {
                Ingredient requirement = new Ingredient(
                    UUID.randomUUID().toString(), requirementName.getText(),
                    quantity(requirementQuantity), unit(requirementUnit));
                draftRequirements.add(requirement);
                model(requirementTable).addRow(new Object[] {
                    requirement.getName(), number(requirement.getQuantity()),
                    unitText(requirement.getUnit()) });
                requirementName.setText("");
                requirementQuantity.setText("");
                feedback("Recipe ingredient added.", false);
            }),
            button("requirementRemove", "Remove selected", () ->
            {
                int index = selectedRow(requirementTable);
                if (index < 0)
                {
                    throw new IllegalArgumentException(
                        "Select a recipe ingredient to remove.");
                }
                draftRequirements.remove(index);
                model(requirementTable).removeRow(index);
                feedback("Recipe ingredient removed.", false);
            })), BorderLayout.NORTH);
        requirements.add(tableScroll(requirementTable),
            BorderLayout.CENTER);
        panel.add(requirements, BorderLayout.CENTER);
        JPanel steps = section();
        steps.setBorder(BorderFactory.createTitledBorder(
            "Instructions - one step per line"));
        steps.add(new JScrollPane(instructions), BorderLayout.CENTER);
        steps.add(row(button("recipeCreate", "Save recipe", () ->
        {
            String[] lines = instructions.getText().isBlank()
                ? new String[0] : instructions.getText().strip().split("\\R");
            Recipe recipe = controller.addCustomRecipe(recipeName.getText(),
                draftRequirements.toArray(new Ingredient[0]), lines,
                minutes(prepTime), minutes(cookTime),
                Effort.valueOf((String)recipeEffort.getSelectedItem()));
            resetRecipeForm();
            showRecipe(recipe);
            feedback("Recipe added to your collection.", false);
        })), BorderLayout.SOUTH);
        panel.add(steps, BorderLayout.SOUTH);
        return panel;
    }


    private void showRecipe(Recipe recipe)
    {
        recipeQuery.setText("");
        maxCookTime.setText("");
        effortFilter.setSelectedIndex(0);
        refreshRecipes();
        tabs.setSelectedIndex(1);
        for (int i = 0; i < shownRecipes.length; i++)
        {
            if (shownRecipes[i].getId().equals(recipe.getId()))
            {
                int viewRow = recipeTable.convertRowIndexToView(i);
                recipeTable.setRowSelectionInterval(viewRow, viewRow);
                recipeTable.scrollRectToVisible(
                    recipeTable.getCellRect(viewRow, 0, true));
                break;
            }
        }
    }


    private void refreshPantry()
    {
        refreshingPantry = true;
        try
        {
            pantryTable.clearSelection();
            pantryTable.getSelectionModel().setAnchorSelectionIndex(-1);
            pantryTable.getSelectionModel().setLeadSelectionIndex(-1);
            selectedPantryId = null;
            model(pantryTable).setRowCount(0);
            shownPantry = controller.searchPantryIngredients(
                pantryQuery.getText());
            for (Ingredient entry : shownPantry)
            {
                model(pantryTable).addRow(new Object[] { entry.getName(),
                    number(entry.getQuantity()), unitText(entry.getUnit()) });
            }
        }
        finally
        {
            refreshingPantry = false;
        }
    }


    private void refreshRecipes()
    {
        Integer maximum = minutes(maxCookTime);
        Effort effort = effortFilter.getSelectedIndex() == 0 ? null
            : Effort.valueOf((String)effortFilter.getSelectedItem());
        Recipe[] filtered = controller.filterSavedRecipes(maximum, effort);
        List<Recipe> visible = new ArrayList<Recipe>();
        String query = recipeQuery.getText().strip().toLowerCase(Locale.ROOT);
        for (Recipe recipe : filtered)
        {
            if (recipe.getName().toLowerCase(Locale.ROOT).contains(query))
            {
                visible.add(recipe);
            }
        }
        recipeTable.clearSelection();
        recipeTable.getSelectionModel().setAnchorSelectionIndex(-1);
        recipeTable.getSelectionModel().setLeadSelectionIndex(-1);
        model(recipeTable).setRowCount(0);
        shownRecipes = visible.toArray(new Recipe[0]);
        for (Recipe recipe : shownRecipes)
        {
            model(recipeTable).addRow(new Object[] { recipe.getName(),
                time(recipe.getCookTimeMinutes()), recipe.getEffort() });
        }
        refreshComparison();
    }


    private void refreshComparison()
    {
        model(comparisonTable).setRowCount(0);
        int index = selectedRow(recipeTable);
        if (index < 0)
        {
            details.setText("");
            comparisonSummary.setText("Select a recipe to see availability.");
            return;
        }
        Recipe recipe = shownRecipes[index];
        details.setText(recipe.getName() + "\nPrep: "
            + time(recipe.getPrepTimeMinutes()) + " min  |  Cook: "
            + time(recipe.getCookTimeMinutes()) + " min  |  Effort: "
            + recipe.getEffort() + "\n\n"
            + String.join("\n", recipe.getInstructions())
            + (recipe.getSourceUrl().isBlank() ? ""
                : "\n\nSource: " + recipe.getSourceUrl()));
        details.setCaretPosition(0);
        RecipeMatchResult result = controller.compareRecipeWithPantry(recipe);
        comparisonSummary.setText(result.canCook() ? "Ready to cook."
            : "Not ready - check shortages and unknown quantities below.");
        for (IngredientMatch match : result.getIngredientMatches())
        {
            Ingredient required = match.getRequiredIngredient();
            model(comparisonTable).addRow(new Object[] { required.getName(),
                number(required.getQuantity()),
                amount(match.getAvailableQuantity()),
                amount(match.getMissingQuantity()),
                unitText(required.getUnit()),
                !match.canCompareQuantities() ? "Incompatible units"
                    : match.isSufficient() ? "Ready" : "Missing" });
        }
    }


    private Recipe requireRecipe()
    {
        int index = selectedRow(recipeTable);
        if (index < 0)
        {
            throw new IllegalArgumentException(shownRecipes.length == 0
                ? "No recipes found. Create a recipe first."
                : "Select a recipe first.");
        }
        return shownRecipes[index];
    }


    private void requirePantrySelection()
    {
        if (selectedPantryId == null)
        {
            throw new IllegalArgumentException(
                controller.getPantryIngredients().length == 0
                    ? "Your pantry is empty."
                    : "Select a pantry ingredient first.");
        }
    }


    private void clearPantryForm()
    {
        selectedPantryId = null;
        pantryName.setText("");
        pantryQuantity.setText("");
    }


    private void resetRecipeForm()
    {
        recipeName.setText("");
        prepTime.setText("");
        cookTime.setText("");
        instructions.setText("");
        requirementName.setText("");
        requirementQuantity.setText("");
        recipeEffort.setSelectedIndex(0);
        draftRequirements.clear();
        model(requirementTable).setRowCount(0);
    }


    private void feedback(String message, boolean error)
    {
        importPanel.refreshSavedCount();
        storageStatus.setText(!controller.isPersistenceEnabled()
            ? "Session-only mode: all data is lost when you close."
            : loadFailed ? "Load failed: changes are blocked to protect your save."
            : controller.hasUnsavedChanges()
                ? "Unsaved changes: click Save now to retry."
                : "Autosave is on. Pantry and recipes are saved locally.");
        status.setForeground(error ? new Color(151, 37, 30)
            : new Color(35, 83, 61));
        status.setText((error ? "Please try again: " : "") + message);
    }


    private void perform(Action action)
    {
        try
        {
            action.run();
        }
        catch (NumberFormatException exception)
        {
            feedback("Enter a valid number.", true);
        }
        catch (IllegalArgumentException | IllegalStateException exception)
        {
            feedback(exception.getMessage(), true);
        }
        catch (IOException exception)
        {
            // Mutation succeeded in memory. Show the current collections even
            // if an unrelated browse filter contains invalid numeric text.
            pantryQuery.setText("");
            recipeQuery.setText("");
            maxCookTime.setText("");
            effortFilter.setSelectedIndex(0);
            refreshPantry();
            refreshRecipes();
            feedback("Could not save: " + exception.getMessage()
                + " Your changes remain in this session. Click Save now "
                + "to retry saving; do not repeat the original action.", true);
        }
    }


    private JButton button(String name, String title, Action action)
    {
        JButton button = new JButton(title);
        button.setName(name);
        button.setFocusPainted(false);
        button.setMargin(new Insets(7, 12, 7, 12));
        button.addActionListener(event -> perform(action));
        return button;
    }


    private static double quantity(JTextField input)
    {
        return Double.parseDouble(input.getText().strip());
    }


    private static Integer minutes(JTextField input)
    {
        if (input.getText().isBlank())
        {
            return null;
        }
        try
        {
            int value = Integer.parseInt(input.getText().strip());
            if (value < 0)
            {
                throw new NumberFormatException();
            }
            return value;
        }
        catch (NumberFormatException exception)
        {
            throw new IllegalArgumentException(
                "Enter whole minutes from 0 to 2,147,483,647, "
                    + "or leave blank for unknown.");
        }
    }


    private static String number(double value)
    {
        return Double.toString(value);
    }


    private static String amount(Double value)
    {
        return value == null ? "Unknown" : number(value);
    }


    private static String time(Integer value)
    {
        return value == null ? "Unknown" : value.toString();
    }


    private static String unitText(Unit value)
    {
        return value.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }


    private static Unit unit(JComboBox<String> input)
    {
        return Unit.valueOf(((String)input.getSelectedItem())
            .toUpperCase(Locale.ROOT).replace(' ', '_'));
    }


    private static JTextField field(String name)
    {
        JTextField field = new JTextField(10);
        field.setName(name);
        return field;
    }


    private static JTextArea textArea(String name, int rows)
    {
        JTextArea area = new JTextArea(rows, 30);
        area.setName(name);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        return area;
    }


    private static JComboBox<String> units(String name)
    {
        String[] labels = new String[Unit.values().length];
        for (int i = 0; i < labels.length; i++)
        {
            labels[i] = unitText(Unit.values()[i]);
        }
        return choices(name, labels);
    }


    private static JComboBox<String> choices(String name, String... values)
    {
        JComboBox<String> combo = new JComboBox<String>(values);
        combo.setName(name);
        return combo;
    }


    private static JTable table(String name, String... columns)
    {
        DefaultTableModel data = new DefaultTableModel(columns, 0)
        {
            @Override
            public boolean isCellEditable(int row, int column)
            {
                return false;
            }
        };
        JTable table = new JTable(data);
        table.setName(name);
        table.setRowHeight(27);
        table.setAutoCreateRowSorter(true);
        table.setFillsViewportHeight(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setSelectionBackground(new Color(213, 232, 216));
        table.setSelectionForeground(Color.BLACK);
        table.setShowGrid(false);
        table.getTableHeader().setReorderingAllowed(false);
        return table;
    }


    private static DefaultTableModel model(JTable table)
    {
        return (DefaultTableModel)table.getModel();
    }


    private static JScrollPane tableScroll(JTable table)
    {
        JScrollPane scroll = new JScrollPane(table);
        scroll.setColumnHeaderView(table.getTableHeader());
        return scroll;
    }


    private static int selectedRow(JTable table)
    {
        int viewRow = table.getSelectedRow();
        return viewRow < 0 ? -1 : table.convertRowIndexToModel(viewRow);
    }


    private static JPanel section()
    {
        JPanel panel = new JPanel(new BorderLayout(8, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        return panel;
    }


    private static JPanel row(JComponent... components)
    {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        for (JComponent component : components)
        {
            panel.add(component);
        }
        return panel;
    }


    private static JPanel form()
    {
        return new JPanel(new GridBagLayout());
    }


    private static void formRow(
        JPanel panel,
        int index,
        String label,
        JComponent input)
    {
        GridBagConstraints layout = new GridBagConstraints();
        layout.gridx = 0;
        layout.gridy = index;
        layout.anchor = GridBagConstraints.WEST;
        layout.insets = new Insets(4, 8, 4, 8);
        JLabel caption = new JLabel(label);
        caption.setLabelFor(input);
        panel.add(caption, layout);
        layout.gridx = 1;
        layout.weightx = 1;
        layout.fill = GridBagConstraints.HORIZONTAL;
        panel.add(input, layout);
    }


    /** An application operation that can report a future persistence error. */
    @FunctionalInterface
    private interface Action
    {
        void run() throws IOException;
    }
}
