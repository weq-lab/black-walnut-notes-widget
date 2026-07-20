package com.blackwalnut.noteswidget;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class ColorPresetsTest {
    @Test
    public void exposesAllRequiredPresetsInOrder() {
        assertArrayEquals(new String[]{
                "HEX 직접 입력", "블랙 월넛", "다크 브라운", "앤티크 브론즈",
                "앤티크 골드", "딥 앰버", "번트 코퍼", "골드 가독성"
        }, ColorPresets.names());
    }

    @Test
    public void builtInPresetsKeepPureBlackBackground() {
        assertNull(ColorPresets.ALL.get(0).background);
        for (int i = 1; i < ColorPresets.ALL.size(); i++) {
            assertEquals("#000000", ColorPresets.ALL.get(i).background);
        }
    }
}
