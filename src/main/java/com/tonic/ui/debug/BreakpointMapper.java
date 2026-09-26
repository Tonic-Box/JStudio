package com.tonic.ui.debug;

/** Maps one editor view's 1-based lines to and from bytecode-offset breakpoints, so a breakpoint set in the source view shows in the bytecode view and back. */
public interface BreakpointMapper
{

    /**
     * Names the class this view shows.
     *
     * @return the dotted name of the class whose breakpoints this view draws
     */
    String className();

    /**
     * Finds the breakpoint a click on a line would toggle.
     *
     * @param line the 1-based line in this view
     * @return the breakpoint, or null if the line is not executable
     */
    Breakpoint breakpointAtLine(int line);

    /**
     * Finds where a breakpoint is drawn in this view.
     *
     * @param bp the breakpoint
     * @return the 1-based line, or -1 if the breakpoint does not map to this view
     */
    int lineForBreakpoint(Breakpoint bp);
}
