export const NOTE_FIELDS = [
  "noteId",
  "title",
  "body",
  "checklist",
  "createdAt",
  "updatedAt",
  "colorPreset",
  "schemaVersion",
] as const;

export const DEFAULT_PRESET = "블랙 월넛";

export const COLOR_PRESETS = {
  "블랙 월넛": { background: "#000000", title: "#5A3021", body: "#3A2017", accent: "#D1AE6F" },
  "다크 브라운": { background: "#000000", title: "#6B351C", body: "#4A2414", accent: "#9A5835" },
  "앤티크 브론즈": { background: "#000000", title: "#8C5A32", body: "#5A351F", accent: "#B77A45" },
  "앤티크 골드": { background: "#000000", title: "#A8864B", body: "#6A532F", accent: "#D4B06A" },
  "딥 앰버": { background: "#000000", title: "#A85D1A", body: "#6F3B12", accent: "#E49A3A" },
  "번트 코퍼": { background: "#000000", title: "#9C4F2E", body: "#5E2E1C", accent: "#C76D3E" },
  "골드 가독성": { background: "#000000", title: "#D0A95B", body: "#9A7B3E", accent: "#F0C86E" },
} as const;

export type ColorPresetName = keyof typeof COLOR_PRESETS;
export const CUSTOM_PRESET = "HEX 직접 입력";
export const PRESET_NAMES = [CUSTOM_PRESET, ...Object.keys(COLOR_PRESETS)] as string[];

export interface ChecklistItem {
  text: string;
  checked: boolean;
  position: number;
}

export interface Note {
  noteId: string;
  title: string;
  body: string;
  checklist: ChecklistItem[];
  createdAt: number;
  updatedAt: number;
  colorPreset: string;
  schemaVersion: 1;
}

export interface ValidationResult {
  valid: boolean;
  note?: Note;
  errors: string[];
}

const isInteger = (value: unknown): value is number => Number.isInteger(value) && Number.isSafeInteger(value);

export function normalizeChecklist(items: readonly ChecklistItem[]): ChecklistItem[] {
  return items
    .filter((item) => typeof item.text === "string" && item.text.trim().length > 0)
    .map((item, position) => ({ text: item.text.trim(), checked: item.checked === true, position }));
}

export function safePreset(name: string): ColorPresetName {
  return Object.prototype.hasOwnProperty.call(COLOR_PRESETS, name) ? (name as ColorPresetName) : DEFAULT_PRESET;
}

export function createNote(now = Date.now(), noteId: string = crypto.randomUUID()): Note {
  return {
    noteId,
    title: "",
    body: "",
    checklist: [],
    createdAt: now,
    updatedAt: now,
    colorPreset: DEFAULT_PRESET,
    schemaVersion: 1,
  };
}

export function serializeNote(note: Note): Note {
  return {
    noteId: String(note.noteId),
    title: String(note.title),
    body: String(note.body),
    checklist: normalizeChecklist(note.checklist),
    createdAt: note.createdAt,
    updatedAt: note.updatedAt,
    colorPreset: note.colorPreset === CUSTOM_PRESET || Object.prototype.hasOwnProperty.call(COLOR_PRESETS, note.colorPreset)
      ? note.colorPreset
      : DEFAULT_PRESET,
    schemaVersion: 1,
  };
}

export function withUpdatedContent(note: Note, patch: Partial<Pick<Note, "title" | "body" | "checklist" | "colorPreset">>, now = Date.now()): Note {
  return serializeNote({
    ...note,
    ...patch,
    createdAt: note.createdAt,
    updatedAt: now,
    schemaVersion: 1,
  });
}

export function validateNote(value: unknown, expectedId?: string): ValidationResult {
  const errors: string[] = [];
  if (typeof value !== "object" || value === null || Array.isArray(value)) {
    return { valid: false, errors: ["노트가 객체가 아닙니다."] };
  }
  const raw = value as Record<string, unknown>;
  const extra = Object.keys(raw).filter((key) => !NOTE_FIELDS.includes(key as (typeof NOTE_FIELDS)[number]));
  if (extra.length > 0) errors.push(`허용되지 않은 필드: ${extra.join(", ")}`);
  if (typeof raw.noteId !== "string" || !raw.noteId) errors.push("noteId가 올바르지 않습니다.");
  if (expectedId && raw.noteId !== expectedId) errors.push("문서 ID와 noteId가 다릅니다.");
  if (typeof raw.title !== "string") errors.push("title이 문자열이 아닙니다.");
  if (typeof raw.body !== "string") errors.push("body가 문자열이 아닙니다.");
  if (!isInteger(raw.createdAt)) errors.push("createdAt이 정수 시각이 아닙니다.");
  if (!isInteger(raw.updatedAt)) errors.push("updatedAt이 정수 시각이 아닙니다.");
  if (typeof raw.colorPreset !== "string") errors.push("colorPreset이 문자열이 아닙니다.");
  if (raw.schemaVersion !== 1) errors.push("schemaVersion이 1이 아닙니다.");
  if (!Array.isArray(raw.checklist)) {
    errors.push("checklist가 배열이 아닙니다.");
  } else {
    raw.checklist.forEach((item, index) => {
      if (typeof item !== "object" || item === null || Array.isArray(item)) {
        errors.push(`체크리스트 ${index + 1}이 객체가 아닙니다.`);
        return;
      }
      const row = item as Record<string, unknown>;
      const keys = Object.keys(row);
      if (keys.some((key) => !["text", "checked", "position"].includes(key))) errors.push(`체크리스트 ${index + 1}에 허용되지 않은 필드가 있습니다.`);
      if (typeof row.text !== "string") errors.push(`체크리스트 ${index + 1} text가 문자열이 아닙니다.`);
      if (typeof row.checked !== "boolean") errors.push(`체크리스트 ${index + 1} checked가 불리언이 아닙니다.`);
      if (!isInteger(row.position) || row.position !== index) errors.push(`체크리스트 ${index + 1} position이 연속적이지 않습니다.`);
    });
  }

  if (errors.length > 0) return { valid: false, errors };
  const note = serializeNote(raw as unknown as Note);
  return { valid: true, note, errors: [] };
}

export function sameContent(left: Note, right: Note): boolean {
  const a = serializeNote(left);
  const b = serializeNote(right);
  return a.noteId === b.noteId
    && a.title === b.title
    && a.body === b.body
    && a.createdAt === b.createdAt
    && a.colorPreset === b.colorPreset
    && JSON.stringify(a.checklist) === JSON.stringify(b.checklist);
}

export function noteUtf8Size(note: Note): number {
  return new TextEncoder().encode(JSON.stringify(serializeNote(note))).byteLength;
}

export const NOTE_WARN_BYTES = 800 * 1024;
export const NOTE_BLOCK_BYTES = 950 * 1024;
