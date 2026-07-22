import { sameContent, validateNote, type Note } from "../models/note";

export interface StoredDraft {
  note: Note;
  baseUpdatedAt: number | null;
  savedAt: number;
  hasBase: boolean;
}

export type DraftRestoreDecision =
  | { kind: "none" }
  | { kind: "restore"; draft: StoredDraft; dirty: boolean }
  | { kind: "conflict"; draft: StoredDraft };

export function createStoredDraft(note: Note, baseUpdatedAt: number | null, savedAt = Date.now()): StoredDraft {
  return { note, baseUpdatedAt, savedAt, hasBase: true };
}

export function parseStoredDraft(raw: string | null, expectedNoteId: string): StoredDraft | null {
  if (!raw) return null;
  try {
    const parsed = JSON.parse(raw) as unknown;
    if (typeof parsed === "object" && parsed !== null && "note" in parsed) {
      const value = parsed as Record<string, unknown>;
      const validation = validateNote(value.note, expectedNoteId);
      if (!validation.valid || !validation.note) return null;
      const hasBase = Object.prototype.hasOwnProperty.call(value, "baseUpdatedAt")
        && (value.baseUpdatedAt === null || Number.isSafeInteger(value.baseUpdatedAt));
      return {
        note: validation.note,
        baseUpdatedAt: hasBase ? value.baseUpdatedAt as number | null : null,
        savedAt: Number.isSafeInteger(value.savedAt) ? value.savedAt as number : 0,
        hasBase,
      };
    }
    const legacy = validateNote(parsed, expectedNoteId);
    if (!legacy.valid || !legacy.note) return null;
    return { note: legacy.note, baseUpdatedAt: null, savedAt: 0, hasBase: false };
  } catch {
    return null;
  }
}

export function decideDraftRestore(remote: Note | null, draft: StoredDraft | null): DraftRestoreDecision {
  if (!draft) return { kind: "none" };
  if (remote === null) return { kind: "restore", draft, dirty: true };
  if (!draft.hasBase || draft.baseUpdatedAt === null || draft.baseUpdatedAt !== remote.updatedAt) {
    return { kind: "conflict", draft };
  }
  return { kind: "restore", draft, dirty: !sameContent(draft.note, remote) };
}
