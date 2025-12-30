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

    /**
     * Refresh all analysis views.
     */
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

    /**
     * Get the call graph panel.
     */
    public CallGraphPanel getCallGraphPanel() {
        return callGraphPanel;
    }

    /**
     * Get the dependency panel.
     */
    public DependencyPanel getDependencyPanel() {
        return dependencyPanel;
    }

    /**
     * Get the search panel.
     */
    public SearchPanel getSearchPanel() {
        return searchPanel;
    }

    /**
     * Select the call graph tab.
     */
    public void showCallGraph() {
        tabbedPane.setSelectedComponent(callGraphPanel);
    }

    /**
     * Select the dependency tab.
     */
    public void showDependencies() {
        tabbedPane.setSelectedComponent(dependencyPanel);
    }

    /**
     * Select the search tab.
     */
    public void showSearch() {
        tabbedPane.setSelectedComponent(searchPanel);
    }

    /**
     * Get the strings panel.
     */
    public StringsPanel getStringsPanel() {
        return stringsPanel;
    }

    /**
     * Select the strings tab.
     */
    public void showStrings() {
        tabbedPane.setSelectedComponent(stringsPanel);
    }

    /**
     * Get the usages panel.
     */
    public UsagesPanel getUsagesPanel() {
        return usagesPanel;
    }

    /**
     * Select the find usages tab.
     */
    public void showUsages() {
        tabbedPane.setSelectedComponent(usagesPanel);
    }

    /**
     * Show the find usages tab and search for a term.
     */
    public void findUsages(String term, String type) {
        tabbedPane.setSelectedComponent(usagesPanel);
        usagesPanel.searchFor(term, type);
    }

    /**
     * Get the cross-references panel.
     */
    public XrefPanel getXrefPanel() {
        return xrefPanel;
    }

    /**
     * Select the cross-references tab.
     */
    public void showXrefs() {
        tabbedPane.setSelectedComponent(xrefPanel);
    }

    /**
     * Show cross-references for a specific class.
     */
    public void showXrefsForClass(String className) {
        tabbedPane.setSelectedComponent(xrefPanel);
        xrefPanel.showXrefsForClass(className);
    }

    /**
     * Show cross-references for a specific method.
     */
    public void showXrefsForMethod(String className, String methodName, String methodDesc) {
        tabbedPane.setSelectedComponent(xrefPanel);
        xrefPanel.showXrefsForMethod(className, methodName, methodDesc);
    }

    /**
     * Show cross-references for a specific field.
     */
    public void showXrefsForField(String className, String fieldName, String fieldDesc) {
        tabbedPane.setSelectedComponent(xrefPanel);
        xrefPanel.showXrefsForField(className, fieldName, fieldDesc);
    }

    /**
     * Get the data flow panel.
     */
    public DataFlowPanel getDataFlowPanel() {
        return dataFlowPanel;
    }

    /**
     * Select the data flow tab.
     */
    public void showDataFlow() {
        tabbedPane.setSelectedComponent(dataFlowPanel);
    }

    /**
     * Get the similarity panel.
     */
    public SimilarityPanel getSimilarityPanel() {
        return similarityPanel;
    }

    /**
     * Select the similarity tab.
     */
    public void showSimilarity() {
        tabbedPane.setSelectedComponent(similarityPanel);
    }

    /**
     * Get the comments panel.
     */
    public CommentsPanel getCommentsPanel() {
        return commentsPanel;
    }

    /**
     * Select the comments tab.
     */
    public void showComments() {
        tabbedPane.setSelectedComponent(commentsPanel);
    }

    /**
     * Get the bookmarks panel.
     */
    public BookmarksPanel getBookmarksPanel() {
        return bookmarksPanel;
    }

    /**
     * Select the bookmarks tab.
     */
    public void showBookmarks() {
        tabbedPane.setSelectedComponent(bookmarksPanel);
    }

    /**
     * Get the simulation panel.
     */
    public SimulationPanel getSimulationPanel() {
        return simulationPanel;
    }

    /**
     * Select the simulation tab.
     */
    public void showSimulation() {
        tabbedPane.setSelectedComponent(simulationPanel);
    }
}
