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
    private final EditRevisionTracker revisions = new EditRevisionTracker();
    private long noteId;
    private NoteEntity note;
    private boolean loading = true;
    private boolean deleting;
    private boolean saving;
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
            markChanged();
        });
        findViewById(R.id.button_delete_note).setOnClickListener(v -> confirmDelete());
        TextWatcher watcher = new SimpleWatcher(this::markChanged);
        title.addTextChangedListener(watcher);
        body.addTextChangedListener(watcher);
        title.addTextChangedListener(new TypographyWatcher(title, true));
        body.addTextChangedListener(new TypographyWatcher(body, false));
        NoteTypography.applyTitle(this, title, title.getText());
        NoteTypography.applyBody(this, body, body.getText());
        preset.setOnItemSelectedListener(new SimpleItemSelectedListener(this::presetChanged));
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
            FirestoreSyncManager.prepareNewNote(this, created);
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
        revisions.reset(captureDraft().contentKey);
        setEditorEnabled(true);
        status.setText("저장됨");
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
        NoteTypography.applyBody(this, field, text);
        box.setOnCheckedChangeListener((button, value) -> markChanged());
        field.addTextChangedListener(new SimpleWatcher(this::markChanged));
        field.addTextChangedListener(new TypographyWatcher(field, false));
        row.findViewById(R.id.button_remove_check_item).setOnClickListener(v -> {
            checklist.removeView(row);
            markChanged();
        });
        checklist.addView(row);
        if (text.isEmpty()) field.requestFocus();
    }

    private void markChanged() {
        if (loading || deleting || note == null) return;
        revisions.changed();
        status.setText("저장 중…");
        handler.removeCallbacks(autoSave);
        handler.postDelayed(autoSave, AUTO_SAVE_DELAY_MS);
    }

    private void presetChanged() {
        if (loading || note == null) return;
        String selected = ColorPresets.ALL.get(preset.getSelectedItemPosition()).name;
        if (!selected.equals(note.colorPreset)) markChanged();
    }

    private void saveNow() {
        if (loading || deleting || note == null || saving) return;
        handler.removeCallbacks(autoSave);
        DraftValues draft = captureDraft();
        EditRevisionTracker.Attempt attempt = revisions.begin(draft.contentKey);
        if (attempt == null) {
            status.setText("저장됨");
            return;
        }
        saving = true;
        NoteEntity toSave = copyNote(note);
        toSave.title = draft.title;
        toSave.body = draft.body;
        toSave.colorPreset = draft.preset;
        toSave.updatedAt = Math.max(System.currentTimeMillis(), note.updatedAt + 1L);
        FirestoreSyncManager.prepareLocalEdit(this, toSave);
        AppDatabase.IO.execute(() -> {
            try {
                NoteDao dao = AppDatabase.get(this).noteDao();
                dao.updateNote(toSave);
                dao.replaceItems(noteId, draft.items);
                FirestoreSyncManager.kick(this);
                NoteWidgetProvider.notifyAllWidgets(this);
                runOnUiThread(() -> {
                    note = toSave;
                    revisions.complete(attempt);
                    saving = false;
                    if (revisions.isDirty()) {
                        status.setText("저장 중…");
                        handler.postDelayed(autoSave, AUTO_SAVE_DELAY_MS);
                    } else {
                        status.setText("저장됨");
                    }
                });
            } catch (RuntimeException error) {
                runOnUiThread(() -> {
                    saving = false;
                    status.setText("저장 실패 · 다시 시도");
                });
            }
        });
    }

    private DraftValues captureDraft() {
        String nextTitle = title.getText().toString();
        String nextBody = body.getText().toString();
        String nextPreset = ColorPresets.ALL.get(preset.getSelectedItemPosition()).name;
        List<ChecklistItemEntity> items = new ArrayList<>();
        StringBuilder content = new StringBuilder();
        appendContent(content, nextTitle);
        appendContent(content, nextBody);
        appendContent(content, nextPreset);
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
            appendContent(content, item.text);
            content.append(item.checked ? '1' : '0').append(';');
        }
        return new DraftValues(nextTitle, nextBody, nextPreset, items, content.toString());
    }

    private static void appendContent(StringBuilder target, String value) {
        target.append(value.length()).append(':').append(value).append(';');
    }

    private static NoteEntity copyNote(NoteEntity source) {
        NoteEntity copy = new NoteEntity();
        copy.id = source.id;
        copy.title = source.title;
        copy.body = source.body;
        copy.createdAt = source.createdAt;
        copy.updatedAt = source.updatedAt;
        copy.colorPreset = source.colorPreset;
        copy.cloudId = source.cloudId;
        copy.ownerUid = source.ownerUid;
        copy.syncPending = source.syncPending;
        copy.lastSyncedUpdatedAt = source.lastSyncedUpdatedAt;
        return copy;
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
                        AppDatabase.get(this).noteDao().deleteForSync(note);
                        FirestoreSyncManager.kick(this);
                        NoteWidgetProvider.notifyAllWidgets(this);
                        runOnUiThread(this::finish);
                    });
                })
                .show();
    }

    @Override
    protected void onPause() {
        if (revisions.isDirty()) saveNow();
        super.onPause();
    }

    private static final class DraftValues {
        final String title;
        final String body;
        final String preset;
        final List<ChecklistItemEntity> items;
        final String contentKey;

        DraftValues(String title, String body, String preset, List<ChecklistItemEntity> items, String contentKey) {
            this.title = title;
            this.body = body;
            this.preset = preset;
            this.items = items;
            this.contentKey = contentKey;
        }
    }

    private static final class SimpleWatcher implements TextWatcher {
        private final Runnable changed;
        SimpleWatcher(Runnable changed) { this.changed = changed; }
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) { changed.run(); }
        @Override public void afterTextChanged(Editable s) { }
    }

    private final class TypographyWatcher implements TextWatcher {
        private final TextView view;
        private final boolean titleRole;
        TypographyWatcher(TextView view, boolean titleRole) {
            this.view = view;
            this.titleRole = titleRole;
        }
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
            if (titleRole) NoteTypography.applyTitle(NoteEditActivity.this, view, s);
            else NoteTypography.applyBody(NoteEditActivity.this, view, s);
        }
        @Override public void afterTextChanged(Editable s) { }
    }
}
