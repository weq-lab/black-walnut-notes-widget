package com.blackwalnut.noteswidget;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QuerySnapshot;

import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class FirestoreSyncManager {
    interface Listener {
        void onSyncStatus(String status);
        void onLocalDataChanged();
    }

    interface ImportCallback { void onComplete(int count); }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static ListenerRegistration registration;
    private static String listeningUid = "";
    private static Context appContext;
    private static WeakReference<Listener> listener = new WeakReference<>(null);

    static synchronized void start(Context context, Listener nextListener) {
        appContext = context.getApplicationContext();
        listener = new WeakReference<>(nextListener);
        FirebaseUser user = FirebaseAuthController.currentUser(context);
        if (user == null) {
            status(FirebaseAuthController.isConfigured(context) ? "로컬 전용 모드 · 로그인하지 않음" : "로컬 전용 모드 · Firebase 미설정");
            return;
        }
        String uid = user.getUid();
        if (registration != null && uid.equals(listeningUid)) {
            status("동기화 연결됨 · " + displayUser(user));
            flushPending(uid);
            return;
        }
        stopRegistration();
        listeningUid = uid;
        status("동기화 연결 중…");
        registration = notes(uid).addSnapshotListener((snapshot, error) -> {
            if (error != null) {
                status("동기화 대기 · " + safeMessage(error));
                return;
            }
            if (snapshot == null) return;
            AppDatabase.IO.execute(() -> applySnapshot(uid, snapshot));
        });
        flushPending(uid);
    }

    static synchronized void setListener(Listener nextListener) {
        listener = new WeakReference<>(nextListener);
    }

    static synchronized void stop() {
        stopRegistration();
        listeningUid = "";
        listener = new WeakReference<>(null);
    }

    private static void stopRegistration() {
        if (registration != null) registration.remove();
        registration = null;
    }

    static void prepareNewNote(Context context, NoteEntity note) {
        FirebaseUser user = FirebaseAuthController.currentUser(context);
        if (user == null) return;
        note.ownerUid = user.getUid();
        note.cloudId = UUID.randomUUID().toString();
        note.syncPending = true;
    }

    static void prepareLocalEdit(Context context, NoteEntity note) {
        String uid = FirebaseAuthController.currentUid(context);
        if (!uid.isEmpty() && uid.equals(note.ownerUid) && !note.cloudId.isEmpty()) note.syncPending = true;
    }

    static void kick(Context context) {
        FirebaseUser user = FirebaseAuthController.currentUser(context);
        if (user == null) return;
        appContext = context.getApplicationContext();
        flushPending(user.getUid());
    }

    static void importLocalNotes(Context context, ImportCallback callback) {
        FirebaseUser user = FirebaseAuthController.currentUser(context);
        if (user == null) {
            callback.onComplete(0);
            return;
        }
        appContext = context.getApplicationContext();
        AppDatabase.IO.execute(() -> {
            NoteDao dao = AppDatabase.get(appContext).noteDao();
            List<NoteEntity> local = dao.listImportableNotes();
            for (NoteEntity note : local) dao.linkForImport(note.id, user.getUid(), UUID.randomUUID().toString());
            MAIN.post(() -> callback.onComplete(local.size()));
            flushPending(user.getUid());
        });
    }

    private static com.google.firebase.firestore.CollectionReference notes(String uid) {
        return FirebaseFirestore.getInstance().collection("users").document(uid).collection("notes");
    }

    private static void flushPending(String uid) {
        if (appContext == null || uid.isEmpty()) return;
        AppDatabase.IO.execute(() -> {
            NoteDao dao = AppDatabase.get(appContext).noteDao();
            for (NoteEntity note : dao.listPendingNotes(uid)) uploadNote(uid, note, dao.getItems(note.id));
            for (PendingDeleteEntity pending : dao.listPendingDeletes(uid)) uploadDelete(pending);
        });
    }

    private static void uploadNote(String uid, NoteEntity note, List<ChecklistItemEntity> items) {
        if (note.cloudId.isEmpty()) return;
        Map<String, Object> data = new HashMap<>();
        data.put("noteId", note.cloudId);
        data.put("title", note.title);
        data.put("body", note.body);
        data.put("createdAt", note.createdAt);
        data.put("updatedAt", note.updatedAt);
        data.put("colorPreset", note.colorPreset);
        data.put("schemaVersion", 1);
        List<Map<String, Object>> checklist = new ArrayList<>();
        for (ChecklistItemEntity item : items) {
            Map<String, Object> row = new HashMap<>();
            row.put("text", item.text);
            row.put("checked", item.checked);
            row.put("position", item.position);
            checklist.add(row);
        }
        data.put("checklist", checklist);
        long uploadedVersion = note.updatedAt;
        notes(uid).document(note.cloudId).set(data)
                .addOnSuccessListener(unused -> AppDatabase.IO.execute(() -> {
                    AppDatabase.get(appContext).noteDao().markSynced(note.id, uploadedVersion);
                    status("동기화 완료");
                }))
                .addOnFailureListener(error -> status("오프라인 변경 보관 중 · 연결되면 재시도"));
    }

    private static void uploadDelete(PendingDeleteEntity pending) {
        notes(pending.ownerUid).document(pending.cloudId).delete()
                .addOnSuccessListener(unused -> AppDatabase.IO.execute(() ->
                        AppDatabase.get(appContext).noteDao().deletePendingDelete(pending.ownerUid, pending.cloudId)))
                .addOnFailureListener(error -> status("삭제 변경 보관 중 · 연결되면 재시도"));
    }

    private static void applySnapshot(String uid, QuerySnapshot snapshot) {
        NoteDao dao = AppDatabase.get(appContext).noteDao();
        for (DocumentChange change : snapshot.getDocumentChanges()) {
            DocumentSnapshot document = change.getDocument();
            if (document.getMetadata().hasPendingWrites()) continue;
            if (change.getType() == DocumentChange.Type.REMOVED) {
                NoteEntity local = dao.getNoteByCloudId(uid, document.getId());
                if (local != null && local.syncPending) uploadNote(uid, local, dao.getItems(local.id));
                else dao.deleteRemoteNoteIfClean(uid, document.getId());
                continue;
            }
            applyRemoteDocument(uid, document, dao);
        }
        if (!snapshot.getMetadata().isFromCache()) reconcileServerSnapshot(uid, snapshot, dao);
        NoteWidgetProvider.notifyAllWidgets(appContext);
        changed();
        status(snapshot.getMetadata().isFromCache() ? "오프라인 캐시 사용 중" : "실시간 동기화 연결됨");
    }

    private static void applyRemoteDocument(String uid, DocumentSnapshot document, NoteDao dao) {
        NoteEntity remote = noteFrom(document, uid);
        List<ChecklistItemEntity> remoteItems = itemsFrom(document);
        NoteEntity local = dao.getNoteByCloudId(uid, document.getId());
        if (local == null) {
            dao.applyRemote(remote, remoteItems);
            return;
        }
        SyncPolicy.Decision decision = SyncPolicy.decide(local.updatedAt, remote.updatedAt);
        if (decision == SyncPolicy.Decision.UPLOAD_LOCAL) {
            uploadNote(uid, local, dao.getItems(local.id));
            return;
        }
        List<ChecklistItemEntity> localItems = dao.getItems(local.id);
        boolean different = !snapshot(local, localItems).equals(snapshot(remote, remoteItems));
        if (different && local.syncPending) logConflict(local, remote.updatedAt, localItems, dao);
        dao.applyRemote(remote, remoteItems);
    }

    private static void reconcileServerSnapshot(String uid, QuerySnapshot snapshot, NoteDao dao) {
        Set<String> remoteIds = new HashSet<>();
        for (DocumentSnapshot document : snapshot.getDocuments()) remoteIds.add(document.getId());
        for (NoteEntity local : dao.listLinkedNotes(uid)) {
            if (remoteIds.contains(local.cloudId)) continue;
            if (local.syncPending) uploadNote(uid, local, dao.getItems(local.id));
            else dao.deleteRemoteNoteIfClean(uid, local.cloudId);
        }
    }

    private static NoteEntity noteFrom(DocumentSnapshot document, String uid) {
        NoteEntity note = new NoteEntity();
        note.cloudId = document.getId();
        note.ownerUid = uid;
        note.title = stringValue(document.get("title"));
        note.body = stringValue(document.get("body"));
        note.createdAt = longValue(document.get("createdAt"));
        note.updatedAt = longValue(document.get("updatedAt"));
        if (note.createdAt == 0) note.createdAt = note.updatedAt;
        note.colorPreset = stringValue(document.get("colorPreset"));
        if (note.colorPreset.isEmpty()) note.colorPreset = ColorPresets.BLACK_WALNUT;
        note.syncPending = false;
        return note;
    }

    private static List<ChecklistItemEntity> itemsFrom(DocumentSnapshot document) {
        List<ChecklistItemEntity> result = new ArrayList<>();
        Object raw = document.get("checklist");
        if (!(raw instanceof List<?>)) return result;
        for (Object value : (List<?>) raw) {
            if (!(value instanceof Map<?, ?>)) continue;
            Map<?, ?> map = (Map<?, ?>) value;
            ChecklistItemEntity item = new ChecklistItemEntity();
            item.text = stringValue(map.get("text"));
            item.checked = Boolean.TRUE.equals(map.get("checked"));
            item.position = (int) longValue(map.get("position"));
            result.add(item);
        }
        return result;
    }

    private static void logConflict(NoteEntity local, long remoteUpdatedAt, List<ChecklistItemEntity> items, NoteDao dao) {
        SyncConflictEntity conflict = new SyncConflictEntity();
        conflict.localNoteId = local.id;
        conflict.ownerUid = local.ownerUid;
        conflict.cloudId = local.cloudId;
        conflict.detectedAt = System.currentTimeMillis();
        conflict.localUpdatedAt = local.updatedAt;
        conflict.remoteUpdatedAt = remoteUpdatedAt;
        conflict.localSnapshot = snapshot(local, items);
        dao.insertConflict(conflict);
    }

    private static String snapshot(NoteEntity note, List<ChecklistItemEntity> items) {
        try {
            JSONObject root = new JSONObject();
            root.put("title", note.title);
            root.put("body", note.body);
            root.put("colorPreset", note.colorPreset);
            root.put("updatedAt", note.updatedAt);
            JSONArray rows = new JSONArray();
            for (ChecklistItemEntity item : items) {
                JSONObject row = new JSONObject();
                row.put("text", item.text);
                row.put("checked", item.checked);
                row.put("position", item.position);
                rows.put(row);
            }
            root.put("checklist", rows);
            return root.toString();
        } catch (Exception error) {
            return note.title + "\n" + note.body + "\n" + note.updatedAt;
        }
    }

    private static long longValue(Object value) { return value instanceof Number ? ((Number) value).longValue() : 0L; }
    private static String stringValue(Object value) { return value == null ? "" : String.valueOf(value); }
    private static String displayUser(FirebaseUser user) { return user.getEmail() == null ? "Google 계정" : user.getEmail(); }
    private static String safeMessage(Throwable error) { return error.getMessage() == null ? "연결 오류" : error.getMessage(); }

    private static void status(String value) {
        MAIN.post(() -> {
            Listener target = listener.get();
            if (target != null) target.onSyncStatus(value);
        });
    }

    private static void changed() {
        MAIN.post(() -> {
            Listener target = listener.get();
            if (target != null) target.onLocalDataChanged();
        });
    }

    private FirestoreSyncManager() { }
}
