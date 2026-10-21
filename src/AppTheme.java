import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.math.BigDecimal;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JComboBox;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.border.AbstractBorder;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.BasicTabbedPaneUI;
import javax.swing.table.DefaultTableCellRenderer;

/** Shared Swing design, readable typography and responsive layout primitives. */
public final class AppTheme
{
    public static final Color BACKGROUND = new Color(246, 247, 242);
    public static final Color INK = new Color(31, 53, 42);
    public static final Color GREEN = new Color(39, 90, 62);
    public static final Color MUTED = new Color(91, 111, 97);
    public static final Color LINE = new Color(216, 226, 214);
    public static final Color TINT = new Color(233, 241, 228);
    public static final Color DANGER = new Color(153, 49, 43);

    private AppTheme() { }

    /** Sets defaults for popups and controls created after startup. */
    public static void install()
    {
        Font font = new Font("Segoe UI", Font.PLAIN, 18);
        for (String key : new String[] { "Label", "Button", "ComboBox", "TextField",
            "TextArea", "Table", "TableHeader", "List", "OptionPane", "ToolTip" })
        { UIManager.put(key + ".font", font); }
        UIManager.put("Panel.background", BACKGROUND);
        UIManager.put("Label.foreground", INK);
        UIManager.put("TextField.background", Color.WHITE);
        UIManager.put("TextArea.background", Color.WHITE);
        UIManager.put("TextField.selectionBackground", TINT);
        UIManager.put("TextArea.selectionBackground", TINT);
        UIManager.put("ComboBox.background", Color.WHITE);
        UIManager.put("ComboBox.selectionBackground", TINT);
        UIManager.put("ComboBox.selectionForeground", INK);
        UIManager.put("ScrollBar.width", 16);
        UIManager.put("OptionPane.messageFont", font);
        UIManager.put("OptionPane.buttonFont", font);
    }

    /** A type role remains consistent when the user changes text size. */
    public static <T extends JComponent> T type(T component, int size, boolean bold)
    {
        component.putClientProperty("theme.size", size);
        component.putClientProperty("theme.bold", bold);
        component.setFont(new Font("Segoe UI", bold ? Font.BOLD : Font.PLAIN, size));
        return component;
    }

    public static JLabel title(String text)
    { return type(new JLabel(text), 23, true); }

    public static JLabel wrappingLabel(String text)
    { return new WrappingLabel(text); }

    public static void height(JComponent component, int base)
    {
        component.putClientProperty("theme.contentHeight", base);
        component.setPreferredSize(new Dimension(0, base));
    }

    public static JLabel muted(String text)
    {
        JLabel label = type(new JLabel(text), 17, false);
        label.setForeground(MUTED);
        return label;
    }

    /** Multiline help remains readable without forcing the window wider. */
    public static JTextArea note(String text)
    {
        JTextArea area = type(new JTextArea(text, 2, 0), 17, false);
        area.setEditable(false);area.setLineWrap(true);area.setWrapStyleWord(true);
        area.setOpaque(false);area.setForeground(MUTED);
        return area;
    }

    /** Form pages track viewport width and scroll vertically when text is enlarged. */
    public static JPanel page()
    { return new PagePanel(); }

    public static JScrollPane pageScroll(JComponent content)
    {
        JScrollPane pane = new JScrollPane(content);
        pane.putClientProperty("theme.pageScroll", true);
        pane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        pane.setBorder(BorderFactory.createEmptyBorder());
        pane.getViewport().setBackground(BACKGROUND);
        return pane;
    }

    /** Short windows scroll a complete workspace instead of crushing its controls. */
    public static JPanel screen(JPanel content, int narrowExtra)
    {
        JPanel page = new PagePanel()
        {
            @Override public Dimension getPreferredSize()
            {
                double scale = getFont() == null ? 1.0 : getFont().getSize() / 18.0;
                return new Dimension(0, (int)((getWidth() >= 1050 ? 600 : 600+narrowExtra)*scale));
            }
        };
        page.add(content, java.awt.BorderLayout.CENTER);
        JPanel outer = new JPanel(new java.awt.BorderLayout());
        outer.add(pageScroll(page), java.awt.BorderLayout.CENTER);
        return outer;
    }

