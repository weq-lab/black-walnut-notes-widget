package com.blackwalnut.noteswidget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class NoteTypographyTest {
    @Test public void detectsSupportedHangulBlocks() {
        for (String sample : new String[]{"ᄀ", "ㄱ", "ꥠ", "한", "ힰ"}) {
            assertTrue(NoteTypography.containsHangul(sample));
        }
        assertFalse(NoteTypography.containsHangul("Make Each Day Count"));
    }

    @Test public void usesOneFamilyForMixedText() {
        assertEquals("maruburi-semibold", NoteTypography.familyKey(NoteTypography.Role.TITLE, "오늘 Deep Work"));
        assertEquals("maruburi-regular", NoteTypography.familyKey(NoteTypography.Role.BODY, "Focus 후 휴식"));
    }

    @Test public void usesCormorantWhenHangulIsAbsent() {
        assertEquals("cormorant-semibold", NoteTypography.familyKey(NoteTypography.Role.TITLE, "Make Each Day Count"));
        assertEquals("cormorant-regular", NoteTypography.familyKey(NoteTypography.Role.BODY, "Focus, patience."));
    }
}
