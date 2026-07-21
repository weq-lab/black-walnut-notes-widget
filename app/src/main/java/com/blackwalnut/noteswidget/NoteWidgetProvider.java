package com.blackwalnut.noteswidget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.BroadcastReceiver.PendingResult;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.RemoteViews;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class NoteWidgetProvider extends AppWidgetProvider {
    static final String ACTION_REFRESH = "com.blackwalnut.noteswidget.ACTION_REFRESH";
    static final String ACTION_TOGGLE_ITEM = "com.blackwalnut.noteswidget.ACTION_TOGGLE_ITEM";
    static final String ACTION_OPEN_NOTE = "com.blackwalnut.noteswidget.ACTION_OPEN_NOTE";
    static final String ACTION_OPEN_FILE = "com.blackwalnut.noteswidget.ACTION_OPEN_FILE";
    static final String EXTRA_WIDGET_ID = "widget_id";
    static final String EXTRA_NOTE_ID = "note_id";
    static final String EXTRA_ITEM_ID = "item_id";
    private static final String UNTITLED = "제목 없음";
    private static final String FILE_UNAVAILABLE = "파일을 열 수 없음";

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] appWidgetIds) {
        for (int id : appWidgetIds) updateWidget(context, manager, id);
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager manager, int appWidgetId, Bundle newOptions) {
        updateWidget(context, manager, appWidgetId);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        String action = intent.getAction();
        int widgetId = intent.getIntExtra(EXTRA_WIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        if (ACTION_REFRESH.equals(action)) {
            PendingResult pending = goAsync();
            FirestoreSyncManager.pullOnce(context, () -> {
                refresh(context, widgetId);
                pending.finish();
            });
        } else if (ACTION_TOGGLE_ITEM.equals(action)) {
            long itemId = intent.getLongExtra(EXTRA_ITEM_ID, 0);
            long noteId = intent.getLongExtra(EXTRA_NOTE_ID, 0);
            if (itemId > 0 && noteId > 0) {
                PendingResult pending = goAsync();
                AppDatabase.IO.execute(() -> {
                    AppDatabase.get(context).noteDao().toggleItemAndTouch(itemId, noteId, System.currentTimeMillis());
                    FirestoreSyncManager.kick(context);
                    refresh(context, widgetId);
                    pending.finish();
                });
            }
        } else if (ACTION_OPEN_NOTE.equals(action)) {
            long noteId = intent.getLongExtra(EXTRA_NOTE_ID, 0);
            Intent editor = new Intent(context, NoteEditActivity.class)
                    .putExtra(NoteEditActivity.EXTRA_NOTE_ID, noteId)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            context.startActivity(editor);
        } else if (ACTION_OPEN_FILE.equals(action)) {
            openFile(context, widgetId);
        }
    }

    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        for (int id : appWidgetIds) WidgetPrefs.delete(context, id);
    }

    static void updateWidget(Context context, AppWidgetManager manager, int widgetId) {
        String initialTitle = WidgetPrefs.SOURCE_FILE.equals(WidgetPrefs.source(context, widgetId))
                ? displayTitle(WidgetPrefs.fileName(context, widgetId), UNTITLED)
                : UNTITLED;
        RemoteViews views = createRemoteViews(context, widgetId, initialTitle);
        manager.updateAppWidget(widgetId, views);
        manager.notifyAppWidgetViewDataChanged(widgetId, R.id.widget_list);
        loadHeaderTitle(context, widgetId);
    }

    private static RemoteViews createRemoteViews(Context context, int widgetId, String headerTitle) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_note);
        views.setInt(R.id.widget_root, "setBackgroundColor", android.graphics.Color.parseColor(WidgetPrefs.background(context, widgetId)));
        views.setTextViewText(R.id.widget_header_title, headerTitle);
        views.setTextColor(R.id.widget_header_title, android.graphics.Color.parseColor(WidgetPrefs.title(context, widgetId)));
        Intent service = new Intent(context, NoteWidgetService.class)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
        service.setData(Uri.parse(service.toUri(Intent.URI_INTENT_SCHEME)));
        views.setRemoteAdapter(R.id.widget_list, service);
        views.setEmptyView(R.id.widget_list, R.id.widget_empty);

        Intent rowTemplate = new Intent(context, NoteWidgetProvider.class);
        PendingIntent rowPending = PendingIntent.getBroadcast(
                context, 40000 + widgetId, rowTemplate,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE
        );
        views.setPendingIntentTemplate(R.id.widget_list, rowPending);
        views.setOnClickPendingIntent(R.id.widget_refresh, refreshIntent(context, widgetId));
        views.setOnClickPendingIntent(R.id.widget_new, newNoteIntent(context, widgetId));
        views.setOnClickPendingIntent(R.id.widget_settings, configIntent(context, widgetId));
        views.setOnClickPendingIntent(R.id.widget_header_title, headerIntent(context, widgetId));
        return views;
    }

    private static void loadHeaderTitle(Context context, int widgetId) {
        Context appContext = context.getApplicationContext();
        String source = WidgetPrefs.source(appContext, widgetId);
        long noteId = WidgetPrefs.noteId(appContext, widgetId);
        String uriText = WidgetPrefs.uri(appContext, widgetId);
        AppDatabase.IO.execute(() -> {
            String title;
            if (WidgetPrefs.SOURCE_FILE.equals(source)) {
                title = readFileTitle(appContext, widgetId, uriText);
            } else {
                NoteEntity note = noteId > 0 ? AppDatabase.get(appContext).noteDao().getNote(noteId) : null;
                title = note == null ? UNTITLED : displayTitle(note.title, UNTITLED);
            }
            if (!selectionMatches(appContext, widgetId, source, noteId, uriText)) return;
            AppWidgetManager manager = AppWidgetManager.getInstance(appContext);
            manager.updateAppWidget(widgetId, createRemoteViews(appContext, widgetId, title));
        });
    }

    private static String readFileTitle(Context context, int widgetId, String uriText) {
        if (uriText == null) return FILE_UNAVAILABLE;
        try (InputStream input = context.getContentResolver().openInputStream(Uri.parse(uriText))) {
            if (input == null) return FILE_UNAVAILABLE;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                StringBuilder raw = new StringBuilder();
                char[] buffer = new char[4096];
                int count;
                while ((count = reader.read(buffer)) != -1 && raw.length() < 200000) {
                    raw.append(buffer, 0, count);
                }
                int accent = parseColor(WidgetPrefs.accent(context, widgetId), android.graphics.Color.rgb(209, 174, 111));
                MarkdownRenderer.RenderedNote rendered = MarkdownRenderer.render(
                        raw.toString(), WidgetPrefs.fileName(context, widgetId), accent
                );
                return displayTitle(rendered.title, WidgetPrefs.fileName(context, widgetId));
            }
        } catch (Exception ignored) {
            return FILE_UNAVAILABLE;
        }
    }

    private static boolean selectionMatches(Context context, int widgetId, String source, long noteId, String uriText) {
        if (!source.equals(WidgetPrefs.source(context, widgetId))) return false;
        if (WidgetPrefs.SOURCE_FILE.equals(source)) {
            String currentUri = WidgetPrefs.uri(context, widgetId);
            return uriText == null ? currentUri == null : uriText.equals(currentUri);
        }
        return noteId == WidgetPrefs.noteId(context, widgetId);
    }

    private static String displayTitle(String value, String fallback) {
        if (value != null && !value.trim().isEmpty()) return value.trim();
        if (fallback != null && !fallback.trim().isEmpty()) return fallback.trim();
        return UNTITLED;
    }

    private static int parseColor(String value, int fallback) {
        try {
            return android.graphics.Color.parseColor(value);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    static void notifyAllWidgets(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, NoteWidgetProvider.class));
        for (int id : ids) updateWidget(context, manager, id);
    }

    private static void refresh(Context context, int widgetId) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            manager.notifyAppWidgetViewDataChanged(widgetId, R.id.widget_list);
            updateWidget(context, manager, widgetId);
        } else {
            notifyAllWidgets(context);
        }
    }

    private static void openFile(Context context, int widgetId) {
        String uriText = WidgetPrefs.uri(context, widgetId);
        if (uriText == null) return;
        Uri uri = Uri.parse(uriText);
        Intent view = new Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "text/plain")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        view.setClipData(ClipData.newRawUri("note", uri));
        context.startActivity(Intent.createChooser(view, "메모 앱으로 열기").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    private static PendingIntent refreshIntent(Context context, int widgetId) {
        Intent intent = new Intent(context, NoteWidgetProvider.class).setAction(ACTION_REFRESH).putExtra(EXTRA_WIDGET_ID, widgetId);
        return PendingIntent.getBroadcast(context, 10000 + widgetId, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent newNoteIntent(Context context, int widgetId) {
        Intent intent = new Intent(context, NoteEditActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(context, 20000 + widgetId, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent headerIntent(Context context, int widgetId) {
        if (!WidgetPrefs.SOURCE_FILE.equals(WidgetPrefs.source(context, widgetId))) {
            long noteId = WidgetPrefs.noteId(context, widgetId);
            if (noteId <= 0) return newNoteIntent(context, widgetId);
            Intent openNote = new Intent(context, NoteWidgetProvider.class)
                    .setAction(ACTION_OPEN_NOTE)
                    .putExtra(EXTRA_WIDGET_ID, widgetId)
                    .putExtra(EXTRA_NOTE_ID, noteId);
            return PendingIntent.getBroadcast(
                    context, 50000 + widgetId, openNote,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
        }
        Intent openFile = new Intent(context, NoteWidgetProvider.class)
                .setAction(ACTION_OPEN_FILE)
                .putExtra(EXTRA_WIDGET_ID, widgetId);
        return PendingIntent.getBroadcast(
                context, 50000 + widgetId, openFile,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private static PendingIntent configIntent(Context context, int widgetId) {
        Intent intent = new Intent(context, WidgetConfigActivity.class)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return PendingIntent.getActivity(context, 30000 + widgetId, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
