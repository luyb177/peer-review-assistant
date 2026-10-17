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
    private final JTextField username;
    private final JPasswordField password;

    public LoginFrame(AppConfig config, Database db, AdviceProvider ai) {
        super("课堂同伴互评助手 · 登录"); this.config = config; this.db = db; this.ai = ai;
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        LoginPanel view = new LoginPanel();
        username = view.getUsernameField(); password = view.getPasswordField();
        view.getLoginButton().addActionListener(event -> Ui.safely(this::login));
        view.getConfigButton().addActionListener(event -> Ui.safely(() -> new ConfigDialog(this, config, () -> { username.setText(""); password.setText(""); }).setVisible(true)));
        view.getTestConnectionButton().addActionListener(event -> Ui.safely(() -> Ui.work(this, () -> { db.ping(); return true; }, v -> Ui.info(this, "数据库连接成功。"))));
        view.getInitializeButton().addActionListener(event -> Ui.safely(this::initialize));
        getRootPane().setDefaultButton(view.getLoginButton());
        if (config.usesCourseDemo()) {
            JMenuBar notice = new JMenuBar(); JLabel label = new JLabel("课程演示服务：结课后关闭；继续使用请在连接配置中切换自己的 MySQL 和 AI。");
            label.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12)); notice.add(label); setJMenuBar(notice);
        }
        setContentPane(view); setSize(1000, 620); setMinimumSize(new Dimension(950, 580)); setLocationRelativeTo(null);
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
