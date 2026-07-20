package com.blackwalnut.noteswidget;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SyncPolicyTest {
    @Test public void newerRemoteWins() {
        assertEquals(SyncPolicy.Decision.APPLY_REMOTE, SyncPolicy.decide(10, 11));
    }

    @Test public void newerLocalUploads() {
        assertEquals(SyncPolicy.Decision.UPLOAD_LOCAL, SyncPolicy.decide(12, 11));
    }

    @Test public void equalTimestampIsSameVersion() {
        assertEquals(SyncPolicy.Decision.SAME_VERSION, SyncPolicy.decide(12, 12));
    }
}
