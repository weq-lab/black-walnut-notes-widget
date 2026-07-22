import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import type { Auth, User } from "firebase/auth";
import type { Firestore } from "firebase/firestore";
import { BackupPanel } from "./components/BackupPanel";
import { ChecklistEditor } from "./components/ChecklistEditor";
import { useAuth } from "./hooks/useAuth";
import { usePwa } from "./hooks/usePwa";
import { createDebouncedTask, type DebouncedTask } from "./lib/debounce";
import { initialNoteSelection } from "./lib/editorSelection";
import { decideRemoteUpdate } from "./lib/conflict";
import { deferDelete, type DeferredDelete } from "./lib/deferredDelete";
import { createStoredDraft, decideDraftRestore, parseStoredDraft, type StoredDraft } from "./lib/storedDraft";
import { bodyFontClass, titleFontClass } from "./lib/typography";
import {
  firebaseErrorMessage,
  getNotesFirestore,
  missingFirebaseConfig,
  signInWithGoogle,
  signOutGoogle,
  type CacheMode,
} from "./lib/firebase";
import {
  firebaseDebugCounter,
  NoteWriteConflictError,
  removeNote,
  subscribeToNotes,
  writeNote,
  type RemoteNote,
} from "./lib/notesRepository";
import {
  NOTE_BLOCK_BYTES,
  NOTE_WARN_BYTES,
  PRESET_NAMES,
  createNote,
  noteUtf8Size,
  sameContent,
  serializeNote,
  safePreset,
  type ChecklistItem,
  type Note,
} from "./models/note";
import "./styles/app.css";

const CACHE_MODE_KEY = "black-walnut-cache-mode";
const LAST_NOTE_KEY = "black-walnut-last-note";

type SaveStatus = "저장 중" | "동기화됨" | "오프라인" | "오프라인 변경 보관됨" | "연결 복구 중" | "동기화 충돌" | "동기화 오류";

interface ConflictState {
  remote: Note | null;
  deleted: boolean;
}

function draftKey(uid: string, noteId: string) {
  return `black-walnut-draft:${uid}:${noteId}`;
}

function loadStoredDrafts(uid: string): StoredDraft[] {
  const prefix = `black-walnut-draft:${uid}:`;
  const drafts: StoredDraft[] = [];
  for (let index = 0; index < localStorage.length; index += 1) {
    const key = localStorage.key(index);
    if (!key?.startsWith(prefix)) continue;
    const parsed = parseStoredDraft(localStorage.getItem(key), key.slice(prefix.length));
    if (parsed) drafts.push(parsed);
  }
  return drafts.sort((left, right) => right.savedAt - left.savedAt);
}

function storeDraft(uid: string, note: Note, baseUpdatedAt: number | null) {
  localStorage.setItem(draftKey(uid, note.noteId), JSON.stringify(createStoredDraft(note, baseUpdatedAt)));
}

function ConfigScreen() {
  const missing = missingFirebaseConfig();
  return (
    <main className="center-screen">
      <section className="setup-card">
        <span className="walnut-mark">BW</span>
        <h1>Firebase 웹 설정이 필요합니다</h1>
        <p>앱은 검은 빈 화면 대신 누락된 설정을 안전하게 알려드립니다. Firebase Console에서 Web App을 등록한 뒤 <code>web/.env.local</code>에 값을 입력하세요.</p>
        <ul>{missing.map((name) => <li key={name}><code>{name}</code></li>)}</ul>
        <p className="muted">Android의 google-services.json은 웹 설정으로 사용하지 않습니다.</p>
      </section>
    </main>
  );
}

function LoginScreen({ auth, error, setError }: { auth: Auth; error: string; setError(value: string): void }) {
  const [busy, setBusy] = useState(false);
  const login = async () => {
    setBusy(true);
    setError("");
    try { await signInWithGoogle(auth); }
    catch (cause) { setError(firebaseErrorMessage(cause)); }
    finally { setBusy(false); }
  };
  return (
    <main className="center-screen">
      <section className="login-card">
        <span className="walnut-mark">BW</span>
        <div>
          <span className="eyebrow">개인용 메모 · 체크리스트</span>
          <h1>Black Walnut Notes</h1>
        </div>
        <p>Android 앱과 같은 Google 계정으로 로그인하면 메모가 실시간으로 연결됩니다.</p>
        <button className="primary login-button" onClick={() => void login()} disabled={busy}>{busy ? "Google 로그인 중…" : "Google 계정으로 로그인"}</button>
        {error && <p className="error-message" role="alert">{error}</p>}
        <p className="muted">로그인 전에는 Firestore에 접근하지 않습니다.</p>
      </section>
    </main>
  );
}

