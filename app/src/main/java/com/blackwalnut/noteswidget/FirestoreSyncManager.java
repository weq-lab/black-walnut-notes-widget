package com.blackwalnut.noteswidget;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.Source;
import com.google.android.gms.tasks.Tasks;

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
import java.util.concurrent.TimeUnit;

final class FirestoreSyncManager {
    interface Listener {
        void onSyncStatus(String status);
        void onLocalDataChanged();
    }

    interface ImportCallback { void onComplete(int count); }

    private static final String SYNC_PREFS = "black_walnut_background_sync";
    private static final String LAST_SUCCESS = "last_success";
    private static final String LAST_FULL = "last_full";
    private static final Object PULL_LOCK = new Object();

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

    static void pullOnce(Context context, Runnable completion) {
        FirebaseUser user = FirebaseAuthController.currentUser(context);
        if (user == null) {
            completion.run();
            return;
        }
        appContext = context.getApplicationContext();
        AppDatabase.IO.execute(() -> {
            performServerPull(appContext, user.getUid(), true, 0L);
            MAIN.post(completion);
        });
    }

    static boolean performPeriodicSync(Context context) {
        FirebaseUser user = FirebaseAuthController.currentUser(context);
        if (user == null) return true;
        appContext = context.getApplicationContext();
        SharedPreferences prefs = appContext.getSharedPreferences(SYNC_PREFS, Context.MODE_PRIVATE);
        long now = System.currentTimeMillis();
        long lastSuccess = prefs.getLong(LAST_SUCCESS, 0L);
        boolean full = BackgroundSyncPolicy.shouldReconcileAll(now, prefs.getLong(LAST_FULL, 0L));
        boolean success = performServerPull(
                appContext,
                user.getUid(),
                full,
                BackgroundSyncPolicy.incrementalStart(lastSuccess)
        );
        if (success) {
            SharedPreferences.Editor editor = prefs.edit().putLong(LAST_SUCCESS, now);
            if (full) editor.putLong(LAST_FULL, now);
            editor.apply();
        }
        return success;
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
        transactionalUpload(uid, note, items)
                .addOnSuccessListener(result -> AppDatabase.IO.execute(() -> {
                    NoteDao dao = AppDatabase.get(appContext).noteDao();
                    if (result.conflict) {
                        if (result.remote != null) resolveKeepBoth(note, items, result.remote, result.remoteItems, dao);
                        else resolveRemoteDeletionConflict(note, items, dao);
                        status("동기화 충돌 · 원격 원본과 로컬 충돌 사본 보존됨");
                        NoteWidgetProvider.notifyAllWidgets(appContext);
                        changed();
                        return;
                    }
                    if (result.sameContent) dao.markSameContentSynced(note.id, note.updatedAt, result.remoteUpdatedAt);
                    else dao.markSynced(note.id, note.updatedAt);
                    status("동기화 완료");
                }))
                .addOnFailureListener(error -> status("오프라인 변경 보관 중 · 연결되면 재시도"));
    }

    private static com.google.android.gms.tasks.Task<UploadResult> transactionalUpload(
            String uid,
            NoteEntity local,
            List<ChecklistItemEntity> localItems
    ) {
        DocumentReference reference = notes(uid).document(local.cloudId);
        return FirebaseFirestore.getInstance().runTransaction(transaction -> {
            DocumentSnapshot snapshot = transaction.get(reference);
            boolean remoteExists = snapshot.exists();
            NoteEntity remote = remoteExists ? noteFrom(snapshot, uid) : null;
            List<ChecklistItemEntity> remoteItems = remoteExists ? itemsFrom(snapshot) : new ArrayList<>();
            boolean sameContent = remote != null && sameContent(local, localItems, remote, remoteItems);
            long remoteUpdatedAt = remote == null ? 0L : remote.updatedAt;
            SyncPolicy.UploadDecision decision = SyncPolicy.decideUpload(
                    local.lastSyncedUpdatedAt,
                    remoteExists,
                    remoteUpdatedAt,
                    sameContent
            );
            if (decision == SyncPolicy.UploadDecision.KEEP_LOCAL_CONFLICT) {
                return UploadResult.conflict(remote, remoteItems, remoteUpdatedAt);
            }
            if (decision == SyncPolicy.UploadDecision.SAME_CONTENT) {
                return UploadResult.sameContent(remoteUpdatedAt);
            }
            transaction.set(reference, serializeNote(local, localItems));
            return UploadResult.written(local.updatedAt);
        });
    }

