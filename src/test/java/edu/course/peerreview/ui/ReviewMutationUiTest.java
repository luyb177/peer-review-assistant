package edu.course.peerreview.ui;

import edu.course.peerreview.TestFixture;
import edu.course.peerreview.config.AppConfig;
import edu.course.peerreview.domain.Domain.*;
import org.junit.jupiter.api.Test;
import javax.swing.*;
import java.awt.*;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

class ReviewMutationUiTest {
    @Test void ownerMustExplicitlyEnterEditModeAndPreviewDoesNotPersist() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        TestFixture fixture = new TestFixture(); fixture.service.allocate(fixture.teacher, fixture.readyId, 1);
        User student = fixture.students.get(0); ReviewTask task = fixture.service.tasks(student, fixture.readyId).get(0);
        Assignment assignment = fixture.service.assignment(student, fixture.readyId);
        fixture.service.completeReview(student, task.id(), fixture.scores(assignment, "80"), "原人工评语");
        ReviewDetail detail = fixture.service.reviewDetail(student, task.id());
        SwingUtilities.invokeAndWait(() -> {
            Ui.theme(); ReviewDialog dialog = dialog(fixture, student, assignment, fixture.service.task(student, task.id()), detail);
            try {
                assertFalse(button(dialog, "保存修改").isEnabled());
                assertTrue(button(dialog, "修改评分").isEnabled()); assertTrue(button(dialog, "撤回互评").isEnabled());
                assertTrue(find(dialog, JTextField.class).stream().noneMatch(JTextField::isEditable));
                button(dialog, "修改评分").doClick();
                assertTrue(button(dialog, "保存修改").isEnabled());
                assertTrue(find(dialog, JTextField.class).stream().allMatch(JTextField::isEditable));
                find(dialog, JTextField.class).forEach(input -> input.setText("60"));
                button(dialog, "计算当前分数").doClick();
                assertTrue(find(dialog, JLabel.class).stream().anyMatch(label -> "总分：60.00".equals(label.getText())));
                assertEquals(detail, fixture.service.reviewDetail(student, task.id()));
            } finally { dialog.dispose(); }
        });
    }
    @Test void teacherClosedExpiredAndNonOwnerRemainReadOnly() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        TestFixture fixture = new TestFixture(); fixture.service.allocate(fixture.teacher, fixture.readyId, 1);
        User student = fixture.students.get(0); ReviewTask task = fixture.service.tasks(student, fixture.readyId).get(0);
        Assignment a = fixture.service.assignment(student, fixture.readyId);
        fixture.service.completeReview(student, task.id(), fixture.scores(a, "80"), "原人工评语");
        ReviewTask completed = fixture.service.task(student, task.id()); ReviewDetail detail = fixture.service.reviewDetail(student, task.id());
        Assignment closed = new Assignment(a.id(), a.courseId(), a.title(), a.description(), a.reviewOpen(), a.reviewDeadline(), a.blind(), Phase.CLOSED, a.criteria());
        Assignment expired = new Assignment(a.id(), a.courseId(), a.title(), a.description(), a.reviewOpen(), LocalDateTime.now().minusSeconds(1), a.blind(), Phase.REVIEWING, a.criteria());
        SwingUtilities.invokeAndWait(() -> {
            Ui.theme();
            List<ReviewDialog> dialogs = List.of(dialog(fixture, fixture.teacher, a, completed, detail), dialog(fixture, student, closed, completed, detail),
                    dialog(fixture, student, expired, completed, detail), dialog(fixture, fixture.students.get(1), a, completed, detail));
            try {
                for (ReviewDialog d : dialogs) {
                    assertFalse(button(d, "修改评分").isEnabled()); assertFalse(button(d, "撤回互评").isEnabled());
                    assertFalse(button(d, "保存修改").isEnabled()); assertTrue(find(d, JTextField.class).stream().noneMatch(JTextField::isEditable));
                }
            } finally { dialogs.forEach(Window::dispose); }
        });
    }
    @Test void withdrawnTaskReturnsToBlankManualEntryWithoutMutationActions() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        TestFixture fixture = new TestFixture(); fixture.service.allocate(fixture.teacher, fixture.readyId, 1);
        User student = fixture.students.get(0); Assignment a = fixture.service.assignment(student, fixture.readyId); ReviewTask task = fixture.service.tasks(student, fixture.readyId).get(0);
        fixture.service.completeReview(student, task.id(), fixture.scores(a, "80"), "原人工评语");
        ReviewDetail detail = fixture.service.reviewDetail(student, task.id()); fixture.service.withdrawReview(student, task.id(), detail.reviewId(), detail.version());
        SwingUtilities.invokeAndWait(() -> {
            Ui.theme(); ReviewDialog d = dialog(fixture, student, a, fixture.service.task(student, task.id()), null);
            try {
                assertTrue(button(d, "提交人工互评").isEnabled());
                assertFalse(button(d, "修改评分").isVisible()); assertFalse(button(d, "撤回互评").isVisible());
                assertTrue(find(d, JTextField.class).stream().allMatch(input -> input.isEditable() && input.getText().isEmpty()));
            } finally { d.dispose(); }
        });
    }
    @Test void openedProductDialogsSaveWithdrawAndResubmitThroughRealEvents() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        TestFixture f = new TestFixture(); f.service.allocate(f.teacher, f.readyId, 1);
        User student = f.students.get(0); Assignment assignment = f.service.assignment(student, f.readyId); ReviewTask task = f.service.tasks(student, f.readyId).get(0);
        f.service.completeReview(student, task.id(), f.scores(assignment, "80"), "原人工评语");
        ReviewDetail original = f.service.reviewDetail(student, task.id());
        List<Window> opened = new ArrayList<>();
        try {
            ReviewDialog edit = onEdt(() -> { Ui.theme(); return dialog(f, student, assignment, f.service.task(student, task.id()), original); });
            opened.add(edit); show(edit); press(edit, "修改评分");
            onEdt(() -> { find(edit, JTextField.class).forEach(t -> t.setText("60")); find(edit, JTextArea.class).stream().filter(JTextArea::isEditable).forEach(t -> t.setText("窗口人工修改的评语")); return null; });
            snapshot(edit, "review-edit.png");
            press(edit, "保存修改"); respond(edit, "请确认", "确认保存"); respond(edit, "课堂同伴互评", "评分修改已保存"); await(() -> !onEdtUnchecked(edit::isShowing));
            ReviewDetail latest = f.service.reviewDetail(student, task.id()); assertEquals(new java.math.BigDecimal("60.00"), latest.total()); assertEquals(2, latest.version());
            ReviewDialog stale = onEdt(() -> dialog(f, student, assignment, f.service.task(student, task.id()), original)); opened.add(stale); show(stale);
            press(stale, "撤回互评"); respond(stale, "请确认", "确认撤回"); respond(stale, "操作提示", "刷新");
            assertTrue(onEdt(stale::isEnabled)); assertEquals(latest, f.service.reviewDetail(student, task.id())); onEdt(() -> { stale.dispose(); return null; });
            ReviewDialog withdrawal = onEdt(() -> dialog(f, student, assignment, f.service.task(student, task.id()), latest)); opened.add(withdrawal); show(withdrawal);
            snapshot(withdrawal, "review-saved.png");
            press(withdrawal, "撤回互评"); respond(withdrawal, "请确认", "确认撤回"); respond(withdrawal, "课堂同伴互评", "互评已撤回"); await(() -> !onEdtUnchecked(withdrawal::isShowing));
            assertNull(f.service.reviewDetail(student, task.id())); assertEquals(TaskStatus.PENDING, f.service.task(student, task.id()).status());
            ReviewDialog again = onEdt(() -> dialog(f, student, assignment, f.service.task(student, task.id()), null)); opened.add(again); show(again);
            assertTrue(onEdt(() -> find(again, JTextField.class).stream().allMatch(t -> t.getText().isEmpty())));
            snapshot(again, "review-withdrawn.png");
            onEdt(() -> { find(again, JTextField.class).forEach(t -> t.setText("70")); find(again, JTextArea.class).stream().filter(JTextArea::isEditable).forEach(t -> t.setText("撤回后重新独立填写")); return null; });
            press(again, "提交人工互评"); respond(again, "请确认", "确认提交"); respond(again, "课堂同伴互评", "互评已保存"); await(() -> !onEdtUnchecked(again::isShowing));
            ReviewDetail replacement = f.service.reviewDetail(student, task.id());
            assertEquals(new java.math.BigDecimal("70.00"), replacement.total()); assertNotEquals(original.reviewId(), replacement.reviewId());
        } finally { onEdt(() -> { for (Window w : opened) { for (Window child : w.getOwnedWindows()) child.dispose(); w.dispose(); } return null; }); }
    }
    private void snapshot(ReviewDialog dialog, String filename) throws Exception {
        java.awt.image.BufferedImage image = onEdt(() -> {
            JRootPane pane = dialog.getRootPane(); var capture = new java.awt.image.BufferedImage(pane.getWidth(), pane.getHeight(), java.awt.image.BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = capture.createGraphics(); try { pane.printAll(graphics); } finally { graphics.dispose(); } return capture;
        });
        var directory = Path.of("target/ui-preview"); java.nio.file.Files.createDirectories(directory); javax.imageio.ImageIO.write(image, "png", directory.resolve(filename).toFile());
    }
    private <T> T onEdt(java.util.concurrent.Callable<T> action) throws Exception {
        java.util.concurrent.FutureTask<T> task = new java.util.concurrent.FutureTask<>(action); SwingUtilities.invokeAndWait(task); return task.get();
    }
    private <T> T onEdtUnchecked(java.util.concurrent.Callable<T> action) { try { return onEdt(action); } catch (Exception e) { throw new IllegalStateException(e); } }
    private void await(java.util.function.BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + java.time.Duration.ofSeconds(10).toNanos();
        while (System.nanoTime() < deadline) { if (condition.getAsBoolean()) return; Thread.sleep(30); }
        fail("Timed out waiting for product UI event");
    }
    private void show(ReviewDialog dialog) throws Exception { SwingUtilities.invokeLater(() -> dialog.setVisible(true)); await(() -> onEdtUnchecked(dialog::isShowing)); }
    private void press(ReviewDialog dialog, String text) throws Exception {
        JButton b = onEdt(() -> button(dialog, text)); assertTrue(onEdt(b::isEnabled)); SwingUtilities.invokeLater(b::doClick);
    }
    private void respond(ReviewDialog owner, String title, String expected) throws Exception {
        await(() -> onEdtUnchecked(() -> Arrays.stream(owner.getOwnedWindows()).anyMatch(w -> w instanceof JDialog d && d.isShowing() && d.getTitle().equals(title))));
        JDialog popup = onEdt(() -> Arrays.stream(owner.getOwnedWindows()).filter(w -> w instanceof JDialog d && d.isShowing() && d.getTitle().equals(title)).map(JDialog.class::cast).findFirst().orElseThrow());
        assertTrue(onEdt(() -> String.valueOf(find(popup, JOptionPane.class).get(0).getMessage()).contains(expected)));
        JButton accept = onEdt(() -> popup.getRootPane().getDefaultButton()); SwingUtilities.invokeLater(accept::doClick);
        await(() -> !onEdtUnchecked(popup::isShowing));
    }
    private ReviewDialog dialog(TestFixture f, User actor, Assignment a, ReviewTask task, ReviewDetail detail) {
        return new ReviewDialog(null, new AppConfig(Path.of("target/ui-preview/unused.properties")), f.service, input -> "test-only", actor, a, task, detail, null, () -> { });
    }
    private JButton button(Component root, String text) { return find(root, JButton.class).stream().filter(b -> text.equals(b.getText())).findFirst().orElseThrow(); }
    private <T extends Component> List<T> find(Component root, Class<T> type) {
        List<T> result = new ArrayList<>(); if (type.isInstance(root)) result.add(type.cast(root));
        if (root instanceof Container c) for (Component child : c.getComponents()) result.addAll(find(child, type)); return result;
    }
}
