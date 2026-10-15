package edu.course.peerreview.ui;

import edu.course.peerreview.config.AppConfig;
import javax.swing.*;
import java.awt.*;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ConfigDialog extends JDialog {
    public ConfigDialog(Window owner, AppConfig config) {
        super(owner, "本地连接配置", ModalityType.APPLICATION_MODAL);
        JPanel form = Ui.column(); form.setBorder(BorderFactory.createEmptyBorder(18, 22, 18, 22));
        Map<String, JTextField> fields = new LinkedHashMap<>();
        String[][] names = {{"db.url", "MySQL JDBC 地址"}, {"db.user", "数据库用户名"}, {"db.password", "数据库密码"}, {"db.trustStore", "MySQL CA 信任库"}, {"db.trustStorePassword", "CA 信任库口令"}, {"ai.baseUrl", "AI 服务地址"}, {"ai.model", "AI 模型"}, {"ai.apiKey", "AI API Key"}, {"ai.timeoutSeconds", "AI 超时（秒）"}};
        for (String[] pair : names) {
            JTextField input = pair[0].toLowerCase(java.util.Locale.ROOT).contains("password") || pair[0].contains("apiKey") ? new JPasswordField(config.get(pair[0]), 36) : new JTextField(config.get(pair[0]), 36);
            fields.put(pair[0], input); Ui.field(form, pair[1], input);
        }
        JCheckBox enabled = new JCheckBox("启用 AI（仅点击生成并确认后发送；可能产生费用）", config.aiEnabled()); form.add(enabled);
        form.add(new JLabel("<html><br>远程 MySQL 使用专用账号和证书校验；CA 信任库路径相对于配置目录。<br>本机 AI 网关允许 HTTP；外部 AI 服务必须 HTTPS。<br>凭据以明文保存在本机配置，请限制文件权限，不要提交。<br>PEER_REVIEW_AI_KEY 环境变量优先于文件中的 Key。<br>AI 使用 Anthropic Messages API 与官方 SDK，不是聊天网页地址。</html>"));
        form.add(Ui.toolbar(Ui.primary("保存配置", () -> {
            fields.forEach((key, input) -> config.set(key, input instanceof JPasswordField p ? new String(p.getPassword()) : input.getText().strip()));
            config.set("ai.enabled", Boolean.toString(enabled.isSelected()));
            config.timeoutSeconds(); config.save(); Ui.info(this, "配置已保存到：\n" + config.path()); dispose();
        }), Ui.button("取消", this::dispose)));
        add(new JScrollPane(form)); setSize(800, 640); setLocationRelativeTo(owner);
    }
}
