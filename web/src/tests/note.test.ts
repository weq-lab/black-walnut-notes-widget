import { describe, expect, it } from "vitest";
import {
  DEFAULT_PRESET,
  NOTE_FIELDS,
  createNote,
  normalizeChecklist,
  noteUtf8Size,
  sameContent,
  serializeNote,
  withUpdatedContent,
} from "../models/note";

describe("note schema", () => {
  it("serializes exactly the allowed eight fields", () => {
    const source = { ...createNote(100, "n1"), extra: "remove" };
    expect(Object.keys(serializeNote(source)).sort()).toEqual([...NOTE_FIELDS].sort());
  });

  it("creates Android-compatible defaults", () => {
    expect(createNote(123, "id-1")).toEqual({
      noteId: "id-1", title: "", body: "", checklist: [], createdAt: 123,
      updatedAt: 123, colorPreset: DEFAULT_PRESET, schemaVersion: 1,
    });
  });

  it("preserves createdAt and updates only updatedAt", () => {
    const note = createNote(100, "n1");
    expect(withUpdatedContent(note, { title: "변경" }, 250)).toMatchObject({ createdAt: 100, updatedAt: 250, title: "변경" });
  });

  it("removes empty checklist rows and recalculates positions", () => {
    expect(normalizeChecklist([
      { text: " first ", checked: false, position: 8 },
      { text: " ", checked: true, position: 9 },
      { text: "third", checked: true, position: 11 },
    ])).toEqual([
      { text: "first", checked: false, position: 0 },
      { text: "third", checked: true, position: 1 },
    ]);
  });

  it("preserves the custom marker but falls back unknown presets safely", () => {
    expect(serializeNote({ ...createNote(1, "n"), colorPreset: "HEX 직접 입력" }).colorPreset).toBe("HEX 직접 입력");
    expect(serializeNote({ ...createNote(1, "n"), colorPreset: "임의" }).colorPreset).toBe(DEFAULT_PRESET);
  });

  it("does not consider updatedAt-only changes new content", () => {
    const note = createNote(1, "n");
    expect(sameContent(note, { ...note, updatedAt: 99 })).toBe(true);
    expect(sameContent(note, { ...note, body: "new" })).toBe(false);
  });

  it("measures UTF-8 bytes rather than JavaScript character count", () => {
    const ascii = { ...createNote(1, "a"), body: "a".repeat(100) };
    const korean = { ...createNote(1, "b"), body: "한".repeat(100) };
    expect(noteUtf8Size(korean) - noteUtf8Size(ascii)).toBeGreaterThan(150);
  });
});
