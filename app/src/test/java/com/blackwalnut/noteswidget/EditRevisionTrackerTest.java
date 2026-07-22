package com.blackwalnut.noteswidget;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class EditRevisionTrackerTest {
    @Test public void openingAndClosingWithoutChangesDoesNotStartSave() {
        EditRevisionTracker tracker = new EditRevisionTracker();
        tracker.reset("loaded note");
        assertFalse(tracker.isDirty());
        assertNull(tracker.begin("loaded note"));
    }

    @Test public void sameEffectiveContentDoesNotStartSave() {
        EditRevisionTracker tracker = new EditRevisionTracker();
        tracker.reset("loaded note");
        tracker.changed();
        assertNull(tracker.begin("loaded note"));
        assertFalse(tracker.isDirty());
    }

    @Test public void editsDuringSaveRemainDirtyForAnotherSave() {
        EditRevisionTracker tracker = new EditRevisionTracker();
        tracker.reset("base");
        tracker.changed();
        EditRevisionTracker.Attempt first = tracker.begin("first edit");
        assertNotNull(first);
        tracker.changed();
        tracker.complete(first);
        assertTrue(tracker.isDirty());
        assertNotNull(tracker.begin("later edit"));
    }
}