    private static final class UploadResult {
        final boolean conflict;
        final boolean sameContent;
        final long remoteUpdatedAt;
        final NoteEntity remote;
        final List<ChecklistItemEntity> remoteItems;

        private UploadResult(boolean conflict, boolean sameContent, long remoteUpdatedAt,
                             NoteEntity remote, List<ChecklistItemEntity> remoteItems) {
            this.conflict = conflict;
            this.sameContent = sameContent;
            this.remoteUpdatedAt = remoteUpdatedAt;
            this.remote = remote;
            this.remoteItems = remoteItems;
        }

        static UploadResult conflict(NoteEntity remote, List<ChecklistItemEntity> remoteItems, long remoteUpdatedAt) {
            return new UploadResult(true, false, remoteUpdatedAt, remote, remoteItems);
        }
        static UploadResult sameContent(long remoteUpdatedAt) {
            return new UploadResult(false, true, remoteUpdatedAt, null, new ArrayList<>());
        }
        static UploadResult written(long updatedAt) {
            return new UploadResult(false, false, updatedAt, null, new ArrayList<>());
        }
    }

    private static Map<String, Object> serializeNote(NoteEntity note, List<ChecklistItemEntity> items) {
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
        return data;
    }

    private static boolean performServerPull(Context context, String uid, boolean full, long incrementalStart) {
        synchronized (PULL_LOCK) {
            try {
                Query query = full ? notes(uid) : notes(uid).whereGreaterThanOrEqualTo("updatedAt", incrementalStart);
                QuerySnapshot snapshot = Tasks.await(query.get(Source.SERVER), 30, TimeUnit.SECONDS);
                NoteDao dao = AppDatabase.get(context).noteDao();
                boolean conflict = false;
                for (DocumentSnapshot document : snapshot.getDocuments()) {
                    conflict |= applyPulledDocument(uid, document, dao);
                }
                if (full) conflict |= reconcilePulledSnapshot(uid, snapshot, dao);
                UploadBatchResult uploads = uploadPendingBlocking(uid, dao);
                conflict |= uploads.conflict;
                NoteWidgetProvider.notifyAllWidgets(context);
                changed();
                if (conflict) status("동기화 충돌 · 로컬 변경 보존됨");
                else status(uploads.success ? "백그라운드 동기화 완료" : "오프라인 변경 재시도 대기");
                return uploads.success;
            } catch (Exception error) {
                status("백그라운드 동기화 대기 · " + safeMessage(error));
                return false;
            }
        }
    }

    private static boolean applyPulledDocument(String uid, DocumentSnapshot document, NoteDao dao) {
        NoteEntity remote = noteFrom(document, uid);
        List<ChecklistItemEntity> remoteItems = itemsFrom(document);
        NoteEntity local = dao.getNoteByCloudId(uid, document.getId());
        if (local == null) {
            dao.applyRemote(remote, remoteItems);
            return false;
        }
        List<ChecklistItemEntity> localItems = dao.getItems(local.id);
        boolean different = !sameContent(local, localItems, remote, remoteItems);
        SyncPolicy.RemoteDecision decision = SyncPolicy.decideRemote(local.syncPending, different);
        if (decision == SyncPolicy.RemoteDecision.KEEP_LOCAL_CONFLICT) {
            resolveKeepBoth(local, localItems, remote, remoteItems, dao);
            return true;
        }
        dao.applyRemote(remote, remoteItems);
        return false;
    }

    private static boolean reconcilePulledSnapshot(String uid, QuerySnapshot snapshot, NoteDao dao) throws Exception {
        boolean conflict = false;
        Set<String> remoteIds = new HashSet<>();
        for (DocumentSnapshot document : snapshot.getDocuments()) remoteIds.add(document.getId());
        for (NoteEntity local : dao.listLinkedNotes(uid)) {
            if (remoteIds.contains(local.cloudId)) continue;
            if (local.syncPending) conflict |= uploadOneBlocking(uid, local, dao.getItems(local.id), dao).conflict;
            else dao.deleteRemoteNoteIfClean(uid, local.cloudId);
        }
        return conflict;
    }

    private static UploadBatchResult uploadPendingBlocking(String uid, NoteDao dao) {
        UploadBatchResult batch = new UploadBatchResult();
        try {
            for (NoteEntity note : dao.listPendingNotes(uid)) {
                batch.conflict |= uploadOneBlocking(uid, note, dao.getItems(note.id), dao).conflict;
            }
            for (PendingDeleteEntity pending : dao.listPendingDeletes(uid)) {
                DeleteResult result = Tasks.await(transactionalDelete(pending), 30, TimeUnit.SECONDS);
                finishDelete(pending, result, dao);
                batch.conflict |= result.conflict;
            }
            return batch;
        } catch (Exception error) {
            batch.success = false;
            return batch;
        }
    }