function CacheChoice({ choose }: { choose(mode: CacheMode): void }) {
  return (
    <main className="center-screen">
      <section className="choice-card">
        <span className="eyebrow">첫 로그인 설정</span>
        <h1>이 PC에 오프라인 메모를 저장하시겠습니까?</h1>
        <p>개인 PC에서만 영구 캐시를 사용하세요. 이 선택은 브라우저에 저장됩니다.</p>
        <div className="choice-grid">
          <button className="choice" onClick={() => choose("persistent")}>
            <strong>개인 PC</strong>
            <span>IndexedDB에 암호화되지 않은 오프라인 캐시를 유지하고 여러 탭에서 공유합니다.</span>
          </button>
          <button className="choice" onClick={() => choose("memory")}>
            <strong>공용 또는 임시 PC</strong>
            <span>현재 실행 중인 메모리에만 캐시합니다.</span>
          </button>
        </div>
      </section>
    </main>
  );
}

function NotesApp({ auth, user, cacheMode }: { auth: Auth; user: User; cacheMode: CacheMode }) {
  const database = useMemo<{ db: Firestore | null; error: string }>(() => {
    try { return { db: getNotesFirestore(cacheMode), error: "" }; }
    catch (error) { return { db: null, error: firebaseErrorMessage(error) }; }
  }, [cacheMode]);
  if (!database.db) return <main className="center-screen"><section className="setup-card"><h1>오프라인 캐시를 시작하지 못했습니다</h1><p>{database.error}</p><p>사이트 데이터를 삭제하고 다시 로그인하여 공용 PC 모드를 선택해 보세요.</p></section></main>;
  return <NotesWorkspace auth={auth} user={user} db={database.db} cacheMode={cacheMode} />;
}

