package com.tonic.ui.vm.debugger;

import com.tonic.parser.ClassFile;
import com.tonic.parser.MethodEntry;
import com.tonic.service.ProjectService;

import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

final class FrameNavigator
{

    private final Supplier<MethodEntry> currentMethod;
    private final Consumer<MethodEntry> setCurrentMethod;
    private final Supplier<MethodEntry> displayedMethod;
    private final Consumer<MethodEntry> loadBytecode;
    private final IntConsumer highlightInstruction;

    FrameNavigator(Supplier<MethodEntry> currentMethod, Consumer<MethodEntry> setCurrentMethod, Supplier<MethodEntry> displayedMethod, Consumer<MethodEntry> loadBytecode, IntConsumer highlightInstruction)
    {
        this.currentMethod = currentMethod;
        this.setCurrentMethod = setCurrentMethod;
        this.displayedMethod = displayedMethod;
        this.loadBytecode = loadBytecode;
        this.highlightInstruction = highlightInstruction;
    }

    MethodEntry findMethod(String className, String methodName, String desc)
    {
        ClassFile classFile = ProjectService.getInstance().getCurrentProject()
                .getClassPool().get(className);
        if (classFile != null)
        {
            for (MethodEntry m : classFile.getMethods())
            {
                if (m.getName().equals(methodName) && m.getDesc().equals(desc))
                {
                    return m;
                }
            }
        }
        return null;
    }

    void navigateToFrame(FrameEntry frame)
    {
        MethodEntry current = currentMethod.get();
        if (current != null &&
                frame.getClassName().equals(current.getOwnerName()) &&
                frame.getMethodName().equals(current.getName()))
        {
            highlightInstruction.accept(frame.getInstructionIndex());
        }
        else
        {
            MethodEntry m = findMethod(frame.getClassName(), frame.getMethodName(), frame.getDescriptor());
            if (m != null)
            {
                setCurrentMethod.accept(m);
                loadBytecode.accept(m);
                highlightInstruction.accept(frame.getInstructionIndex());
            }
        }
    }

    boolean onMethodMaybeChanged(String className, String methodName, String desc)
    {
        MethodEntry displayed = displayedMethod.get();
        boolean methodChanged = displayed == null ||
                !className.equals(displayed.getOwnerName()) ||
                !methodName.equals(displayed.getName()) ||
                !desc.equals(displayed.getDesc());

        if (methodChanged)
        {
            MethodEntry m = findMethod(className, methodName, desc);
            if (m != null)
            {
                loadBytecode.accept(m);
            }
        }
        return methodChanged;
    }
}
