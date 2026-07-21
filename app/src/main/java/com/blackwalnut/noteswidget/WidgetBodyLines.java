package com.blackwalnut.noteswidget;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Splits widget text without losing blank or trailing logical lines. */
final class WidgetBodyLines {
    private WidgetBodyLines() {}

    static List<String> split(CharSequence body) {
        if (body == null || body.length() == 0) return Collections.emptyList();

        String normalized = body.toString().replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = normalized.split("\n", -1);
        List<String> result = new ArrayList<>(lines.length);
        Collections.addAll(result, lines);
        return result;
    }
}
