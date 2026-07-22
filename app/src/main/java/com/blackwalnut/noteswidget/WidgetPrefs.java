package com.blackwalnut.noteswidget;

import android.content.Context;
import android.content.SharedPreferences;

final class WidgetPrefs {
    static final String SOURCE_LOCAL = "local";
    static final String SOURCE_FILE = "file";
    static final String DEFAULT_BACKGROUND = "#000000";
    static final String DEFAULT_TITLE = "#5A3021";
    static final String DEFAULT_BODY = "#3A2017";
    static final String READABLE_BODY = "#75432E";
    static final String DEFAULT_ACCENT = "#D1AE6F";
    static final float DEFAULT_TEXT_SIZE = 16f;
    private static final String PREFS = "black_walnut_widget_prefs";

    private WidgetPrefs() { }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static void saveLocal(Context context, int widgetId, long noteId, String preset,
                          String background, String title, String body, String accent, float textSize) {
        prefs(context).edit()
                .putString("source_" + widgetId, SOURCE_LOCAL)
                .putLong("note_" + widgetId, noteId)
                .putString("preset_" + widgetId, preset)
                .putString("bg_" + widgetId, background)
                .putString("title_" + widgetId, title)
                .putString("body_" + widgetId, body)
                .putString("accent_" + widgetId, accent)
                .putFloat("size_" + widgetId, textSize)
                .apply();
    }

    static void saveFile(Context context, int widgetId, String uri, String fileName, String preset,
                         String background, String title, String body, String accent, float textSize) {
        prefs(context).edit()
                .putString("source_" + widgetId, SOURCE_FILE)
                .putString("uri_" + widgetId, uri)
                .putString("file_" + widgetId, fileName)
                .putString("preset_" + widgetId, preset)
                .putString("bg_" + widgetId, background)
                .putString("title_" + widgetId, title)
                .putString("body_" + widgetId, body)
                .putString("accent_" + widgetId, accent)
                .putFloat("size_" + widgetId, textSize)
                .apply();
    }

    static String source(Context context, int id) { return prefs(context).getString("source_" + id, SOURCE_LOCAL); }
    static long noteId(Context context, int id) { return prefs(context).getLong("note_" + id, 0); }
    static String uri(Context context, int id) { return prefs(context).getString("uri_" + id, null); }
    static String fileName(Context context, int id) { return prefs(context).getString("file_" + id, "메모"); }
    static String preset(Context context, int id) { return prefs(context).getString("preset_" + id, ColorPresets.BLACK_WALNUT); }
    static String background(Context context, int id) { return prefs(context).getString("bg_" + id, DEFAULT_BACKGROUND); }
    static String title(Context context, int id) { return prefs(context).getString("title_" + id, DEFAULT_TITLE); }
    static String body(Context context, int id) { return prefs(context).getString("body_" + id, DEFAULT_BODY); }
    static String accent(Context context, int id) { return prefs(context).getString("accent_" + id, DEFAULT_ACCENT); }
    static float textSize(Context context, int id) { return prefs(context).getFloat("size_" + id, DEFAULT_TEXT_SIZE); }
    static boolean collapsed(Context context, int id) { return prefs(context).getBoolean(collapseKey(id), false); }
    static void setCollapsed(Context context, int id, boolean collapsed) {
        prefs(context).edit().putBoolean(collapseKey(id), collapsed).apply();
    }
    static String collapseKey(int id) { return "collapsed_" + id; }

    static void delete(Context context, int id) {
        SharedPreferences.Editor editor = prefs(context).edit();
        String[] keys = {"source_", "note_", "uri_", "file_", "preset_", "bg_", "title_", "body_", "accent_", "size_", "collapsed_"};
        for (String key : keys) editor.remove(key + id);
        editor.apply();
    }
}
