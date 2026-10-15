package edu.course.peerreview.ui;

import edu.course.peerreview.ai.AdviceProvider;
import edu.course.peerreview.config.AppConfig;
import edu.course.peerreview.domain.Domain.*;
import edu.course.peerreview.service.*;
import javax.swing.*;
import java.awt.*;
import java.math.BigDecimal;
import java.util.*;

public final class ReviewDialog extends JDialog {
    public ReviewDialog(Window owner, AppConfig config, PeerReviewService service, AdviceProvider ai, User actor,
                        Assignment assignment, ReviewTask task, ReviewDetail detail, Advice advice, Runnable updated) {
        super(owner, "互评任务 " + task.id(), ModalityType.APPLICATION_MODAL);
        boolean editable = actor.role() == Role.STUDENT && task.status() == TaskStatus.PENDING && assignment.phase() == Phase.REVIEWING;
        JPanel root = new JPanel(new BorderLayout(16, 12)); root.setBorder(BorderFactory.createEmptyBorder(20, 22, 18, 22));
        JPanel header = Ui.column(); header.add(Ui.title(assignment.title(), 22));
        header.add(new JLabel("作者：" + task.author() + "    评审人：" + task.reviewer() + "    截止：" + AssignmentDialog.TIME.format(assignment.reviewDeadline())));
        header.add(new JLabel("评分和人工评语必须由学生独立填写，AI 不会替你提交。")); root.add(header, BorderLayout.NORTH);
        JPanel left = new JPanel(new BorderLayout(0, 8)); left.add(new JLabel("提交内容"), BorderLayout.NORTH); left.add(new JScrollPane(Ui.area(task.content(), false)));
        JTextArea reference = Ui.area(advice == null ? "尚未生成 AI 参考意见。可以直接完成人工互评。" : advice.text(), false);
        JPanel aiPanel = new JPanel(new BorderLayout(0, 8)); aiPanel.add(new JLabel("AI 参考阅读（不计入评分）"), BorderLayout.NORTH); aiPanel.add(new JScrollPane(reference));
        JButton generate = Ui.button("生成参考意见", () -> {
            if (!Ui.confirm(this, "将把本份作业文本和评分细则发送给配置的 AI 服务。\n不会发送账号或作者姓名字段，但正文若自行署名仍可能包含个人信息。\n模型：" + config.get("ai.model") + "\n服务：" + config.get("ai.baseUrl") + "\n" + (config.refusalFallbackEnabled() ? "已启用服务端默认拒绝回退。" : "当前网关未附加服务端拒绝回退参数。") + "可能按服务方规则计费。是否继续？")) return;
            String previous = reference.getText();
            reference.setText("正在请求参考意见，请稍候……不会自动重试。");
            Ui.work(this, () -> {
                String text = ai.generate(service.adviceInput(actor, task.id()));
                service.saveAdvice(actor, task.id(), text, config.get("ai.model")); return text;
            }, text -> { reference.setText(text); reference.setCaretPosition(0); }, error -> {
                reference.setText(previous); Ui.error(this, error);
            });
        });
        generate.setEnabled(editable); aiPanel.add(Ui.toolbar(generate), BorderLayout.SOUTH);
        JSplitPane reading = new JSplitPane(JSplitPane.VERTICAL_SPLIT, left, aiPanel); reading.setResizeWeight(0.55);
        JPanel scoring = new JPanel(new BorderLayout(0, 10)); JPanel fields = Ui.column(); Map<Long, JTextField> inputs = new LinkedHashMap<>();
        fields.add(Ui.title("人工评分", 18)); fields.add(new JLabel("各项按区间归一化，加权总分为 100 分。")); fields.add(Box.createVerticalStrut(12));
        for (Criterion c : assignment.criteria()) {
            JTextField input = new JTextField(detail == null ? "" : detail.scores().get(c.id()).toPlainString(), 9); input.setEditable(editable); inputs.put(c.id(), input);
            Ui.field(fields, c.name() + " (" + c.weight() + "%)", input); fields.add(new JLabel("允许区间：" + c.min() + "—" + c.max()));
        }
        JLabel total = Ui.title(detail == null ? "总分：未计算" : "总分：" + detail.total(), 18);
        JButton calculate = Ui.button("计算当前分数", () -> total.setText("总分：" + service.previewScore(assignment.criteria(), scores(inputs)))); calculate.setEnabled(editable);
        fields.add(Ui.toolbar(calculate)); fields.add(total);
        JTextArea comment = Ui.area(detail == null ? "" : detail.comment(), editable);
        JPanel comments = new JPanel(new BorderLayout(0, 8)); comments.add(new JLabel("人工评语（必填）"), BorderLayout.NORTH); comments.add(new JScrollPane(comment));
        JScrollPane scoreFields = new JScrollPane(fields); scoreFields.setPreferredSize(new Dimension(420, 350));
        scoring.add(scoreFields, BorderLayout.NORTH); scoring.add(comments);
        JButton save = Ui.primary("提交人工互评", () -> {
            Map<Long, BigDecimal> values = scores(inputs); String text = comment.getText();
            BigDecimal preview = service.previewScore(assignment.criteria(), values);
            if (!Ui.confirm(this, "提交后不能重复评审或修改。\n确认本次人工评分：" + preview + " 分？")) return;
            Ui.work(this, () -> service.completeReview(actor, task.id(), values, text), result -> { Ui.info(this, "互评已保存，加权总分 " + result + "。"); dispose(); updated.run(); });
        }); save.setEnabled(editable);
        scoring.add(Ui.toolbar(save, Ui.button("关闭", this::dispose)), BorderLayout.SOUTH);
        JSplitPane body = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, reading, scoring); body.setResizeWeight(0.58); body.setDividerLocation(600); root.add(body);
        add(root); setSize(1160, 820); setMinimumSize(new Dimension(1050, 720)); setLocationRelativeTo(owner);
    }
    private Map<Long, BigDecimal> scores(Map<Long, JTextField> inputs) {
        Map<Long, BigDecimal> result = new LinkedHashMap<>();
        inputs.forEach((id, input) -> result.put(id, Validation.parseDecimal(input.getText(), "得分")));
        return result;
    }
}
