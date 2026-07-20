package com.blackwalnut.noteswidget;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;

import java.util.List;

@Dao
public abstract class NoteDao {
    @Query("SELECT * FROM notes ORDER BY updatedAt DESC")
    public abstract List<NoteEntity> listNotes();

    @Query("SELECT * FROM notes WHERE id = :id LIMIT 1")
    public abstract NoteEntity getNote(long id);

    @Query("SELECT * FROM notes WHERE ownerUid = :ownerUid AND cloudId = :cloudId LIMIT 1")
    public abstract NoteEntity getNoteByCloudId(String ownerUid, String cloudId);

    @Query("SELECT * FROM notes WHERE ownerUid = :ownerUid AND cloudId != ''")
    public abstract List<NoteEntity> listLinkedNotes(String ownerUid);

    @Query("SELECT * FROM notes WHERE ownerUid = :ownerUid AND cloudId != '' AND syncPending = 1")
    public abstract List<NoteEntity> listPendingNotes(String ownerUid);

    @Query("SELECT * FROM notes WHERE cloudId = '' ORDER BY updatedAt DESC")
    public abstract List<NoteEntity> listImportableNotes();

    @Query("SELECT * FROM checklist_items WHERE noteId = :noteId ORDER BY position, id")
    public abstract List<ChecklistItemEntity> getItems(long noteId);

    @Transaction
    @Query("SELECT * FROM notes WHERE id = :id LIMIT 1")
    public abstract NoteWithItems getNoteWithItems(long id);

    @Insert
    public abstract long insertNote(NoteEntity note);

    @Update
    public abstract void updateNote(NoteEntity note);

    @Delete
    public abstract void deleteNote(NoteEntity note);

    @Insert
    public abstract void insertItems(List<ChecklistItemEntity> items);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public abstract void insertPendingDelete(PendingDeleteEntity pendingDelete);

    @Insert
    public abstract void insertConflict(SyncConflictEntity conflict);

    @Query("SELECT * FROM pending_deletes WHERE ownerUid = :ownerUid")
    public abstract List<PendingDeleteEntity> listPendingDeletes(String ownerUid);

    @Query("DELETE FROM pending_deletes WHERE ownerUid = :ownerUid AND cloudId = :cloudId")
    public abstract void deletePendingDelete(String ownerUid, String cloudId);

    @Query("DELETE FROM checklist_items WHERE noteId = :noteId")
    public abstract void deleteItems(long noteId);

    @Query("UPDATE checklist_items SET checked = CASE checked WHEN 1 THEN 0 ELSE 1 END WHERE id = :itemId")
    public abstract void toggleItem(long itemId);

    @Query("UPDATE notes SET updatedAt = :updatedAt, syncPending = CASE WHEN cloudId != '' THEN 1 ELSE syncPending END WHERE id = :noteId")
    public abstract void touchNote(long noteId, long updatedAt);

    @Query("UPDATE notes SET syncPending = 0 WHERE id = :noteId AND updatedAt = :expectedUpdatedAt")
    public abstract void markSynced(long noteId, long expectedUpdatedAt);

    @Query("UPDATE notes SET ownerUid = :ownerUid, cloudId = :cloudId, syncPending = 1 WHERE id = :noteId AND cloudId = ''")
    public abstract void linkForImport(long noteId, String ownerUid, String cloudId);

    @Query("DELETE FROM notes WHERE ownerUid = :ownerUid AND cloudId = :cloudId AND syncPending = 0")
    public abstract void deleteRemoteNoteIfClean(String ownerUid, String cloudId);

    @Transaction
    public void replaceItems(long noteId, List<ChecklistItemEntity> items) {
        deleteItems(noteId);
        if (!items.isEmpty()) insertItems(items);
    }

    @Transaction
    public void toggleItemAndTouch(long itemId, long noteId, long updatedAt) {
        toggleItem(itemId);
        touchNote(noteId, updatedAt);
    }

    @Transaction
    public void deleteForSync(NoteEntity note) {
        if (!note.ownerUid.isEmpty() && !note.cloudId.isEmpty()) {
            PendingDeleteEntity pending = new PendingDeleteEntity();
            pending.ownerUid = note.ownerUid;
            pending.cloudId = note.cloudId;
            pending.deletedAt = System.currentTimeMillis();
            insertPendingDelete(pending);
        }
        deleteNote(note);
    }

    @Transaction
    public long applyRemote(NoteEntity remote, List<ChecklistItemEntity> items) {
        NoteEntity existing = getNoteByCloudId(remote.ownerUid, remote.cloudId);
        long noteId;
        if (existing == null) {
            noteId = insertNote(remote);
        } else {
            noteId = existing.id;
            remote.id = noteId;
            updateNote(remote);
            deleteItems(noteId);
        }
        for (ChecklistItemEntity item : items) {
            item.id = 0;
            item.noteId = noteId;
        }
        if (!items.isEmpty()) insertItems(items);
        return noteId;
    }
}
