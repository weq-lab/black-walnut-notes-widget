export interface DeferredDelete {
  cancel(): void;
  pending(): boolean;
}

export function deferDelete(commit: () => void | Promise<void>, delayMs = 10_000): DeferredDelete {
  let timer: ReturnType<typeof setTimeout> | undefined = setTimeout(() => {
    timer = undefined;
    void commit();
  }, delayMs);
  return {
    cancel() {
      if (timer !== undefined) clearTimeout(timer);
      timer = undefined;
    },
    pending: () => timer !== undefined,
  };
}
