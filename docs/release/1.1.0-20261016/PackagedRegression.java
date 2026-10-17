import java.io.PrintWriter;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectPackage;
public final class PackagedRegression {
  public static void main(String[] args) {
    System.out.println("Product loaded from: " + edu.course.peerreview.App.class.getProtectionDomain().getCodeSource().getLocation());
    var listener = new SummaryGeneratingListener();
    var launcher = LauncherFactory.create();
    launcher.registerTestExecutionListeners(listener);
    launcher.execute(LauncherDiscoveryRequestBuilder.request().selectors(selectPackage("edu.course.peerreview")).build());
    var summary = listener.getSummary();
    summary.printTo(new PrintWriter(System.out, true));
    summary.printFailuresTo(new PrintWriter(System.out, true));
    if (summary.getTestsFoundCount() != 123 || summary.getTestsFailedCount() > 0 || summary.getTestsSkippedCount() > 0) System.exit(1);
  }
}
