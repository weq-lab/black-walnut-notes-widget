package com.blackwalnut.noteswidget;

import androidx.room.Embedded;
import androidx.room.Relation;

import java.util.List;

public class NoteWithItems {
    @Embedded
    public NoteEntity note;

    @Relation(parentColumn = "id", entityColumn = "noteId")
    public List<ChecklistItemEntity> items;
}
