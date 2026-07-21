package com.blackwalnut.noteswidget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class WidgetBodyLinesTest {
    @Test
    public void split_preservesLfBlankAndTrailingLines() {
        assertEquals(
                Arrays.asList("first", "", "third", ""),
                WidgetBodyLines.split("first\n\nthird\n")
        );
    }

    @Test
    public void split_normalizesCrLfAndStandaloneCr() {
        assertEquals(
                Arrays.asList("one", "two", "three"),
                WidgetBodyLines.split("one\r\ntwo\rthree")
        );
    }

    @Test
    public void split_doesNotTruncateLongLogicalLine() {
        String longLine = repeat("black walnut ", 2000);
        List<String> rows = WidgetBodyLines.split(longLine);

        assertEquals(1, rows.size());
        assertEquals(longLine, rows.get(0));
    }

    @Test
    public void split_emptyInputHasNoRows() {
        assertTrue(WidgetBodyLines.split("").isEmpty());
        assertTrue(WidgetBodyLines.split(null).isEmpty());
    }

    private static String repeat(String value, int count) {
        StringBuilder result = new StringBuilder(value.length() * count);
        for (int i = 0; i < count; i++) result.append(value);
        return result.toString();
    }
}
