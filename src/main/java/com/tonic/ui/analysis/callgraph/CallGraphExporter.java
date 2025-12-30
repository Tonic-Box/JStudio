package com.tonic.ui.analysis.callgraph;

import com.mxgraph.util.mxCellRenderer;
import com.mxgraph.view.mxGraph;
import com.tonic.analysis.common.MethodReference;

import com.tonic.ui.theme.JStudioTheme;

import javax.imageio.ImageIO;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.Component;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.function.Consumer;

public class CallGraphExporter {

    private static final int EXPORT_SCALE = 2;

    public void exportAsPng(Component parent, mxGraph graph, MethodReference focusMethod,
                           Consumer<String> statusCallback) {
        if (focusMethod == null) {
            statusCallback.accept("No graph to export. Build a call graph and select a method first.");
            return;
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Export Call Graph as PNG");
        chooser.setFileFilter(new FileNameExtensionFilter("PNG Images", "png"));

        String suggestedName = focusMethod.getOwner().replace('/', '_') + "_" +
                              focusMethod.getName() + "_callgraph.png";
        chooser.setSelectedFile(new File(suggestedName));

        if (chooser.showSaveDialog(parent) == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            if (!file.getName().toLowerCase().endsWith(".png")) {
                file = new File(file.getAbsolutePath() + ".png");
            }

            try {
                BufferedImage image = mxCellRenderer.createBufferedImage(
                        graph, null, EXPORT_SCALE, JStudioTheme.getBgPrimary(), true, null);

                if (image != null) {
                    ImageIO.write(image, "PNG", file);
                    statusCallback.accept("Graph exported to: " + file.getAbsolutePath());
                } else {
                    statusCallback.accept("Failed to create image - graph may be empty.");
                }
            } catch (IOException ex) {
                statusCallback.accept("Failed to export graph: " + ex.getMessage());
            }
        }
    }
}
