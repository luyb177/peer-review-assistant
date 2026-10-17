package edu.course.peerreview.ui;

import edu.course.peerreview.ai.AiServices;
import edu.course.peerreview.config.*;
import edu.course.peerreview.dao.Database;
import edu.course.peerreview.service.*;
import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

/** Drafts are isolated from active settings until validation and atomic persistence succeed. */
public final class ConfigDialog extends JDialog {
    private final AppConfig active;
    private final ConfigProfiles profiles;
    private final Runnable databaseChanged;
    public ConfigDialog(Window owner, AppConfig config) { this(owner, config, () -> { }); }
    public ConfigDialog(Window owner, AppConfig config, Runnable databaseChanged) {
        super(owner, "连接配置与方案切换", ModalityType.APPLICATION_MODAL);
        active = config; profiles = new ConfigProfiles(config); this.databaseChanged = databaseChanged;
        JPanel root = new JPanel(new BorderLayout(10, 10)); root.setBorder(BorderFactory.createEmptyBorder(16, 20, 12, 20));
        JLabel notice = new JLabel("<html>课程默认服务仅供验收，课程结束后会关闭；继续使用请切换自己的数据库和 AI。<br>MySQL 切换会退出当前登录；不会自动迁移原库数据。AI 测试只查询模型列表，不生成内容。</html>");
        root.add(notice, BorderLayout.NORTH); JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("MySQL", new GroupPanel("db")); tabs.addTab("AI 接口", new GroupPanel("ai")); root.add(tabs);
        root.add(Ui.toolbar(Ui.button("关闭", this::dispose)), BorderLayout.SOUTH); setContentPane(root);
        setSize(880, 780); setMinimumSize(new Dimension(800, 700)); setLocationRelativeTo(owner);
    }
    private final class GroupPanel extends JPanel {
        private final String group;
        private final Map<String,JTextField> fields = new LinkedHashMap<>();
        private final JComboBox<Object> preset = new JComboBox<>();
        private final JTextField profileName = new JTextField(22);
        private final JComboBox<String> protocol = new JComboBox<>(new String[]{"anthropic", "openai-compatible"});
        private final JCheckBox enabled = new JCheckBox("启用 AI"), environment = new JCheckBox("优先使用 PEER_REVIEW_AI_KEY 环境变量");
        private final JTextArea result = Ui.area("修改草稿不会改变当前连接。测试或应用结果将在这里显示。", false);
        GroupPanel(String group) {
            super(new BorderLayout(8, 8)); this.group = group;
            JPanel form = Ui.column(); form.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
            preset.addItem("当前配置"); if (active.hasCourseDefaults()) preset.addItem("课程默认服务"); profiles.list(group).forEach(preset::addItem);
            Ui.field(form, "读取配置方案", preset); Ui.field(form, "另存方案名称", profileName);
            String[][] names = group.equals("db")
                    ? new String[][]{{"db.url","MySQL JDBC 地址"},{"db.user","数据库用户名"},{"db.password","数据库密码"},{"db.trustStore","CA 信任库路径"},{"db.trustStorePassword","CA 信任库口令"}}
                    : new String[][]{{"ai.baseUrl","API 地址"},{"ai.model","模型名称"},{"ai.apiKey","API Key"},{"ai.timeoutSeconds","超时（5—300秒）"}};
            if (group.equals("ai")) Ui.field(form, "接口协议", protocol);
            for (String[] pair : names) {
                JTextField input = pair[0].toLowerCase(Locale.ROOT).contains("password") || pair[0].contains("apiKey") ? new JPasswordField(36) : new JTextField(36);
                fields.put(pair[0], input); Ui.field(form, pair[1], input);
            }
            if (group.equals("ai")) { form.add(enabled); form.add(environment); }
            else form.add(new JLabel("<html>仅自动创建缺表、缺失的专用库，以及已知安全版本字段。<br>冲突结构不删除重建；创建库/表需相应权限，升级字段需 ALTER。<br>请使用自己的专用库，不要填写其他业务数据库。</html>"));
            form.add(Ui.toolbar(Ui.button(group.equals("db") ? "测试连接与结构" : "测试 AI 连通性", this::test), Ui.primary("保存并切换", this::apply)));
            add(new JScrollPane(form)); result.setRows(5); result.setColumns(45); JScrollPane report = new JScrollPane(result); report.setPreferredSize(new Dimension(700, 150)); add(report, BorderLayout.SOUTH);
            fill(active);
            preset.addActionListener(event -> Ui.safely(() -> {
                Object item = preset.getSelectedItem();
                AppConfig selected = item instanceof ConfigProfiles.Profile p ? profiles.select(active, group, p)
                        : "课程默认服务".equals(item) ? active.courseDefaults() : active;
                fill(selected); result.setText("方案已读入草稿，尚未切换。请测试或保存并切换。");
            }));
        }
        private void fill(AppConfig config) {
            fields.forEach((key,input) -> input.setText(config.get(key)));
            protocol.setSelectedItem(config.get("ai.protocol")); enabled.setSelected(config.aiEnabled()); environment.setSelected(Boolean.parseBoolean(config.get("ai.useEnvironmentKey")));
        }
        private AppConfig candidate() {
            AppConfig copy = active.copy();
            fields.forEach((key,input) -> copy.set(key, input instanceof JPasswordField p ? new String(p.getPassword()) : input.getText().strip()));
            if (group.equals("ai")) { copy.set("ai.protocol", protocol.getSelectedItem().toString()); copy.set("ai.enabled", Boolean.toString(enabled.isSelected())); copy.set("ai.useEnvironmentKey", Boolean.toString(environment.isSelected())); }
            copy.set("course.demo." + group, Boolean.toString(isCourseService(copy, group)));
            return copy;
        }
        private void test() {
            AppConfig draft = candidate(); result.setText("正在测试，当前连接配置不会改变……");
            Ui.work(ConfigDialog.this, () -> {
                if (group.equals("ai")) return AiServices.test(draft);
                SchemaManager.validateTarget(draft.get("db.url")); return new SchemaManager().inspect(new Database(draft)).summary();
            }, result::setText, error -> { result.setText("测试未通过，当前配置保持不变。"); Ui.error(ConfigDialog.this, error); });
        }
        private void apply() {
            AppConfig draft = candidate(); String name = profileName.getText().strip();
            if (group.equals("db") && !Ui.confirm(ConfigDialog.this, "将检查并准备指定的专用数据库。缺失表会自动创建；已知旧版本会安全补字段。\n不会删除已有表或数据，结构冲突将拒绝切换。\n切换后须重新登录，原数据库的数据不会迁移。是否继续？")) return;
            result.setText("正在验证并准备配置……");
            Ui.work(ConfigDialog.this, () -> {
                if (group.equals("db")) return new SchemaManager().prepare(draft).summary();
                if (!draft.aiEnabled()) return "AI 已选择关闭，未发起任何接口请求。";
                return AiServices.test(draft);
            }, report -> {
                if (!name.isBlank()) profiles.save(group, name, draft);
                active.applyAndSave(draft);
                result.setText(report); Ui.info(ConfigDialog.this, report + (group.equals("db") ? "\n数据库已切换，请重新登录；新空库请先初始化教师账号。" : "\nAI 配置已切换，未发起生成请求。"));
                dispose(); if (group.equals("db")) databaseChanged.run();
            }, error -> { result.setText("未切换，原配置保持不变。若建表因权限中断，请修复权限后重试，不要清空数据。"); Ui.error(ConfigDialog.this, error); });
        }
    }
    private boolean isCourseService(AppConfig candidate, String group) {
        if (!active.hasCourseDefaults()) return false;
        AppConfig defaults = active.courseDefaults();
        List<String> keys = group.equals("db") ? List.of("db.url","db.user","db.password") : List.of("ai.baseUrl","ai.apiKey");
        return keys.stream().allMatch(key -> candidate.get(key).equals(defaults.get(key)));
    }
}
