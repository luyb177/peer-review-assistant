package edu.course.peerreview.ui;

import edu.course.peerreview.domain.Domain.*;
import edu.course.peerreview.service.*;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.*;
import java.time.format.*;
import java.util.ArrayList;
import java.util.List;

public final class AssignmentDialog extends JDialog {
    public static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm").withResolverStyle(ResolverStyle.STRICT);
    public AssignmentDialog(Window owner, PeerReviewService service, User actor, List<Course> courses, Assignment old, Runnable updated) {
        super(owner, old == null ? "发布新作业" : "编辑作业", ModalityType.APPLICATION_MODAL);
        JComboBox<Course> course = new JComboBox<>(courses.toArray(Course[]::new));
        if (old != null) { courses.stream().filter(c -> c.id() == old.courseId()).findFirst().ifPresent(course::setSelectedItem); course.setEnabled(false); }
        JTextField title = new JTextField(old == null ? "" : old.title());
        JTextArea description = Ui.area(old == null ? "" : old.description(), true);
        JTextField open = new JTextField(TIME.format(old == null ? LocalDateTime.now().plusDays(1).withSecond(0) : old.reviewOpen()));
        JTextField deadline = new JTextField(TIME.format(old == null ? LocalDateTime.now().plusDays(7).withSecond(0) : old.reviewDeadline()));
        JCheckBox blind = new JCheckBox("开启盲评：学生评审时不显示作者姓名", old == null || old.blind());
        JPanel form = Ui.column(); form.setBorder(BorderFactory.createEmptyBorder(12, 20, 8, 20));
        Ui.field(form, "所属课程", course); Ui.field(form, "作业名称", title); Ui.field(form, "作业要求", new JScrollPane(description));
        Ui.field(form, "互评开始", open); Ui.field(form, "互评截止", deadline); form.add(new JLabel("时间格式：2026-10-15 14:30（运行电脑本地时间）；互评开始即停止提交。")); form.add(blind);
        DefaultTableModel model = new DefaultTableModel(new String[]{"评分维度", "权重（%）", "最低分", "最高分"}, 0);
        if (old == null) { model.addRow(new Object[]{"完整性", "60", "0", "100"}); model.addRow(new Object[]{"规范性", "30", "0", "100"}); model.addRow(new Object[]{"创新性", "10", "0", "100"}); }
        else for (Criterion c : old.criteria()) model.addRow(new Object[]{c.name(), c.weight(), c.min(), c.max()});
        JTable criteria = new JTable(model); criteria.setRowHeight(32); criteria.putClientProperty("terminateEditOnFocusLost", true);
        JPanel rules = new JPanel(new BorderLayout()); rules.setBorder(BorderFactory.createEmptyBorder(0, 20, 0, 20));
        rules.add(Ui.toolbar(Ui.title("评分规则", 16), Ui.button("添加维度", () -> model.addRow(new Object[]{"新维度", "0", "0", "100"})), Ui.button("删除所选维度", () -> { if (criteria.getSelectedRow() >= 0) model.removeRow(criteria.getSelectedRow()); })), BorderLayout.NORTH);
        rules.add(new JScrollPane(criteria));
        JButton save = Ui.primary("保存作业", () -> {
            if (criteria.isEditing() && !criteria.getCellEditor().stopCellEditing()) throw new BusinessException("请先完成评分项编辑。");
            if (course.getSelectedItem() == null) throw new BusinessException("请先创建课程。");
            LocalDateTime start, end;
            try { start = LocalDateTime.parse(open.getText().strip(), TIME); end = LocalDateTime.parse(deadline.getText().strip(), TIME); }
            catch (DateTimeParseException e) { throw new BusinessException("时间格式不合法，请按 yyyy-MM-dd HH:mm 输入真实日期。"); }
            List<Criterion> items = new ArrayList<>();
            for (int row = 0; row < model.getRowCount(); row++) items.add(new Criterion(row + 1, String.valueOf(model.getValueAt(row, 0)), Validation.parseDecimal(String.valueOf(model.getValueAt(row, 1)), "权重"), Validation.parseDecimal(String.valueOf(model.getValueAt(row, 2)), "最低分"), Validation.parseDecimal(String.valueOf(model.getValueAt(row, 3)), "最高分")));
            long courseId = ((Course) course.getSelectedItem()).id(); String name = title.getText(), desc = description.getText(); boolean anonymous = blind.isSelected();
            Ui.work(this, () -> { service.saveAssignment(actor, old == null ? 0 : old.id(), courseId, name, desc, start, end, anonymous, items); return true; }, v -> { dispose(); updated.run(); });
        });
        add(form, BorderLayout.NORTH); add(rules); add(Ui.toolbar(save, Ui.button("取消", this::dispose)), BorderLayout.SOUTH);
        setSize(880, 800); setMinimumSize(new Dimension(780, 700)); setLocationRelativeTo(owner);
    }
}
