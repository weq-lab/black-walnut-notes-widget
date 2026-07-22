package com.blackwalnut.noteswidget;

import androidx.annotation.NonNull;
import androidx.room.Entity;

@Entity(tableName = "pending_deletes", primaryKeys = {"ownerUid", "cloudId"})
public class PendingDeleteEntity {
    @NonNull public String ownerUid = "";
    @NonNull public String cloudId = "";
    public long deletedAt;
    public long expectedRemoteUpdatedAt;
}
