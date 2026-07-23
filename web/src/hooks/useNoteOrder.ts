import {
  collection,
  deleteDoc,
  doc,
  onSnapshot,
  setDoc,
  writeBatch,
  type Firestore,
} from "firebase/firestore";
import { useCallback, useEffect, useMemo, useState } from "react";
import type { Note } from "../models/note";

export interface NoteOrderMeta {
  noteId: string;
  pinned: boolean;
  order: number;
  updatedAt: number;
}

interface OrderableEntry {
  note: Note;
}

function noteMetaCollection(db: Firestore, uid: string) {
  if (!uid) throw new Error("인증된 UID가 필요합니다.");
  return collection(db, "users", uid, "noteMeta");
}

function parseMeta(value: unknown, expectedId: string): NoteOrderMeta | null {
  if (typeof value !== "object" || value === null || Array.isArray(value)) return null;
  const raw = value as Record<string, unknown>;
  if (raw.noteId !== expectedId) return null;
  if (typeof raw.pinned !== "boolean") return null;
  if (typeof raw.order !== "number" || !Number.isSafeInteger(raw.order)) return null;
  if (typeof raw.updatedAt !== "number" || !Number.isSafeInteger(raw.updatedAt)) return null;
  return {
    noteId: expectedId,
    pinned: raw.pinned,
    order: raw.order,
    updatedAt: raw.updatedAt,
  };
}

function errorMessage(error: unknown): string {
  if (error instanceof Error && error.message) return error.message;
  return "노트 순서를 저장하지 못했습니다.";
}

function fallbackOrder(entry: OrderableEntry): number {
  return -entry.note.updatedAt;
}

export function removeNoteOrderMeta(db: Firestore, uid: string, noteId: string): Promise<void> {
  return deleteDoc(doc(noteMetaCollection(db, uid), noteId));
}

export function useNoteOrder<T extends OrderableEntry>(
  db: Firestore,
  uid: string,
  entries: readonly T[],
) {
  const [metaById, setMetaById] = useState<Record<string, NoteOrderMeta>>({});
  const [error, setError] = useState("");

  useEffect(() => {
    setError("");
    return onSnapshot(noteMetaCollection(db, uid), { includeMetadataChanges: true }, (snapshot) => {
      const next: Record<string, NoteOrderMeta> = {};
      snapshot.docs.forEach((document) => {
        const parsed = parseMeta(document.data(), document.id);
        if (parsed) next[document.id] = parsed;
      });
      setMetaById(next);
    }, (cause) => {
      setError(errorMessage(cause));
    });
  }, [db, uid]);

  const isPinned = useCallback(
    (noteId: string) => metaById[noteId]?.pinned === true,
    [metaById],
  );

  const orderedEntries = useMemo<T[]>(() => {
    return [...entries].sort((left, right) => {
      const leftMeta = metaById[left.note.noteId];
      const rightMeta = metaById[right.note.noteId];
      const leftPinned = leftMeta?.pinned === true;
      const rightPinned = rightMeta?.pinned === true;

      if (leftPinned !== rightPinned) return leftPinned ? -1 : 1;

      const leftOrder = leftMeta?.order ?? fallbackOrder(left);
      const rightOrder = rightMeta?.order ?? fallbackOrder(right);
      if (leftOrder !== rightOrder) return leftOrder - rightOrder;

      return right.note.updatedAt - left.note.updatedAt;
    });
  }, [entries, metaById]);

  const togglePinned = useCallback((noteId: string) => {
    const current = metaById[noteId];
    const nextPinned = current?.pinned !== true;
    const targetOrders = orderedEntries
      .filter((entry) => isPinned(entry.note.noteId) === nextPinned)
      .map((entry) => metaById[entry.note.noteId]?.order ?? fallbackOrder(entry));

    const nextMeta: NoteOrderMeta = {
      noteId,
      pinned: nextPinned,
      order: targetOrders.length > 0 ? Math.min(...targetOrders) - 1 : 0,
      updatedAt: Date.now(),
    };

    setError("");
    setMetaById((currentMap) => ({ ...currentMap, [noteId]: nextMeta }));
    void setDoc(doc(noteMetaCollection(db, uid), noteId), nextMeta).catch((cause) => {
      setError(errorMessage(cause));
    });
  }, [db, isPinned, metaById, orderedEntries, uid]);

  const moveNote = useCallback((sourceId: string, targetId: string) => {
    if (!sourceId || !targetId || sourceId === targetId) return;

    const pinned = isPinned(sourceId);
    if (isPinned(targetId) !== pinned) return;

    const group = orderedEntries.filter((entry) => isPinned(entry.note.noteId) === pinned);
    const sourceIndex = group.findIndex((entry) => entry.note.noteId === sourceId);
    const targetIndex = group.findIndex((entry) => entry.note.noteId === targetId);
    if (sourceIndex < 0 || targetIndex < 0) return;

    const reordered = [...group];
    const [moved] = reordered.splice(sourceIndex, 1);
    reordered.splice(targetIndex, 0, moved);

    const updatedAt = Date.now();
    const optimistic = { ...metaById };
    reordered.forEach((entry, order) => {
      const noteId = entry.note.noteId;
      optimistic[noteId] = { noteId, pinned, order, updatedAt };
    });

    setError("");
    setMetaById(optimistic);

    const persist = async () => {
      for (let start = 0; start < reordered.length; start += 450) {
        const batch = writeBatch(db);
        reordered.slice(start, start + 450).forEach((entry, offset) => {
          const order = start + offset;
          const noteId = entry.note.noteId;
          batch.set(doc(noteMetaCollection(db, uid), noteId), {
            noteId,
            pinned,
            order,
            updatedAt,
          } satisfies NoteOrderMeta);
        });
        await batch.commit();
      }
    };

    void persist().catch((cause) => {
      setError(errorMessage(cause));
    });
  }, [db, isPinned, metaById, orderedEntries, uid]);

  return {
    orderedEntries,
    isPinned,
    togglePinned,
    moveNote,
    error,
  };
}