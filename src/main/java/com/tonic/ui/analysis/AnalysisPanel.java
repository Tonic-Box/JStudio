package com.tonic.ui.analysis;

import com.tonic.ui.core.component.ThemedJPanel;
import com.tonic.model.ProjectModel;
import com.tonic.ui.theme.JStudioTheme;
import lombok.Getter;

import javax.swing.JTabbedPane;
import java.awt.BorderLayout;

public class AnalysisPanel extends ThemedJPanel {

    private final JTabbedPane tabbedPane;
    @Getter
    private final SearchPanel searchPanel;
    @Getter
    private final StringsPanel stringsPanel;
    @Getter
    private final SimilarityPanel similarityPanel;
    @Getter
    private final SimulationPanel simulationPanel;

    public AnalysisPanel(ProjectModel project) {
        super(BackgroundStyle.SECONDARY, new BorderLayout());

        tabbedPane = new JTabbedPane(JTabbedPane.TOP);
        tabbedPane.setBackground(JStudioTheme.getBgSecondary());
        tabbedPane.setForeground(JStudioTheme.getTextPrimary());
        tabbedPane.setBorder(null);

        // Create panels
        searchPanel = new SearchPanel(project);
        stringsPanel = new StringsPanel(project);
        similarityPanel = new SimilarityPanel(project);
        simulationPanel = new SimulationPanel(project);

        // Add tabs
        tabbedPane.addTab("Similarity", similarityPanel);
        tabbedPane.addTab("Search", searchPanel);
        tabbedPane.addTab("Strings", stringsPanel);
        tabbedPane.addTab("Code Analysis", simulationPanel);

        add(tabbedPane, BorderLayout.CENTER);
    }

    public void refresh() {
        searchPanel.refresh();
        stringsPanel.refresh();
        similarityPanel.refresh();
        simulationPanel.refresh();
    }

    public void showSearch() { tabbedPane.setSelectedComponent(searchPanel); }
    public void showStrings() { tabbedPane.setSelectedComponent(stringsPanel); }
    public void showSimilarity() { tabbedPane.setSelectedComponent(similarityPanel); }
    public void showSimulation() { tabbedPane.setSelectedComponent(simulationPanel); }
}
