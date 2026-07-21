export interface DebouncedTask {
  schedule(): void;
  flush(): Promise<void>;
  cancel(): void;
  pending(): boolean;
}

export function createDebouncedTask(callback: () => void | Promise<void>, delayMs = 500): DebouncedTask {
  let timer: ReturnType<typeof setTimeout> | undefined;

  const run = async () => {
    if (timer !== undefined) clearTimeout(timer);
    timer = undefined;
    await callback();
  };

  return {
    schedule() {
      if (timer !== undefined) clearTimeout(timer);
      timer = setTimeout(() => void run(), delayMs);
    },
    flush: run,
    cancel() {
      if (timer !== undefined) clearTimeout(timer);
      timer = undefined;
    },
    pending: () => timer !== undefined,
  };
}