    public static JPanel formPage(JComponent... cards)
    {
        JPanel page = page();
        page.setLayout(new java.awt.GridBagLayout());
        java.awt.GridBagConstraints constraints = new java.awt.GridBagConstraints();
        constraints.gridx=0;constraints.weightx=1;constraints.fill=java.awt.GridBagConstraints.HORIZONTAL;
        constraints.insets=new Insets(0, 0, 18, 0);
        for (int i=0; i<cards.length; i++)
        { constraints.gridy=i;page.add(cards[i], constraints); }
        constraints.gridy=cards.length;constraints.weighty=1;
        page.add(new JPanel(), constraints);
        return page;
    }

    /** White card with a section heading and padded content. */
    public static JPanel card(String title, JComponent content)
    {
        JPanel card = new CardPanel();
        card.setLayout(new java.awt.BorderLayout(14, 16));
        card.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        if (title != null && !title.isBlank())
        { card.add(title(title), java.awt.BorderLayout.NORTH); }
        card.add(content, java.awt.BorderLayout.CENTER);
        card.setMinimumSize(new Dimension(0, 0));
        return card;
    }

    /** Toolbar rows wrap and report their actual height to BorderLayout. */
    public static JPanel flow(JComponent... components)
    {
        JPanel panel = new JPanel(new WrapLayout());
        panel.setOpaque(false);
        panel.addComponentListener(new java.awt.event.ComponentAdapter()
        {
            @Override public void componentResized(java.awt.event.ComponentEvent event)
            { panel.revalidate(); }
        });
        for (JComponent component : components) { panel.add(component); }
        return panel;
    }

    /** The backing tab selection remains available to existing app workflows. */
    public static BasicTabbedPaneUI hiddenTabs()
    {
        return new BasicTabbedPaneUI()
        {
            @Override protected void installDefaults()
            {
                super.installDefaults();
                contentBorderInsets = new Insets(0, 0, 0, 0);
                tabAreaInsets = new Insets(0, 0, 0, 0);
            }
            @Override protected int calculateTabAreaHeight(int placement, int runs, int height)
            { return 0; }
            @Override protected int calculateTabAreaWidth(int placement, int runs, int width)
            { return 0; }
            @Override protected void paintTabArea(Graphics g, int placement, int selected) { }
            @Override protected void paintContentBorder(Graphics g, int placement, int selected) { }
        };
    }

