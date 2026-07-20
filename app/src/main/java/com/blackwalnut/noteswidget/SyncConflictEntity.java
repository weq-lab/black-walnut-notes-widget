package com.blackwalnut.noteswidget;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "sync_conflicts")
public class SyncConflictEntity {
    @PrimaryKey(autoGenerate = true) public long id;
    public long localNoteId;
    public String ownerUid = "";
    public String cloudId = "";
    public long detectedAt;
    public long localUpdatedAt;
    public long remoteUpdatedAt;
    public String localSnapshot = "";
}
