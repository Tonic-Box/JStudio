package com.tonic.util;

/** JVM access flag constants and tests for class, field and method access flags. */
public final class AccessFlags
{

    public static final int PUBLIC = 0x0001;
    public static final int PRIVATE = 0x0002;
    public static final int PROTECTED = 0x0004;
    public static final int STATIC = 0x0008;
    public static final int FINAL = 0x0010;
    public static final int SYNCHRONIZED = 0x0020;
    public static final int VOLATILE = 0x0040;
    public static final int TRANSIENT = 0x0080;
    public static final int NATIVE = 0x0100;
    public static final int INTERFACE = 0x0200;
    public static final int ABSTRACT = 0x0400;
    public static final int STRICT = 0x0800;
    public static final int SYNTHETIC = 0x1000;
    public static final int ANNOTATION = 0x2000;
    public static final int ENUM = 0x4000;

    private AccessFlags()
    {
    }

    /**
     * Tests the ACC_PUBLIC bit.
     *
     * @param flags the access flags
     * @return whether the bit is set
     */
    public static boolean isPublic(int flags)
    {
        return (flags & PUBLIC) != 0;
    }

    /**
     * Tests the ACC_PRIVATE bit.
     *
     * @param flags the access flags
     * @return whether the bit is set
     */
    public static boolean isPrivate(int flags)
    {
        return (flags & PRIVATE) != 0;
    }

    /**
     * Tests the ACC_PROTECTED bit.
     *
     * @param flags the access flags
     * @return whether the bit is set
     */
    public static boolean isProtected(int flags)
    {
        return (flags & PROTECTED) != 0;
    }

    /**
     * Tests for package-private access.
     *
     * @param flags the access flags
     * @return whether none of the public, private and protected bits is set
     */
    public static boolean isPackagePrivate(int flags)
    {
        return !isPublic(flags) && !isPrivate(flags) && !isProtected(flags);
    }

    /**
     * Tests the ACC_STATIC bit.
     *
     * @param flags the access flags
     * @return whether the bit is set
     */
    public static boolean isStatic(int flags)
    {
        return (flags & STATIC) != 0;
    }

    /**
     * Tests the ACC_FINAL bit.
     *
     * @param flags the access flags
     * @return whether the bit is set
     */
    public static boolean isFinal(int flags)
    {
        return (flags & FINAL) != 0;
    }

    /**
     * Tests the ACC_SYNCHRONIZED bit.
     *
     * @param flags the access flags
     * @return whether the bit is set
     */
    public static boolean isSynchronized(int flags)
    {
        return (flags & SYNCHRONIZED) != 0;
    }

    /**
     * Tests the ACC_VOLATILE bit.
     *
     * @param flags the access flags
     * @return whether the bit is set
     */
    public static boolean isVolatile(int flags)
    {
        return (flags & VOLATILE) != 0;
    }

    /**
     * Tests the ACC_TRANSIENT bit.
     *
     * @param flags the access flags
     * @return whether the bit is set
     */
    public static boolean isTransient(int flags)
    {
        return (flags & TRANSIENT) != 0;
    }

    /**
     * Tests the ACC_NATIVE bit.
     *
     * @param flags the access flags
     * @return whether the bit is set
     */
    public static boolean isNative(int flags)
    {
        return (flags & NATIVE) != 0;
    }

    /**
     * Tests the ACC_INTERFACE bit.
     *
     * @param flags the access flags
     * @return whether the bit is set
     */
    public static boolean isInterface(int flags)
    {
        return (flags & INTERFACE) != 0;
    }

    /**
     * Tests the ACC_ABSTRACT bit.
     *
     * @param flags the access flags
     * @return whether the bit is set
     */
    public static boolean isAbstract(int flags)
    {
        return (flags & ABSTRACT) != 0;
    }

    /**
     * Tests the ACC_STRICT bit.
     *
     * @param flags the access flags
     * @return whether the bit is set
     */
    public static boolean isStrict(int flags)
    {
        return (flags & STRICT) != 0;
    }

    /**
     * Tests the ACC_SYNTHETIC bit.
     *
     * @param flags the access flags
     * @return whether the bit is set
     */
    public static boolean isSynthetic(int flags)
    {
        return (flags & SYNTHETIC) != 0;
    }

    /**
     * Tests the ACC_ANNOTATION bit.
     *
     * @param flags the access flags
     * @return whether the bit is set
     */
    public static boolean isAnnotation(int flags)
    {
        return (flags & ANNOTATION) != 0;
    }

    /**
     * Tests the ACC_ENUM bit.
     *
     * @param flags the access flags
     * @return whether the bit is set
     */
    public static boolean isEnum(int flags)
    {
        return (flags & ENUM) != 0;
    }
}
