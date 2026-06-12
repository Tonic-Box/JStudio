package com.tonic.plugin.annotations;

import com.tonic.ui.JStudio;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface JStudioPlugin {

    String id() default "";

    String name();

    String version() default JStudio.APP_VERSION;

    String description() default "";

    String author() default "";

    String[] tags() default {};

    String category() default "general";

    int priority() default 0;
}
