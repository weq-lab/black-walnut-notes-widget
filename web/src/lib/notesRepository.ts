import {
  collection,
  deleteDoc,
  doc,
  onSnapshot,
  setDoc,
  writeBatch,
  type Firestore,
  type Unsubscribe,
} from "firebase/firestore";
import { noteUtf8Size, NOTE_BLOCK_BYTES, serializeNote, validateNote, type Note } from "../models/note";

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

export function writeNote(db: Firestore, uid: string, note: Note): Promise<void> {
  const serialized = serializeNote(note);
  const validation = validateNote(serialized, serialized.noteId);
  if (!validation.valid) throw new Error(validation.errors.join(" "));
  if (noteUtf8Size(serialized) >= NOTE_BLOCK_BYTES) throw new Error("노트가 Firestore 안전 크기를 초과했습니다.");
  firebaseDebugCounter.writes += 1;
  return setDoc(doc(notesCollection(db, uid), serialized.noteId), serialized);
}

export function removeNote(db: Firestore, uid: string, noteId: string): Promise<void> {
  firebaseDebugCounter.deletes += 1;
  return deleteDoc(doc(notesCollection(db, uid), noteId));
}

export async function writeNotes(db: Firestore, uid: string, notes: readonly Note[]): Promise<void> {
  for (let start = 0; start < notes.length; start += 400) {
    const batch = writeBatch(db);
    const chunk = notes.slice(start, start + 400);
    chunk.forEach((note) => {
      const serialized = serializeNote(note);
      const validation = validateNote(serialized, serialized.noteId);
      if (!validation.valid) throw new Error(validation.errors.join(" "));
      if (noteUtf8Size(serialized) >= NOTE_BLOCK_BYTES) throw new Error(`${serialized.noteId}: 노트가 너무 큽니다.`);
      batch.set(doc(notesCollection(db, uid), serialized.noteId), serialized);
    });
    firebaseDebugCounter.writes += chunk.length;
    await batch.commit();
  }
}
