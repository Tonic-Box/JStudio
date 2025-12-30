package com.tonic.ui.event.events;

import com.tonic.ui.event.Event;
import lombok.Getter;

@Getter
public class ShowXrefsEvent extends Event {

    public enum TargetType {
        CLASS,
        METHOD,
        FIELD
    }

    private final TargetType targetType;
    private final String className;
    private final String memberName;
    private final String memberDescriptor;

    public ShowXrefsEvent(Object source, String className) {
        super(source);
        this.targetType = TargetType.CLASS;
        this.className = className;
        this.memberName = null;
        this.memberDescriptor = null;
    }

    public static ShowXrefsEvent forMethod(Object source, String className,
                                            String methodName, String methodDesc) {
        return new ShowXrefsEvent(source, TargetType.METHOD, className, methodName, methodDesc);
    }

    public static ShowXrefsEvent forField(Object source, String className,
                                           String fieldName, String fieldDesc) {
        return new ShowXrefsEvent(source, TargetType.FIELD, className, fieldName, fieldDesc);
    }

    private ShowXrefsEvent(Object source, TargetType targetType, String className,
                           String memberName, String memberDescriptor) {
        super(source);
        this.targetType = targetType;
        this.className = className;
        this.memberName = memberName;
        this.memberDescriptor = memberDescriptor;
    }

    public String getTargetDisplay() {
        String displayClass = className != null ? className.replace('/', '.') : "unknown";
        switch (targetType) {
            case CLASS:
                return displayClass;
            case METHOD:
                return displayClass + "." + memberName + "()";
            case FIELD:
                return displayClass + "." + memberName;
            default:
                return displayClass;
        }
    }
}
