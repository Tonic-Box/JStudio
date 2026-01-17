package com.tonic.ui.analysis;

import com.tonic.ui.core.component.ThemedJPanel;
import com.tonic.ui.model.ProjectModel;
import com.tonic.ui.theme.JStudioTheme;
import lombok.Getter;

import javax.swing.JTabbedPane;
import java.awt.BorderLayout;

public class AnalysisPanel extends ThemedJPanel {

    private final JTabbedPane tabbedPane;
    @Getter
    private final DependencyPanel dependencyPanel;
    @Getter
    private final SearchPanel searchPanel;
    @Getter
    private final StringsPanel stringsPanel;
    @Getter
    private final DataFlowPanel dataFlowPanel;
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
        dependencyPanel = new DependencyPanel(project);
        searchPanel = new SearchPanel(project);
        stringsPanel = new StringsPanel(project);
        dataFlowPanel = new DataFlowPanel(project);
        similarityPanel = new SimilarityPanel(project);
        simulationPanel = new SimulationPanel(project);

        // Add tabs
        tabbedPane.addTab("Dependencies", dependencyPanel);
        tabbedPane.addTab("Data Flow", dataFlowPanel);
        tabbedPane.addTab("Similarity", similarityPanel);
        tabbedPane.addTab("Search", searchPanel);
        tabbedPane.addTab("Strings", stringsPanel);
        tabbedPane.addTab("Simulation", simulationPanel);

        add(tabbedPane, BorderLayout.CENTER);
    }

    public void refresh() {
        dependencyPanel.refresh();
        searchPanel.refresh();
        stringsPanel.refresh();
        dataFlowPanel.refresh();
        similarityPanel.refresh();
        simulationPanel.refresh();
    }

    public void showDependencies() { tabbedPane.setSelectedComponent(dependencyPanel); }
    public void showSearch() { tabbedPane.setSelectedComponent(searchPanel); }
    public void showStrings() { tabbedPane.setSelectedComponent(stringsPanel); }
    public void showDataFlow() { tabbedPane.setSelectedComponent(dataFlowPanel); }
    public void showSimilarity() { tabbedPane.setSelectedComponent(similarityPanel); }
    public void showSimulation() { tabbedPane.setSelectedComponent(simulationPanel); }
}
