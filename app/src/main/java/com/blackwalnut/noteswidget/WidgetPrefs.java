package com.blackwalnut.noteswidget;

import android.content.Context;
import android.content.SharedPreferences;

final class WidgetPrefs {
    static final String DEFAULT_BACKGROUND = "#000000";
    static final String DEFAULT_TITLE = "#5A3021";
    static final String DEFAULT_BODY = "#3A2017";
    static final String READABLE_BODY = "#75432E";
    static final String DEFAULT_ACCENT = "#D1AE6F";
    static final float DEFAULT_TEXT_SIZE = 16f;

    private static final String PREFS = "black_walnut_widget_prefs";

    private WidgetPrefs() {}

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static void save(
            Context context,
            int widgetId,
            String uri,
            String fileName,
            String background,
            String title,
            String body,
            String accent,
            float textSize
    ) {
        prefs(context).edit()
                .putString("uri_" + widgetId, uri)
                .putString("file_" + widgetId, fileName)
                .putString("bg_" + widgetId, background)
                .putString("title_" + widgetId, title)
                .putString("body_" + widgetId, body)
                .putString("accent_" + widgetId, accent)
                .putFloat("size_" + widgetId, textSize)
                .apply();
    }

    static String uri(Context context, int id) {
        return prefs(context).getString("uri_" + id, null);
    }

    static String fileName(Context context, int id) {
        return prefs(context).getString("file_" + id, "메모");
    }

    static String background(Context context, int id) {
        return prefs(context).getString("bg_" + id, DEFAULT_BACKGROUND);
    }

    static String title(Context context, int id) {
        return prefs(context).getString("title_" + id, DEFAULT_TITLE);
    }

    static String body(Context context, int id) {
        return prefs(context).getString("body_" + id, DEFAULT_BODY);
    }

    static String accent(Context context, int id) {
        return prefs(context).getString("accent_" + id, DEFAULT_ACCENT);
    }

    static float textSize(Context context, int id) {
        return prefs(context).getFloat("size_" + id, DEFAULT_TEXT_SIZE);
    }

    static void delete(Context context, int id) {
        prefs(context).edit()
                .remove("uri_" + id)
                .remove("file_" + id)
                .remove("bg_" + id)
                .remove("title_" + id)
                .remove("body_" + id)
                .remove("accent_" + id)
                .remove("size_" + id)
                .apply();
    }
}
