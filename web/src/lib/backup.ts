import { createNote, serializeNote, validateNote, type Note } from "../models/note";

export const BACKUP_FORMAT = "black-walnut-notes";
export const BACKUP_VERSION = 1;

export interface NotesBackup {
  format: typeof BACKUP_FORMAT;
  version: typeof BACKUP_VERSION;
  createdAt: number;
  notes: Note[];
}

export interface RestorePreview {
  valid: Note[];
  invalid: { index: number; errors: string[] }[];
}

export function createBackup(notes: readonly Note[], now = Date.now()): NotesBackup {
  return {
    format: BACKUP_FORMAT,
    version: BACKUP_VERSION,
    createdAt: now,
    notes: notes.map(serializeNote),
  };
}

export function previewRestore(value: unknown): RestorePreview {
  const result: RestorePreview = { valid: [], invalid: [] };
  if (typeof value !== "object" || value === null) {
    return { valid: [], invalid: [{ index: -1, errors: ["지원하는 백업 파일이 아닙니다."] }] };
  }
  const backup = value as Record<string, unknown>;
  if (backup.format !== BACKUP_FORMAT || backup.version !== BACKUP_VERSION || !Array.isArray(backup.notes)) {
    return { valid: [], invalid: [{ index: -1, errors: ["백업 포맷 또는 버전이 올바르지 않습니다."] }] };
  }
  const notes = backup.notes;
  notes.forEach((raw, index) => {
    if (typeof raw !== "object" || raw === null) {
      result.invalid.push({ index, errors: ["노트가 객체가 아닙니다."] });
      return;
    }
    const source = raw as Record<string, unknown>;
    const cleaned = Object.fromEntries([
      "noteId", "title", "body", "checklist", "createdAt", "updatedAt", "colorPreset", "schemaVersion",
    ].filter((key) => key in source).map((key) => [key, source[key]]));
    const validation = validateNote(cleaned);
    if (validation.valid && validation.note) result.valid.push(validation.note);
    else result.invalid.push({ index, errors: validation.errors });
  });
  return result;
}

export function copyForRestore(note: Note, now = Date.now(), id: string = crypto.randomUUID()): Note {
  const copy = createNote(now, id);
  return serializeNote({
    ...copy,
    title: note.title,
    body: note.body,
    checklist: note.checklist,
    colorPreset: note.colorPreset,
  });
}

export function notesAsMarkdown(notes: readonly Note[]): string {
  return notes.map((note) => {
    const title = note.title.trim() || "제목 없음";
    const checklist = note.checklist.map((item) => `- [${item.checked ? "x" : " "}] ${item.text}`).join("\n");
    return [`# ${title}`, "", note.body, checklist ? `\n${checklist}` : ""].join("\n");
  }).join("\n\n---\n\n");
}
