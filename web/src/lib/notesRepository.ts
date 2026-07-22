import {
  collection,
  doc,
  onSnapshot,
  runTransaction,
  type Firestore,
  type Unsubscribe,
} from "firebase/firestore";
import { noteUtf8Size, NOTE_BLOCK_BYTES, sameContent, serializeNote, validateNote, type Note } from "../models/note";

export interface RemoteNote {
  note: Note;
  hasPendingWrites: boolean;
}

export interface NotesSnapshot {
  notes: RemoteNote[];
  fromCache: boolean;
  hasPendingWrites: boolean;
  invalidDocuments: string[];
}

export type ConditionalWriteDecision =
  | { kind: "write" }
  | { kind: "same-content"; remote: Note }
  | { kind: "conflict"; remote: Note | null };

export class NoteWriteConflictError extends Error {
  readonly remote: Note | null;

  constructor(remote: Note | null) {
    super(remote ? "서버의 노트가 편집 시작 이후 변경되었습니다." : "서버의 노트가 삭제되었습니다.");
    this.name = "NoteWriteConflictError";
    this.remote = remote;
  }
}

export type ConditionalDeleteDecision = "delete" | "already-deleted" | "conflict";

export function decideConditionalDelete(expectedUpdatedAt: number, remote: Note | null): ConditionalDeleteDecision {
  if (remote === null) return "already-deleted";
  return remote.updatedAt === expectedUpdatedAt ? "delete" : "conflict";
}

export function decideConditionalWrite(
  baseUpdatedAt: number | null,
  candidate: Note,
  remote: Note | null,
): ConditionalWriteDecision {
  if (baseUpdatedAt === null) {
    return remote === null ? { kind: "write" } : { kind: "conflict", remote };
  }
  if (remote === null) return { kind: "conflict", remote: null };
  if (remote.updatedAt === baseUpdatedAt) return { kind: "write" };
  if (sameContent(candidate, remote)) return { kind: "same-content", remote };
  return { kind: "conflict", remote };
}

export function requireConditionalWrite(
  baseUpdatedAt: number | null,
  candidate: Note,
  remote: Note | null,
): Exclude<ConditionalWriteDecision, { kind: "conflict" }> {
  const decision = decideConditionalWrite(baseUpdatedAt, candidate, remote);
  if (decision.kind === "conflict") throw new NoteWriteConflictError(decision.remote);
  return decision;
}

export const firebaseDebugCounter = {
  listenersStarted: 0,
  activeListeners: 0,
  maxActiveListeners: 0,
  snapshots: 0,
  writes: 0,
  deletes: 0,
  reset() {
    this.listenersStarted = 0;
    this.activeListeners = 0;
    this.maxActiveListeners = 0;
    this.snapshots = 0;
    this.writes = 0;
    this.deletes = 0;
  },
};

export class SingleListenerGuard {
  private activeKey = "";

  acquire(key: string): () => void {
    if (this.activeKey) throw new Error(`노트 리스너가 이미 활성화되어 있습니다: ${this.activeKey}`);
    this.activeKey = key;
    let released = false;
    return () => {
      if (released) return;
      released = true;
      if (this.activeKey === key) this.activeKey = "";
    };
  }
}

const listenerGuard = new SingleListenerGuard();

function notesCollection(db: Firestore, uid: string) {
  if (!uid) throw new Error("인증된 UID가 필요합니다.");
  return collection(db, "users", uid, "notes");
}

export function subscribeToNotes(
  db: Firestore,
  uid: string,
  next: (snapshot: NotesSnapshot) => void,
  failed: (error: unknown) => void,
): Unsubscribe {
  const releaseGuard = listenerGuard.acquire(uid);
  firebaseDebugCounter.listenersStarted += 1;
  firebaseDebugCounter.activeListeners += 1;
  firebaseDebugCounter.maxActiveListeners = Math.max(firebaseDebugCounter.maxActiveListeners, firebaseDebugCounter.activeListeners);
  let active = true;
  const unsubscribe = onSnapshot(notesCollection(db, uid), { includeMetadataChanges: true }, (snapshot) => {
    firebaseDebugCounter.snapshots += 1;
    const invalidDocuments: string[] = [];
    const notes: RemoteNote[] = [];
    snapshot.docs.forEach((document) => {
      const validation = validateNote(document.data(), document.id);
      if (!validation.valid || !validation.note) {
        invalidDocuments.push(document.id);
        return;
      }
      notes.push({ note: validation.note, hasPendingWrites: document.metadata.hasPendingWrites });
    });
    notes.sort((left, right) => right.note.updatedAt - left.note.updatedAt);
    next({
      notes,
      fromCache: snapshot.metadata.fromCache,
      hasPendingWrites: snapshot.metadata.hasPendingWrites,
      invalidDocuments,
    });
  }, failed);
  return () => {
    if (!active) return;
    active = false;
    firebaseDebugCounter.activeListeners -= 1;
    releaseGuard();
    unsubscribe();
  };
}

export function writeNote(db: Firestore, uid: string, note: Note, baseUpdatedAt: number | null): Promise<Note> {
  const serialized = serializeNote(note);
  const validation = validateNote(serialized, serialized.noteId);
  if (!validation.valid) throw new Error(validation.errors.join(" "));
  if (noteUtf8Size(serialized) >= NOTE_BLOCK_BYTES) throw new Error("노트가 Firestore 안전 크기를 초과했습니다.");
  firebaseDebugCounter.writes += 1;
  const reference = doc(notesCollection(db, uid), serialized.noteId);
  return runTransaction(db, async (transaction) => {
    const snapshot = await transaction.get(reference);
    let remote: Note | null = null;
    if (snapshot.exists()) {
      const remoteValidation = validateNote(snapshot.data(), snapshot.id);
      if (!remoteValidation.valid || !remoteValidation.note) {
        throw new Error(`서버 노트 스키마가 올바르지 않습니다: ${remoteValidation.errors.join(" ")}`);
      }
      remote = remoteValidation.note;
    }
    const decision = requireConditionalWrite(baseUpdatedAt, serialized, remote);
    if (decision.kind === "same-content") return decision.remote;
    transaction.set(reference, serialized);
    return serialized;
  });
}

export function removeNote(db: Firestore, uid: string, noteId: string, expectedUpdatedAt: number): Promise<void> {
  firebaseDebugCounter.deletes += 1;
  const reference = doc(notesCollection(db, uid), noteId);
  return runTransaction(db, async (transaction) => {
    const snapshot = await transaction.get(reference);
    if (!snapshot.exists()) return;
    const validation = validateNote(snapshot.data(), snapshot.id);
    if (!validation.valid || !validation.note) {
      throw new Error(`서버 노트 스키마가 올바르지 않습니다: ${validation.errors.join(" ")}`);
    }
    if (decideConditionalDelete(expectedUpdatedAt, validation.note) === "conflict") {
      throw new NoteWriteConflictError(validation.note);
    }
    transaction.delete(reference);
  });
}

export async function writeNotes(
  db: Firestore,
  uid: string,
  notes: readonly Note[],
  expectedVersions: ReadonlyMap<string, number> = new Map(),
): Promise<void> {
  for (const note of notes) {
    await writeNote(db, uid, note, expectedVersions.get(note.noteId) ?? null);
  }
}