    /** Reapplies sizing to the entire view, including table headers and editors. */
    public static void apply(Component component, double scale)
    {
        if (component instanceof JComponent)
        {
            JComponent control = (JComponent)component;
            Object role = control.getClientProperty("theme.size");
            int size = role instanceof Integer ? (Integer)role : 18;
            boolean bold = Boolean.TRUE.equals(control.getClientProperty("theme.bold"));
            control.setFont(new Font("Segoe UI", bold ? Font.BOLD : Font.PLAIN,
                (int)Math.round(size * scale)));
            Object contentHeight=control.getClientProperty("theme.contentHeight");
            if (contentHeight instanceof Integer)
            { control.setPreferredSize(new Dimension(0, (int)Math.round((Integer)contentHeight*scale))); }
            if (control instanceof JLabel)
            {
                control.putClientProperty("html.disable", Boolean.TRUE);
            }
            if (control instanceof JButton)
            {
                JButton button = (JButton)control;
                button.setUI(new ButtonUI());
                button.setOpaque(false);
                button.setContentAreaFilled(false);
                button.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));
                button.setFocusPainted(true);
                button.setRolloverEnabled(true);
                button.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
            }
            else if (control instanceof JTextField)
            {
                control.setForeground(INK);
                control.setBackground(Color.WHITE);
                control.setBorder(new FieldBorder());
            }
            else if (control instanceof JComboBox<?>)
            {
                JComboBox<?> combo = (JComboBox<?>)control;
                if (!Boolean.TRUE.equals(combo.getClientProperty("theme.combo")))
                {
                    combo.setUI(new BasicComboBoxUI()
                    {
                        @Override protected JButton createArrowButton()
                        {
                            JButton arrow = new JButton()
                            {
                                @Override protected void paintComponent(Graphics graphics)
                                {
                                    Graphics2D g = (Graphics2D)graphics.create();
                                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                                    int x=getWidth()/2, y=getHeight()/2;
                                    g.setColor(isEnabled() ? GREEN : MUTED);
                                    g.fillPolygon(new int[] { x-5, x+5, x }, new int[] { y-2, y-2, y+4 }, 3);
                                    g.dispose();
                                }
                            };
                            arrow.setUI(new ButtonUI());
                            arrow.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 5));
                            arrow.setContentAreaFilled(false);
                            arrow.setFocusable(false);
                            return arrow;
                        }
                    });
                    combo.putClientProperty("theme.combo", true);
                }
                combo.setBackground(Color.WHITE);
                combo.setForeground(INK);
                combo.setBorder(new FieldBorder());
            }
            else if (control instanceof JTable)
            {
                JTable table = (JTable)control;
                table.setRowHeight((int)Math.round(48 * scale));
                table.setIntercellSpacing(new Dimension(0, 0));
                table.setShowGrid(false);
                table.setSelectionBackground(TINT);
                table.setSelectionForeground(INK);
                table.setFillsViewportHeight(true);
                table.setBackground(Color.WHITE);
                table.setForeground(INK);
                table.setDefaultRenderer(Object.class, new CellRenderer());
                table.getTableHeader().setDefaultRenderer(new HeaderRenderer());
                table.getTableHeader().setFont(control.getFont().deriveFont(Font.BOLD));
                table.getTableHeader().setPreferredSize(new Dimension(0, (int)(44 * scale)));
                table.getTableHeader().setReorderingAllowed(false);
                java.awt.FontMetrics metrics=table.getFontMetrics(table.getFont().deriveFont(Font.BOLD));
                for (int column=0; column<table.getColumnCount(); column++)
                {
                    javax.swing.table.TableColumn col=table.getColumnModel().getColumn(column);
                    int min=metrics.stringWidth(table.getColumnName(column))+28;
                    col.setMinWidth(min);
                    col.setPreferredWidth(Math.max(col.getPreferredWidth(),min));
                }
            }
            else if (control instanceof JTextArea)
            {
                JTextArea area = (JTextArea)control;
                area.setLineWrap(true);
                area.setWrapStyleWord(true);
                area.setMargin(new Insets(10, 12, 10, 12));
            }
            else if (control instanceof JScrollPane)
            {
                JScrollPane scroll = (JScrollPane)control;
                boolean page = Boolean.TRUE.equals(scroll.getClientProperty("theme.pageScroll"));
                scroll.setBorder(page ? BorderFactory.createEmptyBorder() : BorderFactory.createLineBorder(LINE));
                scroll.getViewport().setBackground(page ? BACKGROUND : Color.WHITE);
                Object surface=scroll.getClientProperty("theme.scrollSurface");
                if (surface instanceof Color) { scroll.getViewport().setBackground((Color)surface); }
                scroll.getVerticalScrollBar().setUnitIncrement(24);
                scroll.getHorizontalScrollBar().setUnitIncrement(24);
            }
            else if (control instanceof javax.swing.JScrollBar)
            {
                javax.swing.JScrollBar bar = (javax.swing.JScrollBar)control;
                bar.setUI(new javax.swing.plaf.basic.BasicScrollBarUI()
                {
                    @Override protected void configureScrollBarColors()
                    { trackColor=new Color(244,247,239);thumbColor=new Color(175,193,169); }
                    private JButton noArrow()
                    {
                        return new JButton()
                        {
                            @Override public Dimension getPreferredSize() { return new Dimension(0,0); }
                            @Override public Dimension getMinimumSize() { return new Dimension(0,0); }
                            @Override public Dimension getMaximumSize() { return new Dimension(0,0); }
                        };
                    }
                    @Override protected JButton createDecreaseButton(int direction) { return noArrow(); }
                    @Override protected JButton createIncreaseButton(int direction) { return noArrow(); }
                    @Override protected void paintThumb(Graphics graphics, JComponent c, java.awt.Rectangle bounds)
                    {
                        Graphics2D g=(Graphics2D)graphics.create();
                        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                        g.setColor(isDragging ? GREEN : thumbColor);
                        g.fillRoundRect(bounds.x+3,bounds.y+3,Math.max(0,bounds.width-6),
                            Math.max(0,bounds.height-6),9,9);g.dispose();
                    }
                });
            }
            else if (control instanceof JSplitPane)
            {
                JSplitPane split = (JSplitPane)control;
                split.setBorder(BorderFactory.createEmptyBorder());
                split.setDividerSize(12);
                split.setContinuousLayout(true);
                split.setBackground(BACKGROUND);
            }
            else if (control instanceof JPanel && !(control instanceof CardPanel)
                && control.getClientProperty("theme.surface") == null)
            { control.setOpaque(false); }
        }
        if (component instanceof Container)
        {
            for (Component child : ((Container)component).getComponents())
            { apply(child, scale); }
        }
    }

    /** List/detail becomes stacked in narrower windows; divider remains draggable. */
    public static JSplitPane adaptiveSplit(JComponent first, JComponent second, double share)
    {
        return new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, first, second)
        {
            private int lastOrientation = -1;
            @Override public void doLayout()
            {
                int desired = getWidth() >= 1050 ? HORIZONTAL_SPLIT : VERTICAL_SPLIT;
                if (lastOrientation != desired)
                {
                    lastOrientation = desired;
                    setOrientation(desired);
                    setResizeWeight(share);
                    int extent = desired == HORIZONTAL_SPLIT ? getWidth() : getHeight();
                    setDividerLocation((int)(extent * share));
                }
                super.doLayout();
            }
        };
    }

    private static final class CardPanel extends JPanel
    {
        CardPanel() { setOpaque(false); }
        @Override protected void paintComponent(Graphics graphics)
        {
            Graphics2D g = (Graphics2D)graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(Color.WHITE); g.fillRoundRect(0, 0, getWidth()-1, getHeight()-1, 22, 22);
            g.setColor(LINE); g.drawRoundRect(0, 0, getWidth()-1, getHeight()-1, 22, 22);
            g.dispose(); super.paintComponent(graphics);
        }
    }

    private static class PagePanel extends JPanel implements javax.swing.Scrollable
    {
        PagePanel() { super(new java.awt.BorderLayout(14, 18));setOpaque(false); }
        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(java.awt.Rectangle r, int orientation, int direction)
        { return 24; }
        @Override public int getScrollableBlockIncrement(java.awt.Rectangle r, int orientation, int direction)
        { return Math.max(24, r.height-40); }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight()
        { return getParent() != null && getPreferredSize().height < getParent().getHeight(); }
    }

    /** JLabel API is retained while provider messages wrap to the available width. */
    private static final class WrappingLabel extends JLabel
    {
        WrappingLabel(String text) { super(text); }
        private java.util.List<java.awt.font.TextLayout> lines(int width)
        {
            java.util.List<java.awt.font.TextLayout> result = new java.util.ArrayList<java.awt.font.TextLayout>();
            if (getText() == null || getText().isEmpty()) { return result; }
            java.text.AttributedString text = new java.text.AttributedString(getText());
            text.addAttribute(java.awt.font.TextAttribute.FONT,
                getFont() == null ? new Font("Segoe UI", Font.PLAIN, 18) : getFont());
            java.awt.font.LineBreakMeasurer measure = new java.awt.font.LineBreakMeasurer(
                text.getIterator(), new java.awt.font.FontRenderContext(null, true, true));
            while (measure.getPosition() < getText().length())
            { result.add(measure.nextLayout(Math.max(20, width))); }
            return result;
        }
        @Override public Dimension getPreferredSize()
        {
            Insets in=getInsets();
            int width = getWidth()>0 ? getWidth() : getParent()!=null && getParent().getWidth()>0
                ? getParent().getWidth() : 600;
            float height=0;
            for (java.awt.font.TextLayout line : lines(width-in.left-in.right))
            { height+=line.getAscent()+line.getDescent()+line.getLeading()+3; }
            return new Dimension(240, (int)Math.ceil(height)+in.top+in.bottom);
        }
        @Override public Dimension getMinimumSize() { return new Dimension(0, getPreferredSize().height); }
        @Override protected void paintComponent(Graphics graphics)
        {
            Graphics2D g=(Graphics2D)graphics.create();
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            Insets in=getInsets();float y=in.top;g.setColor(getForeground());
            for (java.awt.font.TextLayout line : lines(getWidth()-in.left-in.right))
            { y+=line.getAscent();line.draw(g,in.left,y);y+=line.getDescent()+line.getLeading()+3; }
            g.dispose();
        }
    }

    private static final class FieldBorder extends AbstractBorder
    {
        @Override public Insets getBorderInsets(Component c) { return new Insets(10, 12, 10, 12); }
        @Override public Insets getBorderInsets(Component c, Insets insets)
        { insets.set(10, 12, 10, 12); return insets; }
        @Override public void paintBorder(Component c, Graphics graphics, int x, int y, int w, int h)
        {
            Graphics2D g = (Graphics2D)graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(c.hasFocus() ? GREEN : LINE);
            g.drawRoundRect(x, y, w-1, h-1, 10, 10); g.dispose();
        }
    }

    private static final class ButtonUI extends BasicButtonUI
    {
        @Override public void paint(Graphics graphics, JComponent component)
        {
            JButton b = (JButton)component;
            String kind = String.valueOf(b.getClientProperty("theme.button"));
            boolean nav = kind.equals("nav");
            boolean active = Boolean.TRUE.equals(b.getClientProperty("theme.selected"));
            Color fill = kind.equals("primary") ? GREEN : kind.equals("danger")
                ? new Color(250, 234, 229) : nav ? (active ? new Color(69, 115, 83) : GREEN) : TINT;
            Color ink = kind.equals("primary") || nav ? Color.WHITE
                : kind.equals("danger") ? DANGER : GREEN;
            if (!b.isEnabled()) { fill = new Color(237, 239, 233); ink = new Color(130, 140, 129); }
            else if (b.getModel().isPressed()) { fill = fill.darker(); }
            else if (b.getModel().isRollover()) { fill = nav ? new Color(61, 106, 75) : fill.darker(); }
            b.setForeground(ink);
            Graphics2D g = (Graphics2D)graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(fill);g.fillRoundRect(0, 0, b.getWidth(), b.getHeight(), 12, 12);
            if (b.hasFocus())
            { g.setColor(new Color(133, 167, 109));g.drawRoundRect(2, 2, b.getWidth()-5, b.getHeight()-5, 10, 10); }
            g.dispose();super.paint(graphics, component);
        }
    }

    private static final class HeaderRenderer extends DefaultTableCellRenderer
    {
        @Override public Component getTableCellRendererComponent(JTable table, Object value,
            boolean selected, boolean focus, int row, int column)
        {
            super.getTableCellRendererComponent(table, value, false, false, row, column);
            putClientProperty("html.disable", Boolean.TRUE);
            setFont(table.getFont().deriveFont(Font.BOLD));setForeground(MUTED);setBackground(TINT);
            setToolTipText(String.valueOf(value));
            setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));return this;
        }
    }

    /** Formatting is display-only; quantities and sortable model values are preserved. */
    private static final class CellRenderer extends DefaultTableCellRenderer
    {
        @Override public Component getTableCellRendererComponent(JTable table, Object value,
            boolean selected, boolean focus, int row, int column)
        {
            super.getTableCellRendererComponent(table, value, selected, focus, row, column);
            putClientProperty("html.disable", Boolean.TRUE);
            setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0,
                new Color(237, 242, 233)), BorderFactory.createEmptyBorder(5, 12, 5, 12)));
            setBackground(selected ? TINT : row % 2 == 0 ? Color.WHITE : new Color(250, 251, 248));
            setForeground(INK);setHorizontalAlignment(SwingConstants.LEFT);
            String label = value == null ? "" : value.toString();
            String columnName = table.getColumnName(column);
            if (columnName.equals("Unit")) { label = shortUnit(label); }
            else if (columnName.equals("Effort"))
            { label = label.equals("UNKNOWN") ? "Not specified" : label.isEmpty() ? label
                : label.substring(0, 1) + label.substring(1).toLowerCase(java.util.Locale.ROOT); }
            else if (label.matches("-?[0-9]+\\.[0-9]+"))
            { label = new BigDecimal(label).stripTrailingZeros().toPlainString(); }
            if (label.equals("Ready") || label.equals("Ready to cook"))
            { setForeground(GREEN);setFont(table.getFont().deriveFont(Font.BOLD)); }
            if (label.equals("Missing") || label.equals("Incompatible units")) { setForeground(DANGER); }
            setText(label);setToolTipText(value == null ? null : "Details: " + value);return this;
        }
    }

    public static String shortUnit(String label)
    {
        switch (label.toLowerCase(java.util.Locale.ROOT))
        {
            case "gram": return "g";
            case "kilogram": return "kg";
            case "milliliter": return "mL";
            case "liter": return "L";
            case "tablespoon": return "tbsp";
            case "teaspoon": return "tsp";
            case "count": return "count";
            case "fluid ounce": return "fl oz";
            case "ounce": return "oz";
            case "pound": return "lb";
            default: return label;
        }
    }

    private static final class WrapLayout extends FlowLayout
    {
        WrapLayout() { super(FlowLayout.LEFT, 10, 8); }
        @Override public Dimension preferredLayoutSize(Container target) { return measure(target); }
        @Override public Dimension minimumLayoutSize(Container target)
        { Dimension size = measure(target);size.width = 0;return size; }
        private Dimension measure(Container target)
        {
            synchronized (target.getTreeLock())
            {
                int available = target.getWidth();
                if (available <= 0) { available = 1000; }
                Insets in = target.getInsets();
                int limit = Math.max(1, available-in.left-in.right-2*getHgap());
                int rowWidth=0, rowHeight=0, width=0, height=getVgap();
                for (Component child : target.getComponents())
                {
                    if (!child.isVisible()) { continue; }
                    Dimension size=child.getPreferredSize();
                    if (rowWidth>0 && rowWidth+getHgap()+size.width>limit)
                    { width=Math.max(width,rowWidth);height+=rowHeight+getVgap();rowWidth=0;rowHeight=0; }
                    rowWidth+=(rowWidth==0?0:getHgap())+size.width;
                    rowHeight=Math.max(rowHeight,size.height);
                }
                width=Math.max(width,rowWidth);height+=rowHeight+getVgap();
                return new Dimension(width+in.left+in.right+2*getHgap(),height+in.top+in.bottom);
            }
        }
    }
}
