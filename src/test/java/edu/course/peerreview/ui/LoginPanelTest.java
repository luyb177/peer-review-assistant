package edu.course.peerreview.ui;

import edu.course.peerreview.TestFixture;
import edu.course.peerreview.config.AppConfig;
import edu.course.peerreview.dao.Database;
import org.junit.jupiter.api.Test;
import javax.swing.*;
import java.awt.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

class LoginPanelTest {
    @Test void designerViewConstructsWithoutServicesAndPreservesAccessibleLabels() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            LoginPanel view = new LoginPanel();
            assertNotNull(view.getUsernameField()); assertNotNull(view.getPasswordField());
            assertEquals(0, view.getLoginButton().getActionListeners().length, "Design-time view must not run login logic");
            assertEquals(0, view.getTestConnectionButton().getActionListeners().length);
            assertEquals("登录", view.getLoginButton().getText()); assertEquals("连接配置", view.getConfigButton().getText());
            assertTrue(find(view, JLabel.class).stream().anyMatch(label -> "登录互评课堂".equals(label.getText())), "The property saved by WindowBuilder must reach the product view");
            assertTrue(find(view, JLabel.class).stream().anyMatch(label -> "用户名".equals(label.getText()) && label.getLabelFor() == view.getUsernameField()));
            assertTrue(find(view, JLabel.class).stream().anyMatch(label -> "密码".equals(label.getText()) && label.getLabelFor() == view.getPasswordField()));
            assertEquals(new Dimension(984, 582), view.getPreferredSize());
        });
    }
    @Test void realFrameBindsDesignerButtonsWithoutOpeningDatabaseAtConstruction() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless()); AtomicInteger connections = new AtomicInteger();
        Database disabled = new Database(() -> { connections.incrementAndGet(); throw new java.sql.SQLException("Must not connect in constructor"); });
        SwingUtilities.invokeAndWait(() -> {
            Ui.theme(); LoginFrame frame = new LoginFrame(new AppConfig(Path.of("target/ui-preview/unused.properties")), disabled, input -> { throw new AssertionError("AI must not run in login view"); });
            try {
                LoginPanel view = assertInstanceOf(LoginPanel.class, frame.getContentPane());
                assertSame(view.getLoginButton(), frame.getRootPane().getDefaultButton());
                assertEquals(1, view.getLoginButton().getActionListeners().length); assertEquals(1, view.getConfigButton().getActionListeners().length);
                assertEquals(1, view.getTestConnectionButton().getActionListeners().length); assertEquals(1, view.getInitializeButton().getActionListeners().length);
                assertEquals(0, connections.get());
            } finally { frame.dispose(); }
        });
    }
    @Test void actualLoginButtonAuthenticatesAndOpensTeacherWorkspaceAfterViewRefactor() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless()); TestFixture fixture = new TestFixture();
        LoginFrame frame = onEdt(() -> {
            Ui.theme(); LoginFrame login = new LoginFrame(new AppConfig(Path.of("target/ui-preview/unused.properties")), fixture.db, input -> "test-only");
            LoginPanel view = (LoginPanel) login.getContentPane(); view.getUsernameField().setText("teacher"); view.getPasswordField().setText("Teacher123!");
            login.setVisible(true); return login;
        });
        try {
            SwingUtilities.invokeLater(() -> ((LoginPanel) frame.getContentPane()).getLoginButton().doClick());
            long limit = System.nanoTime() + java.time.Duration.ofSeconds(10).toNanos(); WorkspaceFrame workspace = null;
            while (System.nanoTime() < limit) {
                workspace = onEdt(() -> java.util.Arrays.stream(Window.getWindows()).filter(w -> w instanceof WorkspaceFrame && w.isShowing() && w.isEnabled()).map(WorkspaceFrame.class::cast).findFirst().orElse(null));
                if (workspace != null) break; Thread.sleep(50);
            }
            assertNotNull(workspace, "Actual login event must open a usable workspace");
            assertFalse(onEdt(frame::isShowing)); assertTrue(onEdt(() -> ((LoginPanel) frame.getContentPane()).getPasswordField().getPassword().length == 0));
            WorkspaceFrame shown = workspace; assertTrue(onEdt(shown::getTitle).contains("教师工作台"));
        } finally { onEdt(() -> { for (Window window : Window.getWindows()) if (window instanceof WorkspaceFrame || window == frame) window.dispose(); return null; }); }
    }
    private <T> T onEdt(Callable<T> action) throws Exception { FutureTask<T> task = new FutureTask<>(action); SwingUtilities.invokeAndWait(task); return task.get(); }
    private <T extends Component> List<T> find(Component root, Class<T> type) {
        List<T> result = new ArrayList<>(); if (type.isInstance(root)) result.add(type.cast(root));
        if (root instanceof Container c) for (Component child : c.getComponents()) result.addAll(find(child, type)); return result;
    }
}
