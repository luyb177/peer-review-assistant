package edu.course.peerreview.ui;

import edu.course.peerreview.ai.AdviceProvider;
import edu.course.peerreview.config.AppConfig;
import edu.course.peerreview.domain.Domain.*;
import edu.course.peerreview.service.*;
import javax.swing.*;
import java.awt.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.List;
import java.util.function.LongFunction;

public final class ReviewDialog extends JDialog {
    public ReviewDialog(Window owner, AppConfig config, PeerReviewService service, AdviceProvider ai, User actor,
                        Assignment assignment, ReviewTask task, ReviewDetail detail, Advice advice, Runnable updated) {
        this(owner,config,service,ai,actor,assignment,task,detail,advice,List.of(),List.of(),null,updated);
    }
    public ReviewDialog(Window owner,AppConfig config,PeerReviewService service,AdviceProvider ai,User actor,
                        Assignment assignment,ReviewTask task,ReviewDetail detail,Advice advice,
                        List<AttachmentInfo> submittedFiles,List<AttachmentInfo> materials,LongFunction<AttachmentDownload> downloader,Runnable updated) {
        super(owner, "互评任务 " + task.id(), ModalityType.APPLICATION_MODAL);
        java.time.LocalDateTime current = java.time.LocalDateTime.now();
        boolean ownWindow = actor.role() == Role.STUDENT && task.reviewerId() == actor.id()
                && assignment.phase() == Phase.REVIEWING && !current.isBefore(assignment.reviewOpen()) && current.isBefore(assignment.reviewDeadline());
        boolean editable = ownWindow && task.status() == TaskStatus.PENDING && detail == null;
        boolean mutable = ownWindow && task.status() == TaskStatus.COMPLETED && detail != null;
        JPanel root = new JPanel(new BorderLayout(16, 12)); root.setBorder(BorderFactory.createEmptyBorder(20, 22, 18, 22));
        JPanel header = Ui.column(); header.add(Ui.title(assignment.title(), 22));
        header.add(new JLabel("作者：" + task.author() + "    评审人：" + task.reviewer() + "    截止：" + AssignmentDialog.TIME.format(assignment.reviewDeadline())));
        header.add(new JLabel("评分和人工评语必须由学生独立填写，AI 不会替你提交。"));
        header.add(new JLabel(detail == null ? "待互评：独立填写后提交。" : mutable
                ? "已提交（版本 " + detail.version() + "）：截止前可修改或撤回；撤回后需重新评分。"
                : "只读记录：教师不可代改，作业结束或互评截止后不可修改或撤回。"));
        root.add(header, BorderLayout.NORTH);
        JPanel left = new JPanel(new BorderLayout(0, 8));left.add(new JLabel("提交正文与文档提取内容（未提取附件请另存人工查看）"),BorderLayout.NORTH);
        left.add(new JScrollPane(Ui.area(task.content().isBlank()?"本次仅提交文档，请预览或另存附件。":task.content(),false)));
        left.add(new AttachmentViewerPanel("学生提交附件（盲评隐藏原文件名，不清除文档内部署名）",submittedFiles,downloader),BorderLayout.SOUTH);
        JPanel requirements=new JPanel(new BorderLayout(0,8));requirements.add(new JScrollPane(Ui.area(assignment.description(),false)));requirements.add(new AttachmentViewerPanel("教师作业资料",materials,downloader),BorderLayout.SOUTH);
        JTabbedPane documentTabs=new JTabbedPane();documentTabs.addTab("学生提交",left);documentTabs.addTab("作业要求与资料",requirements);
        JTextArea reference = Ui.area(advice == null ? "尚未生成 AI 参考意见。可以直接完成人工互评。" : advice.text(), false);
        JPanel aiPanel = new JPanel(new BorderLayout(0, 8)); aiPanel.add(new JLabel("AI 参考阅读（不计入评分）"), BorderLayout.NORTH); aiPanel.add(new JScrollPane(reference));
        JButton generate = Ui.button("生成参考意见", () -> {
            if (!Ui.confirm(this, "将把作业文本、评分细则及成功提取的文档正文发送给配置的 AI 服务。\n扫描、加密或过长文件不在完整评审依据中；不会发送原文件名，但正文仍可能含个人信息。\n模型：" + config.get("ai.model") + "\n服务：" + config.get("ai.baseUrl") + "\n" + (config.refusalFallbackEnabled() ? "已启用服务端默认拒绝回退。" : "当前网关未附加服务端拒绝回退参数。") + "可能按服务方规则计费。是否继续？")) return;
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
        JSplitPane reading = new JSplitPane(JSplitPane.VERTICAL_SPLIT, documentTabs, aiPanel); reading.setResizeWeight(0.55);
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
        JButton save = Ui.primary(detail == null ? "提交人工互评" : "保存修改", () -> {
            Map<Long, BigDecimal> values = scores(inputs); String text = comment.getText();
            BigDecimal preview = service.previewScore(assignment.criteria(), values);
            String prompt = detail == null ? "确认提交人工评分：" : "将更新原评分并重新计算统计。确认保存：";
            if (!Ui.confirm(this, prompt + preview + " 分？\n互评截止前且作业未结束时，本人可修改或撤回。")) return;
            Ui.work(this, () -> detail == null ? service.completeReview(actor, task.id(), values, text)
                    : service.updateReview(actor, task.id(), detail.reviewId(), detail.version(), values, text),
                    result -> { Ui.info(this, detail == null ? "互评已保存，加权总分 " + result + "。" : "评分修改已保存，加权总分 " + result + "。"); dispose(); updated.run(); });
        }); save.setEnabled(editable);
        JButton edit = Ui.button("修改评分", () -> {
            inputs.values().forEach(input -> input.setEditable(true)); comment.setEditable(true);
            calculate.setEnabled(true); save.setEnabled(true); total.setText("总分：修改中，请重新计算");
        }); edit.setEnabled(mutable); edit.setVisible(detail != null);
        JButton withdraw = Ui.button("撤回互评", () -> {
            if (detail == null || !Ui.confirm(this, "撤回将删除本次人工分数和评语，并退出成绩统计。\n任务恢复为待互评，AI 参考意见保留。未保存的修改也不会保留。\n确认撤回？")) return;
            Ui.work(this, () -> { service.withdrawReview(actor, task.id(), detail.reviewId(), detail.version()); return true; },
                    done -> { Ui.info(this, "互评已撤回，任务恢复为待互评，请在截止前重新评分。"); dispose(); updated.run(); });
        }); withdraw.setEnabled(mutable); withdraw.setVisible(detail != null);
        scoring.add(Ui.toolbar(edit, save, withdraw, Ui.button("关闭", this::dispose)), BorderLayout.SOUTH);
        JSplitPane body = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, reading, scoring); body.setResizeWeight(0.58); body.setDividerLocation(600); root.add(body);
        add(root); setSize(1160, 820); setMinimumSize(new Dimension(1050, 720)); setLocationRelativeTo(owner);
    }
    private Map<Long, BigDecimal> scores(Map<Long, JTextField> inputs) {
        Map<Long, BigDecimal> result = new LinkedHashMap<>();
        inputs.forEach((id, input) -> result.put(id, Validation.parseDecimal(input.getText(), "得分")));
        return result;
    }
}
