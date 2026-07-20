package com.blackwalnut.noteswidget;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "notes", indices = {@Index(value = {"ownerUid", "cloudId"})})
public class NoteEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;
    public String title = "";
    public String body = "";
    public long createdAt;
    public long updatedAt;
    public String colorPreset = ColorPresets.BLACK_WALNUT;
    public String cloudId = "";
    public String ownerUid = "";
    public boolean syncPending;
}
