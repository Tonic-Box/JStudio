package com.tonic.ui.theme;

import com.tonic.service.ProjectDatabaseService;
import com.tonic.service.history.LocalHistoryService;
import com.tonic.ui.core.component.ThemedJPanel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("every theme listener registration is undone, so closed panels do not accumulate in ThemeManager")
class ListenerLifecycleTest
{

    @Test
    void themeManagerAddRemoveBalances()
    {
        ThemeManager mgr = ThemeManager.getInstance();
        int before = mgr.getListenerCount();
        ThemeChangeListener l = t ->
        {
        };
        mgr.addThemeChangeListener(l);
        assertEquals(before + 1, mgr.getListenerCount());
        mgr.removeThemeChangeListener(l);
        assertEquals(before, mgr.getListenerCount());
    }

    @Test
    void projectDatabaseServiceAddRemoveBalances()
    {
        ProjectDatabaseService svc = ProjectDatabaseService.getInstance();
        int before = svc.getListenerCount();
        ProjectDatabaseService.DatabaseChangeListener l = (db, dirty) ->
        {
        };
        svc.addListener(l);
        assertEquals(before + 1, svc.getListenerCount());
        svc.removeListener(l);
        assertEquals(before, svc.getListenerCount());
    }

    @Test
    void localHistoryServiceAddRemoveBalances()
    {
        LocalHistoryService svc = LocalHistoryService.getInstance();
        int before = svc.getListenerCount();
        Runnable l = () ->
        {
        };
        svc.addListener(l);
        assertEquals(before + 1, svc.getListenerCount());
        svc.removeListener(l);
        assertEquals(before, svc.getListenerCount());
    }

    @Test
    @DisplayName("a themed panel listens only while displayable, once however often it is added, and again after being re-added")
    void themedPanelListensWhileDisplayable()
    {
        System.setProperty("java.awt.headless", "true");
        ThemeManager mgr = ThemeManager.getInstance();
        int before = mgr.getListenerCount();
        ThemedJPanel panel = new ThemedJPanel();
        assertEquals(before, mgr.getListenerCount());
        panel.addNotify();
        panel.addNotify();
        assertEquals(before + 1, mgr.getListenerCount());
        panel.removeNotify();
        assertEquals(before, mgr.getListenerCount());
        panel.addNotify();
        assertEquals(before + 1, mgr.getListenerCount());
        panel.removeNotify();
        assertEquals(before, mgr.getListenerCount());
    }
}
