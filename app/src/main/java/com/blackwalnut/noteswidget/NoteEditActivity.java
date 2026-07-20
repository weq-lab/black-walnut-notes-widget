package com.blackwalnut.noteswidget;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class NoteEditActivity extends Activity {
    public static final String EXTRA_NOTE_ID = "note_id";
    private static final long AUTO_SAVE_DELAY_MS = 500;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable autoSave = this::saveNow;
    private long noteId;
    private NoteEntity note;
    private boolean loading = true;
    private boolean deleting;
    private EditText title;
    private EditText body;
    private Spinner preset;
    private LinearLayout checklist;
    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_note_edit);
        title = findViewById(R.id.edit_note_title);
        body = findViewById(R.id.edit_note_body);
        preset = findViewById(R.id.edit_note_preset);
        checklist = findViewById(R.id.checklist_container);
        status = findViewById(R.id.auto_save_status);
        preset.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, ColorPresets.names()));
        findViewById(R.id.button_add_check_item).setOnClickListener(v -> {
            addChecklistRow("", false);
            scheduleSave();
        });
        findViewById(R.id.button_delete_note).setOnClickListener(v -> confirmDelete());
        TextWatcher watcher = new SimpleWatcher(this::scheduleSave);
        title.addTextChangedListener(watcher);
        body.addTextChangedListener(watcher);
        preset.setOnItemSelectedListener(new SimpleItemSelectedListener(this::scheduleSave));
        noteId = getIntent().getLongExtra(EXTRA_NOTE_ID, 0);
        if (noteId == 0) createNote(); else loadNote();
    }

    private void createNote() {
        setEditorEnabled(false);
        AppDatabase.IO.execute(() -> {
            long now = System.currentTimeMillis();
            NoteEntity created = new NoteEntity();
            created.createdAt = now;
            created.updatedAt = now;
            created.id = AppDatabase.get(this).noteDao().insertNote(created);
            runOnUiThread(() -> showNote(created, new ArrayList<>()));
        });
    }

    private void loadNote() {
        setEditorEnabled(false);
        AppDatabase.IO.execute(() -> {
            NoteWithItems loaded = AppDatabase.get(this).noteDao().getNoteWithItems(noteId);
            runOnUiThread(() -> {
                if (loaded == null || loaded.note == null) { finish(); return; }
                showNote(loaded.note, loaded.items);
            });
        });
    }

    private void showNote(NoteEntity loaded, List<ChecklistItemEntity> items) {
        note = loaded;
        noteId = loaded.id;
        title.setText(loaded.title);
        body.setText(loaded.body);
        int selected = 1;
        for (int i = 0; i < ColorPresets.ALL.size(); i++) {
            if (ColorPresets.ALL.get(i).name.equals(loaded.colorPreset)) selected = i;
        }
        preset.setSelection(selected);
        checklist.removeAllViews();
        for (ChecklistItemEntity item : items) addChecklistRow(item.text, item.checked);
        loading = false;
        setEditorEnabled(true);
        status.setText("자동 저장 켜짐");
        title.requestFocus();
    }

    private void setEditorEnabled(boolean enabled) {
        title.setEnabled(enabled);
        body.setEnabled(enabled);
        preset.setEnabled(enabled);
        findViewById(R.id.button_add_check_item).setEnabled(enabled);
    }

    private void addChecklistRow(String text, boolean checked) {
        View row = LayoutInflater.from(this).inflate(R.layout.checklist_edit_item, checklist, false);
        CheckBox box = row.findViewById(R.id.check_item_box);
        EditText field = row.findViewById(R.id.check_item_text);
        box.setChecked(checked);
        field.setText(text);
        box.setOnCheckedChangeListener((button, value) -> scheduleSave());
        field.addTextChangedListener(new SimpleWatcher(this::scheduleSave));
        row.findViewById(R.id.button_remove_check_item).setOnClickListener(v -> {
            checklist.removeView(row);
            scheduleSave();
        });
        checklist.addView(row);
        if (text.isEmpty()) field.requestFocus();
    }

    private void scheduleSave() {
        if (loading || deleting || note == null) return;
        status.setText("저장 중…");
        handler.removeCallbacks(autoSave);
        handler.postDelayed(autoSave, AUTO_SAVE_DELAY_MS);
    }

    private void saveNow() {
        if (loading || deleting || note == null) return;
        handler.removeCallbacks(autoSave);
        final String nextTitle = title.getText().toString();
        final String nextBody = body.getText().toString();
        final String nextPreset = ColorPresets.ALL.get(preset.getSelectedItemPosition()).name;
        final List<ChecklistItemEntity> items = new ArrayList<>();
        for (int i = 0; i < checklist.getChildCount(); i++) {
            View row = checklist.getChildAt(i);
            String text = ((EditText) row.findViewById(R.id.check_item_text)).getText().toString().trim();
            if (text.isEmpty()) continue;
            ChecklistItemEntity item = new ChecklistItemEntity();
            item.noteId = noteId;
            item.text = text;
            item.checked = ((CheckBox) row.findViewById(R.id.check_item_box)).isChecked();
            item.position = items.size();
            items.add(item);
        }
        note.title = nextTitle;
        note.body = nextBody;
        note.colorPreset = nextPreset;
        note.updatedAt = System.currentTimeMillis();
        AppDatabase.IO.execute(() -> {
            NoteDao dao = AppDatabase.get(this).noteDao();
            dao.updateNote(note);
            dao.replaceItems(noteId, items);
            NoteWidgetProvider.notifyAllWidgets(this);
            runOnUiThread(() -> status.setText("저장됨"));
        });
    }

    private void confirmDelete() {
        if (note == null) return;
        new AlertDialog.Builder(this)
                .setMessage("이 노트를 삭제할까요?")
                .setNegativeButton("취소", null)
                .setPositiveButton("삭제", (dialog, which) -> {
                    deleting = true;
                    handler.removeCallbacks(autoSave);
                    AppDatabase.IO.execute(() -> {
                        AppDatabase.get(this).noteDao().deleteNote(note);
                        NoteWidgetProvider.notifyAllWidgets(this);
                        runOnUiThread(this::finish);
                    });
                })
                .show();
    }

    @Override
    protected void onPause() {
        saveNow();
        super.onPause();
    }

    private static final class SimpleWatcher implements TextWatcher {
        private final Runnable changed;
        SimpleWatcher(Runnable changed) { this.changed = changed; }
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) { changed.run(); }
        @Override public void afterTextChanged(Editable s) { }
    }
}
