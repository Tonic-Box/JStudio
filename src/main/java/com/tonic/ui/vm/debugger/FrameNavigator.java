package com.tonic.ui.vm.debugger;

import com.tonic.parser.ClassFile;
import com.tonic.parser.MethodEntry;
import com.tonic.service.ProjectService;

import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

/**
 * Resolves debugger navigation across methods: looking up {@link MethodEntry} instances in the current project's
 * class pool by name and descriptor, and driving the bytecode view to the right method/instruction when the user
 * selects a call-stack frame or the VM steps into a different method.
 */
final class FrameNavigator {

    private final Supplier<MethodEntry> currentMethod;
    private final Consumer<MethodEntry> setCurrentMethod;
    private final Supplier<MethodEntry> displayedMethod;
    private final Consumer<MethodEntry> loadBytecode;
    private final IntConsumer highlightInstruction;

    FrameNavigator(Supplier<MethodEntry> currentMethod,
                   Consumer<MethodEntry> setCurrentMethod,
                   Supplier<MethodEntry> displayedMethod,
                   Consumer<MethodEntry> loadBytecode,
                   IntConsumer highlightInstruction) {
        this.currentMethod = currentMethod;
        this.setCurrentMethod = setCurrentMethod;
        this.displayedMethod = displayedMethod;
        this.loadBytecode = loadBytecode;
        this.highlightInstruction = highlightInstruction;
    }

    /** Finds a method in the current project's class pool by name and descriptor, or null if not present. */
    MethodEntry findMethod(String className, String methodName, String desc) {
        ClassFile classFile = ProjectService.getInstance().getCurrentProject()
            .getClassPool().get(className);
        if (classFile != null) {
            for (MethodEntry m : classFile.getMethods()) {
                if (m.getName().equals(methodName) && m.getDesc().equals(desc)) {
                    return m;
                }
            }
        }
        return null;
    }

    /**
     * Highlights the selected frame: if it is the current method, just moves the highlight; otherwise re-resolves
     * the frame's method, loads its bytecode, and highlights the frame's instruction.
     */
    void navigateToFrame(FrameEntry frame) {
        MethodEntry current = currentMethod.get();
        if (current != null &&
            frame.getClassName().equals(current.getOwnerName()) &&
            frame.getMethodName().equals(current.getName())) {
            highlightInstruction.accept(frame.getInstructionIndex());
        } else {
            MethodEntry m = findMethod(frame.getClassName(), frame.getMethodName(), frame.getDescriptor());
            if (m != null) {
                setCurrentMethod.accept(m);
                loadBytecode.accept(m);
                highlightInstruction.accept(frame.getInstructionIndex());
            }
        }
    }

    /**
     * When the VM has stepped into a method other than the displayed one, re-resolves and loads that method's
     * bytecode. Returns true when a method change was handled.
     */
    boolean onMethodMaybeChanged(String className, String methodName, String desc) {
        MethodEntry displayed = displayedMethod.get();
        boolean methodChanged = displayed == null ||
            !className.equals(displayed.getOwnerName()) ||
            !methodName.equals(displayed.getName()) ||
            !desc.equals(displayed.getDesc());

        if (methodChanged) {
            MethodEntry m = findMethod(className, methodName, desc);
            if (m != null) {
                loadBytecode.accept(m);
            }
        }
        return methodChanged;
    }
}
