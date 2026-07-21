import type { Note } from "../models/note";

export interface EditorSyncState {
  baseUpdatedAt: number;
  localDirty: boolean;
  lastWrittenUpdatedAt: number;
}

export type RemoteDecision = "apply" | "keep-local" | "ignore-own-write";

export function decideRemoteUpdate(state: EditorSyncState, remote: Note, hasPendingWrites: boolean): RemoteDecision {
  if (hasPendingWrites || remote.updatedAt === state.lastWrittenUpdatedAt) return "ignore-own-write";
  if (!state.localDirty) return "apply";
  if (remote.updatedAt > state.baseUpdatedAt) return "keep-local";
  return "ignore-own-write";
}
