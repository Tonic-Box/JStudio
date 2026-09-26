package com.tonic.plugin.api.ui;

/** A handle that undoes one UI contribution; the ones UiApi returns are safe to remove more than once, and a plugin's own ones passed to track should be too. */
@FunctionalInterface
public interface Registration
{

    /** Undoes the contribution. */
    void remove();
}
