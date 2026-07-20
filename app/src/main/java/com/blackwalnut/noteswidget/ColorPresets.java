package com.blackwalnut.noteswidget;

import java.util.Arrays;
import java.util.List;

final class ColorPresets {
    static final String CUSTOM = "HEX 직접 입력";
    static final String BLACK_WALNUT = "블랙 월넛";

    static final class Preset {
        final String name;
        final String background;
        final String title;
        final String body;
        final String accent;

        Preset(String name, String background, String title, String body, String accent) {
            this.name = name;
            this.background = background;
            this.title = title;
            this.body = body;
            this.accent = accent;
        }
    }

    static final List<Preset> ALL = Arrays.asList(
            new Preset(CUSTOM, null, null, null, null),
            new Preset(BLACK_WALNUT, "#000000", "#5A3021", "#3A2017", "#D1AE6F"),
            new Preset("다크 브라운", "#000000", "#6B351C", "#4A2414", "#9A5835"),
            new Preset("앤티크 브론즈", "#000000", "#8C5A32", "#5A351F", "#B77A45"),
            new Preset("앤티크 골드", "#000000", "#A8864B", "#6A532F", "#D4B06A"),
            new Preset("딥 앰버", "#000000", "#A85D1A", "#6F3B12", "#E49A3A"),
            new Preset("번트 코퍼", "#000000", "#9C4F2E", "#5E2E1C", "#C76D3E"),
            new Preset("골드 가독성", "#000000", "#D0A95B", "#9A7B3E", "#F0C86E")
    );

    static Preset byName(String name) {
        for (Preset preset : ALL) if (preset.name.equals(name)) return preset;
        return ALL.get(1);
    }

    static String[] names() {
        String[] result = new String[ALL.size()];
        for (int i = 0; i < ALL.size(); i++) result[i] = ALL.get(i).name;
        return result;
    }

    private ColorPresets() { }
}
