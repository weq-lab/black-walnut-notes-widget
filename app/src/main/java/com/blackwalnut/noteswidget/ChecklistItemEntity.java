package com.blackwalnut.noteswidget;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "checklist_items",
        foreignKeys = @ForeignKey(
                entity = NoteEntity.class,
                parentColumns = "id",
                childColumns = "noteId",
                onDelete = ForeignKey.CASCADE
        ),
        indices = {@Index("noteId")}
)
public class ChecklistItemEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;
    public long noteId;
    public String text = "";
    public boolean checked;
    public int position;
}
