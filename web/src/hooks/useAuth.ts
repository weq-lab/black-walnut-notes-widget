import { useEffect, useState } from "react";
import type { Auth, User } from "firebase/auth";
import { firebaseErrorMessage, observeAuth, prepareAuth } from "../lib/firebase";

export function useAuth() {
  const [auth, setAuth] = useState<Auth | null>(null);
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    let unsubscribe: (() => void) | undefined;
    let mounted = true;
    void prepareAuth().then((nextAuth) => {
      if (!mounted) return;
      setAuth(nextAuth);
      unsubscribe = observeAuth(nextAuth, (nextUser) => {
        setUser(nextUser);
        setLoading(false);
      });
    }).catch((cause) => {
      if (!mounted) return;
      setError(firebaseErrorMessage(cause));
      setLoading(false);
    });
    return () => {
      mounted = false;
      unsubscribe?.();
    };
  }, []);

  return { auth, user, loading, error, setError };
}
