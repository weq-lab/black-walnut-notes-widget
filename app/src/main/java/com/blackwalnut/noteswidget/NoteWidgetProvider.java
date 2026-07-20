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

public class NoteWidgetProvider extends AppWidgetProvider {
    static final String ACTION_REFRESH = "com.blackwalnut.noteswidget.ACTION_REFRESH";
    static final String ACTION_TOGGLE_ITEM = "com.blackwalnut.noteswidget.ACTION_TOGGLE_ITEM";
    static final String ACTION_OPEN_NOTE = "com.blackwalnut.noteswidget.ACTION_OPEN_NOTE";
    static final String ACTION_OPEN_FILE = "com.blackwalnut.noteswidget.ACTION_OPEN_FILE";
    static final String EXTRA_WIDGET_ID = "widget_id";
    static final String EXTRA_NOTE_ID = "note_id";
    static final String EXTRA_ITEM_ID = "item_id";

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
            refresh(context, widgetId);
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
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_note);
        views.setInt(R.id.widget_root, "setBackgroundColor", android.graphics.Color.parseColor(WidgetPrefs.background(context, widgetId)));
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
        manager.updateAppWidget(widgetId, views);
        manager.notifyAppWidgetViewDataChanged(widgetId, R.id.widget_list);
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

    private static PendingIntent configIntent(Context context, int widgetId) {
        Intent intent = new Intent(context, WidgetConfigActivity.class)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return PendingIntent.getActivity(context, 30000 + widgetId, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
