package com.blackwalnut.noteswidget;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class RoomPersistenceTest {
    private AppDatabase database;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase.class).allowMainThreadQueries().build();
    }

    @After
    public void tearDown() { database.close(); }

    @Test
    public void persistsNoteAndChecklistAndTogglesItem() {
        NoteEntity note = new NoteEntity();
        note.title = "장보기";
        note.body = "오늘 살 것";
        note.createdAt = 1;
        note.updatedAt = 1;
        note.id = database.noteDao().insertNote(note);
        ChecklistItemEntity item = new ChecklistItemEntity();
        item.noteId = note.id;
        item.text = "호두";
        database.noteDao().insertItems(Collections.singletonList(item));
        NoteWithItems stored = database.noteDao().getNoteWithItems(note.id);
        assertEquals("장보기", stored.note.title);
        assertEquals("호두", stored.items.get(0).text);
        assertEquals("", stored.note.cloudId);
        database.noteDao().toggleItemAndTouch(stored.items.get(0).id, note.id, 2);
        assertTrue(database.noteDao().getItems(note.id).get(0).checked);
    }
}
