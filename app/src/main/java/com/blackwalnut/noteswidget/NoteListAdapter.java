package com.blackwalnut.noteswidget;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;

final class NoteListAdapter extends BaseAdapter {
    private final LayoutInflater inflater;
    private final List<NoteEntity> notes;

    NoteListAdapter(Context context, List<NoteEntity> notes) {
        inflater = LayoutInflater.from(context);
        this.notes = notes;
    }

    @Override public int getCount() { return notes.size(); }
    @Override public NoteEntity getItem(int position) { return notes.get(position); }
    @Override public long getItemId(int position) { return getItem(position).id; }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view = convertView == null ? inflater.inflate(R.layout.note_list_item, parent, false) : convertView;
        NoteEntity note = getItem(position);
        TextView title = view.findViewById(R.id.note_row_title);
        TextView preview = view.findViewById(R.id.note_row_preview);
        TextView time = view.findViewById(R.id.note_row_time);
        ColorPresets.Preset preset = ColorPresets.byName(note.colorPreset);
        if (preset.title == null || preset.body == null || preset.accent == null) {
            preset = ColorPresets.byName(ColorPresets.BLACK_WALNUT);
        }
        String displayTitle = note.title.trim().isEmpty() ? "제목 없음" : note.title;
        String displayPreview = note.body.trim().isEmpty() ? "체크리스트 또는 본문을 추가하세요" : note.body;
        title.setText(displayTitle);
        preview.setText(displayPreview);
        NoteTypography.applyTitle(view.getContext(), title, displayTitle);
        NoteTypography.applyBody(view.getContext(), preview, displayPreview);
        time.setText(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date(note.updatedAt)));
        title.setTextColor(Color.parseColor(preset.title));
        preview.setTextColor(Color.parseColor(preset.body));
        time.setTextColor(Color.parseColor(preset.accent));
        return view;
    }
}
