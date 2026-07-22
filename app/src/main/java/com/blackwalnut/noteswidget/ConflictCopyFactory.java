package com.blackwalnut.noteswidget;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

final class ConflictCopyFactory {
    static final String TITLE_SUFFIX = " — 충돌 사본";

    static NoteEntity createNoteCopy(NoteEntity source, long now) {
        NoteEntity copy = new NoteEntity();
        copy.title = source.title + TITLE_SUFFIX;
        copy.body = source.body;
        copy.createdAt = source.createdAt;
        copy.updatedAt = Math.max(now, source.updatedAt + 1L);
        copy.colorPreset = source.colorPreset;
        copy.cloudId = UUID.randomUUID().toString();
        copy.ownerUid = source.ownerUid;
        copy.syncPending = true;
        copy.lastSyncedUpdatedAt = 0L;
        return copy;
    }

    static List<ChecklistItemEntity> createItemCopies(List<ChecklistItemEntity> source) {
        List<ChecklistItemEntity> copies = new ArrayList<>();
        for (ChecklistItemEntity item : source) {
            ChecklistItemEntity copy = new ChecklistItemEntity();
            copy.text = item.text;
            copy.checked = item.checked;
            copy.position = item.position;
            copies.add(copy);
        }
        return copies;
    }

    private ConflictCopyFactory() { }
}