function NotesWorkspace({ auth, user, db, cacheMode }: { auth: Auth; user: User; db: Firestore; cacheMode: CacheMode }) {
  const [notes, setNotes] = useState<RemoteNote[]>([]);
  const [localDrafts, setLocalDrafts] = useState<StoredDraft[]>(() => loadStoredDrafts(user.uid));
  const [loaded, setLoaded] = useState(false);
  const [selectedId, setSelectedId] = useState(() => initialNoteSelection(localStorage.getItem(LAST_NOTE_KEY), []));
  const [draft, setDraft] = useState<Note | null>(null);
  const [status, setStatus] = useState<SaveStatus>(navigator.onLine ? "연결 복구 중" : "오프라인");
  const [syncError, setSyncError] = useState("");
  const [search, setSearch] = useState("");
  const [conflict, setConflict] = useState<ConflictState | null>(null);
  const [showSettings, setShowSettings] = useState(false);
  const [deletingId, setDeletingId] = useState("");
  const [narrowEditor, setNarrowEditor] = useState(false);
  const [invalidCount, setInvalidCount] = useState(0);
  const [sizeMessage, setSizeMessage] = useState("");
  const { canInstall, install, updateAvailable, applyUpdate } = usePwa();

  const notesRef = useRef(notes);
  const selectedRef = useRef(selectedId);
  const draftRef = useRef<Note | null>(draft);
  const lastRemoteRef = useRef<Note | null>(null);
  const baseUpdatedAtRef = useRef<number | null>(null);
  const dirtyRef = useRef(false);
  const draftRevisionRef = useRef(0);
  const saveRunningRef = useRef(false);
  const lastWrittenRef = useRef<Note | null>(null);
  const conflictRef = useRef<ConflictState | null>(null);
  const saveNowRef = useRef<() => Promise<void>>(async () => undefined);
  const debouncedRef = useRef<DebouncedTask | null>(null);
  const deletionRef = useRef<DeferredDelete | null>(null);
  if (!debouncedRef.current) debouncedRef.current = createDebouncedTask(() => saveNowRef.current(), 500);

  const refreshLocalDrafts = useCallback(() => setLocalDrafts(loadStoredDrafts(user.uid)), [user.uid]);

  const applyRemote = useCallback((note: Note, restoreDraft = false) => {
    let next = note;
    let dirty = false;
    let restoredBase: number | null = note.updatedAt;
    let restoredConflict: ConflictState | null = null;
    if (restoreDraft) {
      const stored = parseStoredDraft(localStorage.getItem(draftKey(user.uid, note.noteId)), note.noteId);
      const decision = decideDraftRestore(note, stored);
      if (decision.kind === "restore") {
        next = decision.draft.note;
        dirty = decision.dirty;
        restoredBase = decision.draft.baseUpdatedAt;
      } else if (decision.kind === "conflict") {
        next = decision.draft.note;
        dirty = true;
        restoredBase = decision.draft.baseUpdatedAt;
        restoredConflict = { remote: note, deleted: false };
      }
    }
    draftRef.current = next;
    setDraft(next);
    lastRemoteRef.current = note;
    baseUpdatedAtRef.current = restoredBase;
    dirtyRef.current = dirty;
    draftRevisionRef.current = dirty ? 1 : 0;
    lastWrittenRef.current = null;
    conflictRef.current = restoredConflict;
    setConflict(restoredConflict);
    setSizeMessage("");
    setStatus(restoredConflict ? "동기화 충돌" : dirty ? "저장 중" : "동기화됨");
    if (restoredConflict) {
      refreshLocalDrafts();
    } else if (dirty) {
      debouncedRef.current?.schedule();
    } else {
      localStorage.removeItem(draftKey(user.uid, note.noteId));
      refreshLocalDrafts();
    }
  }, [refreshLocalDrafts, user.uid]);

  const clearSelection = useCallback(() => {
    selectedRef.current = "";
    setSelectedId("");
    draftRef.current = null;
    setDraft(null);
    lastRemoteRef.current = null;
    lastWrittenRef.current = null;
    baseUpdatedAtRef.current = null;
    dirtyRef.current = false;
    draftRevisionRef.current = 0;
    conflictRef.current = null;
    setConflict(null);
    localStorage.removeItem(LAST_NOTE_KEY);
    setNarrowEditor(false);
  }, []);

  useEffect(() => {
    const unsubscribe = subscribeToNotes(db, user.uid, (snapshot) => {
      notesRef.current = snapshot.notes;
      setNotes(snapshot.notes);
      setLoaded(true);
      setInvalidCount(snapshot.invalidDocuments.length);
      setSyncError("");
      if (!conflictRef.current) {
        if (!navigator.onLine) setStatus(snapshot.hasPendingWrites ? "오프라인 변경 보관됨" : "오프라인");
        else if (snapshot.hasPendingWrites) setStatus("저장 중");
        else setStatus(snapshot.fromCache ? "연결 복구 중" : "동기화됨");
      }

      const currentId = selectedRef.current;
      if (!currentId) return;
      const remoteEntry = snapshot.notes.find((entry) => entry.note.noteId === currentId);
      if (!remoteEntry) {
        if (lastRemoteRef.current && dirtyRef.current) {
          const nextConflict = { remote: null, deleted: true };
          conflictRef.current = nextConflict;
          setConflict(nextConflict);
          setStatus("동기화 충돌");
        }
        else if (lastRemoteRef.current) clearSelection();
        return;
      }
      if (dirtyRef.current && draftRef.current && sameContent(draftRef.current, remoteEntry.note)) {
        applyRemote(remoteEntry.note);
        return;
      }
      if (dirtyRef.current && !remoteEntry.hasPendingWrites && lastWrittenRef.current && sameContent(lastWrittenRef.current, remoteEntry.note)) {
        lastRemoteRef.current = remoteEntry.note;
        baseUpdatedAtRef.current = remoteEntry.note.updatedAt;
        lastWrittenRef.current = null;
        setStatus("저장 중");
        debouncedRef.current?.schedule();
        return;
      }
      const decision = decideRemoteUpdate({
        baseUpdatedAt: baseUpdatedAtRef.current,
        localDirty: dirtyRef.current,
      }, remoteEntry.note, remoteEntry.hasPendingWrites);
      if (decision === "apply") applyRemote(remoteEntry.note);
      else if (decision === "keep-local") {
        const nextConflict = { remote: remoteEntry.note, deleted: false };
        conflictRef.current = nextConflict;
        setConflict(nextConflict);
        setStatus("동기화 충돌");
      }
    }, (error) => {
      setSyncError(firebaseErrorMessage(error));
      setStatus("동기화 오류");
    });
    return unsubscribe;
  }, [applyRemote, clearSelection, db, user.uid]);

  const saveNow = useCallback(async () => {
    const current = draftRef.current;
    if (!current || !dirtyRef.current || saveRunningRef.current) return;
    if (conflictRef.current) return;
    const remote = lastRemoteRef.current;
    if (remote && sameContent(current, remote)) {
      dirtyRef.current = false;
      localStorage.removeItem(draftKey(user.uid, current.noteId));
      refreshLocalDrafts();
      setStatus("동기화됨");
      return;
    }
    if (!navigator.onLine) {
      storeDraft(user.uid, current, baseUpdatedAtRef.current);
      refreshLocalDrafts();
      setStatus("오프라인 변경 보관됨");
      return;
    }
    const baseUpdatedAt = baseUpdatedAtRef.current;
    const nextTime = Math.max(Date.now(), (baseUpdatedAt ?? 0) + 1);
    const candidate = serializeNote({ ...current, updatedAt: nextTime, createdAt: current.createdAt });
    const size = noteUtf8Size(candidate);
    if (size >= NOTE_BLOCK_BYTES) {
      setSizeMessage("노트가 950KiB를 넘어 Firestore 저장을 차단했습니다. 현재 초안은 보존됩니다. JSON 또는 Markdown으로 내보내세요.");
      setStatus("동기화 오류");
      return;
    }
    setSizeMessage(size >= NOTE_WARN_BYTES ? "노트가 매우 큽니다. 여러 노트로 나누는 것이 좋습니다." : "");
    const savingRevision = draftRevisionRef.current;
    saveRunningRef.current = true;
    lastWrittenRef.current = candidate;
    setStatus("저장 중");
    try {
      const committed = await writeNote(db, user.uid, candidate, baseUpdatedAt);
      lastRemoteRef.current = committed;
      baseUpdatedAtRef.current = committed.updatedAt;
      lastWrittenRef.current = null;
      conflictRef.current = null;
      setConflict(null);
      const latest = draftRef.current;
      if (latest && latest.noteId === candidate.noteId && sameContent(latest, candidate)
          && (!dirtyRef.current || draftRevisionRef.current === savingRevision)) {
        draftRef.current = committed;
        setDraft(committed);
        dirtyRef.current = false;
        localStorage.removeItem(draftKey(user.uid, candidate.noteId));
        refreshLocalDrafts();
        setStatus("동기화됨");
      } else if (latest && latest.noteId === candidate.noteId) {
        dirtyRef.current = true;
        storeDraft(user.uid, latest, baseUpdatedAtRef.current);
        refreshLocalDrafts();
      }
    } catch (error) {
      lastWrittenRef.current = null;
      dirtyRef.current = true;
      const latest = draftRef.current ?? candidate;
      storeDraft(user.uid, latest, baseUpdatedAtRef.current);
      refreshLocalDrafts();
      if (error instanceof NoteWriteConflictError) {
        const nextConflict = { remote: error.remote, deleted: error.remote === null };
        conflictRef.current = nextConflict;
        setConflict(nextConflict);
        setSyncError("");
        setStatus("동기화 충돌");
      } else {
        setSyncError(firebaseErrorMessage(error));
        setStatus("동기화 오류");
      }
    } finally {
      saveRunningRef.current = false;
      if (dirtyRef.current && !conflictRef.current && navigator.onLine && draftRevisionRef.current > savingRevision) {
        debouncedRef.current?.schedule();
      }
    }
  }, [db, refreshLocalDrafts, user.uid]);
  saveNowRef.current = saveNow;

  const editDraft = useCallback((change: (current: Note) => Note) => {
    const current = draftRef.current;
    if (!current) return;
    const next = change(current);
    draftRef.current = next;
    setDraft(next);
    dirtyRef.current = true;
    draftRevisionRef.current += 1;
    storeDraft(user.uid, next, baseUpdatedAtRef.current);
    refreshLocalDrafts();
    setStatus("저장 중");
    debouncedRef.current?.schedule();
  }, [refreshLocalDrafts, user.uid]);

  const selectNote = useCallback(async (note: Note, localOnlyDraft: StoredDraft | null = null) => {
    await debouncedRef.current?.flush();
    selectedRef.current = note.noteId;
    setSelectedId(note.noteId);
    localStorage.setItem(LAST_NOTE_KEY, note.noteId);
    if (localOnlyDraft) {
      draftRef.current = localOnlyDraft.note;
      setDraft(localOnlyDraft.note);
      lastRemoteRef.current = null;
      lastWrittenRef.current = null;
      baseUpdatedAtRef.current = localOnlyDraft.baseUpdatedAt;
      dirtyRef.current = true;
      draftRevisionRef.current = 1;
      conflictRef.current = null;
      setConflict(null);
      setStatus(navigator.onLine ? "저장 중" : "오프라인 변경 보관됨");
      if (navigator.onLine) debouncedRef.current?.schedule();
    } else {
      applyRemote(note, true);
    }
    setNarrowEditor(true);
  }, [applyRemote]);

  const newNote = useCallback(async () => {
    await debouncedRef.current?.flush();
    const note = createNote();
    selectedRef.current = note.noteId;
    setSelectedId(note.noteId);
    localStorage.setItem(LAST_NOTE_KEY, note.noteId);
    lastRemoteRef.current = null;
    baseUpdatedAtRef.current = null;
    draftRef.current = note;
    setDraft(note);
    dirtyRef.current = true;
    draftRevisionRef.current = 1;
    storeDraft(user.uid, note, null);
    refreshLocalDrafts();
    setNarrowEditor(true);
    await saveNowRef.current();
  }, [refreshLocalDrafts, user.uid]);

  useEffect(() => {
    const online = () => {
      setStatus("연결 복구 중");
      if (dirtyRef.current && !conflictRef.current) void saveNowRef.current();
    };
    const offline = () => setStatus(dirtyRef.current ? "오프라인 변경 보관됨" : "오프라인");
    window.addEventListener("online", online);
    window.addEventListener("offline", offline);
    const visibility = () => { if (document.visibilityState === "hidden") void debouncedRef.current?.flush(); };
    document.addEventListener("visibilitychange", visibility);
    return () => {
      window.removeEventListener("online", online);
      window.removeEventListener("offline", offline);
      document.removeEventListener("visibilitychange", visibility);
      debouncedRef.current?.cancel();
      deletionRef.current?.cancel();
    };
  }, []);

  useEffect(() => {
    const shortcuts = (event: KeyboardEvent) => {
      if (event.ctrlKey && event.key.toLowerCase() === "n") { event.preventDefault(); void newNote(); }
      if (event.ctrlKey && event.key.toLowerCase() === "f") { event.preventDefault(); document.getElementById("note-search")?.focus(); }
      if (event.ctrlKey && event.key.toLowerCase() === "s") { event.preventDefault(); void debouncedRef.current?.flush(); }
      if (event.key === "Escape" && window.matchMedia("(max-width: 720px)").matches) setNarrowEditor(false);
    };
    window.addEventListener("keydown", shortcuts);
    return () => window.removeEventListener("keydown", shortcuts);
  }, [newNote]);

  const logout = async () => {
    await debouncedRef.current?.flush();
    await signOutGoogle(auth);
  };

  const requestDelete = () => {
    if (!draft || !window.confirm("이 노트를 삭제할까요? 10초 동안 실행 취소할 수 있습니다.")) return;
    deletionRef.current?.cancel();
    const deletingNote = draft;
    const expectedUpdatedAt = baseUpdatedAtRef.current;
    const wasRemote = lastRemoteRef.current !== null;
    setDeletingId(deletingNote.noteId);
    deletionRef.current = deferDelete(async () => {
      const id = deletingNote.noteId;
      setDeletingId("");
      if (!wasRemote && expectedUpdatedAt === null) {
        localStorage.removeItem(draftKey(user.uid, id));
        refreshLocalDrafts();
        if (selectedRef.current === id) clearSelection();
        return;
      }
      if (expectedUpdatedAt === null) {
        setSyncError("삭제할 원격 버전을 확인할 수 없습니다. 원격 변경을 먼저 불러오세요.");
        setStatus("동기화 충돌");
        return;
      }
      try {
        await removeNote(db, user.uid, id, expectedUpdatedAt);
        localStorage.removeItem(draftKey(user.uid, id));
        refreshLocalDrafts();
        if (selectedRef.current === id) clearSelection();
      } catch (error) {
        if (error instanceof NoteWriteConflictError) {
          const nextConflict = { remote: error.remote, deleted: error.remote === null };
          conflictRef.current = nextConflict;
          setConflict(nextConflict);
          setSyncError("");
          setStatus("동기화 충돌");
        } else {
          setSyncError(firebaseErrorMessage(error));
          setStatus("동기화 오류");
        }
      }
    });
  };

  const undoDelete = () => {
    deletionRef.current?.cancel();
    deletionRef.current = null;
    setDeletingId("");
  };

  const listedNotes = useMemo(() => {
    const remoteIds = new Set(notes.map(({ note }) => note.noteId));
    const localOnly = localDrafts
      .filter((draft) => !remoteIds.has(draft.note.noteId))
      .map((draft) => ({ note: draft.note, hasPendingWrites: true, localOnlyDraft: draft }));
    return [
      ...localOnly,
      ...notes.map((entry) => ({ ...entry, localOnlyDraft: null })),
    ].sort((left, right) => right.note.updatedAt - left.note.updatedAt);
  }, [localDrafts, notes]);

  const filteredNotes = useMemo(() => {
    const query = search.trim().toLocaleLowerCase("ko");
    if (!query) return listedNotes;
    return listedNotes.filter(({ note }) => [note.title, note.body, ...note.checklist.map((item) => item.text)].some((value) => value.toLocaleLowerCase("ko").includes(query)));
  }, [listedNotes, search]);

  const backupNotes = useMemo(() => {
    const current = notes.map((entry) => entry.note);
    if (!draft) return current;
    const serializedDraft = serializeNote(draft);
    const index = current.findIndex((note) => note.noteId === draft.noteId);
    if (index < 0) return [serializedDraft, ...current];
    return current.map((note, position) => position === index ? serializedDraft : note);
  }, [draft, notes]);

  return (
    <main className={`app-shell ${narrowEditor ? "show-editor" : "show-list"}`}>
      <aside className="sidebar">
        <header className="app-header">
          <div className="compact-brand"><span className="walnut-mark small">BW</span><span>Walnut Notes</span></div>
          <button className="primary new-note" onClick={() => void newNote()}>＋ 새 노트</button>
        </header>
        <label className="search-wrap" htmlFor="note-search"><span>⌕</span><input id="note-search" value={search} onChange={(event) => setSearch(event.target.value)} placeholder="노트와 체크리스트 검색" /></label>
        <div className="notes-list" role="listbox" aria-label="노트 목록">
          {!loaded && <p className="list-message">노트를 불러오는 중…</p>}
          {loaded && filteredNotes.length === 0 && <p className="list-message">{search ? "검색 결과가 없습니다." : "새 노트를 만들어 시작하세요."}</p>}
          {filteredNotes.map(({ note, localOnlyDraft }) => (
            <button key={note.noteId} className={`note-list-item ${selectedId === note.noteId ? "selected" : ""}`} onClick={() => void selectNote(note, localOnlyDraft)} role="option" aria-selected={selectedId === note.noteId}>
              <strong className={titleFontClass(note.title.trim() || "제목 없음")}>{note.title.trim() || "제목 없음"}</strong>
              <span className={bodyFontClass(note.body.trim() || note.checklist[0]?.text || "내용 없음")}>{note.body.trim() || note.checklist[0]?.text || "내용 없음"}</span>
              <time>{new Intl.DateTimeFormat("ko-KR", { month: "short", day: "numeric", hour: "2-digit", minute: "2-digit" }).format(note.updatedAt)}</time>
            </button>
          ))}
        </div>
        <footer className="account-bar">
          <div><span className={`status-dot status-${status.replaceAll(" ", "-")}`} /><strong>{status}</strong><small>{user.email ?? "Google 계정"}</small></div>
          <button className="icon-button" onClick={() => setShowSettings(true)} aria-label="백업 및 설정">⚙</button>
          <button className="text-button" onClick={() => void logout()}>로그아웃</button>
        </footer>
      </aside>

      <section className="editor-pane" data-preset={safePreset(draft?.colorPreset ?? "블랙 월넛")}>
        {draft ? (
          <>
            <header className="editor-toolbar">
              <button className="icon-button back-button" onClick={() => setNarrowEditor(false)} aria-label="목록으로 돌아가기">←</button>
              <span className="save-state">{status}</span>
              {canInstall && <button className="text-button" onClick={() => void install()}>앱 설치</button>}
              <button className="danger-button" onClick={requestDelete}>삭제</button>
            </header>
            {conflict && (
              <div className="conflict-banner" role="alert">
                <strong>{conflict.deleted ? "다른 기기에서 이 노트가 삭제되었습니다." : "다른 기기에서 이 노트가 변경되었습니다."}</strong>
                <div>
                  <button onClick={() => {
                    if (!window.confirm("현재 초안을 버리고 원격 변경을 불러올까요?")) return;
                    if (conflict.remote) applyRemote(conflict.remote);
                    else clearSelection();
                  }}>원격 변경 불러오기</button>
                  <button className="primary" onClick={() => {
                    baseUpdatedAtRef.current = conflict.remote?.updatedAt ?? null;
                    lastRemoteRef.current = conflict.remote;
                    dirtyRef.current = true;
                    draftRevisionRef.current += 1;
                    conflictRef.current = null;
                    setConflict(null);
                    if (draftRef.current) {
                      storeDraft(user.uid, draftRef.current, baseUpdatedAtRef.current);
                      refreshLocalDrafts();
                    }
                    void saveNowRef.current();
                  }}>내 변경으로 덮어쓰기</button>
                </div>
              </div>
            )}
            <div className="editor-scroll">
              <input className={`title-input ${titleFontClass(draft.title)}`} value={draft.title} placeholder="제목" onChange={(event) => editDraft((note) => ({ ...note, title: event.target.value }))} />
              <textarea className={`body-input ${bodyFontClass(draft.body)}`} value={draft.body} placeholder="메모를 입력하세요" onChange={(event) => editDraft((note) => ({ ...note, body: event.target.value }))} />
              <ChecklistEditor items={draft.checklist} onChange={(checklist: ChecklistItem[]) => editDraft((note) => ({ ...note, checklist }))} />
              <section className="preset-section">
                <label htmlFor="color-preset">색상 프리셋</label>
                <select id="color-preset" value={PRESET_NAMES.includes(draft.colorPreset) ? draft.colorPreset : "블랙 월넛"} onChange={(event) => editDraft((note) => ({ ...note, colorPreset: event.target.value }))}>
                  {PRESET_NAMES.map((name) => <option key={name}>{name}</option>)}
                </select>
                {draft.colorPreset === "HEX 직접 입력" && <p className="muted">사용자 HEX 값은 현재 Firestore 스키마에 없어 블랙 월넛으로 표시됩니다.</p>}
              </section>
              {sizeMessage && <p className="size-warning" role="alert">{sizeMessage}</p>}
            </div>
          </>
        ) : (
          <div className="empty-editor"><span className="walnut-mark">BW</span><h2>노트를 선택하거나 새로 만드세요</h2><button className="primary" onClick={() => void newNote()}>＋ 새 노트</button></div>
        )}
      </section>

      {(syncError || invalidCount > 0) && <div className="error-toast" role="alert">{syncError || `스키마가 올바르지 않은 원격 문서 ${invalidCount}개를 표시하지 않았습니다.`}</div>}
      {deletingId && <div className="undo-toast" role="status"><span>10초 후 노트를 삭제합니다.</span><button onClick={undoDelete}>실행 취소</button></div>}
      {updateAvailable && <div className="update-banner"><span>업데이트 가능</span><button onClick={async () => { await debouncedRef.current?.flush(); applyUpdate(); }}>저장 후 적용</button></div>}
      {showSettings && <BackupPanel db={db} uid={user.uid} notes={backupNotes} cacheMode={cacheMode} onClose={() => setShowSettings(false)} />}
      {import.meta.env.DEV && <output className="debug-counter" title="개발용 Firebase 호출 수">L {firebaseDebugCounter.activeListeners}/{firebaseDebugCounter.listenersStarted} · R {firebaseDebugCounter.snapshots} · W {firebaseDebugCounter.writes}</output>}
    </main>
  );
}

export default function App() {
  const missing = missingFirebaseConfig();
  if (missing.length > 0) return <ConfigScreen />;
  return <ConfiguredApp />;
}

function ConfiguredApp() {
  const { auth, user, loading, error, setError } = useAuth();
  const [cacheMode, setCacheMode] = useState<CacheMode | null>(() => {
    const saved = localStorage.getItem(CACHE_MODE_KEY);
    return saved === "persistent" || saved === "memory" ? saved : null;
  });
  if (loading || !auth) return <main className="center-screen"><div className="loading-ring" aria-label="로그인 상태 확인 중" /></main>;
  if (!user) return <LoginScreen auth={auth} error={error} setError={setError} />;
  if (!cacheMode) return <CacheChoice choose={(mode) => { localStorage.setItem(CACHE_MODE_KEY, mode); setCacheMode(mode); }} />;
  return <NotesApp auth={auth} user={user} cacheMode={cacheMode} />;
}
