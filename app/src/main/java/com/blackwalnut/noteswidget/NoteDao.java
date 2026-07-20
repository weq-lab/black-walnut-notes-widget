package com.blackwalnut.noteswidget;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
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

    @Query("DELETE FROM checklist_items WHERE noteId = :noteId")
    public abstract void deleteItems(long noteId);

    @Query("UPDATE checklist_items SET checked = CASE checked WHEN 1 THEN 0 ELSE 1 END WHERE id = :itemId")
    public abstract void toggleItem(long itemId);

    @Query("UPDATE notes SET updatedAt = :updatedAt WHERE id = :noteId")
    public abstract void touchNote(long noteId, long updatedAt);

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
}
