package com.blackwalnut.noteswidget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BackgroundSyncPolicyTest {
    @Test
    public void incrementalStartOverlapsFiveMinutes() {
        long last = 10L * 60L * 1000L;
        assertEquals(5L * 60L * 1000L, BackgroundSyncPolicy.incrementalStart(last));
        assertEquals(0L, BackgroundSyncPolicy.incrementalStart(1000L));
    }

    @Test
    public void fullReconcileRunsAboutOncePerDay() {
        long now = 30L * 60L * 60L * 1000L;
        assertTrue(BackgroundSyncPolicy.shouldReconcileAll(now, 0L));
        assertTrue(BackgroundSyncPolicy.shouldReconcileAll(now, now - BackgroundSyncPolicy.FULL_RECONCILE_MS));
        assertFalse(BackgroundSyncPolicy.shouldReconcileAll(now, now - BackgroundSyncPolicy.FULL_RECONCILE_MS + 1L));
    }
}
