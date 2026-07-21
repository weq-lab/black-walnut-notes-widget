import { getApp, getApps, initializeApp, type FirebaseApp, type FirebaseOptions } from "firebase/app";
import {
  browserLocalPersistence,
  getAuth,
  getRedirectResult,
  GoogleAuthProvider,
  onAuthStateChanged,
  setPersistence,
  signInWithPopup,
  signInWithRedirect,
  signOut,
  type Auth,
  type User,
} from "firebase/auth";
import {
  initializeFirestore,
  memoryLocalCache,
  persistentLocalCache,
  persistentMultipleTabManager,
  type Firestore,
} from "firebase/firestore";

export type CacheMode = "persistent" | "memory";

const configEntries = {
  apiKey: import.meta.env.VITE_FIREBASE_API_KEY,
  authDomain: import.meta.env.VITE_FIREBASE_AUTH_DOMAIN,
  projectId: import.meta.env.VITE_FIREBASE_PROJECT_ID,
  storageBucket: import.meta.env.VITE_FIREBASE_STORAGE_BUCKET,
  messagingSenderId: import.meta.env.VITE_FIREBASE_MESSAGING_SENDER_ID,
  appId: import.meta.env.VITE_FIREBASE_APP_ID,
} as const;

const envNames: Record<keyof typeof configEntries, string> = {
  apiKey: "VITE_FIREBASE_API_KEY",
  authDomain: "VITE_FIREBASE_AUTH_DOMAIN",
  projectId: "VITE_FIREBASE_PROJECT_ID",
  storageBucket: "VITE_FIREBASE_STORAGE_BUCKET",
  messagingSenderId: "VITE_FIREBASE_MESSAGING_SENDER_ID",
  appId: "VITE_FIREBASE_APP_ID",
};

let firestore: Firestore | null = null;
let firestoreMode: CacheMode | null = null;

export function missingFirebaseConfig(): string[] {
  return (Object.keys(configEntries) as (keyof typeof configEntries)[])
    .filter((key) => !configEntries[key]?.trim())
    .map((key) => envNames[key]);
}

export function firebaseConfigured(): boolean {
  return missingFirebaseConfig().length === 0;
}

export function firebaseApp(): FirebaseApp {
  if (!firebaseConfigured()) throw new Error("Firebase 웹 설정이 완성되지 않았습니다.");
  if (getApps().length > 0) return getApp();
  return initializeApp(configEntries as FirebaseOptions);
}

export function firebaseAuth(): Auth {
  return getAuth(firebaseApp());
}

export async function prepareAuth(): Promise<Auth> {
  const auth = firebaseAuth();
  await setPersistence(auth, browserLocalPersistence);
  await getRedirectResult(auth);
  return auth;
}

export function observeAuth(auth: Auth, callback: (user: User | null) => void): () => void {
  return onAuthStateChanged(auth, callback);
}

export async function signInWithGoogle(auth: Auth): Promise<void> {
  const provider = new GoogleAuthProvider();
  provider.setCustomParameters({ prompt: "select_account" });
  try {
    await signInWithPopup(auth, provider);
  } catch (error) {
    const code = (error as { code?: string }).code;
    if (code === "auth/popup-blocked" || code === "auth/operation-not-supported-in-this-environment") {
      await signInWithRedirect(auth, provider);
      return;
    }
    throw error;
  }
}

export async function signOutGoogle(auth: Auth): Promise<void> {
  await signOut(auth);
}

export function getNotesFirestore(mode: CacheMode): Firestore {
  if (firestore) {
    if (firestoreMode !== mode) throw new Error("캐시 모드를 바꾸려면 앱을 다시 시작하세요.");
    return firestore;
  }
  firestoreMode = mode;
  firestore = initializeFirestore(firebaseApp(), {
    localCache: mode === "persistent"
      ? persistentLocalCache({ tabManager: persistentMultipleTabManager() })
      : memoryLocalCache(),
  });
  return firestore;
}

export function firebaseErrorMessage(error: unknown): string {
  const code = (error as { code?: string }).code;
  if (code === "auth/popup-closed-by-user") return "Google 로그인 창이 닫혔습니다.";
  if (code === "auth/unauthorized-domain") return "현재 도메인이 Firebase Authentication 승인 도메인에 없습니다.";
  if (code === "permission-denied") return "Firestore 규칙이 이 계정의 접근을 허용하지 않습니다.";
  if (code === "unavailable") return "네트워크에 연결할 수 없습니다. 오프라인 변경은 이 기기에 보관됩니다.";
  return error instanceof Error ? error.message : "알 수 없는 Firebase 오류가 발생했습니다.";
}
