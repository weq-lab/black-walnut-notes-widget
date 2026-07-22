package com.blackwalnut.noteswidget;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class ConflictCopyFactoryTest {
    @Test public void conflictCopyPreservesUserContentAndBecomesANewPendingNote() {
        NoteEntity source = new NoteEntity();
        source.title = "계획";
        source.body = "본문";
        source.createdAt = 10L;
        source.updatedAt = 20L;
        source.colorPreset = "다크 브라운";
        source.cloudId = "original";
        source.ownerUid = "owner";
        source.lastSyncedUpdatedAt = 15L;

        NoteEntity copy = ConflictCopyFactory.createNoteCopy(source, 30L);

        assertEquals("계획 — 충돌 사본", copy.title);
        assertEquals(source.body, copy.body);
        assertEquals(source.createdAt, copy.createdAt);
        assertEquals(source.colorPreset, copy.colorPreset);
        assertEquals(source.ownerUid, copy.ownerUid);
        assertNotEquals(source.cloudId, copy.cloudId);
        assertTrue(copy.syncPending);
        assertEquals(0L, copy.lastSyncedUpdatedAt);
    }

    @Test public void conflictCopyPreservesEveryChecklistValueWithoutReusingIds() {
        ChecklistItemEntity first = new ChecklistItemEntity();
        first.id = 10L; first.noteId = 3L; first.text = "하나"; first.checked = true; first.position = 0;
        ChecklistItemEntity second = new ChecklistItemEntity();
        second.id = 11L; second.noteId = 3L; second.text = "둘"; second.checked = false; second.position = 1;

        List<ChecklistItemEntity> copies = ConflictCopyFactory.createItemCopies(Arrays.asList(first, second));

        assertEquals(2, copies.size());
        assertEquals("하나", copies.get(0).text);
        assertTrue(copies.get(0).checked);
        assertEquals(0, copies.get(0).position);
        assertEquals(0L, copies.get(0).id);
        assertEquals(0L, copies.get(0).noteId);
        assertEquals("둘", copies.get(1).text);
        assertFalse(copies.get(1).checked);
    }
}
