package edu.course.peerreview.ui;

import java.lang.instrument.Instrumentation;
import java.nio.file.*;
import java.util.*;

/** Test-only instrumentation; product classes must come from java -jar. */
public final class PackagedLiveAgent {
    public static void premain(String argument, Instrumentation instrumentation) throws Exception {
        Properties options = new Properties();
        try (var reader = Files.newBufferedReader(Path.of(argument))) { options.load(reader); }
        System.setProperty("peerreview.acceptance.packaged", "true");
        System.setProperty("peerreview.acceptance.jar", options.getProperty("jar"));
        String mode = options.getProperty("mode", "--all-features");
        new Thread(() -> LiveAcceptance.main(mode.equals("--all-features")
                ? new String[]{options.getProperty("root"), mode, "--allow-ai-once"}
                : mode.equals("--resume-all-features")||mode.equals("--configuration-only")||mode.equals("--resume-supplement")
                    ? new String[]{options.getProperty("root"), mode, options.getProperty("resume"), "--allow-ai-once"}
                : mode.equals("--post-check")
                    ? new String[]{options.getProperty("root"), options.getProperty("resume"), mode}
                    : new String[]{options.getProperty("root"), mode}), "packaged-live-test").start();
    }
}
