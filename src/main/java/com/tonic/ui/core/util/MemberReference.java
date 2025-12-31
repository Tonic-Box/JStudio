package com.tonic.ui.core.util;

import java.util.Objects;

public final class MemberReference {

    private final String ownerClass;
    private final String memberName;
    private final String descriptor;

    public MemberReference(String ownerClass, String memberName, String descriptor) {
        this.ownerClass = ownerClass;
        this.memberName = memberName;
        this.descriptor = descriptor;
    }

    public String getOwnerClass() {
        return ownerClass;
    }

    public String getMemberName() {
        return memberName;
    }

    public String getDescriptor() {
        return descriptor;
    }

    public static MemberReference parseMethodRef(String methodRef) {
        if (methodRef == null || methodRef.isEmpty()) {
            return new MemberReference(null, null, null);
        }

        int dotIdx = methodRef.lastIndexOf('.');
        if (dotIdx < 0) {
            return new MemberReference(null, methodRef, null);
        }

        String owner = methodRef.substring(0, dotIdx);
        String rest = methodRef.substring(dotIdx + 1);

        int descIdx = rest.indexOf('(');
        if (descIdx < 0) {
            return new MemberReference(owner, rest, null);
        }

        return new MemberReference(owner, rest.substring(0, descIdx), rest.substring(descIdx));
    }

    public static MemberReference parseFieldRef(String fieldRef) {
        if (fieldRef == null || fieldRef.isEmpty() || fieldRef.equals("*")) {
            return new MemberReference(null, null, null);
        }

        String[] parts = fieldRef.split("[.:]");
        switch (parts.length) {
            case 1:
                return new MemberReference(null, parts[0], null);
            case 2:
                return new MemberReference(parts[0], parts[1], null);
            case 3:
                return new MemberReference(parts[0], parts[1], parts[2]);
            default:
                return new MemberReference(null, fieldRef, null);
        }
    }

    public boolean matches(String owner, String name, String desc) {
        if (ownerClass != null && !ownerClass.equals(owner)) {
            if (owner == null || !owner.endsWith("/" + ownerClass)) {
                return false;
            }
        }
        if (memberName != null && !memberName.equals(name)) {
            return false;
        }
        return descriptor == null || descriptor.equals(desc);
    }

    public boolean isWildcard() {
        return ownerClass == null && memberName == null;
    }

    public boolean hasOwner() {
        return ownerClass != null;
    }

    public boolean hasDescriptor() {
        return descriptor != null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MemberReference)) return false;
        MemberReference that = (MemberReference) o;
        return Objects.equals(ownerClass, that.ownerClass) &&
               Objects.equals(memberName, that.memberName) &&
               Objects.equals(descriptor, that.descriptor);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ownerClass, memberName, descriptor);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (ownerClass != null) {
            sb.append(ownerClass).append(".");
        }
        if (memberName != null) {
            sb.append(memberName);
        }
        if (descriptor != null) {
            sb.append(descriptor);
        }
        return sb.toString();
    }
}
