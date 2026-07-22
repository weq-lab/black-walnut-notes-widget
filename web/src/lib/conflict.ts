import type { Note } from "../models/note";

export interface EditorSyncState {
  baseUpdatedAt: number | null;
  localDirty: boolean;
}

export type RemoteDecision = "apply" | "keep-local" | "ignore-own-write";

export function decideRemoteUpdate(state: EditorSyncState, remote: Note, hasPendingWrites: boolean): RemoteDecision {
  if (hasPendingWrites) return "ignore-own-write";
  if (!state.localDirty) return "apply";
  if (state.baseUpdatedAt === null || remote.updatedAt !== state.baseUpdatedAt) return "keep-local";
  return "ignore-own-write";
}