    private static UploadResult uploadOneBlocking(String uid, NoteEntity note, List<ChecklistItemEntity> items, NoteDao dao) throws Exception {
        if (note.cloudId.isEmpty()) return UploadResult.written(note.updatedAt);
        UploadResult result = Tasks.await(transactionalUpload(uid, note, items), 30, TimeUnit.SECONDS);
        if (result.conflict) {
            if (result.remote != null) resolveKeepBoth(note, items, result.remote, result.remoteItems, dao);
            else resolveRemoteDeletionConflict(note, items, dao);
            return result;
        }
        if (result.sameContent) dao.markSameContentSynced(note.id, note.updatedAt, result.remoteUpdatedAt);
        else dao.markSynced(note.id, note.updatedAt);
        return result;
    }

    private static final class UploadBatchResult {
        boolean success = true;
        boolean conflict;
    }

    private static com.google.android.gms.tasks.Task<DeleteResult> transactionalDelete(PendingDeleteEntity pending) {
        DocumentReference reference = notes(pending.ownerUid).document(pending.cloudId);
        return FirebaseFirestore.getInstance().runTransaction(transaction -> {
            DocumentSnapshot snapshot = transaction.get(reference);
            if (!snapshot.exists()) return DeleteResult.success();
            NoteEntity remote = noteFrom(snapshot, pending.ownerUid);
            List<ChecklistItemEntity> remoteItems = itemsFrom(snapshot);
            SyncPolicy.DeleteDecision decision = SyncPolicy.decideDelete(
                    pending.expectedRemoteUpdatedAt,
                    true,
                    remote.updatedAt
            );
            if (decision == SyncPolicy.DeleteDecision.CONFLICT) {
                return DeleteResult.conflict(remote, remoteItems);
            }
            transaction.delete(reference);
            return DeleteResult.success();
        });
    }

    private static void finishDelete(PendingDeleteEntity pending, DeleteResult result, NoteDao dao) {
        dao.deletePendingDelete(pending.ownerUid, pending.cloudId);
        if (!result.conflict || result.remote == null) return;
        SyncConflictEntity conflict = new SyncConflictEntity();
        conflict.localNoteId = 0L;
        conflict.ownerUid = pending.ownerUid;
        conflict.cloudId = pending.cloudId;
        conflict.detectedAt = System.currentTimeMillis();
        conflict.localUpdatedAt = pending.expectedRemoteUpdatedAt;
        conflict.remoteUpdatedAt = result.remote.updatedAt;
        conflict.localSnapshot = "conditional delete rejected";
        dao.insertConflict(conflict);
        dao.applyRemote(result.remote, result.remoteItems);
    }

    private static final class DeleteResult {
        final boolean conflict;
        final NoteEntity remote;
        final List<ChecklistItemEntity> remoteItems;

        private DeleteResult(boolean conflict, NoteEntity remote, List<ChecklistItemEntity> remoteItems) {
            this.conflict = conflict;
            this.remote = remote;
            this.remoteItems = remoteItems;
        }

        static DeleteResult success() { return new DeleteResult(false, null, new ArrayList<>()); }
        static DeleteResult conflict(NoteEntity remote, List<ChecklistItemEntity> remoteItems) {
            return new DeleteResult(true, remote, remoteItems);
        }
    }

    private static void uploadDelete(PendingDeleteEntity pending) {
        transactionalDelete(pending)
                .addOnSuccessListener(result -> AppDatabase.IO.execute(() -> {
                    NoteDao dao = AppDatabase.get(appContext).noteDao();
                    finishDelete(pending, result, dao);
                    if (result.conflict) status("삭제 충돌 · 원격 변경 보존됨");
                    NoteWidgetProvider.notifyAllWidgets(appContext);
                    changed();
                }))
                .addOnFailureListener(error -> status("삭제 변경 보관 중 · 연결되면 재시도"));
    }

