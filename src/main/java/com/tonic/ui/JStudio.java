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
    public static final String APP_VERSION = "18.0.0";

    public static void main(String[] args) {
        if (hasCliFlag(args)) {
            HeadlessRunner.main(stripCliFlag(args));
            return;
        }

        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");

        if (hasFlag(args, "-dev")) {
            UpdateManager.disableStartupCheck();
        }
        String[] appArgs = stripFlag(args, "-dev");

        EventQueue.invokeLater(() -> {
            try {
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
