package com.tonic.ui.editor.bytecode;

import com.tonic.model.ClassEntryModel;
import com.tonic.ui.debug.Breakpoint;
import com.tonic.ui.debug.BreakpointMapper;
import com.tonic.ui.editor.dual.BcLocation;

/**
 * Maps the bytecode (disassembly) view's instruction lines to breakpoints via the {@link BcLocation} line
 * index: a clicked line resolves to that instruction's exact offset, and a breakpoint resolves back to its
 * instruction's display line. The view uses 0-based display lines; this adapts to the controller's 1-based.
 */
final class BytecodeBreakpointMapper implements BreakpointMapper {

    private final BytecodeView view;
    private final ClassEntryModel classEntry;

    BytecodeBreakpointMapper(BytecodeView view, ClassEntryModel classEntry) {
        this.view = view;
        this.classEntry = classEntry;
    }

    @Override
    public String className() {
        return classEntry.getClassName().replace('/', '.');
    }

    @Override
    public Breakpoint breakpointAtLine(int line) {
        BcLocation loc = view.locationAtLine(line - 1);
        if (loc == null) {
            return null;
        }
        return new Breakpoint(className(), loc.getMethodName(), loc.getMethodDesc(), loc.getPc());
    }

    @Override
    public int lineForBreakpoint(Breakpoint bp) {
        int display = view.displayLineForPc(bp.methodName + bp.methodDesc, (int) bp.pc);
        return display < 0 ? -1 : display + 1;
    }
}