    private static void applySnapshot(String uid, QuerySnapshot snapshot) {
        NoteDao dao = AppDatabase.get(appContext).noteDao();
        boolean conflict = false;
        for (DocumentChange change : snapshot.getDocumentChanges()) {
            DocumentSnapshot document = change.getDocument();
            if (document.getMetadata().hasPendingWrites()) continue;
            if (change.getType() == DocumentChange.Type.REMOVED) {
                NoteEntity local = dao.getNoteByCloudId(uid, document.getId());
                if (local != null && local.syncPending) uploadNote(uid, local, dao.getItems(local.id));
                else dao.deleteRemoteNoteIfClean(uid, document.getId());
                continue;
            }
            conflict |= applyRemoteDocument(uid, document, dao);
        }
        if (!snapshot.getMetadata().isFromCache()) reconcileServerSnapshot(uid, snapshot, dao);
        NoteWidgetProvider.notifyAllWidgets(appContext);
        changed();
        status(conflict
                ? "동기화 충돌 · 로컬 변경 보존됨"
                : snapshot.getMetadata().isFromCache() ? "오프라인 캐시 사용 중" : "실시간 동기화 연결됨");
    }

    private static boolean applyRemoteDocument(String uid, DocumentSnapshot document, NoteDao dao) {
        NoteEntity remote = noteFrom(document, uid);
        List<ChecklistItemEntity> remoteItems = itemsFrom(document);
        NoteEntity local = dao.getNoteByCloudId(uid, document.getId());
        if (local == null) {
            dao.applyRemote(remote, remoteItems);
            return false;
        }
        List<ChecklistItemEntity> localItems = dao.getItems(local.id);
        boolean different = !sameContent(local, localItems, remote, remoteItems);
        SyncPolicy.RemoteDecision decision = SyncPolicy.decideRemote(local.syncPending, different);
        if (decision == SyncPolicy.RemoteDecision.KEEP_LOCAL_CONFLICT) {
            resolveKeepBoth(local, localItems, remote, remoteItems, dao);
            return true;
        }
        dao.applyRemote(remote, remoteItems);
        return false;
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
        note.lastSyncedUpdatedAt = note.updatedAt;
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
        if (dao.conflictCount(local.ownerUid, local.cloudId, local.updatedAt, remoteUpdatedAt) > 0) return;
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

    private static void resolveKeepBoth(
            NoteEntity local,
            List<ChecklistItemEntity> localItems,
            NoteEntity remote,
            List<ChecklistItemEntity> remoteItems,
            NoteDao dao
    ) {
        SyncConflictEntity conflict = new SyncConflictEntity();
        conflict.localNoteId = local.id;
        conflict.ownerUid = local.ownerUid;
        conflict.cloudId = local.cloudId;
        conflict.detectedAt = System.currentTimeMillis();
        conflict.localUpdatedAt = local.updatedAt;
        conflict.remoteUpdatedAt = remote.updatedAt;
        conflict.localSnapshot = snapshot(local, localItems);
        NoteEntity copy = ConflictCopyFactory.createNoteCopy(local, System.currentTimeMillis());
        List<ChecklistItemEntity> copyItems = ConflictCopyFactory.createItemCopies(localItems);
        dao.keepBoth(local, localItems, remote, remoteItems, conflict, copy, copyItems);
    }

    private static void resolveRemoteDeletionConflict(
            NoteEntity local,
            List<ChecklistItemEntity> localItems,
            NoteDao dao
    ) {
        SyncConflictEntity conflict = new SyncConflictEntity();
        conflict.localNoteId = local.id;
        conflict.ownerUid = local.ownerUid;
        conflict.cloudId = local.cloudId;
        conflict.detectedAt = System.currentTimeMillis();
        conflict.localUpdatedAt = local.updatedAt;
        conflict.remoteUpdatedAt = 0L;
        conflict.localSnapshot = snapshot(local, localItems);
        NoteEntity copy = ConflictCopyFactory.createNoteCopy(local, System.currentTimeMillis());
        dao.preserveAfterRemoteDelete(local, conflict, copy);
    }

    private static boolean sameContent(
            NoteEntity left,
            List<ChecklistItemEntity> leftItems,
            NoteEntity right,
            List<ChecklistItemEntity> rightItems
    ) {
        if (!left.title.equals(right.title)
                || !left.body.equals(right.body)
                || left.createdAt != right.createdAt
                || !left.colorPreset.equals(right.colorPreset)
                || leftItems.size() != rightItems.size()) {
            return false;
        }
        for (int index = 0; index < leftItems.size(); index++) {
            ChecklistItemEntity leftItem = leftItems.get(index);
            ChecklistItemEntity rightItem = rightItems.get(index);
            if (!leftItem.text.equals(rightItem.text)
                    || leftItem.checked != rightItem.checked
                    || leftItem.position != rightItem.position) {
                return false;
            }
        }
        return true;
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
