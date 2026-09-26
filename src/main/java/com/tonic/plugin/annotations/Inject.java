package com.tonic.plugin.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Marks a plugin field for injection of a named service; declared for plugin authors, but nothing in JStudio reads it yet, so the field is not populated. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Inject
{

    /**
     * The service to inject.
     *
     * @return the service name; empty by default
     */
    String value() default "";
}
