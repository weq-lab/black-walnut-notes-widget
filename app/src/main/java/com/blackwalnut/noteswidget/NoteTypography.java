package com.blackwalnut.noteswidget;

import android.content.Context;
import android.graphics.Typeface;
import android.widget.TextView;

final class NoteTypography {
    enum Role { TITLE, BODY }

    private static Typeface cormorantRegular;
    private static Typeface cormorantSemiBold;
    private static Typeface maruRegular;
    private static Typeface maruSemiBold;

    private NoteTypography() { }

    static boolean containsHangul(CharSequence value) {
        if (value == null) return false;
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if ((character >= '\u1100' && character <= '\u11ff')
                    || (character >= '\u3130' && character <= '\u318f')
                    || (character >= '\ua960' && character <= '\ua97f')
                    || (character >= '\uac00' && character <= '\ud7af')
                    || (character >= '\ud7b0' && character <= '\ud7ff')) return true;
        }
        return false;
    }

    static String familyKey(Role role, CharSequence value) {
        return (containsHangul(value) ? "maruburi-" : "cormorant-")
                + (role == Role.TITLE ? "semibold" : "regular");
    }

    static void applyTitle(Context context, TextView view, CharSequence value) {
        apply(context, view, Role.TITLE, value);
    }

    static void applyBody(Context context, TextView view, CharSequence value) {
        apply(context, view, Role.BODY, value);
    }

    static void applyBrand(Context context, TextView view) {
        setIfChanged(view, font(context, false, true));
    }

    private static void apply(Context context, TextView view, Role role, CharSequence value) {
        setIfChanged(view, font(context, containsHangul(value), role == Role.TITLE));
    }

    private static synchronized Typeface font(Context context, boolean korean, boolean semiBold) {
        if (korean && semiBold) {
            if (maruSemiBold == null) maruSemiBold = context.getResources().getFont(R.font.maruburi_semibold);
            return maruSemiBold;
        }
        if (korean) {
            if (maruRegular == null) maruRegular = context.getResources().getFont(R.font.maruburi_regular);
            return maruRegular;
        }
        if (semiBold) {
            if (cormorantSemiBold == null) cormorantSemiBold = context.getResources().getFont(R.font.cormorant_garamond_semibold);
            return cormorantSemiBold;
        }
        if (cormorantRegular == null) cormorantRegular = context.getResources().getFont(R.font.cormorant_garamond_regular);
        return cormorantRegular;
    }

    private static void setIfChanged(TextView view, Typeface typeface) {
        if (view.getTypeface() != typeface) view.setTypeface(typeface);
    }
}
