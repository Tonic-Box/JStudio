package com.tonic.ui.live;

import com.tonic.live.AttachLauncher;
import com.tonic.service.ProjectService;
import com.tonic.ui.MainFrame;
import com.tonic.ui.core.component.ThemedJDialog;
import com.tonic.ui.core.component.ThemedJScrollPane;

import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Window;
import java.lang.management.ManagementFactory;
import java.util.List;

/**
 * "Attach to live JVM" dialog: lists local JVMs, loads the Java agent into the chosen one, and builds
 * a fresh project from its loaded classes with a progress bar. The heavy work runs on a SwingWorker so
 * the UI stays responsive while classes stream in.
 */
public final class LiveAttachDialog extends ThemedJDialog {

    private final DefaultListModel<AttachLauncher.JvmProcess> model = new DefaultListModel<>();
    private final JList<AttachLauncher.JvmProcess> list = new JList<>(model);
    private final JCheckBox includeJdk = new JCheckBox("Include JDK / bootstrap classes");
    private final JProgressBar progress = new JProgressBar(0, 100);
    private final JLabel status = new JLabel("Select a running JVM to attach to.");
    private final JButton attachButton = new JButton("Attach");

    public LiveAttachDialog(Frame owner) {
        super(owner, "Attach to Live JVM", true);
        setLayout(new BorderLayout(8, 8));

        list.setVisibleRowCount(10);
        add(new ThemedJScrollPane(list), BorderLayout.CENTER);

        JPanel north = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton refreshList = new JButton("Refresh list");
        refreshList.addActionListener(e -> reloadJvmList());
        north.add(refreshList);
        north.add(includeJdk);
        add(north, BorderLayout.NORTH);

        JPanel south = new JPanel(new BorderLayout(8, 4));
        progress.setStringPainted(true);
        south.add(status, BorderLayout.NORTH);
        south.add(progress, BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton cancel = new JButton("Close");
        cancel.addActionListener(e -> dispose());
        attachButton.addActionListener(e -> doAttach());
        buttons.add(attachButton);
        buttons.add(cancel);
        south.add(buttons, BorderLayout.SOUTH);
        add(south, BorderLayout.SOUTH);

        setPreferredSize(new Dimension(560, 380));
        pack();
        setLocationRelativeTo(owner);
        reloadJvmList();
    }

    private void reloadJvmList() {
        model.clear();
        try {
            List<AttachLauncher.JvmProcess> jvms = AttachLauncher.listJvms();
            String self = currentPid();
            for (AttachLauncher.JvmProcess p : jvms) {
                if (!p.getId().equals(self)) {
                    model.addElement(p);
                }
            }
            status.setText(model.isEmpty() ? "No attachable JVMs found." : "Select a JVM and Attach.");
        } catch (Throwable t) {
            status.setText("Failed to list JVMs: " + t.getMessage());
        }
    }

    private static String currentPid() {
        String name = ManagementFactory.getRuntimeMXBean().getName();
        int at = name.indexOf('@');
        return at > 0 ? name.substring(0, at) : "";
    }

    private void doAttach() {
        AttachLauncher.JvmProcess target = list.getSelectedValue();
        if (target == null) {
            status.setText("Select a JVM first.");
            return;
        }
        attachButton.setEnabled(false);
        progress.setIndeterminate(true);
        status.setText("Attaching to pid " + target.getId() + "...");
        final boolean withJdk = includeJdk.isSelected();

        new SwingWorker<Void, Object[]>() {
            @Override
            protected Void doInBackground() throws Exception {
                ProjectService.ProgressCallback cb = (cur, total, msg) -> publish(new Object[]{cur, total, msg});
                LiveAttachService.getInstance().attach(target.getId(), withJdk, cb);
                return null;
            }

            @Override
            protected void process(List<Object[]> chunks) {
                Object[] last = chunks.get(chunks.size() - 1);
                int cur = (int) last[0];
                int total = (int) last[1];
                progress.setIndeterminate(false);
                progress.setMaximum(Math.max(1, total));
                progress.setValue(cur);
                status.setText(last[2] + "  (" + cur + "/" + total + ")");
            }

            @Override
            protected void done() {
                try {
                    get();
                    status.setText("Attached.");
                    Window owner = getOwner();
                    if (owner instanceof MainFrame) {
                        ((MainFrame) owner).setLiveCaptureEnabled(true);
                    }
                    dispose();
                } catch (Exception e) {
                    progress.setIndeterminate(false);
                    attachButton.setEnabled(true);
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    status.setText("Attach failed: " + cause.getMessage());
                    JOptionPane.showMessageDialog(LiveAttachDialog.this,
                            cause.getMessage(), "Attach failed", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }
}
