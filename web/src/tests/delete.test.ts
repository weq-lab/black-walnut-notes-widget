import { afterEach, describe, expect, it, vi } from "vitest";
import { deferDelete } from "../lib/deferredDelete";

afterEach(() => vi.useRealTimers());

describe("deferred delete", () => {
  it("cancels deletion during the undo window", async () => {
    vi.useFakeTimers();
    const commit = vi.fn();
    const pending = deferDelete(commit, 10_000);
    pending.cancel();
    await vi.advanceTimersByTimeAsync(10_000);
    expect(commit).not.toHaveBeenCalled();
  });

  it("commits once after ten seconds", async () => {
    vi.useFakeTimers();
    const commit = vi.fn();
    deferDelete(commit, 10_000);
    await vi.advanceTimersByTimeAsync(10_000);
    expect(commit).toHaveBeenCalledTimes(1);
  });
});
