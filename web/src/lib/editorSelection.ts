export function initialNoteSelection(rememberedNoteId: string | null, availableNoteIds: readonly string[]): string {
  // Existing notes must only open after an explicit list click. Keeping the inputs
  // makes this policy directly testable against remembered and first-note IDs.
  void rememberedNoteId;
  void availableNoteIds;
  return "";
}
