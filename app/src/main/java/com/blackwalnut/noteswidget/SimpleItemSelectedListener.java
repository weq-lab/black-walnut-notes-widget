package com.blackwalnut.noteswidget;

import android.view.View;
import android.widget.AdapterView;

final class SimpleItemSelectedListener implements AdapterView.OnItemSelectedListener {
    private final Runnable selected;
    SimpleItemSelectedListener(Runnable selected) { this.selected = selected; }
    @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) { selected.run(); }
    @Override public void onNothingSelected(AdapterView<?> parent) { }
}
