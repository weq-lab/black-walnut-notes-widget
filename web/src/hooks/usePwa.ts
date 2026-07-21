import { useCallback, useEffect, useState } from "react";

interface BeforeInstallPromptEvent extends Event {
  prompt(): Promise<void>;
  userChoice: Promise<{ outcome: "accepted" | "dismissed" }>;
}

export function usePwa() {
  const [installPrompt, setInstallPrompt] = useState<BeforeInstallPromptEvent | null>(null);
  const [waitingWorker, setWaitingWorker] = useState<ServiceWorker | null>(null);

  useEffect(() => {
    const captureInstall = (event: Event) => {
      event.preventDefault();
      setInstallPrompt(event as BeforeInstallPromptEvent);
    };
    window.addEventListener("beforeinstallprompt", captureInstall);
    if (import.meta.env.DEV || !("serviceWorker" in navigator)) return () => window.removeEventListener("beforeinstallprompt", captureInstall);

    let refreshing = false;
    navigator.serviceWorker.register("/sw.js").then((registration) => {
      if (registration.waiting) setWaitingWorker(registration.waiting);
      registration.addEventListener("updatefound", () => {
        const worker = registration.installing;
        worker?.addEventListener("statechange", () => {
          if (worker.state === "installed" && navigator.serviceWorker.controller) setWaitingWorker(worker);
        });
      });
    }).catch(() => undefined);
    const reload = () => {
      if (refreshing) return;
      refreshing = true;
      window.location.reload();
    };
    navigator.serviceWorker.addEventListener("controllerchange", reload);
    return () => {
      window.removeEventListener("beforeinstallprompt", captureInstall);
      navigator.serviceWorker.removeEventListener("controllerchange", reload);
    };
  }, []);

  const install = useCallback(async () => {
    if (!installPrompt) return;
    await installPrompt.prompt();
    await installPrompt.userChoice;
    setInstallPrompt(null);
  }, [installPrompt]);

  const applyUpdate = useCallback(() => {
    waitingWorker?.postMessage({ type: "SKIP_WAITING" });
  }, [waitingWorker]);

  return { canInstall: installPrompt !== null, install, updateAvailable: waitingWorker !== null, applyUpdate };
}
