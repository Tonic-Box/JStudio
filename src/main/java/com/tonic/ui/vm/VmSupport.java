package com.tonic.ui.vm;

import com.tonic.parser.ClassFile;
import com.tonic.parser.MethodEntry;

/** Method lookup shared by the VM execution paths. */
public final class VmSupport
{

    private VmSupport()
    {
    }

    /**
     * Finds a method by name and descriptor.
     *
     * @param classFile the class to search
     * @param methodName the method name
     * @param descriptor the method descriptor, or null or empty to take the first method with that name
     * @return the method, or null if none matches
     */
    public static MethodEntry findMethod(ClassFile classFile, String methodName, String descriptor)
    {
        for (MethodEntry method : classFile.getMethods())
        {
            if (method.getName().equals(methodName)
                    && (descriptor == null || descriptor.isEmpty() || method.getDesc().equals(descriptor)))
            {
                return method;
            }
        }
        return null;
    }
}
