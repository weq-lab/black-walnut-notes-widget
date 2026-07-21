package com.blackwalnut.noteswidget;

final class BackgroundSyncPolicy {
    static final long OVERLAP_MS = 5L * 60L * 1000L;
    static final long FULL_RECONCILE_MS = 24L * 60L * 60L * 1000L;

    static long incrementalStart(long lastSuccessfulSync) {
        return Math.max(0L, lastSuccessfulSync - OVERLAP_MS);
    }

    static boolean shouldReconcileAll(long now, long lastFullReconcile) {
        return lastFullReconcile <= 0L || now - lastFullReconcile >= FULL_RECONCILE_MS;
    }

    private BackgroundSyncPolicy() { }
}
