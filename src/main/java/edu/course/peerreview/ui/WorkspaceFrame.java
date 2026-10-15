package edu.course.peerreview.ui;

import edu.course.peerreview.ai.AdviceProvider;
import edu.course.peerreview.config.AppConfig;
import edu.course.peerreview.dao.Database;
import edu.course.peerreview.domain.Domain.*;
import edu.course.peerreview.service.*;
import edu.course.peerreview.util.*;
import javax.swing.*;
import java.awt.*;
import java.nio.file.Path;
import java.util.*;
import java.util.List;

public final class WorkspaceFrame extends JFrame {
    private final AppConfig config;
    private final AdviceProvider ai;
    private final User actor;
    private final PeerReviewService service;
    private final AuthService auth;
    private final Runnable loggedOut;
    private final JComboBox<Choice> courseFilter = new JComboBox<>(), assignmentFilter = new JComboBox<>(), statusFilter = new JComboBox<>();
    private final JTextField regex = new JTextField(18);
    private final JLabel status = new JLabel("请选择记录后使用上方操作；双击提交记录可阅读正文。");
    private final JTable courses = Ui.table("课程编号", "课程名称", "课程说明");
    private final JTable assignments = Ui.table("作业编号", "所属课程", "作业名称", "阶段", "互评开始 / 提交截止", "互评截止", "盲评");
    private final JTable submissions = Ui.table("提交编号", "作业", "学生", "提交时间", "内容摘要");
    private final JTable tasks = Ui.table("任务编号", "作业", "作者", "评审人", "状态", "加权分", "人工评语");
    private final JTable statistics = Ui.table("作业", "学生", "已评 / 分配", "平均分", "最高分", "最低分");
    private final JTable users = Ui.table("用户编号", "用户名", "姓名", "角色");
    private final JTable logs = Ui.table("时间", "账号", "操作", "说明");
    private final JTabbedPane tabs = new JTabbedPane();
    private ViewData data;
    private List<Course> shownCourses = List.of();
    private List<Assignment> shownAssignments = List.of();
    private List<SubmissionRow> shownSubmissions = List.of();
    private List<TaskRow> shownTasks = List.of();
    private List<StatRow> shownStats = List.of();
    private boolean changing;

    private record Choice(String id, String label) { @Override public String toString() { return label; } }
    record SubmissionRow(Assignment assignment, Submission submission) { }
    record TaskRow(Assignment assignment, ReviewTask task) { }
    record StatRow(Assignment assignment, Statistics statistics) { }
    record ViewData(List<Course> courses, List<Assignment> assignments, List<SubmissionRow> submissions,
                    List<TaskRow> tasks, List<StatRow> statistics, List<User> users, List<String[]> logs) { }

