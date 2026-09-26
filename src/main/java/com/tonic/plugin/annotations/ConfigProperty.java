package com.tonic.plugin.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Marks a plugin field as bound to a configuration key; declared for plugin authors, but nothing in JStudio reads it yet, so the field is not populated. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ConfigProperty
{

    /**
     * The configuration key.
     *
     * @return the key the field binds to
     */
    String key();

    /**
     * The fallback value.
     *
     * @return the value to use when the key is absent; empty by default
     */
    String defaultValue() default "";

    /**
     * The property's description.
     *
     * @return a human-readable description; empty by default
     */
    String description() default "";

    /**
     * Whether the key is mandatory.
     *
     * @return true when the key must be present; false by default
     */
    boolean required() default false;
}
