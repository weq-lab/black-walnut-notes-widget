package com.blackwalnut.noteswidget;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Database(entities = {NoteEntity.class, ChecklistItemEntity.class}, version = 1, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {
    private static volatile AppDatabase instance;
    static final ExecutorService IO = Executors.newSingleThreadExecutor();

    public abstract NoteDao noteDao();

    static AppDatabase get(Context context) {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(
                            context.getApplicationContext(),
                            AppDatabase.class,
                            "black-walnut-notes.db"
                    ).build();
                }
            }
        }
        return instance;
    }
}
