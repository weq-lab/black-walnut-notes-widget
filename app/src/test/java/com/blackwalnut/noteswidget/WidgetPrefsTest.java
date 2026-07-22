package com.blackwalnut.noteswidget;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class WidgetPrefsTest {
    @Test public void collapsePreferenceIsIsolatedPerWidget() {
        assertEquals("collapsed_12", WidgetPrefs.collapseKey(12));
        assertEquals("collapsed_13", WidgetPrefs.collapseKey(13));
        assertNotEquals(WidgetPrefs.collapseKey(12), WidgetPrefs.collapseKey(13));
    }
}
