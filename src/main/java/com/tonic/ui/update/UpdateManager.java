package com.tonic.ui.update;

import com.tonic.ui.util.Settings;

import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.ProgressMonitor;
import javax.swing.SwingWorker;
import java.awt.Desktop;
import java.net.URI;
import java.nio.file.Path;

/**
 * Coordinates JStudio's self-update: a throttled, opt-out startup check and a manual
 * "Check for Updates" action, both off the EDT. When a newer release is found the user is prompted
 * and may update (download with SHA-256 verification, then swap-and-relaunch via
 * {@link UpdateInstaller}/{@link com.tonic.cli.Updater}), skip the version, or defer.
 */
public final class UpdateManager {

    private static final long THROTTLE_MS = 24L * 60 * 60 * 1000;

    private static volatile boolean startupCheckDisabled = false;

    private final JFrame parent;
    private final UpdateChecker checker = new UpdateChecker();
    private final UpdateInstaller installer = new UpdateInstaller();

    public UpdateManager(JFrame parent) {
        this.parent = parent;
    }

    /**
     * Suppresses the automatic startup check for this session (the {@code -dev} launch flag). The
     * manual "Check for Updates" action is unaffected.
     */
    public static void disableStartupCheck() {
        startupCheckDisabled = true;
    }

    /**
     * Startup check: silently does nothing in a dev run, when disabled, or within the throttle window,
     * and only prompts for a newer, non-skipped release.
     */
    public void checkOnStartup() {
        if (startupCheckDisabled || !AppVersion.isPackaged() || !Settings.getInstance().isUpdateCheckEnabled()) {
            return;
        }
        if (System.currentTimeMillis() - Settings.getInstance().getLastUpdateCheck() < THROTTLE_MS) {
            return;
        }
        runCheck(false);
    }

    /**
     * Manual check (Help menu): ignores the throttle and the opt-out, and always reports a result.
     */
    public void checkNow() {
        if (!AppVersion.isPackaged()) {
            JOptionPane.showMessageDialog(parent,
                    "Update checks are only available in the packaged release build.",
                    "Check for Updates", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        runCheck(true);
    }

    private void runCheck(boolean manual) {
        new SwingWorker<UpdateInfo, Void>() {
            @Override
            protected UpdateInfo doInBackground() {
                return checker.fetchLatest();
            }

            @Override
            protected void done() {
                Settings.getInstance().setLastUpdateCheck(System.currentTimeMillis());
                UpdateInfo info;
                try {
                    info = get();
                } catch (Exception e) {
                    info = null;
                }
                handleResult(info, manual);
            }
        }.execute();
    }

    private void handleResult(UpdateInfo info, boolean manual) {
        if (info == null) {
            if (manual) {
                JOptionPane.showMessageDialog(parent,
                        "Couldn't check for updates (network error or rate limit).",
                        "Check for Updates", JOptionPane.WARNING_MESSAGE);
            }
            return;
        }
        int current = AppVersion.parse(AppVersion.current());
        if (info.getVersion() <= current) {
            if (manual) {
                JOptionPane.showMessageDialog(parent,
                        "You're up to date (version " + current + ").",
                        "Check for Updates", JOptionPane.INFORMATION_MESSAGE);
            }
            return;
        }
        if (!manual && info.getTag().equals(Settings.getInstance().getSkippedVersion())) {
            return;
        }
        promptUpdate(info, current);
    }

    private void promptUpdate(UpdateInfo info, int current) {
        String[] options = {"Update now", "Skip this version", "Later"};
        int choice = JOptionPane.showOptionDialog(parent,
                "Version " + info.getTag() + " is available (you have version " + current + ").\n\n"
                        + "Update now? JStudio will download it, then restart to apply.",
                "Update available", JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE,
                null, options, options[0]);
        if (choice == 0) {
            startInstall(info);
        } else if (choice == 1) {
            Settings.getInstance().setSkippedVersion(info.getTag());
        }
    }

    private void startInstall(UpdateInfo info) {
        if (info.getJarUrl() == null) {
            JOptionPane.showMessageDialog(parent,
                    "Release " + info.getTag() + " has no downloadable jar.\nOpening the release page instead.",
                    "Update", JOptionPane.WARNING_MESSAGE);
            browse(info.getReleaseUrl());
            return;
        }

        ProgressMonitor monitor = new ProgressMonitor(parent,
                "Downloading " + info.getTag() + "…", "", 0, 100);
        monitor.setMillisToDecideToPopup(0);
        monitor.setMillisToPopup(0);

        SwingWorker<Path, Void> worker = new SwingWorker<>() {
            @Override
            protected Path doInBackground() throws Exception {
                return installer.download(info, percent -> {
                    if (percent >= 0) {
                        setProgress((int) Math.min(100, percent));
                    }
                });
            }

            @Override
            protected void done() {
                monitor.close();
                Path jar;
                try {
                    jar = get();
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(parent,
                            "Update failed: " + rootMessage(e) + "\nOpening the release page instead.",
                            "Update", JOptionPane.ERROR_MESSAGE);
                    browse(info.getReleaseUrl());
                    return;
                }
                confirmRestart(info, jar);
            }
        };
        worker.addPropertyChangeListener(event -> {
            if ("progress".equals(event.getPropertyName())) {
                monitor.setProgress((Integer) event.getNewValue());
            }
        });
        worker.execute();
    }

    private void confirmRestart(UpdateInfo info, Path jar) {
        int restart = JOptionPane.showConfirmDialog(parent,
                info.getTag() + " downloaded. Restart now to finish updating?",
                "Update ready", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (restart != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            installer.applyAndRestart(jar);
            System.exit(0);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(parent,
                    "Couldn't apply the update: " + rootMessage(e),
                    "Update", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void browse(String url) {
        if (url == null || !Desktop.isDesktopSupported()) {
            return;
        }
        try {
            Desktop.getDesktop().browse(URI.create(url));
        } catch (Exception ignored) {
            // best effort
        }
    }

    private static String rootMessage(Throwable t) {
        Throwable cause = t;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        String message = cause.getMessage();
        return message != null ? message : cause.getClass().getSimpleName();
    }
}
