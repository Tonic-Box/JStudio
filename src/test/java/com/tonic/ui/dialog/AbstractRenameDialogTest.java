package com.tonic.ui.dialog;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Covers the shared Java-identifier validation used by all three rename dialogs. */
class AbstractRenameDialogTest {

    @Test
    void acceptsValidIdentifiers() {
        assertTrue(AbstractRenameDialog.isValidJavaIdentifier("foo"));
        assertTrue(AbstractRenameDialog.isValidJavaIdentifier("_bar"));
        assertTrue(AbstractRenameDialog.isValidJavaIdentifier("a1b2"));
        assertTrue(AbstractRenameDialog.isValidJavaIdentifier("$cache"));
    }

    @Test
    void rejectsInvalidIdentifiers() {
        assertFalse(AbstractRenameDialog.isValidJavaIdentifier(null));
        assertFalse(AbstractRenameDialog.isValidJavaIdentifier(""));
        assertFalse(AbstractRenameDialog.isValidJavaIdentifier("1abc"));
        assertFalse(AbstractRenameDialog.isValidJavaIdentifier("has space"));
        assertFalse(AbstractRenameDialog.isValidJavaIdentifier("a.b"));
        assertFalse(AbstractRenameDialog.isValidJavaIdentifier("has-dash"));
    }
}
