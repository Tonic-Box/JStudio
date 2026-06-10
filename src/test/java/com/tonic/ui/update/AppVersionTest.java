package com.tonic.ui.update;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AppVersionTest {

    @Test
    void parsesReleaseTags() {
        assertEquals(9, AppVersion.parse("v9"));
        assertEquals(10, AppVersion.parse("v10"));
        assertEquals(11, AppVersion.parse("V11"));
    }

    @Test
    void parsesGradleVersions() {
        assertEquals(10, AppVersion.parse("10.0-SNAPSHOT"));
        assertEquals(11, AppVersion.parse("11"));
    }

    @Test
    void returnsNegativeForUnparseable() {
        assertEquals(-1, AppVersion.parse("snapshot"));
        assertEquals(-1, AppVersion.parse(""));
        assertEquals(-1, AppVersion.parse(null));
    }

    @Test
    void comparisonDetectsNewerRelease() {
        assertTrue(AppVersion.parse("v11") > AppVersion.parse("10.0-SNAPSHOT"));
        assertFalse(AppVersion.parse("v10") > AppVersion.parse("10.0-SNAPSHOT"));
    }

    @Test
    void notPackagedWhenRunFromClasses() {
        // The test runs from build/classes, not a jar, so there is no manifest version.
        assertNull(AppVersion.current());
        assertFalse(AppVersion.isPackaged());
    }
}
