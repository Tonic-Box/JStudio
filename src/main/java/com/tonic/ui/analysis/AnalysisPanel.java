package com.tonic.ui.analysis;

import com.tonic.ui.core.component.ThemedJPanel;
import com.tonic.ui.model.ProjectModel;
import com.tonic.ui.theme.JStudioTheme;

import javax.swing.JTabbedPane;
import java.awt.BorderLayout;

public class AnalysisPanel extends ThemedJPanel {

    private final ProjectModel project;
    private final JTabbedPane tabbedPane;

    private CallGraphPanel callGraphPanel;
    private DependencyPanel dependencyPanel;
    private SearchPanel searchPanel;
    private StringsPanel stringsPanel;
    private UsagesPanel usagesPanel;
    private XrefPanel xrefPanel;
    private DataFlowPanel dataFlowPanel;
    private SimilarityPanel similarityPanel;
    private CommentsPanel commentsPanel;
    private BookmarksPanel bookmarksPanel;
    private SimulationPanel simulationPanel;

    public AnalysisPanel(ProjectModel project) {
        super(BackgroundStyle.SECONDARY, new BorderLayout());
        this.project = project;

        tabbedPane = new JTabbedPane(JTabbedPane.TOP);
        tabbedPane.setBackground(JStudioTheme.getBgSecondary());
        tabbedPane.setForeground(JStudioTheme.getTextPrimary());
        tabbedPane.setBorder(null);

        // Create panels
        callGraphPanel = new CallGraphPanel(project);
        dependencyPanel = new DependencyPanel(project);
        searchPanel = new SearchPanel(project);
        stringsPanel = new StringsPanel(project);
        usagesPanel = new UsagesPanel(project);
        xrefPanel = new XrefPanel(project);
        dataFlowPanel = new DataFlowPanel(project);
        similarityPanel = new SimilarityPanel(project);
        commentsPanel = new CommentsPanel(project);
        bookmarksPanel = new BookmarksPanel(project);
        simulationPanel = new SimulationPanel(project);

        // Add tabs
        tabbedPane.addTab("Call Graph", callGraphPanel);
        tabbedPane.addTab("Dependencies", dependencyPanel);
        tabbedPane.addTab("Cross-Refs", xrefPanel);
        tabbedPane.addTab("Data Flow", dataFlowPanel);
        tabbedPane.addTab("Similarity", similarityPanel);
        tabbedPane.addTab("Search", searchPanel);
        tabbedPane.addTab("Strings", stringsPanel);
        tabbedPane.addTab("Find Usages", usagesPanel);
        tabbedPane.addTab("Bookmarks", bookmarksPanel);
        tabbedPane.addTab("Comments", commentsPanel);
        tabbedPane.addTab("Simulation", simulationPanel);

        add(tabbedPane, BorderLayout.CENTER);
    }

    public void refresh() {
        callGraphPanel.refresh();
        dependencyPanel.refresh();
        searchPanel.refresh();
        stringsPanel.refresh();
        usagesPanel.refresh();
        xrefPanel.refresh();
        dataFlowPanel.refresh();
        similarityPanel.refresh();
        commentsPanel.refresh();
        bookmarksPanel.refresh();
        simulationPanel.refresh();
    }

    public CallGraphPanel getCallGraphPanel() { return callGraphPanel; }
    public DependencyPanel getDependencyPanel() { return dependencyPanel; }
    public SearchPanel getSearchPanel() { return searchPanel; }
    public StringsPanel getStringsPanel() { return stringsPanel; }
    public UsagesPanel getUsagesPanel() { return usagesPanel; }
    public XrefPanel getXrefPanel() { return xrefPanel; }
    public DataFlowPanel getDataFlowPanel() { return dataFlowPanel; }
    public SimilarityPanel getSimilarityPanel() { return similarityPanel; }
    public CommentsPanel getCommentsPanel() { return commentsPanel; }
    public BookmarksPanel getBookmarksPanel() { return bookmarksPanel; }
    public SimulationPanel getSimulationPanel() { return simulationPanel; }

    public void showCallGraph() { tabbedPane.setSelectedComponent(callGraphPanel); }
    public void showDependencies() { tabbedPane.setSelectedComponent(dependencyPanel); }
    public void showSearch() { tabbedPane.setSelectedComponent(searchPanel); }
    public void showStrings() { tabbedPane.setSelectedComponent(stringsPanel); }
    public void showUsages() { tabbedPane.setSelectedComponent(usagesPanel); }
    public void showXrefs() { tabbedPane.setSelectedComponent(xrefPanel); }
    public void showDataFlow() { tabbedPane.setSelectedComponent(dataFlowPanel); }
    public void showSimilarity() { tabbedPane.setSelectedComponent(similarityPanel); }
    public void showComments() { tabbedPane.setSelectedComponent(commentsPanel); }
    public void showBookmarks() { tabbedPane.setSelectedComponent(bookmarksPanel); }
    public void showSimulation() { tabbedPane.setSelectedComponent(simulationPanel); }

    public void findUsages(String term, String type) {
        showUsages();
        usagesPanel.searchFor(term, type);
    }

    public void showXrefsForClass(String className) {
        showXrefs();
        xrefPanel.showXrefsForClass(className);
    }

    public void showXrefsForMethod(String className, String methodName, String methodDesc) {
        showXrefs();
        xrefPanel.showXrefsForMethod(className, methodName, methodDesc);
    }

    public void showXrefsForField(String className, String fieldName, String fieldDesc) {
        showXrefs();
        xrefPanel.showXrefsForField(className, fieldName, fieldDesc);
    }
}
