import { afterEach, describe, expect, it, vi } from "vitest";
import { decideRemoteUpdate } from "../lib/conflict";
import { createDebouncedTask } from "../lib/debounce";
import { initialNoteSelection } from "../lib/editorSelection";
import { decideConditionalDelete, decideConditionalWrite, NoteWriteConflictError, requireConditionalWrite, SingleListenerGuard } from "../lib/notesRepository";
import { createStoredDraft, decideDraftRestore, parseStoredDraft } from "../lib/storedDraft";
import { createNote } from "../models/note";

afterEach(() => vi.useRealTimers());

describe("autosave and realtime safety", () => {
  it("does not auto-select a remembered note or notes[0] on initial load", () => {
    expect(initialNoteSelection("remembered", ["first", "remembered"])).toBe("");
    expect(initialNoteSelection(null, ["first"])).toBe("");
  });

  it("debounces repeated input to one 500ms save", async () => {
    vi.useFakeTimers();
    const save = vi.fn();
    const task = createDebouncedTask(save, 500);
    task.schedule(); task.schedule(); task.schedule();
    await vi.advanceTimersByTimeAsync(499);
    expect(save).not.toHaveBeenCalled();
    await vi.advanceTimersByTimeAsync(1);
    expect(save).toHaveBeenCalledTimes(1);
  });

  it("flushes a pending save immediately", async () => {
    vi.useFakeTimers();
    const save = vi.fn();
    const task = createDebouncedTask(save, 500);
    task.schedule();
    await task.flush();
    expect(save).toHaveBeenCalledTimes(1);
    expect(task.pending()).toBe(false);
  });

  it("does not feed an own pending snapshot back into the editor", () => {
    const remote = { ...createNote(100, "n"), updatedAt: 200 };
    expect(decideRemoteUpdate({ baseUpdatedAt: 100, localDirty: true }, remote, true)).toBe("ignore-own-write");
  });

  it("keeps a dirty draft when a newer remote note arrives", () => {
    const remote = { ...createNote(100, "n"), updatedAt: 300 };
    expect(decideRemoteUpdate({ baseUpdatedAt: 100, localDirty: true }, remote, false)).toBe("keep-local");
  });

  it("keeps a dirty draft on any base version mismatch, not clock ordering", () => {
    const remote = { ...createNote(100, "n"), updatedAt: 90 };
    expect(decideRemoteUpdate({ baseUpdatedAt: 100, localDirty: true }, remote, false)).toBe("keep-local");
    expect(decideRemoteUpdate({ baseUpdatedAt: null, localDirty: true }, remote, false)).toBe("keep-local");
  });

  it("applies remote notes when the editor is clean", () => {
    expect(decideRemoteUpdate({ baseUpdatedAt: 100, localDirty: false }, createNote(200, "n"), false)).toBe("apply");
  });

  it("rejects a save when the server updatedAt differs from the edit base", () => {
    const candidate = { ...createNote(100, "n"), title: "local", updatedAt: 300 };
    const remote = { ...createNote(100, "n"), title: "remote", updatedAt: 200 };
    expect(decideConditionalWrite(100, candidate, remote)).toEqual({ kind: "conflict", remote });
    expect(() => requireConditionalWrite(100, candidate, remote)).toThrow(NoteWriteConflictError);
  });

  it("rejects creating a new note when its document ID already exists", () => {
    const candidate = createNote(200, "duplicate");
    const remote = createNote(100, "duplicate");
    expect(decideConditionalWrite(null, candidate, remote)).toEqual({ kind: "conflict", remote });
    expect(() => requireConditionalWrite(null, candidate, remote)).toThrow(NoteWriteConflictError);
  });

  it("accepts an own same-content response without reporting a conflict", () => {
    const candidate = { ...createNote(100, "n"), body: "same", updatedAt: 300 };
    const remote = { ...candidate, updatedAt: 250 };
    expect(decideConditionalWrite(100, candidate, remote)).toEqual({ kind: "same-content", remote });
  });

  it("allows an explicit edit when the server still matches its base version", () => {
    const remote = createNote(100, "n");
    const candidate = { ...remote, title: "explicit edit", updatedAt: 200 };
    expect(requireConditionalWrite(100, candidate, remote)).toEqual({ kind: "write" });
  });

  it("restores a persisted draft only against the exact server base", () => {
    const remote = createNote(100, "n");
    const draftNote = { ...remote, title: "local draft" };
    expect(decideDraftRestore(remote, createStoredDraft(draftNote, 100, 110))).toMatchObject({ kind: "restore", dirty: true });
    expect(decideDraftRestore({ ...remote, updatedAt: 200 }, createStoredDraft(draftNote, 100, 110))).toMatchObject({ kind: "conflict" });
  });

  it("treats a legacy raw draft as unsafe when its remote document exists", () => {
    const remote = createNote(100, "legacy");
    const parsed = parseStoredDraft(JSON.stringify({ ...remote, title: "legacy draft" }), remote.noteId);
    expect(parsed?.hasBase).toBe(false);
    expect(decideDraftRestore(remote, parsed)).toMatchObject({ kind: "conflict" });
  });

  it("restores a new local draft when no remote document exists", () => {
    const local = createStoredDraft(createNote(100, "local"), null, 110);
    expect(decideDraftRestore(null, local)).toMatchObject({ kind: "restore", dirty: true });
  });

  it("round-trips the stored base version across browser restart and reselection", () => {
    const note = createNote(100, "restart");
    const parsed = parseStoredDraft(JSON.stringify(createStoredDraft({ ...note, body: "draft" }, 100, 150)), note.noteId);
    expect(parsed?.baseUpdatedAt).toBe(100);
    expect(decideDraftRestore(note, parsed)).toMatchObject({ kind: "restore", dirty: true });
  });

  it("conditionally deletes only the exact server version", () => {
    const remote = createNote(100, "delete");
    expect(decideConditionalDelete(100, remote)).toBe("delete");
    expect(decideConditionalDelete(99, remote)).toBe("conflict");
    expect(decideConditionalDelete(100, null)).toBe("already-deleted");
  });

  it("prevents duplicate active collection listeners", () => {
    const guard = new SingleListenerGuard();
    const release = guard.acquire("uid");
    expect(() => guard.acquire("uid")).toThrow(/이미 활성화/);
    release();
    expect(() => guard.acquire("uid")).not.toThrow();
  });
});
