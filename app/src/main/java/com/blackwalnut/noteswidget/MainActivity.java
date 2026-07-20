package com.blackwalnut.noteswidget;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ListView;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

public class MainActivity extends Activity implements FirestoreSyncManager.Listener {
    private ListView noteList;
    private TextView emptyView;
    private TextView syncStatus;
    private Button signIn;
    private Button signOut;
    private Button importNotes;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        noteList = findViewById(R.id.note_list);
        emptyView = findViewById(R.id.note_empty);
        syncStatus = findViewById(R.id.sync_status);
        signIn = findViewById(R.id.button_sign_in);
        signOut = findViewById(R.id.button_sign_out);
        importNotes = findViewById(R.id.button_import_notes);
        noteList.setEmptyView(emptyView);
        findViewById(R.id.button_new_note).setOnClickListener(v -> openEditor(0));
        findViewById(R.id.button_add_widget).setOnClickListener(v -> requestWidget());
        signIn.setOnClickListener(v -> signIn());
        signOut.setOnClickListener(v -> signOut());
        importNotes.setOnClickListener(v -> confirmImport());
        noteList.setOnItemClickListener((parent, view, position, id) -> openEditor(id));
    }

    @Override
    protected void onResume() {
        super.onResume();
        reloadNotes();
        updateAuthUi();
        FirestoreSyncManager.start(this, this);
    }

    @Override
    protected void onPause() {
        FirestoreSyncManager.setListener(null);
        super.onPause();
    }

    private void reloadNotes() {
        AppDatabase.IO.execute(() -> {
            List<NoteEntity> notes = AppDatabase.get(this).noteDao().listNotes();
            runOnUiThread(() -> noteList.setAdapter(new NoteListAdapter(this, notes)));
        });
    }

    private void openEditor(long noteId) {
        Intent intent = new Intent(this, NoteEditActivity.class);
        if (noteId > 0) intent.putExtra(NoteEditActivity.EXTRA_NOTE_ID, noteId);
        startActivity(intent);
    }

    private void signIn() {
        signIn.setEnabled(false);
        syncStatus.setText("Google 로그인 여는 중…");
        FirebaseAuthController.signIn(this, (success, message) -> {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            signIn.setEnabled(true);
            updateAuthUi();
            if (success) FirestoreSyncManager.start(this, this);
        });
    }

    private void signOut() {
        FirebaseAuthController.signOut(this, (success, message) -> {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            updateAuthUi();
        });
    }

    private void confirmImport() {
        new AlertDialog.Builder(this)
                .setMessage("현재 로컬 전용 노트를 로그인한 계정의 Firestore로 가져올까요? 이미 연결된 노트는 제외됩니다.")
                .setNegativeButton("취소", null)
                .setPositiveButton("가져오기", (dialog, which) -> FirestoreSyncManager.importLocalNotes(this, count -> {
                    Toast.makeText(this, count + "개 노트를 동기화 대상으로 추가했습니다.", Toast.LENGTH_LONG).show();
                    FirestoreSyncManager.start(this, this);
                }))
                .show();
    }

    private void updateAuthUi() {
        boolean configured = FirebaseAuthController.isConfigured(this);
        boolean signedIn = configured && FirebaseAuthController.currentUser(this) != null;
        signIn.setVisibility(signedIn ? View.GONE : View.VISIBLE);
        signIn.setEnabled(configured);
        signIn.setText(configured ? "Google로 로그인" : "Firebase 설정 필요");
        signOut.setVisibility(signedIn ? View.VISIBLE : View.GONE);
        importNotes.setVisibility(signedIn ? View.VISIBLE : View.GONE);
        if (!configured) syncStatus.setText("로컬 전용 모드 · SETUP_FIREBASE_KO.md를 확인하세요.");
        else if (!signedIn) syncStatus.setText("로컬 전용 모드 · 로그인하면 실시간 동기화를 사용할 수 있습니다.");
    }

    @Override public void onSyncStatus(String status) { syncStatus.setText(status); }
    @Override public void onLocalDataChanged() { reloadNotes(); }

    private void requestWidget() {
        AppWidgetManager manager = AppWidgetManager.getInstance(this);
        ComponentName provider = new ComponentName(this, NoteWidgetProvider.class);
        if (manager.isRequestPinAppWidgetSupported()) {
            PendingIntent callback = PendingIntent.getActivity(
                    this,
                    9001,
                    new Intent(this, MainActivity.class),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            manager.requestPinAppWidget(provider, null, callback);
        } else {
            Toast.makeText(this, "홈 화면을 길게 눌러 위젯 목록에서 Black Walnut 메모를 추가하세요.", Toast.LENGTH_LONG).show();
        }
    }
}
