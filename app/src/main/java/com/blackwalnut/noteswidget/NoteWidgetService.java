package com.blackwalnut.noteswidget;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class NoteWidgetService extends RemoteViewsService {
    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new Factory(getApplicationContext(), intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID));
    }

    private static final class Row {
        static final int BODY = 0;
        static final int CHECK = 1;
        final int type;
        final String text;
        final long noteId;
        final long itemId;
        final boolean checked;
        Row(int type, String text, long noteId, long itemId, boolean checked) {
            this.type = type; this.text = text; this.noteId = noteId; this.itemId = itemId; this.checked = checked;
        }
    }

    private static final class Factory implements RemoteViewsFactory {
        private final Context context;
        private final int widgetId;
        private final List<Row> rows = new ArrayList<>();
        private int background;
        private int bodyColor;
        private int accentColor;

        Factory(Context context, int widgetId) { this.context = context; this.widgetId = widgetId; }
        @Override public void onCreate() { }

        @Override
        public void onDataSetChanged() {
            rows.clear();
            background = parse(WidgetPrefs.background(context, widgetId), Color.BLACK);
            bodyColor = parse(WidgetPrefs.body(context, widgetId), Color.rgb(58, 32, 23));
            accentColor = parse(WidgetPrefs.accent(context, widgetId), Color.rgb(209, 174, 111));
            if (WidgetPrefs.SOURCE_FILE.equals(WidgetPrefs.source(context, widgetId))) loadFile(); else loadLocal();
        }

        private void loadLocal() {
            long noteId = WidgetPrefs.noteId(context, widgetId);
            NoteWithItems data = noteId > 0 ? AppDatabase.get(context).noteDao().getNoteWithItems(noteId) : null;
            if (data == null || data.note == null) return;
            if (!data.note.body.trim().isEmpty()) rows.add(new Row(Row.BODY, data.note.body, noteId, 0, false));
            for (ChecklistItemEntity item : data.items) rows.add(new Row(Row.CHECK, item.text, noteId, item.id, item.checked));
        }

        private void loadFile() {
            String uriText = WidgetPrefs.uri(context, widgetId);
            if (uriText == null) return;
            try (InputStream input = context.getContentResolver().openInputStream(Uri.parse(uriText));
                 BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                StringBuilder raw = new StringBuilder();
                char[] buffer = new char[4096];
                int count;
                while ((count = reader.read(buffer)) != -1 && raw.length() < 200000) raw.append(buffer, 0, count);
                MarkdownRenderer.RenderedNote rendered = MarkdownRenderer.render(raw.toString(), WidgetPrefs.fileName(context, widgetId), accentColor);
                String[] lines = rendered.body.toString().split("\\n");
                for (int i = 0; i < lines.length && i < 100; i++) if (!lines[i].trim().isEmpty()) rows.add(new Row(Row.BODY, lines[i], 0, 0, false));
            } catch (Exception error) {
                rows.add(new Row(Row.BODY, "위젯 설정에서 파일을 다시 선택하세요.", 0, 0, false));
            }
        }

        @Override public void onDestroy() { rows.clear(); }
        @Override public int getCount() { return rows.size(); }

        @Override
        public RemoteViews getViewAt(int position) {
            if (position < 0 || position >= rows.size()) return null;
            Row row = rows.get(position);
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_note_row);
            views.setInt(R.id.widget_row_root, "setBackgroundColor", background);
            String text = row.type == Row.CHECK ? (row.checked ? "☑  " : "☐  ") + row.text : row.text;
            views.setTextViewText(R.id.widget_row_text, text);
            views.setTextColor(R.id.widget_row_text, row.type == Row.CHECK ? accentColor : bodyColor);
            views.setTextViewTextSize(
                    R.id.widget_row_text,
                    android.util.TypedValue.COMPLEX_UNIT_SP,
                    WidgetPrefs.textSize(context, widgetId)
            );
            Intent fill = new Intent();
            fill.putExtra(NoteWidgetProvider.EXTRA_WIDGET_ID, widgetId);
            if (WidgetPrefs.SOURCE_FILE.equals(WidgetPrefs.source(context, widgetId))) {
                fill.setAction(NoteWidgetProvider.ACTION_OPEN_FILE);
            } else if (row.type == Row.CHECK) {
                fill.setAction(NoteWidgetProvider.ACTION_TOGGLE_ITEM)
                        .putExtra(NoteWidgetProvider.EXTRA_NOTE_ID, row.noteId)
                        .putExtra(NoteWidgetProvider.EXTRA_ITEM_ID, row.itemId);
            } else {
                fill.setAction(NoteWidgetProvider.ACTION_OPEN_NOTE).putExtra(NoteWidgetProvider.EXTRA_NOTE_ID, row.noteId);
            }
            views.setOnClickFillInIntent(R.id.widget_row_root, fill);
            return views;
        }

        @Override public RemoteViews getLoadingView() { return null; }
        @Override public int getViewTypeCount() { return 1; }
        @Override public long getItemId(int position) { return position < rows.size() ? rows.get(position).itemId + position : position; }
        @Override public boolean hasStableIds() { return false; }
        private int parse(String value, int fallback) { try { return Color.parseColor(value); } catch (Exception ignored) { return fallback; } }
    }
}
