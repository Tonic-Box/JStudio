package com.tonic.ui.core.component;

import com.tonic.ui.core.constants.UIConstants;
import com.tonic.ui.theme.JStudioTheme;
import com.tonic.ui.theme.Theme;
import com.tonic.ui.theme.ThemeManager;

import javax.swing.JTable;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableModel;

public class ThemedJTable extends JTable implements ThemeManager.ThemeChangeListener {

    public ThemedJTable() {
        super();
        initialize();
    }

    public ThemedJTable(TableModel dm) {
        super(dm);
        initialize();
    }

    public ThemedJTable(int numRows, int numColumns) {
        super(numRows, numColumns);
        initialize();
    }

    public ThemedJTable(Object[][] rowData, Object[] columnNames) {
        super(rowData, columnNames);
        initialize();
    }

    private void initialize() {
        applyTheme();
        setRowHeight(UIConstants.TABLE_ROW_HEIGHT);
        setShowGrid(false);
        setIntercellSpacing(new java.awt.Dimension(0, 0));
        setFillsViewportHeight(true);
        ThemeManager.getInstance().addThemeChangeListener(this);
    }

    @Override
    public void onThemeChanged(Theme newTheme) {
        applyTheme();
        repaint();
        if (getTableHeader() != null) {
            getTableHeader().repaint();
        }
    }

    protected void applyTheme() {
        setBackground(JStudioTheme.getBgSecondary());
        setForeground(JStudioTheme.getTextPrimary());
        setSelectionBackground(JStudioTheme.getSelection());
        setSelectionForeground(JStudioTheme.getTextPrimary());
        setGridColor(JStudioTheme.getBorder());
        setFont(JStudioTheme.getCodeFont(UIConstants.FONT_SIZE_CODE));

        JTableHeader header = getTableHeader();
        if (header != null) {
            header.setBackground(JStudioTheme.getBgTertiary());
            header.setForeground(JStudioTheme.getTextSecondary());
            header.setFont(JStudioTheme.getUIFont(UIConstants.FONT_SIZE_CODE));
        }
    }

    @Override
    public void removeNotify() {
        super.removeNotify();
        ThemeManager.getInstance().removeThemeChangeListener(this);
    }
}
