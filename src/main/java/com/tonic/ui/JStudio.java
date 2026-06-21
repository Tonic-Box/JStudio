package com.tonic.ui;

import com.formdev.flatlaf.FlatDarkLaf;
import com.tonic.cli.HeadlessRunner;
import com.tonic.ui.theme.ThemeManager;
import com.tonic.ui.update.UpdateManager;
import com.tonic.ui.util.KeyboardShortcuts;
import com.tonic.util.Settings;
import java.awt.EventQueue;
import java.util.Arrays;

/**
 * JStudio - Java Reverse Engineering Suite
 * <p>
 * A professional reverse engineering and analysis tool for Java bytecode,
 * featuring decompilation, SSA IR visualization, call graph analysis,
 * and bytecode transformation capabilities.
 */
public class JStudio {

    public static final String APP_NAME = "JStudio";
    public static final String APP_VERSION = "21.0.0";

    public static void main(String[] args) {
        if (hasCliFlag(args)) {
            HeadlessRunner.main(stripCliFlag(args));
            return;
        }

        System.setProperty("sun.java2d.d3d", "false");
        System.setProperty("sun.java2d.noddraw", "true");

        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");

        installCloseDiagnostics();

        if (hasFlag(args, "-dev")) {
            UpdateManager.disableStartupCheck();
        }
        String[] appArgs = stripFlag(args, "-dev");

        EventQueue.invokeLater(() -> {
            try {
                installEdtExceptionLogger();
                FlatDarkLaf.setup();

                String savedTheme = Settings.getInstance().getTheme();
                ThemeManager.getInstance().setTheme(savedTheme);

                MainFrame frame = new MainFrame();

                KeyboardShortcuts.register(frame);

                frame.setVisible(true);

                if (appArgs.length > 0) {
                    frame.openFile(appArgs[0]);
                }

            } catch (Exception e) {
                e.printStackTrace();
                System.err.println("Failed to initialize JStudio: " + e.getMessage());
                System.exit(1);
            }
        });
    }

    /**
     * Diagnostic for the recurring silent close. Probes append to {@code ~/.jstudio/close-diagnostics.log}: a
     * {@code checkExit} SecurityManager (any {@code System.exit}/{@code halt}), a default uncaught-exception handler,
     * a shutdown-hook thread dump, and a STARTUP marker recording pid + the ACTIVE Java2D pipeline (so we can confirm
     * {@code sun.java2d.d3d=false} actually took effect in this build vs a stale one).
     */
    private static void installCloseDiagnostics() {
        try {
            System.setSecurityManager(new SecurityManager() {
                @Override
                public void checkPermission(java.security.Permission perm) {
                }

                @Override
                public void checkPermission(java.security.Permission perm, Object context) {
                }

                @Override
                public void checkExit(int status) {
                    appendDiag("exit/halt(" + status + ") on thread \"" + Thread.currentThread().getName() + "\"",
                            stackText(Thread.currentThread().getStackTrace()));
                }
            });
        } catch (Throwable t) {
            System.err.println("installCloseDiagnostics: SecurityManager not set: " + t);
        }

        Thread.setDefaultUncaughtExceptionHandler((thread, error) ->
                appendDiag("UNCAUGHT on thread \"" + thread.getName() + "\"", throwableText(error)));

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            StringBuilder sb = new StringBuilder();
            Thread.getAllStackTraces().forEach((t, st) ->
                    sb.append("Thread \"").append(t.getName()).append("\" ").append(t.getState())
                    .append(t.isDaemon() ? " (daemon)" : "").append('\n').append(stackText(st))
            );
            appendDiag("JVM shutdown - live thread dump", sb.toString());
        }, "close-diagnostics-shutdown"));

        String pipeline;
        try {
            pipeline = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment()
                    .getDefaultScreenDevice().getDefaultConfiguration().getClass().getName();
        } catch (Throwable t) {
            pipeline = "unknown (" + t + ")";
        }
        appendDiag("STARTUP pid=" + ProcessHandle.current().pid()
                + " d3d=" + System.getProperty("sun.java2d.d3d")
                + " noddraw=" + System.getProperty("sun.java2d.noddraw")
                + " gc=" + pipeline, "");
    }

    /** Wraps EDT event dispatch to log (and re-throw) any uncaught exception, capturing a silent EDT death. */
    private static void installEdtExceptionLogger() {
        try {
            java.awt.Toolkit.getDefaultToolkit().getSystemEventQueue().push(new java.awt.EventQueue() {
                @Override
                protected void dispatchEvent(java.awt.AWTEvent event) {
                    try {
                        super.dispatchEvent(event);
                    } catch (Throwable t) {
                        appendDiag("EDT exception dispatching " + event.getClass().getName(), throwableText(t));
                        throw t;
                    }
                }
            });
        } catch (Throwable t) {
            System.err.println("installEdtExceptionLogger: not installed: " + t);
        }
    }

    private static String stackText(StackTraceElement[] stack) {
        StringBuilder sb = new StringBuilder();
        for (StackTraceElement e : stack) {
            sb.append("    at ").append(e).append('\n');
        }
        return sb.toString();
    }

    private static String throwableText(Throwable t) {
        java.io.StringWriter sw = new java.io.StringWriter();
        t.printStackTrace(new java.io.PrintWriter(sw));
        return sw.toString();
    }

    private static synchronized void appendDiag(String header, String body) {
        try {
            java.nio.file.Path log = java.nio.file.Paths.get(
                    System.getProperty("user.home"), ".jstudio", "close-diagnostics.log");
            java.nio.file.Files.createDirectories(log.getParent());
            String entry = "\n=== " + new java.util.Date() + " | " + header + " ===\n" + body;
            java.nio.file.Files.write(log, entry.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (Throwable ignored) {
        }
    }

    private static boolean hasCliFlag(String[] args) {
        for (String arg : args) {
            if ("--cli".equals(arg)) {
                return true;
            }
        }
        return false;
    }

    private static String[] stripCliFlag(String[] args) {
        return stripFlag(args, "--cli");
    }

    private static boolean hasFlag(String[] args, String flag) {
        for (String arg : args) {
            if (flag.equals(arg)) {
                return true;
            }
        }
        return false;
    }

    private static String[] stripFlag(String[] args, String flag) {
        return Arrays.stream(args)
            .filter(arg -> !flag.equals(arg))
            .toArray(String[]::new);
    }
}
