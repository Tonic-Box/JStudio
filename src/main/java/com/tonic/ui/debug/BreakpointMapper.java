package com.tonic.ui.debug;

/**
 * Bridges a specific editor view to {@link BreakpointGutterController}: it maps between the view's 1-based
 * display lines and canonical {@link Breakpoint}s. The source view maps via the decompiler's offset-to-line
 * maps; the bytecode view maps via the disassembly line index. Both resolve to the same bytecode-offset
 * breakpoint, so a breakpoint set in one view appears in the other.
 */
public interface BreakpointMapper {

    /** The dotted class name whose breakpoints this view renders. */
    String className();

    /** The breakpoint a click on {@code line} (1-based) would toggle, or null if the line isn't executable. */
    Breakpoint breakpointAtLine(int line);

    /** The 1-based line where {@code bp} should be drawn in this view, or -1 if it does not map here. */
    int lineForBreakpoint(Breakpoint bp);
}
