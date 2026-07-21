import { describe, expect, it } from "vitest";
import { createBackup, copyForRestore, previewRestore } from "../lib/backup";
import { createNote } from "../models/note";

describe("backup and restore", () => {
  it("creates a versioned local backup", () => {
    const note = { ...createNote(1, "n"), title: "백업" };
    expect(createBackup([note], 99)).toEqual({ format: "black-walnut-notes", version: 1, createdAt: 99, notes: [note] });
  });

  it("strips unknown fields and reports invalid data", () => {
    const valid = { ...createNote(1, "n"), ownerUid: "must-remove" };
    const preview = previewRestore({ format: "black-walnut-notes", version: 1, createdAt: 1, notes: [valid, { noteId: 3 }] });
    expect(preview.valid).toHaveLength(1);
    expect(Object.keys(preview.valid[0])).not.toContain("ownerUid");
    expect(preview.invalid).toHaveLength(1);
  });

  it("copies a note with a fresh id while keeping content", () => {
    const source = { ...createNote(1, "old"), title: "복사", body: "본문" };
    expect(copyForRestore(source, 20, "new")).toMatchObject({ noteId: "new", title: "복사", body: "본문", createdAt: 20, updatedAt: 20 });
  });
});
