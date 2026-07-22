package com.blackwalnut.noteswidget;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SyncPolicyTest {
    @Test public void cleanLocalAppliesDifferentRemoteContent() {
        assertEquals(SyncPolicy.RemoteDecision.APPLY_REMOTE, SyncPolicy.decideRemote(false, true));
    }

    @Test public void pendingLocalAndDifferentRemotePreservesLocal() {
        assertEquals(SyncPolicy.RemoteDecision.KEEP_LOCAL_CONFLICT, SyncPolicy.decideRemote(true, true));
    }

    @Test public void sameContentIsSafeRegardlessOfPendingState() {
        assertEquals(SyncPolicy.RemoteDecision.SAME_CONTENT, SyncPolicy.decideRemote(true, false));
    }

    @Test public void explicitEditUploadsWhenRemoteStillMatchesBase() {
        assertEquals(SyncPolicy.UploadDecision.WRITE, SyncPolicy.decideUpload(10, true, 10, false));
    }

    @Test public void uploadSkipsWhenRemoteVersionChanged() {
        assertEquals(SyncPolicy.UploadDecision.KEEP_LOCAL_CONFLICT, SyncPolicy.decideUpload(10, true, 11, false));
    }

    @Test public void newDocumentOnlyWritesWhenRemoteDoesNotExist() {
        assertEquals(SyncPolicy.UploadDecision.WRITE, SyncPolicy.decideUpload(0, false, 0, false));
        assertEquals(SyncPolicy.UploadDecision.KEEP_LOCAL_CONFLICT, SyncPolicy.decideUpload(0, true, 9, false));
    }

    @Test public void conditionalDeleteRequiresTheScheduledRemoteVersion() {
        assertEquals(SyncPolicy.DeleteDecision.DELETE, SyncPolicy.decideDelete(10, true, 10));
        assertEquals(SyncPolicy.DeleteDecision.CONFLICT, SyncPolicy.decideDelete(10, true, 11));
        assertEquals(SyncPolicy.DeleteDecision.CONFLICT, SyncPolicy.decideDelete(0, true, 10));
    }

    @Test public void conditionalDeleteTreatsMissingRemoteAsComplete() {
        assertEquals(SyncPolicy.DeleteDecision.ALREADY_DELETED, SyncPolicy.decideDelete(10, false, 0));
    }

    @Test public void eachPendingDeleteUsesItsOwnExpectedVersion() {
        assertEquals(SyncPolicy.DeleteDecision.DELETE, SyncPolicy.decideDelete(10, true, 10));
        assertEquals(SyncPolicy.DeleteDecision.CONFLICT, SyncPolicy.decideDelete(20, true, 10));
    }

    @Test public void repeatedIdenticalConflictCreatesOnlyOneCopy() {
        assertEquals(true, SyncPolicy.shouldCreateConflictCopy(0));
        assertEquals(false, SyncPolicy.shouldCreateConflictCopy(1));
        assertEquals(false, SyncPolicy.shouldCreateConflictCopy(2));
    }
}
