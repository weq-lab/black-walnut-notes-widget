package com.blackwalnut.noteswidget;

final class SyncPolicy {
    enum RemoteDecision { APPLY_REMOTE, KEEP_LOCAL_CONFLICT, SAME_CONTENT }
    enum UploadDecision { WRITE, KEEP_LOCAL_CONFLICT, SAME_CONTENT }
    enum DeleteDecision { DELETE, ALREADY_DELETED, CONFLICT }

    static RemoteDecision decideRemote(boolean localSyncPending, boolean differentContent) {
        if (!differentContent) return RemoteDecision.SAME_CONTENT;
        if (localSyncPending) return RemoteDecision.KEEP_LOCAL_CONFLICT;
        return RemoteDecision.APPLY_REMOTE;
    }

    static UploadDecision decideUpload(
            long baseUpdatedAt,
            boolean remoteExists,
            long remoteUpdatedAt,
            boolean sameContent
    ) {
        if (sameContent) return UploadDecision.SAME_CONTENT;
        if (!remoteExists) return baseUpdatedAt == 0 ? UploadDecision.WRITE : UploadDecision.KEEP_LOCAL_CONFLICT;
        if (baseUpdatedAt == 0 || remoteUpdatedAt != baseUpdatedAt) return UploadDecision.KEEP_LOCAL_CONFLICT;
        return UploadDecision.WRITE;
    }

    static DeleteDecision decideDelete(long expectedUpdatedAt, boolean remoteExists, long remoteUpdatedAt) {
        if (!remoteExists) return DeleteDecision.ALREADY_DELETED;
        return expectedUpdatedAt != 0 && expectedUpdatedAt == remoteUpdatedAt
                ? DeleteDecision.DELETE
                : DeleteDecision.CONFLICT;
    }

    static boolean shouldCreateConflictCopy(int matchingConflictCount) {
        return matchingConflictCount == 0;
    }

    private SyncPolicy() { }
}
