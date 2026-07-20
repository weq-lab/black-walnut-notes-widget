package com.blackwalnut.noteswidget;

import android.app.Activity;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        findViewById(R.id.button_add_widget).setOnClickListener(v -> requestWidget());
    }

    private void requestWidget() {
        AppWidgetManager manager = AppWidgetManager.getInstance(this);
        ComponentName provider = new ComponentName(this, NoteWidgetProvider.class);
        if (manager.isRequestPinAppWidgetSupported()) {
            Intent callbackIntent = new Intent(this, MainActivity.class);
            PendingIntent callback = PendingIntent.getActivity(
                    this, 9001, callbackIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            manager.requestPinAppWidget(provider, null, callback);
        } else {
            Toast.makeText(this, "홈 화면을 길게 눌러 위젯 목록에서 Black Walnut 메모를 추가하세요.", Toast.LENGTH_LONG).show();
        }
    }
}
