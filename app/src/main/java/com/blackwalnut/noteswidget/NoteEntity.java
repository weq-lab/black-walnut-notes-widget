package com.blackwalnut.noteswidget;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "notes")
public class NoteEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;
    public String title = "";
    public String body = "";
    public long createdAt;
    public long updatedAt;
    public String colorPreset = ColorPresets.BLACK_WALNUT;
}
