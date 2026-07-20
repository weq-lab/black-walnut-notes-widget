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

import java.util.Locale;

public class WidgetConfigActivity extends Activity {
    private static final int REQUEST_OPEN_DOCUMENT = 41;

    private int widgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    private Uri selectedUri;
    private String selectedFileName = "메모";

    private TextView selectedFile;
    private EditText background;
    private EditText titleColor;
    private EditText bodyColor;
    private EditText accentColor;
    private EditText textSize;
    private LinearLayout previewRoot;
    private TextView previewTitle;
    private TextView previewBody;
    private TextView previewAccent;
    private Spinner colorPresetSpinner;

    private static final PalettePreset[] COLOR_PRESETS = new PalettePreset[]{
            new PalettePreset("직접 입력 / 기존 값", null, null, null, null),
            new PalettePreset("블랙 월넛", "#000000", "#5A3021", "#3A2017", "#D1AE6F"),
            new PalettePreset("다크 브라운", "#000000", "#6B351C", "#4A2414", "#9A5835"),
            new PalettePreset("스틸 브론즈", "#000000", "#8C5A32", "#5A351F", "#B77A45"),
            new PalettePreset("스틸 골드", "#000000", "#A8864B", "#6A532F", "#D4B06A"),
            new PalettePreset("앰버", "#000000", "#A85D1A", "#6F3B12", "#E49A3A"),
            new PalettePreset("번트 코퍼", "#000000", "#9C4F2E", "#5E2E1C", "#C76D3E"),
            new PalettePreset("골드 가독성", "#000000", "#D0A95B", "#9A7B3E", "#F0C86E")
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setResult(RESULT_CANCELED);
        setContentView(R.layout.activity_widget_config);
        widgetId = getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish();
            return;
        }
        bindViews();
        loadExisting();
        setupPresetSpinner();
        findViewById(R.id.button_choose_file).setOnClickListener(v -> chooseFile());
        findViewById(R.id.button_preset_match).setOnClickListener(v -> applyMatchPreset());
        findViewById(R.id.button_preset_readable).setOnClickListener(v -> applyReadablePreset());
        findViewById(R.id.button_save).setOnClickListener(v -> saveAndFinish());
        View.OnFocusChangeListener previewUpdater = (v, hasFocus) -> { if (!hasFocus) updatePreview(); };
        background.setOnFocusChangeListener(previewUpdater);
        titleColor.setOnFocusChangeListener(previewUpdater);
        bodyColor.setOnFocusChangeListener(previewUpdater);
        accentColor.setOnFocusChangeListener(previewUpdater);
        textSize.setOnFocusChangeListener(previewUpdater);
        updatePreview();
    }

    private void bindViews() {
        selectedFile = findViewById(R.id.text_selected_file);
        background = findViewById(R.id.edit_background);
        titleColor = findViewById(R.id.edit_title_color);
        bodyColor = findViewById(R.id.edit_body_color);
        accentColor = findViewById(R.id.edit_accent_color);
        textSize = findViewById(R.id.edit_text_size);
        previewRoot = findViewById(R.id.preview_root);
        previewTitle = findViewById(R.id.preview_title);
        previewBody = findViewById(R.id.preview_body);
        previewAccent = findViewById(R.id.preview_accent);
        colorPresetSpinner = findViewById(R.id.spinner_color_preset);
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
    }

    private void setupPresetSpinner() {
        String[] labels = new String[COLOR_PRESETS.length];
        for (int i = 0; i < COLOR_PRESETS.length; i++) labels[i] = COLOR_PRESETS[i].label;
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        colorPresetSpinner.setAdapter(adapter);
        colorPresetSpinner.setSelection(0, false);
        colorPresetSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position > 0) applyPreset(COLOR_PRESETS[position]);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
    }

    private void applyPreset(PalettePreset preset) {
        if (preset.background == null) return;
        background.setText(preset.background);
        titleColor.setText(preset.title);
        bodyColor.setText(preset.body);
        accentColor.setText(preset.accent);
        updatePreview();
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
        try {
            getContentResolver().takePersistableUriPermission(uri, flags);
        } catch (SecurityException error) {
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
        String last = uri.getLastPathSegment();
        return last == null ? "메모" : last;
    }

    private void applyMatchPreset() { applyPreset(COLOR_PRESETS[1]); colorPresetSpinner.setSelection(1); }

    private void applyReadablePreset() {
        background.setText(WidgetPrefs.DEFAULT_BACKGROUND);
        titleColor.setText("#945D3D");
        bodyColor.setText(WidgetPrefs.READABLE_BODY);
        accentColor.setText("#D8B977");
        updatePreview();
    }

    private void updatePreview() {
        previewRoot.setBackgroundColor(parseOr(background, Color.BLACK));
        previewTitle.setTextColor(parseOr(titleColor, Color.rgb(90, 48, 33)));
        previewBody.setTextColor(parseOr(bodyColor, Color.rgb(58, 32, 23)));
        previewAccent.setTextColor(parseOr(accentColor, Color.rgb(209, 174, 111)));
        try { previewBody.setTextSize(Float.parseFloat(textSize.getText().toString().trim())); }
        catch (Exception ignored) { previewBody.setTextSize(WidgetPrefs.DEFAULT_TEXT_SIZE); }
    }

    private int parseOr(EditText field, int fallback) {
        try { return Color.parseColor(normalizeColor(field.getText().toString())); }
        catch (Exception ignored) { return fallback; }
    }

    private void saveAndFinish() {
        if (selectedUri == null) {
            Toast.makeText(this, "먼저 메모 파일을 선택하세요.", Toast.LENGTH_SHORT).show();
            return;
        }
        String bg = validateColor(background, "배경색");
        String title = validateColor(titleColor, "제목색");
        String body = validateColor(bodyColor, "본문색");
        String accent = validateColor(accentColor, "강조색");
        if (bg == null || title == null || body == null || accent == null) return;
        float size;
        try {
            size = Float.parseFloat(textSize.getText().toString().trim());
            if (size < 10f || size > 30f) throw new NumberFormatException();
        } catch (NumberFormatException error) {
            textSize.setError("10~30 사이 숫자를 입력하세요.");
            return;
        }
        WidgetPrefs.save(this, widgetId, selectedUri.toString(), selectedFileName, bg, title, body, accent, size);
        AppWidgetManager manager = AppWidgetManager.getInstance(this);
        NoteWidgetProvider.updateWidget(this, manager, widgetId);
        setResult(RESULT_OK, new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId));
        finish();
    }

    private String validateColor(EditText field, String label) {
        String value = normalizeColor(field.getText().toString());
        try { Color.parseColor(value); return value; }
        catch (Exception error) { field.setError(label + "은 #RRGGBB 또는 #AARRGGBB 형식이어야 합니다."); return null; }
    }

    private String normalizeColor(String value) {
        String result = value == null ? "" : value.trim().toUpperCase(Locale.US);
        return result.startsWith("#") ? result : "#" + result;
    }

    private static final class PalettePreset {
        final String label, background, title, body, accent;
        PalettePreset(String label, String background, String title, String body, String accent) {
            this.label = label; this.background = background; this.title = title; this.body = body; this.accent = accent;
        }
    }
}
