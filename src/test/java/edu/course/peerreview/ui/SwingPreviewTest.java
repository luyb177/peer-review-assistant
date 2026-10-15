package edu.course.peerreview.ui;

import edu.course.peerreview.TestFixture;
import edu.course.peerreview.ai.AdviceProvider;
import edu.course.peerreview.config.AppConfig;
import edu.course.peerreview.domain.Domain.*;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/** 在 Swing 事件线程构建并离屏渲染真正的产品组件，不截取用户桌面。 */
class SwingPreviewTest {
    @Test void renderLoginTeacherStudentAndReviewWithoutExternalServices() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "No graphical environment; run on desktop to render Swing previews");
        TestFixture f = new TestFixture(); f.service.allocate(f.teacher, f.readyId, 2);
        var config = new AppConfig(Path.of("target/ui-preview/unused.properties"));
        AdviceProvider fake = input -> "测试替身意见，不会自动给分。";
        var student = f.students.get(0); var task = f.service.tasks(student, f.readyId).get(0); var assignment = f.service.assignment(student, f.readyId);
        Path directory = Path.of("target/ui-preview"); Files.createDirectories(directory);
        SwingUtilities.invokeAndWait(() -> {
            Ui.theme(); List<Window> windows = new ArrayList<>();
            try {
                LoginFrame login = new LoginFrame(config, f.db, fake); windows.add(login); save(login, directory.resolve("login.png"));
                WorkspaceFrame teacher = new WorkspaceFrame(config, f.db, fake, f.teacher, () -> { }); windows.add(teacher);
                teacher.render(teacher.load()); find(teacher, JTabbedPane.class).get(0).setSelectedIndex(1); save(teacher, directory.resolve("teacher.png"));
                WorkspaceFrame pupil = new WorkspaceFrame(config, f.db, fake, student, () -> { }); windows.add(pupil);
                pupil.render(pupil.load()); find(pupil, JTabbedPane.class).get(0).setSelectedIndex(2); save(pupil, directory.resolve("student.png"));
                assertTrue(find(pupil, JTable.class).stream().anyMatch(t -> t.getRowCount() == 2));
                ReviewDialog review = new ReviewDialog(pupil, config, f.service, fake, student, assignment, task, null, null, () -> { }); windows.add(review);
                assertTrue(find(review, JTextField.class).stream().allMatch(t -> t.getText().isEmpty()), "Human scores must start empty");
                save(review, directory.resolve("review.png"));
                find(review, JTextField.class).forEach(input -> input.setText("80"));
                find(review, JButton.class).stream().filter(b -> "计算当前分数".equals(b.getText())).findFirst().orElseThrow().doClick();
                assertTrue(find(review, JLabel.class).stream().anyMatch(label -> label.getText().equals("总分：80.00")));
                assertNull(f.service.reviewDetail(student, task.id()), "Preview must not persist a review");
                AssignmentDialog create = new AssignmentDialog(teacher, f.service, f.teacher, f.service.courses(f.teacher), null, () -> { }); windows.add(create);
                save(create, directory.resolve("assignment.png"));
            } catch (Exception e) { throw new RuntimeException(e); }
            finally { windows.forEach(Window::dispose); }
        });
        assertNull(f.service.reviewDetail(student, task.id()));
    }
    private void save(Window window, Path path) throws Exception {
        window.addNotify(); window.validate();
        JRootPane root = window instanceof JFrame f ? f.getRootPane() : ((JDialog) window).getRootPane();
        assertTrue(root.getWidth() > 0 && root.getHeight() > 0);
        BufferedImage image = new BufferedImage(root.getWidth(), root.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics(); try { root.printAll(graphics); } finally { graphics.dispose(); }
        ImageIO.write(image, "png", path.toFile());
    }
    private <T extends Component> List<T> find(Component component, Class<T> type) {
        List<T> result = new ArrayList<>(); if (type.isInstance(component)) result.add(type.cast(component));
        if (component instanceof Container c) for (Component child : c.getComponents()) result.addAll(find(child, type)); return result;
    }
}
