package com.blackwalnut.noteswidget;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class WidgetConfigActivity extends Activity {
    private static final int REQUEST_OPEN_DOCUMENT = 41;
    private int widgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    private Uri selectedUri;
    private String selectedFileName = "메모";
    private final List<NoteEntity> notes = new ArrayList<>();
    private Spinner sourceSpinner;
    private Spinner noteSpinner;
    private Spinner presetSpinner;
    private LinearLayout localSection;
    private LinearLayout fileSection;
    private TextView selectedFile;
    private EditText background;
    private EditText titleColor;
    private EditText bodyColor;
    private EditText accentColor;
    private EditText textSize;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setResult(RESULT_CANCELED);
        setContentView(R.layout.activity_widget_config);
        widgetId = getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return; }
        bindViews();
        setupSpinners();
        loadExisting();
        loadNotes();
        findViewById(R.id.button_choose_file).setOnClickListener(v -> chooseFile());
        findViewById(R.id.button_save).setOnClickListener(v -> saveAndFinish());
    }

    private void bindViews() {
        sourceSpinner = findViewById(R.id.spinner_widget_source);
        noteSpinner = findViewById(R.id.spinner_widget_note);
        presetSpinner = findViewById(R.id.spinner_color_preset);
        localSection = findViewById(R.id.local_note_section);
        fileSection = findViewById(R.id.file_note_section);
        selectedFile = findViewById(R.id.text_selected_file);
        background = findViewById(R.id.edit_background);
        titleColor = findViewById(R.id.edit_title_color);
        bodyColor = findViewById(R.id.edit_body_color);
        accentColor = findViewById(R.id.edit_accent_color);
        textSize = findViewById(R.id.edit_text_size);
    }

    private void setupSpinners() {
        sourceSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"앱 내부 로컬 노트", "TXT / Markdown 파일"}));
        sourceSpinner.setOnItemSelectedListener(new SimpleItemSelectedListener(this::updateSourceVisibility));
        presetSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, ColorPresets.names()));
        presetSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                ColorPresets.Preset preset = ColorPresets.ALL.get(position);
                if (preset.background != null) {
                    background.setText(preset.background);
                    titleColor.setText(preset.title);
                    bodyColor.setText(preset.body);
                    accentColor.setText(preset.accent);
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
    }

    private void loadExisting() {
        String uriText = WidgetPrefs.uri(this, widgetId);
        if (uriText != null) selectedUri = Uri.parse(uriText);
        selectedFileName = WidgetPrefs.fileName(this, widgetId);
        selectedFile.setText(selectedUri == null ? "선택한 파일 없음" : selectedFileName);
        background.setText(WidgetPrefs.background(this, widgetId));
        titleColor.setText(WidgetPrefs.title(this, widgetId));
        bodyColor.setText(WidgetPrefs.body(this, widgetId));
        accentColor.setText(WidgetPrefs.accent(this, widgetId));
        textSize.setText(String.format(Locale.US, "%.0f", WidgetPrefs.textSize(this, widgetId)));
        sourceSpinner.setSelection(WidgetPrefs.SOURCE_FILE.equals(WidgetPrefs.source(this, widgetId)) ? 1 : 0);
        String presetName = WidgetPrefs.preset(this, widgetId);
        for (int i = 0; i < ColorPresets.ALL.size(); i++) if (ColorPresets.ALL.get(i).name.equals(presetName)) presetSpinner.setSelection(i);
        updateSourceVisibility();
    }

    private void loadNotes() {
        AppDatabase.IO.execute(() -> {
            List<NoteEntity> loaded = AppDatabase.get(this).noteDao().listNotes();
            runOnUiThread(() -> {
                notes.clear();
                notes.addAll(loaded);
                List<String> labels = new ArrayList<>();
                for (NoteEntity note : notes) labels.add(note.title.trim().isEmpty() ? "제목 없음" : note.title);
                noteSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, labels));
                long saved = WidgetPrefs.noteId(this, widgetId);
                for (int i = 0; i < notes.size(); i++) if (notes.get(i).id == saved) noteSpinner.setSelection(i);
            });
        });
    }

    private void updateSourceVisibility() {
        boolean local = sourceSpinner.getSelectedItemPosition() == 0;
        localSection.setVisibility(local ? View.VISIBLE : View.GONE);
        fileSection.setVisibility(local ? View.GONE : View.VISIBLE);
    }

    private void chooseFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("text/*")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_OPEN_DOCUMENT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_OPEN_DOCUMENT || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        int flags = data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION;
        try { getContentResolver().takePersistableUriPermission(uri, flags); }
        catch (SecurityException error) {
            Toast.makeText(this, "이 문서 제공자는 영구 접근 권한을 지원하지 않습니다.", Toast.LENGTH_LONG).show();
            return;
        }
        selectedUri = uri;
        selectedFileName = displayName(uri);
        selectedFile.setText(selectedFileName);
    }

    private String displayName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) return cursor.getString(index);
            }
        } catch (Exception ignored) { }
        return uri.getLastPathSegment() == null ? "메모" : uri.getLastPathSegment();
    }

    private void saveAndFinish() {
        String bg = validateColor(background, "배경색");
        String title = validateColor(titleColor, "제목색");
        String body = validateColor(bodyColor, "본문색");
        String accent = validateColor(accentColor, "강조색");
        if (bg == null || title == null || body == null || accent == null) return;
        float size;
        try {
            size = Float.parseFloat(textSize.getText().toString().trim());
            if (size < 10f || size > 30f) throw new NumberFormatException();
        } catch (NumberFormatException error) { textSize.setError("10~30 사이 숫자를 입력하세요."); return; }
        String preset = ColorPresets.ALL.get(presetSpinner.getSelectedItemPosition()).name;
        if (sourceSpinner.getSelectedItemPosition() == 0) {
            if (notes.isEmpty()) { Toast.makeText(this, "앱에서 먼저 노트를 만들어 주세요.", Toast.LENGTH_SHORT).show(); return; }
            long noteId = notes.get(noteSpinner.getSelectedItemPosition()).id;
            WidgetPrefs.saveLocal(this, widgetId, noteId, preset, bg, title, body, accent, size);
        } else {
            if (selectedUri == null) { Toast.makeText(this, "메모 파일을 선택하세요.", Toast.LENGTH_SHORT).show(); return; }
            WidgetPrefs.saveFile(this, widgetId, selectedUri.toString(), selectedFileName, preset, bg, title, body, accent, size);
        }
        NoteWidgetProvider.updateWidget(this, AppWidgetManager.getInstance(this), widgetId);
        setResult(RESULT_OK, new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId));
        finish();
    }

    private String validateColor(EditText field, String label) {
        String value = field.getText().toString().trim().toUpperCase(Locale.US);
        if (!value.startsWith("#")) value = "#" + value;
        try { Color.parseColor(value); return value; }
        catch (Exception error) { field.setError(label + "은 #RRGGBB 형식이어야 합니다."); return null; }
    }
}
