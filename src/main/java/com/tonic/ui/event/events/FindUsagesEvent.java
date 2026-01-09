package com.tonic.ui.event.events;

import com.tonic.ui.event.Event;
import lombok.Getter;

@Getter
public class FindUsagesEvent extends Event {

    public enum TargetType {
        CLASS,
        METHOD,
        FIELD
    }

    private final TargetType targetType;
    private final String className;
    private final String memberName;
    private final String memberDescriptor;

    public FindUsagesEvent(Object source, String className) {
        super(source);
        this.targetType = TargetType.CLASS;
        this.className = className;
        this.memberName = null;
        this.memberDescriptor = null;
    }

    public static FindUsagesEvent forClass(Object source, String className) {
        return new FindUsagesEvent(source, className);
    }

    public static FindUsagesEvent forMethod(Object source, String className,
                                            String methodName, String methodDesc) {
        return new FindUsagesEvent(source, TargetType.METHOD, className, methodName, methodDesc);
    }

    public static FindUsagesEvent forField(Object source, String className,
                                           String fieldName, String fieldDesc) {
        return new FindUsagesEvent(source, TargetType.FIELD, className, fieldName, fieldDesc);
    }

    private FindUsagesEvent(Object source, TargetType targetType, String className,
                            String memberName, String memberDescriptor) {
        super(source);
        this.targetType = targetType;
        this.className = className;
        this.memberName = memberName;
        this.memberDescriptor = memberDescriptor;
    }

    public String getTargetDisplay() {
        String displayClass = className != null ? className.replace('/', '.') : "unknown";
        int lastDot = displayClass.lastIndexOf('.');
        String simpleName = lastDot >= 0 ? displayClass.substring(lastDot + 1) : displayClass;

        switch (targetType) {
            case METHOD:
                return simpleName + "." + memberName + "()";
            case FIELD:
                return simpleName + "." + memberName;
            default:
                return simpleName;
        }
    }
}
