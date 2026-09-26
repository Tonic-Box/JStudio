package com.tonic.event.events;

import com.tonic.event.Event;
import lombok.Getter;

/** Posted to show cross-references to a class, method or field. */
@Getter
public class ShowXrefsEvent extends Event
{

    /** The kind of symbol whose cross-references are shown. */
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
     * Creates the event for a class.
     *
     * @param source the poster
     * @param className the class's internal name, with slashes
     */
    public ShowXrefsEvent(Object source, String className)
    {
        super(source);
        this.targetType = TargetType.CLASS;
        this.className = className;
        this.memberName = null;
        this.memberDescriptor = null;
    }

    /**
     * Creates the event for a method.
     *
     * @param source the poster
     * @param className the owner's internal name, with slashes
     * @param methodName the method name
     * @param methodDesc the method descriptor
     * @return the event
     */
    public static ShowXrefsEvent forMethod(Object source, String className, String methodName, String methodDesc)
    {
        return new ShowXrefsEvent(source, TargetType.METHOD, className, methodName, methodDesc);
    }

    /**
     * Creates the event for a field.
     *
     * @param source the poster
     * @param className the owner's internal name, with slashes
     * @param fieldName the field name
     * @param fieldDesc the field descriptor
     * @return the event
     */
    public static ShowXrefsEvent forField(Object source, String className, String fieldName, String fieldDesc)
    {
        return new ShowXrefsEvent(source, TargetType.FIELD, className, fieldName, fieldDesc);
    }

    private ShowXrefsEvent(Object source, TargetType targetType, String className, String memberName, String memberDescriptor)
    {
        super(source);
        this.targetType = targetType;
        this.className = className;
        this.memberName = memberName;
        this.memberDescriptor = memberDescriptor;
    }

    /**
     * Describes the target for display, using the dotted class name.
     *
     * @return the dotted class name, followed by ".member()" for a method or ".member" for a field; "unknown" when the class is null
     */
    public String getTargetDisplay()
    {
        String displayClass = className != null ? className.replace('/', '.') : "unknown";
        switch (targetType)
        {
            case METHOD:
                return displayClass + "." + memberName + "()";
            case FIELD:
                return displayClass + "." + memberName;
            default:
                return displayClass;
        }
    }
}
