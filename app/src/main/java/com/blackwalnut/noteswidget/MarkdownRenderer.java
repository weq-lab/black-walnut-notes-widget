package com.blackwalnut.noteswidget;

import android.graphics.Color;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class MarkdownRenderer {
    private static final int MAX_CHARS = 12000;
    private static final Pattern COLOR_SPAN = Pattern.compile(
            "(?is)<span\\s+style\\s*=\\s*[\"']\\s*color\\s*:\\s*(#[0-9a-fA-F]{6,8})\\s*;?\\s*[\"']\\s*>(.*?)</span>"
    );
    private static final Pattern BOLD = Pattern.compile("(?s)(\\*\\*|__)(.+?)\\1");
    private static final Pattern CODE = Pattern.compile("`([^`]+)`");
    private static final Pattern LINK = Pattern.compile("\\[([^\\]]+)]\\(([^)]+)\\)");

    static final class RenderedNote {
        final String title;
        final CharSequence body;

        RenderedNote(String title, CharSequence body) {
            this.title = title;
            this.body = body;
        }
    }

    private MarkdownRenderer() {}

    static RenderedNote render(String input, String fallbackTitle, int accentColor) {
        if (input == null) input = "";
        input = input.replace("\r\n", "\n").replace('\r', '\n');

        String[] lines = input.split("\n", -1);
        String title = fallbackTitle == null || fallbackTitle.trim().isEmpty() ? "메모" : fallbackTitle;
        List<String> bodyLines = new ArrayList<>();
        boolean titleFound = false;
        boolean inFrontMatter = false;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();

            if (i == 0 && trimmed.equals("---")) {
                inFrontMatter = true;
                continue;
            }
            if (inFrontMatter) {
                if (trimmed.equals("---")) inFrontMatter = false;
                continue;
            }

            if (!titleFound && trimmed.startsWith("# ")) {
                title = trimmed.substring(2).trim();
                titleFound = true;
                continue;
            }

            if (trimmed.matches("^#{2,6}\\s+.*")) {
                line = "\n" + trimmed.replaceFirst("^#{2,6}\\s+", "");
            } else if (trimmed.matches("^- \\[ \\].*")) {
                line = "☐ " + trimmed.substring(6);
            } else if (trimmed.matches("^- \\[[xX]\\].*")) {
                line = "☑ " + trimmed.substring(6);
            } else if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
                line = "• " + trimmed.substring(2);
            } else if (trimmed.startsWith("> ")) {
                line = "› " + trimmed.substring(2);
            }

            bodyLines.add(line);
        }

        String bodyText = String.join("\n", bodyLines).trim();
        if (bodyText.length() > MAX_CHARS) {
            bodyText = bodyText.substring(0, MAX_CHARS) + "\n…";
        }
        if (bodyText.trim().isEmpty()) bodyText = "할 일 없음";

        SpannableStringBuilder styled = new SpannableStringBuilder(bodyText);
        replaceLinks(styled, accentColor);
        applyColorSpans(styled);
        applyDelimitedStyle(styled, BOLD, true);
        applyCodeStyle(styled);
        stripRemainingHtml(styled);

        return new RenderedNote(title, SpannedStringBuilderCompat.freeze(styled));
    }

    private static void replaceLinks(SpannableStringBuilder text, int accentColor) {
        List<Match> matches = collect(LINK, text.toString());
        for (int i = matches.size() - 1; i >= 0; i--) {
            Match m = matches.get(i);
            String label = m.group2;
            text.replace(m.start, m.end, label);
            text.setSpan(new ForegroundColorSpan(accentColor), m.start, m.start + label.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    private static void applyColorSpans(SpannableStringBuilder text) {
        List<ColorMatch> matches = new ArrayList<>();
        Matcher matcher = COLOR_SPAN.matcher(text.toString());
        while (matcher.find()) {
            matches.add(new ColorMatch(
                    matcher.start(), matcher.end(), matcher.start(2), matcher.end(2), matcher.group(1)
            ));
        }
        for (int i = matches.size() - 1; i >= 0; i--) {
            ColorMatch m = matches.get(i);
            try {
                int color = Color.parseColor(m.color);
                int innerLength = m.innerEnd - m.innerStart;
                text.delete(m.innerEnd, m.end);
                text.delete(m.start, m.innerStart);
                text.setSpan(new ForegroundColorSpan(color), m.start, m.start + innerLength, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } catch (IllegalArgumentException ignored) {
                // Keep text readable even when a malformed color is supplied.
            }
        }
    }

    private static void applyDelimitedStyle(SpannableStringBuilder text, Pattern pattern, boolean bold) {
        List<DelimitedMatch> matches = new ArrayList<>();
        Matcher matcher = pattern.matcher(text.toString());
        while (matcher.find()) {
            matches.add(new DelimitedMatch(matcher.start(), matcher.end(), matcher.group(1).length(), matcher.group(2).length()));
        }
        for (int i = matches.size() - 1; i >= 0; i--) {
            DelimitedMatch m = matches.get(i);
            text.delete(m.end - m.markerLength, m.end);
            text.delete(m.start, m.start + m.markerLength);
            if (bold) {
                text.setSpan(new StyleSpan(Typeface.BOLD), m.start, m.start + m.contentLength, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
    }

    private static void applyCodeStyle(SpannableStringBuilder text) {
        List<Match> matches = collect(CODE, text.toString());
        for (int i = matches.size() - 1; i >= 0; i--) {
            Match m = matches.get(i);
            String content = m.group2;
            text.replace(m.start, m.end, content);
            text.setSpan(new TypefaceSpan("monospace"), m.start, m.start + content.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    private static void stripRemainingHtml(SpannableStringBuilder text) {
        String cleaned = text.toString()
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?s)<[^>]+>", "");
        if (!cleaned.contentEquals(text)) {
            text.replace(0, text.length(), cleaned);
        }
    }

    private static List<Match> collect(Pattern pattern, String source) {
        List<Match> matches = new ArrayList<>();
        Matcher matcher = pattern.matcher(source);
        while (matcher.find()) {
            String group = matcher.groupCount() >= 1 ? matcher.group(1) : "";
            matches.add(new Match(matcher.start(), matcher.end(), group));
        }
        return matches;
    }

    private static final class Match {
        final int start;
        final int end;
        final String group2;

        Match(int start, int end, String group2) {
            this.start = start;
            this.end = end;
            this.group2 = group2 == null ? "" : group2;
        }
    }

    private static final class ColorMatch {
        final int start;
        final int end;
        final int innerStart;
        final int innerEnd;
        final String color;

        ColorMatch(int start, int end, int innerStart, int innerEnd, String color) {
            this.start = start;
            this.end = end;
            this.innerStart = innerStart;
            this.innerEnd = innerEnd;
            this.color = color;
        }
    }

    private static final class DelimitedMatch {
        final int start;
        final int end;
        final int markerLength;
        final int contentLength;

        DelimitedMatch(int start, int end, int markerLength, int contentLength) {
            this.start = start;
            this.end = end;
            this.markerLength = markerLength;
            this.contentLength = contentLength;
        }
    }

    /** Avoids relying on mutable builders after RemoteViews has been parcelled. */
    private static final class SpannedStringBuilderCompat {
        static CharSequence freeze(SpannableStringBuilder builder) {
            return new android.text.SpannedString(builder);
        }
    }
}
