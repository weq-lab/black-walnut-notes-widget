import { afterEach, describe, expect, it, vi } from "vitest";
import { decideRemoteUpdate } from "../lib/conflict";
import { createDebouncedTask } from "../lib/debounce";
import { SingleListenerGuard } from "../lib/notesRepository";
import { createNote } from "../models/note";

afterEach(() => vi.useRealTimers());

describe("autosave and realtime safety", () => {
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
    expect(decideRemoteUpdate({ baseUpdatedAt: 100, localDirty: true, lastWrittenUpdatedAt: 200 }, remote, true)).toBe("ignore-own-write");
  });

  it("keeps a dirty draft when a newer remote note arrives", () => {
    const remote = { ...createNote(100, "n"), updatedAt: 300 };
    expect(decideRemoteUpdate({ baseUpdatedAt: 100, localDirty: true, lastWrittenUpdatedAt: 0 }, remote, false)).toBe("keep-local");
  });

  it("applies remote notes when the editor is clean", () => {
    expect(decideRemoteUpdate({ baseUpdatedAt: 100, localDirty: false, lastWrittenUpdatedAt: 0 }, createNote(200, "n"), false)).toBe("apply");
  });

  it("prevents duplicate active collection listeners", () => {
    const guard = new SingleListenerGuard();
    const release = guard.acquire("uid");
    expect(() => guard.acquire("uid")).toThrow(/이미 활성화/);
    release();
    expect(() => guard.acquire("uid")).not.toThrow();
  });
});