    public WorkspaceFrame(AppConfig config, Database db, AdviceProvider ai, User actor, Runnable loggedOut) {
        super("课堂同伴互评助手 · " + (actor.role() == Role.TEACHER ? "教师工作台" : "学生工作台"));
        this.config = config; this.ai = ai; this.actor = actor; this.loggedOut = loggedOut;
        service = new PeerReviewService(db); auth = new AuthService(db);
        setJMenuBar(menuBar());
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        JPanel root = new JPanel(new BorderLayout(0, 14)); root.setBorder(BorderFactory.createEmptyBorder(20, 24, 16, 24)); root.setBackground(Ui.PAPER);
        JPanel top = new JPanel(new BorderLayout()); top.setOpaque(false);
        JPanel heading = Ui.column(); heading.setOpaque(false); heading.add(Ui.title("课堂同伴互评", 28));
        heading.add(new JLabel((teacher() ? "教师工作台" : "学生工作台") + "    " + actor.name() + "（" + actor.username() + "）")); top.add(heading);
        top.add(Ui.toolbar(Ui.button("刷新数据", this::refresh), Ui.button("修改密码", this::changePassword), Ui.button("连接配置", () -> new ConfigDialog(this, config).setVisible(true)), Ui.button("退出登录", () -> { dispose(); loggedOut.run(); })), BorderLayout.EAST);
        JPanel north = new JPanel(new BorderLayout(0, 12)); north.setOpaque(false); north.add(top, BorderLayout.NORTH);
        courseFilter.setPreferredSize(new Dimension(170, 32)); assignmentFilter.setPreferredSize(new Dimension(240, 32));
        statusFilter.addItem(new Choice("", "全部任务状态")); statusFilter.addItem(new Choice("PENDING", "待互评")); statusFilter.addItem(new Choice("COMPLETED", "已完成"));
        north.add(Ui.toolbar(new JLabel("课程"), courseFilter, new JLabel("作业"), assignmentFilter, statusFilter, new JLabel("正则搜索"), regex, Ui.button("筛选", this::applyFilters), Ui.button("清空", () -> {
            changing = true; courseFilter.setSelectedIndex(0); assignmentFilter.setSelectedIndex(0); statusFilter.setSelectedIndex(0); regex.setText(""); changing = false; applyFilters();
        })), BorderLayout.SOUTH);
        root.add(north, BorderLayout.NORTH);
        if (teacher()) tabs.addTab("课程管理", panel(courses, Ui.button("新建课程", () -> editCourse(null)), Ui.button("编辑课程", () -> editCourse(shownCourses.get(Ui.selected(courses)))), Ui.button("删除课程", this::deleteCourse)));
        if (teacher()) tabs.addTab("作业管理", panel(assignments, Ui.primary("发布作业", () -> editAssignment(null)), Ui.button("编辑作业", () -> editAssignment(selectedAssignment())), Ui.button("删除作业", this::deleteAssignment), Ui.button("随机分配互评", this::allocate), Ui.button("结束互评", this::closeAssignment), Ui.button("查看要求", this::requirements)));
        else tabs.addTab("课堂作业", panel(assignments, Ui.primary("提交 / 更新作业", this::submit), Ui.button("查看要求", this::requirements)));
        tabs.addTab(teacher() ? "全部提交" : "我的提交", panel(submissions, Ui.button("阅读所选提交", this::readSubmission)));
        tabs.addTab(teacher() ? "互评进度" : "我的互评任务", panel(tasks, Ui.primary(teacher() ? "查看互评详情" : "打开人工评分", this::review)));
        tabs.addTab(teacher() ? "结果统计" : "我的成绩", teacher() ? panel(statistics, Ui.button("导出当前统计 CSV", this::export)) : panel(statistics));
        if (teacher()) {
            tabs.addTab("学生账号", panel(users, Ui.primary("添加学生账号", this::createStudent), Ui.button("编辑学生姓名", this::renameStudent), Ui.button("删除未使用学生", this::deleteStudent)));
            tabs.addTab("操作记录", panel(logs));
        }
        root.add(tabs);
        JPanel footer = new JPanel(new BorderLayout()); footer.setOpaque(false); footer.add(status); JLabel note = new JLabel("AI 仅供参考 · 最终评分由学生决定"); note.setForeground(Ui.MUTED); footer.add(note, BorderLayout.EAST); root.add(footer, BorderLayout.SOUTH);
        add(root); setSize(1320, 850); setMinimumSize(new Dimension(1200, 740)); setLocationRelativeTo(null);
        courseFilter.addActionListener(e -> { if (!changing) Ui.safely(this::applyFilters); });
        assignmentFilter.addActionListener(e -> { if (!changing) Ui.safely(this::applyFilters); });
        statusFilter.addActionListener(e -> { if (!changing) Ui.safely(this::applyFilters); });
        regex.addActionListener(e -> Ui.safely(this::applyFilters));
        submissions.addMouseListener(new java.awt.event.MouseAdapter() { @Override public void mouseClicked(java.awt.event.MouseEvent e) { if (e.getClickCount() == 2) Ui.safely(WorkspaceFrame.this::readSubmission); } });
    }
    private JMenuBar menuBar() {
        JMenuBar bar = new JMenuBar(); JMenu classroom = new JMenu("课堂"); JMenu help = new JMenu("帮助");
        JMenuItem reload = new JMenuItem("刷新数据"); reload.setAccelerator(KeyStroke.getKeyStroke("F5")); reload.addActionListener(e -> refresh());
        JMenuItem settings = new JMenuItem("本地连接配置"); settings.addActionListener(e -> new ConfigDialog(this, config).setVisible(true));
        JMenuItem logout = new JMenuItem("退出登录"); logout.addActionListener(e -> { dispose(); loggedOut.run(); });
        classroom.add(reload); classroom.add(settings); classroom.addSeparator(); classroom.add(logout);
        JMenuItem guide = new JMenuItem("使用流程"); guide.addActionListener(e -> Ui.info(this, "教师：发布课程作业 → 等待学生提交 → 随机分配 → 查看进度与统计。\n学生：提交作业 → 打开待评任务 → 手动逐项评分、撰写评语 → 提交。\nAI 是可选的参考阅读，不会替你给分。详细说明见项目 README。"));
        JMenuItem about = new JMenuItem("关于"); about.addActionListener(e -> Ui.info(this, "课堂同伴互评助手 1.0.0\nJava / Swing / JDBC / MySQL\n课程个人项目：以规则和人工判断为核心。"));
        help.add(guide); help.add(about); bar.add(classroom); bar.add(help); return bar;
    }
    private boolean teacher() { return actor.role() == Role.TEACHER; }
    private JPanel panel(JTable table, JComponent... buttons) { JPanel p = new JPanel(new BorderLayout()); p.add(Ui.toolbar(buttons), BorderLayout.NORTH); p.add(new JScrollPane(table)); return p; }
    public void refresh() {
        status.setText("正在加载课堂数据……");
        Ui.work(this, this::load, this::render, error -> { status.setText("加载失败，请检查连接配置后刷新。"); Ui.error(this, error); });
    }
    ViewData load() {
        List<Course> cs = service.courses(actor); List<Assignment> as = service.assignments(actor);
        List<SubmissionRow> ss = new ArrayList<>(); List<TaskRow> ts = new ArrayList<>(); List<StatRow> rs = new ArrayList<>();
        for (Assignment a : as) {
            service.submissions(actor, a.id()).forEach(s -> ss.add(new SubmissionRow(a, s)));
            service.tasks(actor, a.id()).forEach(t -> ts.add(new TaskRow(a, t)));
            service.statistics(actor, a.id()).forEach(s -> rs.add(new StatRow(a, s)));
        }
        return new ViewData(cs, as, ss, ts, rs, teacher() ? auth.users(actor) : List.of(), teacher() ? service.logs(actor) : List.of());
    }
    void render(ViewData snapshot) {
        data = snapshot; String courseId = choice(courseFilter), assignmentId = choice(assignmentFilter);
        changing = true; courseFilter.removeAllItems(); courseFilter.addItem(new Choice("", "全部课程")); assignmentFilter.removeAllItems(); assignmentFilter.addItem(new Choice("", "全部作业"));
        for (Course c : data.courses()) courseFilter.addItem(new Choice(Long.toString(c.id()), c.name()));
        for (Assignment a : data.assignments()) assignmentFilter.addItem(new Choice(Long.toString(a.id()), a.title()));
        select(courseFilter, courseId); select(assignmentFilter, assignmentId); changing = false; applyFilters();
    }
    private String choice(JComboBox<Choice> box) { return box.getSelectedItem() instanceof Choice c ? c.id() : ""; }
    private void select(JComboBox<Choice> box, String id) { for (int i = 0; i < box.getItemCount(); i++) if (box.getItemAt(i).id().equals(id)) { box.setSelectedIndex(i); return; } box.setSelectedIndex(0); }
    private boolean visible(Assignment a) { return (choice(courseFilter).isEmpty() || choice(courseFilter).equals(Long.toString(a.courseId()))) && (choice(assignmentFilter).isEmpty() || choice(assignmentFilter).equals(Long.toString(a.id()))); }
    private String courseName(long id) { return data.courses().stream().filter(c -> c.id() == id).map(Course::name).findFirst().orElse(""); }
    private void applyFilters() {
        if (data == null) return;
        Map<String, String> selected = Map.of("course", choice(courseFilter), "assignment", choice(assignmentFilter), "status", choice(statusFilter));
        TableFilter<TaskRow> filter = new TableFilter<>(Map.of("course", r -> Long.toString(r.assignment().courseId()), "assignment", r -> Long.toString(r.assignment().id()), "status", r -> r.task().status().name()));
        // 先验证正则并计算所有候选列表，再更新界面，避免半刷新状态。
        List<TaskRow> filteredTasks = filter.apply(data.tasks(), selected, regex.getText(), r -> r.task().content() + "\n" + Objects.toString(r.task().comment(), ""));
        TableFilter<SubmissionRow> submissionFilter = new TableFilter<>(Map.of("course", r -> Long.toString(r.assignment().courseId()), "assignment", r -> Long.toString(r.assignment().id())));
        List<SubmissionRow> filteredSubmissions = submissionFilter.apply(data.submissions(), Map.of("course", choice(courseFilter), "assignment", choice(assignmentFilter)), regex.getText(), r -> r.submission().content());
        shownCourses = data.courses().stream().filter(c -> choice(courseFilter).isEmpty() || choice(courseFilter).equals(Long.toString(c.id()))).toList();
        shownAssignments = data.assignments().stream().filter(this::visible).toList(); shownSubmissions = filteredSubmissions; shownTasks = filteredTasks;
        shownStats = data.statistics().stream().filter(r -> visible(r.assignment())).toList();
        Ui.rows(courses, shownCourses.stream().map(c -> new Object[]{c.id(), c.name(), c.description()}).toList());
        Ui.rows(assignments, shownAssignments.stream().map(a -> new Object[]{a.id(), courseName(a.courseId()), a.title(), phase(a.phase()), AssignmentDialog.TIME.format(a.reviewOpen()), AssignmentDialog.TIME.format(a.reviewDeadline()), a.blind() ? "是" : "否"}).toList());
        Ui.rows(submissions, shownSubmissions.stream().map(r -> new Object[]{r.submission().id(), r.assignment().title(), r.submission().author(), AssignmentDialog.TIME.format(r.submission().submittedAt()), excerpt(r.submission().content())}).toList());
        Ui.rows(tasks, shownTasks.stream().map(r -> new Object[]{r.task().id(), r.assignment().title(), r.task().author(), r.task().reviewer(), r.task().status() == TaskStatus.PENDING ? "待互评" : "已完成", r.task().total(), r.task().comment()}).toList());
        Ui.rows(statistics, shownStats.stream().map(r -> new Object[]{r.assignment().title(), r.statistics().author(), r.statistics().completed() + " / " + r.statistics().assigned(), r.statistics().average(), r.statistics().highest(), r.statistics().lowest()}).toList());
        Ui.rows(users, data.users().stream().map(u -> new Object[]{u.id(), u.username(), u.name(), u.role() == Role.TEACHER ? "教师" : "学生"}).toList());
        Ui.rows(logs, data.logs().stream().map(row -> (Object[]) row).toList());
        status.setText("显示 " + shownAssignments.size() + " 份作业、" + shownTasks.size() + " 条互评任务。正则仅搜索提交正文和评语；状态仅筛选任务。");
    }
    private String excerpt(String s) { return s.replace('\n', ' ').substring(0, Math.min(70, s.length())); }
    private String phase(Phase phase) { return switch (phase) { case SUBMISSION -> "收集提交"; case REVIEWING -> "互评中"; case CLOSED -> "已结束"; }; }
    private Assignment selectedAssignment() { return shownAssignments.get(Ui.selected(assignments)); }
    private void editCourse(Course old) {
        JTextField name = new JTextField(old == null ? "" : old.name(), 30); JTextArea desc = Ui.area(old == null ? "" : old.description(), true);
        JPanel form = Ui.column(); Ui.field(form, "课程名称", name); Ui.field(form, "课程说明", new JScrollPane(desc));
        if (JOptionPane.showConfirmDialog(this, form, old == null ? "新建课程" : "编辑课程", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
        String title = name.getText(), description = desc.getText(); Ui.work(this, () -> { service.saveCourse(actor, old == null ? 0 : old.id(), title, description); return true; }, v -> refresh());
    }
    private void deleteCourse() { Course c = shownCourses.get(Ui.selected(courses)); if (Ui.confirm(this, "删除课程「" + c.name() + "」？有作业的课程不会被删除。")) Ui.work(this, () -> { service.deleteCourse(actor, c.id()); return true; }, v -> refresh()); }
    private void editAssignment(Assignment old) { if (data.courses().isEmpty()) throw new BusinessException("请先创建课程。"); new AssignmentDialog(this, service, actor, data.courses(), old, this::refresh).setVisible(true); }
    private void deleteAssignment() { Assignment a = selectedAssignment(); if (Ui.confirm(this, "删除作业「" + a.title() + "」？已有学生提交时不会删除。")) Ui.work(this, () -> { service.deleteAssignment(actor, a.id()); return true; }, v -> refresh()); }
    private void allocate() {
        Assignment a = selectedAssignment(); String input = JOptionPane.showInputDialog(this, "每份提交由几名学生评审？\n只在已提交学生间分配，排除自评并保证负载均衡。", "2"); if (input == null) return;
        int n; try { n = Integer.parseInt(input.strip()); } catch (NumberFormatException e) { throw new BusinessException("评审人数必须是整数。"); }
        if (Ui.confirm(this, "开始分配「" + a.title() + "」？成功后不能重复分配。")) Ui.work(this, () -> service.allocate(actor, a.id(), n), count -> { Ui.info(this, "已生成 " + count + " 条互评任务。"); refresh(); });
    }
    private void closeAssignment() { Assignment a = selectedAssignment(); if (Ui.confirm(this, "结束「" + a.title() + "」的互评？\n截止前须完成全部任务；截止后允许保留未完成任务结束。")) Ui.work(this, () -> { service.closeAssignment(actor, a.id()); return true; }, v -> refresh()); }
    private void requirements() {
        Assignment a = selectedAssignment(); StringBuilder text = new StringBuilder(a.description()).append("\n\n互评开始：").append(AssignmentDialog.TIME.format(a.reviewOpen())).append("\n互评截止：").append(AssignmentDialog.TIME.format(a.reviewDeadline())).append("\n\n评分规则：\n");
        a.criteria().forEach(c -> text.append(c.name()).append("：").append(c.weight()).append("%，区间 ").append(c.min()).append("—").append(c.max()).append('\n'));
        Ui.textDialog(this, a.title(), text.toString());
    }
    private void submit() {
        Assignment a = selectedAssignment(); String old = data.submissions().stream().filter(s -> s.assignment().id() == a.id()).map(s -> s.submission().content()).findFirst().orElse("");
        JDialog dialog = new JDialog(this, "提交作业 · " + a.title(), true); JTextArea content = Ui.area(old, true);
        dialog.add(new JScrollPane(content)); dialog.add(Ui.toolbar(Ui.primary("保存提交", () -> { String text = content.getText(); Ui.work(dialog, () -> { service.submit(actor, a.id(), text); return true; }, v -> { dialog.dispose(); refresh(); }); }), Ui.button("取消", dialog::dispose)), BorderLayout.SOUTH);
        dialog.setSize(780, 580); dialog.setLocationRelativeTo(this); dialog.setVisible(true);
    }
    private void readSubmission() { SubmissionRow row = shownSubmissions.get(Ui.selected(submissions)); Ui.textDialog(this, row.assignment().title() + " · " + row.submission().author(), row.submission().content()); }
    private record ReviewView(Assignment assignment, ReviewTask task, ReviewDetail detail, Advice advice) { }
    private void review() {
        long id = shownTasks.get(Ui.selected(tasks)).task().id();
        Ui.work(this, () -> { ReviewTask t = service.task(actor, id); return new ReviewView(service.assignment(actor, t.assignmentId()), t, service.reviewDetail(actor, id), service.advice(actor, id)); }, v -> new ReviewDialog(this, config, service, ai, actor, v.assignment(), v.task(), v.detail(), v.advice(), this::refresh).setVisible(true));
    }
    private void export() {
        if (!teacher()) throw new BusinessException("完整 CSV 导出仅对教师开放；学生可查看自己的成绩。");
        Set<Long> ids = new HashSet<>(); shownStats.forEach(r -> ids.add(r.assignment().id()));
        if (ids.size() != 1) throw new BusinessException("请在上方作业筛选框中选择一份有提交记录的作业后导出。");
        JFileChooser chooser = new JFileChooser(); chooser.setSelectedFile(new java.io.File("互评结果.csv")); if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        Path path = chooser.getSelectedFile().toPath(); if (java.nio.file.Files.exists(path) && !Ui.confirm(this, "文件已存在，确认覆盖？")) return;
        List<Statistics> rows = shownStats.stream().map(StatRow::statistics).toList(); Ui.work(this, () -> { CsvExporter.write(path, rows); return true; }, v -> Ui.info(this, "CSV 已导出：\n" + path));
    }
    private void createStudent() {
        JTextField login = new JTextField(24), name = new JTextField(24); JPasswordField pass = new JPasswordField(24); JPanel form = Ui.column(); Ui.field(form, "用户名", login); Ui.field(form, "学生姓名", name); Ui.field(form, "初始密码（8位起）", pass);
        if (JOptionPane.showConfirmDialog(this, form, "添加学生", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
        String username = login.getText(), displayName = name.getText(); char[] secret = pass.getPassword(); Ui.work(this, () -> { auth.createStudent(actor, username, displayName, secret); return true; }, v -> refresh());
    }
    private void renameStudent() {
        User student = data.users().get(Ui.selected(users)); String name = JOptionPane.showInputDialog(this, "新的学生姓名", student.name());
        if (name == null) return;
        Ui.work(this, () -> { auth.renameStudent(actor, student.id(), name); return true; }, v -> refresh());
    }
    private void deleteStudent() {
        User student = data.users().get(Ui.selected(users));
        if (Ui.confirm(this, "删除学生「" + student.name() + "」？已有业务历史的学生不会删除。"))
            Ui.work(this, () -> { auth.deleteStudent(actor, student.id()); return true; }, v -> refresh());
    }
    private void changePassword() {
        JPasswordField old = new JPasswordField(24), next = new JPasswordField(24); JPanel form = Ui.column(); Ui.field(form, "原密码", old); Ui.field(form, "新密码（8位起）", next);
        if (JOptionPane.showConfirmDialog(this, form, "修改密码", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
        char[] before = old.getPassword(), after = next.getPassword(); Ui.work(this, () -> { auth.changePassword(actor, before, after); return true; }, v -> Ui.info(this, "密码已修改。"));
    }
}
