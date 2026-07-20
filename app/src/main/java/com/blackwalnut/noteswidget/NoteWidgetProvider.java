package com.blackwalnut.noteswidget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.util.TypedValue;
import android.widget.RemoteViews;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class NoteWidgetProvider extends AppWidgetProvider {
    static final String ACTION_REFRESH = "com.blackwalnut.noteswidget.ACTION_REFRESH";
    private static final String EXTRA_WIDGET_ID = "widget_id";

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
        if (ACTION_REFRESH.equals(intent.getAction())) {
            int id = intent.getIntExtra(EXTRA_WIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
            AppWidgetManager manager = AppWidgetManager.getInstance(context);
            if (id != AppWidgetManager.INVALID_APPWIDGET_ID) {
                updateWidget(context, manager, id);
            } else {
                int[] ids = manager.getAppWidgetIds(new ComponentName(context, NoteWidgetProvider.class));
                for (int widgetId : ids) updateWidget(context, manager, widgetId);
            }
        }
    }

    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        for (int id : appWidgetIds) WidgetPrefs.delete(context, id);
    }

    static void updateWidget(Context context, AppWidgetManager manager, int widgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_note);

        int background = parseColor(WidgetPrefs.background(context, widgetId), Color.BLACK);
        int titleColor = parseColor(WidgetPrefs.title(context, widgetId), Color.rgb(90, 48, 33));
        int bodyColor = parseColor(WidgetPrefs.body(context, widgetId), Color.rgb(58, 32, 23));
        int accentColor = parseColor(WidgetPrefs.accent(context, widgetId), Color.rgb(209, 174, 111));
        float textSize = WidgetPrefs.textSize(context, widgetId);

        views.setInt(R.id.widget_root, "setBackgroundColor", background);
        views.setTextColor(R.id.widget_title, titleColor);
        views.setTextColor(R.id.widget_body, bodyColor);
        views.setTextColor(R.id.widget_updated, blend(background, bodyColor, 0.55f));
        views.setInt(R.id.widget_refresh, "setColorFilter", accentColor);
        views.setInt(R.id.widget_settings, "setColorFilter", accentColor);
        views.setTextViewTextSize(R.id.widget_body, TypedValue.COMPLEX_UNIT_SP, textSize);

        String uriText = WidgetPrefs.uri(context, widgetId);
        if (uriText == null || uriText.isBlank()) {
            views.setTextViewText(R.id.widget_title, context.getString(R.string.widget_empty_title));
            views.setTextViewText(R.id.widget_body, context.getString(R.string.widget_empty_body));
        } else {
            try {
                Uri uri = Uri.parse(uriText);
                String raw = readText(context, uri);
                MarkdownRenderer.RenderedNote note = MarkdownRenderer.render(
                        raw,
                        WidgetPrefs.fileName(context, widgetId),
                        accentColor
                );
                views.setTextViewText(R.id.widget_title, note.title);
                views.setTextViewText(R.id.widget_body, note.body);
                setOpenFileIntent(context, views, widgetId, uri);
            } catch (Exception error) {
                views.setTextViewText(R.id.widget_title, "파일을 열 수 없음");
                views.setTextViewText(R.id.widget_body, "파일 권한이 만료됐거나 동기화 앱이 파일을 이동했습니다. 설정에서 파일을 다시 선택하세요.");
            }
        }

        String time = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
        views.setTextViewText(R.id.widget_updated, context.getString(R.string.updated_prefix) + " " + time);
        views.setOnClickPendingIntent(R.id.widget_refresh, refreshIntent(context, widgetId));
        views.setOnClickPendingIntent(R.id.widget_settings, configIntent(context, widgetId));
        views.setOnClickPendingIntent(R.id.widget_title, configIntent(context, widgetId));

        manager.updateAppWidget(widgetId, views);
    }

    private static void setOpenFileIntent(Context context, RemoteViews views, int widgetId, Uri uri) {
        Intent view = new Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "text/plain")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        view.setClipData(ClipData.newRawUri("note", uri));
        Intent chooser = Intent.createChooser(view, "메모 앱으로 열기");
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pending = PendingIntent.getActivity(
                context,
                30000 + widgetId,
                chooser,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        views.setOnClickPendingIntent(R.id.widget_body, pending);
    }

    private static PendingIntent refreshIntent(Context context, int widgetId) {
        Intent intent = new Intent(context, NoteWidgetProvider.class)
                .setAction(ACTION_REFRESH)
                .putExtra(EXTRA_WIDGET_ID, widgetId);
        return PendingIntent.getBroadcast(
                context,
                10000 + widgetId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private static PendingIntent configIntent(Context context, int widgetId) {
        Intent intent = new Intent(context, WidgetConfigActivity.class)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return PendingIntent.getActivity(
                context,
                20000 + widgetId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private static String readText(Context context, Uri uri) throws IOException {
        try (InputStream input = context.getContentResolver().openInputStream(uri)) {
            if (input == null) throw new IOException("Content provider returned no stream");
            StringBuilder out = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                char[] buffer = new char[4096];
                int count;
                while ((count = reader.read(buffer)) != -1 && out.length() < 200_000) {
                    out.append(buffer, 0, count);
                }
            }
            return out.toString();
        }
    }

    private static int parseColor(String value, int fallback) {
        try {
            return Color.parseColor(value);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static int blend(int background, int foreground, float foregroundRatio) {
        float bgRatio = 1f - foregroundRatio;
        return Color.argb(
                255,
                Math.round(Color.red(background) * bgRatio + Color.red(foreground) * foregroundRatio),
                Math.round(Color.green(background) * bgRatio + Color.green(foreground) * foregroundRatio),
                Math.round(Color.blue(background) * bgRatio + Color.blue(foreground) * foregroundRatio)
        );
    }
}
