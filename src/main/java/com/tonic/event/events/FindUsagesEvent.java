package com.tonic.event.events;

import com.tonic.event.Event;
import lombok.Getter;

/** Posted to search for usages of a class, method or field. */
@Getter
public class FindUsagesEvent extends Event
{

    /** The kind of symbol a usage search targets. */
    public enum TargetType
    {
        CLASS,
        METHOD,
        FIELD
    }

    private final TargetType targetType;
    private final String className;
    private final String memberName;
    private final String memberDescriptor;

    /**
     * Creates a search for usages of a class.
     *
     * @param source the poster
     * @param className the class's internal name, with slashes
     */
    public FindUsagesEvent(Object source, String className)
    {
        super(source);
        this.targetType = TargetType.CLASS;
        this.className = className;
        this.memberName = null;
        this.memberDescriptor = null;
    }

    /**
     * Creates a search for usages of a class.
     *
     * @param source the poster
     * @param className the class's internal name, with slashes
     * @return the event
     */
    public static FindUsagesEvent forClass(Object source, String className)
    {
        return new FindUsagesEvent(source, className);
    }

    /**
     * Creates a search for usages of a method.
     *
     * @param source the poster
     * @param className the owner's internal name, with slashes
     * @param methodName the method name
     * @param methodDesc the method descriptor
     * @return the event
     */
    public static FindUsagesEvent forMethod(Object source, String className, String methodName, String methodDesc)
    {
        return new FindUsagesEvent(source, TargetType.METHOD, className, methodName, methodDesc);
    }

    /**
     * Creates a search for usages of a field.
     *
     * @param source the poster
     * @param className the owner's internal name, with slashes
     * @param fieldName the field name
     * @param fieldDesc the field descriptor
     * @return the event
     */
    public static FindUsagesEvent forField(Object source, String className, String fieldName, String fieldDesc)
    {
        return new FindUsagesEvent(source, TargetType.FIELD, className, fieldName, fieldDesc);
    }

    private FindUsagesEvent(Object source, TargetType targetType, String className, String memberName, String memberDescriptor)
    {
        super(source);
        this.targetType = targetType;
        this.className = className;
        this.memberName = memberName;
        this.memberDescriptor = memberDescriptor;
    }

    /**
     * Describes the target for display, using the class's simple name.
     *
     * @return the simple class name, followed by ".member()" for a method or ".member" for a field; "unknown" when the class is null
     */
    public String getTargetDisplay()
    {
        String displayClass = className != null ? className.replace('/', '.') : "unknown";
        int lastDot = displayClass.lastIndexOf('.');
        String simpleName = lastDot >= 0 ? displayClass.substring(lastDot + 1) : displayClass;

        switch (targetType)
        {
            case METHOD:
                return simpleName + "." + memberName + "()";
            case FIELD:
                return simpleName + "." + memberName;
            default:
                return simpleName;
        }
    }
}
