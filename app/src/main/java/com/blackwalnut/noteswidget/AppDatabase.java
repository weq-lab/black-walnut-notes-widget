package com.blackwalnut.noteswidget;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Database(
        entities = {NoteEntity.class, ChecklistItemEntity.class, PendingDeleteEntity.class, SyncConflictEntity.class},
        version = 4,
        exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {
    private static volatile AppDatabase instance;
    static final ExecutorService IO = Executors.newSingleThreadExecutor();
    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE notes ADD COLUMN cloudId TEXT NOT NULL DEFAULT ''");
            database.execSQL("ALTER TABLE notes ADD COLUMN ownerUid TEXT NOT NULL DEFAULT ''");
            database.execSQL("ALTER TABLE notes ADD COLUMN syncPending INTEGER NOT NULL DEFAULT 0");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_notes_ownerUid_cloudId ON notes(ownerUid, cloudId)");
            database.execSQL("CREATE TABLE IF NOT EXISTS pending_deletes (ownerUid TEXT NOT NULL, cloudId TEXT NOT NULL, deletedAt INTEGER NOT NULL, PRIMARY KEY(ownerUid, cloudId))");
            database.execSQL("CREATE TABLE IF NOT EXISTS sync_conflicts (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, localNoteId INTEGER NOT NULL, ownerUid TEXT NOT NULL, cloudId TEXT NOT NULL, detectedAt INTEGER NOT NULL, localUpdatedAt INTEGER NOT NULL, remoteUpdatedAt INTEGER NOT NULL, localSnapshot TEXT NOT NULL)");
        }
    };
    static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE notes ADD COLUMN lastSyncedUpdatedAt INTEGER NOT NULL DEFAULT 0");
        }
    };
    static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE pending_deletes ADD COLUMN expectedRemoteUpdatedAt INTEGER NOT NULL DEFAULT 0");
        }
    };

    public abstract NoteDao noteDao();

    static AppDatabase get(Context context) {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(
                            context.getApplicationContext(),
                            AppDatabase.class,
                            "black-walnut-notes.db"
                    ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build();
                }
            }
        }
        return instance;
    }
}
