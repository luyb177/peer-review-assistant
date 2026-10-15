package edu.course.peerreview.ui;

import com.formdev.flatlaf.FlatLightLaf;
import edu.course.peerreview.service.BusinessException;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

public final class Ui {
    public static final Color INK = new Color(0x203A35);
    public static final Color ACCENT = new Color(0x236A56);
    public static final Color MUTED = new Color(0x596B65);
    public static final Color PAPER = new Color(0xF4F7F5);
    private Ui() { }
    public static void theme() {
        FlatLightLaf.setup();
        UIManager.put("defaultFont", new Font("Microsoft YaHei UI", Font.PLAIN, 13));
        UIManager.put("Component.arc", 10);
        UIManager.put("Button.arc", 10);
        UIManager.put("TextComponent.arc", 8);
        UIManager.put("Component.focusColor", ACCENT);
        UIManager.put("Table.selectionBackground", new Color(0xDCEEE6));
        UIManager.put("Table.selectionForeground", INK);
        UIManager.put("Table.rowHeight", 36);
        UIManager.put("TabbedPane.selectedBackground", Color.WHITE);
    }
    public static JButton button(String text, Runnable action) {
        JButton b = new JButton(text);
        b.addActionListener(e -> safely(action));
        return b;
    }
    public static JButton primary(String text, Runnable action) {
        JButton b = button(text, action);
        b.setBackground(ACCENT); b.setForeground(Color.WHITE);
        return b;
    }
    public static JLabel title(String text, int size) {
        JLabel label = new JLabel(text); label.setFont(label.getFont().deriveFont(Font.BOLD, size)); label.setForeground(INK); return label;
    }
    public static JTextArea area(String text, boolean editable) {
        JTextArea area = new JTextArea(text, 8, 40); area.setLineWrap(true); area.setWrapStyleWord(true); area.setEditable(editable);
        area.setMargin(new Insets(10, 12, 10, 12)); return area;
    }
    public static JPanel column() {
        JPanel p = new JPanel() {
            @Override protected void addImpl(Component component, Object constraints, int index) {
                if (component instanceof JComponent child) child.setAlignmentX(Component.LEFT_ALIGNMENT);
                if (component instanceof JLabel label) label.setMaximumSize(new Dimension(Integer.MAX_VALUE, label.getPreferredSize().height + 2));
                super.addImpl(component, constraints, index);
            }
        };
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS)); return p;
    }
    public static JPanel toolbar(JComponent... controls) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        p.setOpaque(false); p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
        for (JComponent c : controls) p.add(c); return p;
    }
    public static JTable table(String... columns) {
        JTable t = new JTable(model(columns)); t.setSelectionMode(ListSelectionModel.SINGLE_SELECTION); t.setAutoCreateRowSorter(true);
        t.getTableHeader().setReorderingAllowed(false); t.setFillsViewportHeight(true); t.setRowHeight(36); return t;
    }
    public static DefaultTableModel model(String... columns) {
        return new DefaultTableModel(columns, 0) { @Override public boolean isCellEditable(int r, int c) { return false; } };
    }
    public static void rows(JTable table, java.util.List<Object[]> rows) {
        DefaultTableModel model = (DefaultTableModel) table.getModel(); model.setRowCount(0); rows.forEach(model::addRow);
    }
    public static int selected(JTable table) {
        if (table.getSelectedRow() < 0) throw new BusinessException("请先选择一条记录。");
        return table.convertRowIndexToModel(table.getSelectedRow());
    }
    public static void field(JPanel form, String label, JComponent input) {
        JPanel row = new JPanel(new BorderLayout(14, 0)); row.setOpaque(false); row.setBorder(BorderFactory.createEmptyBorder(7, 0, 7, 0));
        JLabel name = new JLabel(label); name.setPreferredSize(new Dimension(130, 30)); row.add(name, BorderLayout.WEST); row.add(input);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, input instanceof JScrollPane ? 180 : 52)); form.add(row);
    }
    public static boolean confirm(Component parent, String text) {
        return JOptionPane.showConfirmDialog(parent, text, "请确认", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION;
    }
    public static void info(Component parent, String text) { JOptionPane.showMessageDialog(parent, text, "课堂同伴互评", JOptionPane.INFORMATION_MESSAGE); }
    public static void error(Component parent, Throwable error) {
        Throwable cause = error;
        while ((cause instanceof ExecutionException || cause instanceof CompletionException) && cause.getCause() != null) cause = cause.getCause();
        String text = cause instanceof BusinessException ? cause.getMessage() : "操作未完成。请检查输入或配置后重试（" + cause.getClass().getSimpleName() + "）。";
        JOptionPane.showMessageDialog(parent, text, "操作提示", JOptionPane.WARNING_MESSAGE);
    }
    public static void safely(Runnable action) { try { action.run(); } catch (RuntimeException e) { error(null, e); } }
    public static <T> void work(Window owner, Callable<T> operation, Consumer<T> completed) {
        work(owner, operation, completed, error -> error(owner, error));
    }
    public static <T> void work(Window owner, Callable<T> operation, Consumer<T> completed, Consumer<Throwable> failed) {
        owner.setEnabled(false); owner.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        new SwingWorker<T, Void>() {
            @Override protected T doInBackground() throws Exception { return operation.call(); }
            @Override protected void done() {
                owner.setEnabled(true); owner.setCursor(Cursor.getDefaultCursor());
                if (!owner.isDisplayable()) return;
                try { completed.accept(get()); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                catch (ExecutionException | RuntimeException e) { failed.accept(e); }
            }
        }.execute();
    }
    public static void textDialog(Window owner, String title, String text) {
        JDialog d = new JDialog(owner, title, Dialog.ModalityType.APPLICATION_MODAL);
        d.add(new JScrollPane(area(text, false))); d.setSize(760, 540); d.setLocationRelativeTo(owner); d.setVisible(true); d.dispose();
    }
}
