import { useState } from "react";
import type { StoredDraft } from "../lib/storedDraft";
import { bodyFontClass, titleFontClass } from "../lib/typography";
import type { Note } from "../models/note";

export interface NoteListEntry {
  note: Note;
  hasPendingWrites: boolean;
  localOnlyDraft: StoredDraft | null;
}

interface NoteListProps {
  loaded: boolean;
  search: string;
  entries: readonly NoteListEntry[];
  selectedId: string;
  isPinned(noteId: string): boolean;
  onSelect(note: Note, localOnlyDraft: StoredDraft | null): void;
  onPin(noteId: string): void;
  onMove(sourceId: string, targetId: string): void;
}

export function NoteList({
  loaded,
  search,
  entries,
  selectedId,
  isPinned,
  onSelect,
  onPin,
  onMove,
}: NoteListProps) {
  const [draggingId, setDraggingId] = useState("");
  const searchActive = search.trim().length > 0;

  return (
    <div className="notes-list" role="listbox" aria-label="노트 목록">
      {!loaded && <p className="list-message">노트를 불러오는 중…</p>}
      {loaded && entries.length === 0 && (
        <p className="list-message">{searchActive ? "검색 결과가 없습니다." : "새 노트를 만들어 시작하세요."}</p>
      )}
      {entries.map(({ note, localOnlyDraft }) => {
        const pinned = isPinned(note.noteId);
        const selected = selectedId === note.noteId;
        return (
          <div
            key={note.noteId}
            className={`note-list-row ${selected ? "selected" : ""} ${draggingId === note.noteId ? "dragging" : ""}`}
            onDragOver={(event) => {
              if (searchActive) return;
              event.preventDefault();
              event.dataTransfer.dropEffect = "move";
            }}
            onDrop={(event) => {
              if (searchActive) return;
              event.preventDefault();
              const sourceId = event.dataTransfer.getData("text/plain") || draggingId;
              setDraggingId("");
              onMove(sourceId, note.noteId);
            }}
          >
            <button
              className="note-list-item"
              onClick={() => onSelect(note, localOnlyDraft)}
              role="option"
              aria-selected={selected}
            >
              <strong className={titleFontClass(note.title.trim() || "제목 없음")}>
                {note.title.trim() || "제목 없음"}
              </strong>
              <span className={bodyFontClass(note.body.trim() || note.checklist[0]?.text || "내용 없음")}>
                {note.body.trim() || note.checklist[0]?.text || "내용 없음"}
              </span>
              <time>
                {new Intl.DateTimeFormat("ko-KR", {
                  month: "short",
                  day: "numeric",
                  hour: "2-digit",
                  minute: "2-digit",
                }).format(note.updatedAt)}
              </time>
            </button>
            <div className="note-list-controls">
              <button
                className={`pin-button ${pinned ? "active" : ""}`}
                onClick={() => onPin(note.noteId)}
                aria-label={pinned ? "노트 고정 해제" : "노트 고정"}
                aria-pressed={pinned}
                title={pinned ? "고정 해제" : "위에 고정"}
              >
                {pinned ? "◆" : "◇"}
              </button>
              <span
                className={`drag-handle ${searchActive ? "disabled" : ""}`}
                draggable={!searchActive}
                title={searchActive ? "검색 중에는 순서를 바꿀 수 없습니다" : "드래그하여 순서 변경"}
                aria-label="드래그하여 노트 순서 변경"
                onDragStart={(event) => {
                  setDraggingId(note.noteId);
                  event.dataTransfer.effectAllowed = "move";
                  event.dataTransfer.setData("text/plain", note.noteId);
                }}
                onDragEnd={() => setDraggingId("")}
              >
                ⋮⋮
              </span>
            </div>
          </div>
        );
      })}
    </div>
  );
}