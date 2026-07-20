package com.blackwalnut.noteswidget;

final class SyncPolicy {
    enum Decision { APPLY_REMOTE, UPLOAD_LOCAL, SAME_VERSION }

    static Decision decide(long localUpdatedAt, long remoteUpdatedAt) {
        if (remoteUpdatedAt > localUpdatedAt) return Decision.APPLY_REMOTE;
        if (localUpdatedAt > remoteUpdatedAt) return Decision.UPLOAD_LOCAL;
        return Decision.SAME_VERSION;
    }

    private SyncPolicy() { }
}
