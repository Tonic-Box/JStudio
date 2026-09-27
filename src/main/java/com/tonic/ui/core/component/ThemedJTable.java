package com.tonic.ui.core.component;

import com.tonic.ui.core.constants.UIConstants;
import com.tonic.ui.theme.JStudioTheme;
import com.tonic.ui.theme.Theme;
import com.tonic.ui.theme.ThemeChangeListener;
import com.tonic.ui.theme.ThemeManager;

import java.awt.Dimension;
import javax.swing.JTable;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableModel;

/** A gridless table with the standard row height whose body and header follow the theme. */
public class ThemedJTable extends JTable implements ThemeChangeListener
{

    /** Creates an empty table and registers it for theme changes. */
    public ThemedJTable()
    {
        super();
        initialize();
    }

    /**
     * Creates a table over a model and registers it for theme changes.
     *
     * @param dm the model
     */
    public ThemedJTable(TableModel dm)
    {
        super(dm);
        initialize();
    }

    /**
     * Creates a table of empty cells and registers it for theme changes.
     *
     * @param numRows the row count
     * @param numColumns the column count
     */
    public ThemedJTable(int numRows, int numColumns)
    {
        super(numRows, numColumns);
        initialize();
    }

    /**
     * Creates a table over fixed data and registers it for theme changes.
     *
     * @param rowData the cell values, by row
     * @param columnNames the column headers
     */
    public ThemedJTable(Object[][] rowData, Object[] columnNames)
    {
        super(rowData, columnNames);
        initialize();
    }

    private void initialize()
    {
        setRowHeight(UIConstants.TABLE_ROW_HEIGHT);
        setShowGrid(false);
        setIntercellSpacing(new Dimension(0, 0));
        setFillsViewportHeight(true);
    }

    @Override
    public void onThemeChanged(Theme newTheme)
    {
        applyTheme();
        repaint();
        if (getTableHeader() != null)
        {
            getTableHeader().repaint();
        }
    }

    protected void applyTheme()
    {
        setBackground(JStudioTheme.getBgSecondary());
        setForeground(JStudioTheme.getTextPrimary());
        setSelectionBackground(JStudioTheme.getSelection());
        setSelectionForeground(JStudioTheme.getTextPrimary());
        setGridColor(JStudioTheme.getBorder());
        setFont(JStudioTheme.getCodeFont(UIConstants.FONT_SIZE_CODE));

        JTableHeader header = getTableHeader();
        if (header != null)
        {
            header.setBackground(JStudioTheme.getBgTertiary());
            header.setForeground(JStudioTheme.getTextSecondary());
            header.setFont(JStudioTheme.getUIFont(UIConstants.FONT_SIZE_CODE));
        }
    }

    @Override
    public void addNotify()
    {
        super.addNotify();
        ThemeManager.getInstance().addThemeChangeListener(this);
        onThemeChanged(ThemeManager.getInstance().getCurrentTheme());
    }

    @Override
    public void removeNotify()
    {
        super.removeNotify();
        ThemeManager.getInstance().removeThemeChangeListener(this);
    }
}
