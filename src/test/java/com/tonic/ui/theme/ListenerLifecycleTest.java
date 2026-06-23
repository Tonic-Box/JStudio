package com.tonic.ui.theme;

import com.tonic.service.ProjectDatabaseService;
import com.tonic.service.history.LocalHistoryService;
import com.tonic.ui.core.component.ThemedJPanel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Guards the listener-lifecycle fix: every register has a matching unregister, and a {@link ThemedJPanel} returns
 * the {@link ThemeManager} listener count to baseline after its {@code removeNotify} (so closed views/dialogs no
 * longer accumulate in the long-lived singleton).
 */
class ListenerLifecycleTest {

    @Test
    void themeManagerAddRemoveBalances() {
        ThemeManager mgr = ThemeManager.getInstance();
        int before = mgr.getListenerCount();
        ThemeChangeListener l = t -> { };
        mgr.addThemeChangeListener(l);
        assertEquals(before + 1, mgr.getListenerCount());
        mgr.removeThemeChangeListener(l);
        assertEquals(before, mgr.getListenerCount());
    }

    @Test
    void projectDatabaseServiceAddRemoveBalances() {
        ProjectDatabaseService svc = ProjectDatabaseService.getInstance();
        int before = svc.getListenerCount();
        ProjectDatabaseService.DatabaseChangeListener l = (db, dirty) -> { };
        svc.addListener(l);
        assertEquals(before + 1, svc.getListenerCount());
        svc.removeListener(l);
        assertEquals(before, svc.getListenerCount());
    }

    @Test
    void localHistoryServiceAddRemoveBalances() {
        LocalHistoryService svc = LocalHistoryService.getInstance();
        int before = svc.getListenerCount();
        Runnable l = () -> { };
        svc.addListener(l);
        assertEquals(before + 1, svc.getListenerCount());
        svc.removeListener(l);
        assertEquals(before, svc.getListenerCount());
    }

    @Test
    void themedPanelRegistersInCtorAndUnregistersOnRemoveNotify() {
        System.setProperty("java.awt.headless", "true");
        ThemeManager mgr = ThemeManager.getInstance();
        int before = mgr.getListenerCount();
        ThemedJPanel panel = new ThemedJPanel();           // registers in ctor
        assertEquals(before + 1, mgr.getListenerCount());
        panel.removeNotify();                              // unregisters (no peer => super is a no-op)
        assertEquals(before, mgr.getListenerCount());
    }
}
