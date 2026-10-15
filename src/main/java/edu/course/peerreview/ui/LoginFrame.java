package edu.course.peerreview.ui;

import edu.course.peerreview.ai.AdviceProvider;
import edu.course.peerreview.config.AppConfig;
import edu.course.peerreview.dao.Database;
import edu.course.peerreview.service.*;
import javax.swing.*;
import java.awt.*;

public final class LoginFrame extends JFrame {
    private final AppConfig config;
    private final Database db;
    private final AdviceProvider ai;
    private final JTextField username = new JTextField(22);
    private final JPasswordField password = new JPasswordField(22);

    public LoginFrame(AppConfig config, Database db, AdviceProvider ai) {
        super("课堂同伴互评助手 · 登录"); this.config = config; this.db = db; this.ai = ai;
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        JPanel root = new JPanel(new GridLayout(1, 2, 28, 0)); root.setBorder(BorderFactory.createEmptyBorder(30, 30, 30, 30)); root.setBackground(Color.WHITE);
        JPanel intro = Ui.column(); intro.setBackground(new Color(0xE6F0E9)); intro.setBorder(BorderFactory.createEmptyBorder(42, 26, 30, 26));
        intro.add(Ui.title("课堂同伴互评", 27)); intro.add(Box.createVerticalStrut(16));
        intro.add(new JLabel("让每一条反馈，都有依据。")); intro.add(Box.createVerticalStrut(44));
        intro.add(Ui.title("教师制定规则", 17)); intro.add(new JLabel("发布作业，分配互评，汇总结果")); intro.add(Box.createVerticalStrut(24));
        intro.add(Ui.title("学生独立判断", 17)); intro.add(new JLabel("逐项评分，写下具体的人工评语")); intro.add(Box.createVerticalStrut(24));
        intro.add(Ui.title("AI 只作参考", 17)); intro.add(new JLabel("不会自动填分，也不会代交评审"));
        JPanel form = Ui.column(); form.setBackground(Color.WHITE); form.setBorder(BorderFactory.createEmptyBorder(40, 0, 10, 4));
        form.add(Ui.title("进入课堂", 25)); form.add(Box.createVerticalStrut(24)); Ui.field(form, "用户名", username); Ui.field(form, "密码", password);
        JButton login = Ui.primary("登录", this::login); getRootPane().setDefaultButton(login);
        form.add(Ui.toolbar(login, Ui.button("连接配置", () -> new ConfigDialog(this, config).setVisible(true))));
        form.add(Box.createVerticalStrut(28));
        form.add(new JLabel("首次使用？先填写远程 MySQL 连接配置。"));
        form.add(Ui.toolbar(Ui.button("测试数据库连接", () -> Ui.work(this, () -> { db.ping(); return true; }, v -> Ui.info(this, "数据库连接成功。"))), Ui.button("初始化专用库", this::initialize)));
        form.add(new JLabel("<html><br>初始化不会创建服务器数据库或清空已有表。<br>已有账号请直接登录，无需再次初始化。</html>"));
        root.add(intro); root.add(form); add(root); setSize(1000, 620); setMinimumSize(new Dimension(950, 580)); setLocationRelativeTo(null);
    }
    private void login() {
        String name = username.getText(); char[] secret = password.getPassword(); password.setText("");
        Ui.work(this, () -> new AuthService(db).login(name, secret), actor -> {
            WorkspaceFrame workspace = new WorkspaceFrame(config, db, ai, actor, () -> new LoginFrame(config, db, ai).setVisible(true));
            workspace.setVisible(true); workspace.refresh(); dispose();
        });
    }
    private void initialize() {
        JTextField account = new JTextField("teacher", 22); JTextField name = new JTextField("任课教师", 22); JPasswordField secret = new JPasswordField(22);
        JCheckBox demo = new JCheckBox("加入合成演示数据（6 名学生、2 份作业）", false);
        JPanel form = Ui.column(); Ui.field(form, "教师用户名", account); Ui.field(form, "教师姓名", name); Ui.field(form, "教师密码（8位起）", secret); form.add(demo);
        if (JOptionPane.showConfirmDialog(this, form, "创建表结构和初始教师", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
        if (!Ui.confirm(this, "将连接当前配置指定的数据库并创建不存在的表。\n请确认这是本作业的专用库，而不是其他业务数据库。\n已有用户时会拒绝写入初始数据。是否继续？")) return;
        char[] pass = secret.getPassword(); boolean withDemo = demo.isSelected();
        String login = account.getText(), displayName = name.getText();
        Ui.work(this, () -> { new InitializationService(db).initialize(login, displayName, pass, withDemo); return true; }, v -> {
            username.setText(login);
            Ui.info(this, "初始化完成，请用刚设置的教师账号登录。" + (withDemo ? "\n演示学生：student01—student06，初始密码 Demo12345!\n演示账号不要用于真实课堂，请及时修改密码。" : ""));
        });
    }
}
