package com.blackwalnut.noteswidget;

import android.app.Activity;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

public class MainActivity extends Activity {
    private ListView noteList;
    private TextView emptyView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        noteList = findViewById(R.id.note_list);
        emptyView = findViewById(R.id.note_empty);
        noteList.setEmptyView(emptyView);
        findViewById(R.id.button_new_note).setOnClickListener(v -> openEditor(0));
        findViewById(R.id.button_add_widget).setOnClickListener(v -> requestWidget());
        noteList.setOnItemClickListener((parent, view, position, id) -> openEditor(id));
    }

    @Override
    protected void onResume() {
        super.onResume();
        reloadNotes();
    }

    private void reloadNotes() {
        AppDatabase.IO.execute(() -> {
            List<NoteEntity> notes = AppDatabase.get(this).noteDao().listNotes();
            runOnUiThread(() -> noteList.setAdapter(new NoteListAdapter(this, notes)));
        });
    }

    private void openEditor(long noteId) {
        Intent intent = new Intent(this, NoteEditActivity.class);
        if (noteId > 0) intent.putExtra(NoteEditActivity.EXTRA_NOTE_ID, noteId);
        startActivity(intent);
    }

    private void requestWidget() {
        AppWidgetManager manager = AppWidgetManager.getInstance(this);
        ComponentName provider = new ComponentName(this, NoteWidgetProvider.class);
        if (manager.isRequestPinAppWidgetSupported()) {
            PendingIntent callback = PendingIntent.getActivity(
                    this,
                    9001,
                    new Intent(this, MainActivity.class),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            manager.requestPinAppWidget(provider, null, callback);
        } else {
            Toast.makeText(this, "홈 화면을 길게 눌러 위젯 목록에서 Black Walnut 메모를 추가하세요.", Toast.LENGTH_LONG).show();
        }
    }
}
