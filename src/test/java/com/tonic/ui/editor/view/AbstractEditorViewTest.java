package com.tonic.ui.editor.view;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Covers the no-op/fallback defaults and the load scaffolding in {@link AbstractEditorView}. */
class AbstractEditorViewTest {

    @BeforeAll
    static void headless() {
        System.setProperty("java.awt.headless", "true");
    }

    /** Minimal concrete view that records the calls the base routes to it. */
    private static class TestView extends AbstractEditorView {
        final AtomicInteger refreshes = new AtomicInteger();
        int goToLineArg = Integer.MIN_VALUE;

        @Override
        public void refresh() {
            refreshes.incrementAndGet();
        }

        @Override
        public void goToLine(int line) {
            goToLineArg = line;
        }
    }

    @Test
    void reloadClearsLoadedThenRefreshes() {
        TestView v = new TestView();
        v.loaded = true;
        v.reload();
        assertFalse(v.loaded);
        assertEquals(1, v.refreshes.get());
    }

    @Test
    void highlightLineFallsBackToGoToLine() {
        TestView v = new TestView();
        v.highlightLine(42);
        assertEquals(42, v.goToLineArg);
    }

    @Test
    void contractDefaultsAreNoOps() {
        TestView v = new TestView();
        assertEquals("", v.getText());
        assertNull(v.getSelectedText());
        // none of these should throw
        v.copySelection();
        v.showFindDialog();
        v.scrollToText("x");
        v.setFontSize(14);
        v.setWordWrap(true);
        assertTrue(true);
    }
}
