package com.tonic.ui.analysis;

import com.tonic.ui.core.component.ThemedJPanel;
import com.tonic.model.ProjectModel;
import com.tonic.ui.theme.JStudioTheme;
import lombok.Getter;

import javax.swing.JTabbedPane;
import java.awt.BorderLayout;

/** The analysis tool window: similarity, search, strings and code-analysis tabs over one project. */
public class AnalysisPanel extends ThemedJPanel
{

    private final JTabbedPane tabbedPane;
    @Getter
    private final SearchPanel searchPanel;
    @Getter
    private final StringsPanel stringsPanel;
    @Getter
    private final SimilarityPanel similarityPanel;
    @Getter
    private final SimulationPanel simulationPanel;

    /**
     * Builds the four tabs.
     *
     * @param project the project every tab analyzes
     * @param editorSelection what the editor has open, for the Code Analysis tab's Analyze Current
     */
    public AnalysisPanel(ProjectModel project, SimulationPanel.EditorSelection editorSelection)
    {
        super(BackgroundStyle.SECONDARY, new BorderLayout());

        tabbedPane = new JTabbedPane(JTabbedPane.TOP);
        tabbedPane.setBackground(JStudioTheme.getBgSecondary());
        tabbedPane.setForeground(JStudioTheme.getTextPrimary());
        tabbedPane.setBorder(null);

        searchPanel = new SearchPanel(project);
        stringsPanel = new StringsPanel(project);
        similarityPanel = new SimilarityPanel(project);
        simulationPanel = new SimulationPanel(project, editorSelection);

        tabbedPane.addTab("Similarity", similarityPanel);
        tabbedPane.addTab("Search", searchPanel);
        tabbedPane.addTab("Strings", stringsPanel);
        tabbedPane.addTab("Code Analysis", simulationPanel);

        add(tabbedPane, BorderLayout.CENTER);
    }

    /** Refreshes every tab. */
    public void refresh()
    {
        searchPanel.refresh();
        stringsPanel.refresh();
        similarityPanel.refresh();
        simulationPanel.refresh();
    }

    /** Brings the search tab forward. */
    public void showSearch()
    {
        tabbedPane.setSelectedComponent(searchPanel);
    }

    /** Brings the strings tab forward. */
    public void showStrings()
    {
        tabbedPane.setSelectedComponent(stringsPanel);
    }

    /** Brings the similarity tab forward. */
    public void showSimilarity()
    {
        tabbedPane.setSelectedComponent(similarityPanel);
    }

    /** Brings the code-analysis tab forward. */
    public void showSimulation()
    {
        tabbedPane.setSelectedComponent(simulationPanel);
    }
}
