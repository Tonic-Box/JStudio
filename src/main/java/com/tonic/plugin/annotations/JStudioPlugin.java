package com.tonic.plugin.annotations;

import com.tonic.ui.JStudio;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Marks a Plugin class for discovery in a plugin jar; the loader checks only its presence, and the plugin's metadata comes from Plugin.getInfo(), not from these attributes. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface JStudioPlugin
{

    /**
     * The plugin id.
     *
     * @return the id; empty by default
     */
    String id() default "";

    /**
     * The plugin's display name.
     *
     * @return the name
     */
    String name();

    /**
     * The plugin version.
     *
     * @return the version; the JStudio version by default
     */
    String version() default JStudio.APP_VERSION;

    /**
     * A short description.
     *
     * @return the description; empty by default
     */
    String description() default "";

    /**
     * The plugin's author.
     *
     * @return the author; empty by default
     */
    String author() default "";

    /**
     * Free-form tags.
     *
     * @return the tags; none by default
     */
    String[] tags() default {};

    /**
     * The plugin's category.
     *
     * @return the category; "general" by default
     */
    String category() default "general";

    /**
     * The ordering priority.
     *
     * @return the priority; 0 by default
     */
    int priority() default 0;
}
