import { useMemo, useState } from "react";
import type { Firestore } from "firebase/firestore";
import { copyForRestore, createBackup, notesAsMarkdown, previewRestore, type RestorePreview } from "../lib/backup";
import { writeNotes } from "../lib/notesRepository";
import type { Note } from "../models/note";

type RestoreStrategy = "skip" | "newest" | "copy";

interface Props {
  db: Firestore;
  uid: string;
  notes: Note[];
  cacheMode: "persistent" | "memory";
  onClose(): void;
}

function download(name: string, type: string, content: string) {
  const url = URL.createObjectURL(new Blob([content], { type }));
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = name;
  anchor.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

export function BackupPanel({ db, uid, notes, cacheMode, onClose }: Props) {
  const [preview, setPreview] = useState<RestorePreview | null>(null);
  const [strategy, setStrategy] = useState<RestoreStrategy>("skip");
  const [message, setMessage] = useState("");
  const [busy, setBusy] = useState(false);

  const restoreNotes = useMemo(() => {
    if (!preview) return [];
    const current = new Map(notes.map((note) => [note.noteId, note]));
    if (strategy === "copy") return preview.valid.map((note) => copyForRestore(note));
    if (strategy === "newest") {
      return preview.valid.filter((note) => !current.has(note.noteId) || note.updatedAt > current.get(note.noteId)!.updatedAt);
    }
    return preview.valid.filter((note) => !current.has(note.noteId));
  }, [notes, preview, strategy]);

  const readBackup = async (file: File | undefined) => {
    if (!file) return;
    try {
      setPreview(previewRestore(JSON.parse(await file.text())));
      setMessage("");
    } catch {
      setPreview({ valid: [], invalid: [{ index: -1, errors: ["JSON 파일을 읽을 수 없습니다."] }] });
    }
  };

  const restore = async () => {
    if (!preview || restoreNotes.length === 0) return;
    if (!window.confirm(`${restoreNotes.length}개 문서를 Firestore에 기록합니다. 계속할까요?`)) return;
    setBusy(true);
    try {
      const expectedVersions = new Map(notes.map((note) => [note.noteId, note.updatedAt]));
      await writeNotes(db, uid, restoreNotes, expectedVersions);
      setMessage(`${restoreNotes.length}개 노트 복원을 요청했습니다.`);
      setPreview(null);
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "복원 중 오류가 발생했습니다.");
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="modal-backdrop" role="presentation">
      <section className="settings-panel" role="dialog" aria-modal="true" aria-labelledby="settings-title">
        <header className="panel-header">
          <div>
            <span className="eyebrow">개인 데이터 관리</span>
            <h2 id="settings-title">백업 및 설정</h2>
          </div>
          <button className="icon-button" onClick={onClose} aria-label="설정 닫기">×</button>
        </header>

        <div className="settings-section">
          <h3>오프라인 캐시</h3>
          <p>현재 모드: <strong>{cacheMode === "persistent" ? "개인 PC · 영구 캐시" : "공용 PC · 메모리 캐시"}</strong></p>
          <p className="muted">로그아웃해도 브라우저 IndexedDB 캐시는 자동 삭제되지 않습니다. 영구 캐시를 끄려면 사이트 데이터 삭제 후 다시 로그인하여 공용 PC 모드를 선택하세요.</p>
        </div>

        <div className="settings-section actions-grid">
          <button onClick={() => download("black-walnut-notes-backup.json", "application/json", JSON.stringify(createBackup(notes), null, 2))}>JSON 전체 백업</button>
          <button onClick={() => download("black-walnut-notes.md", "text/markdown;charset=utf-8", notesAsMarkdown(notes))}>Markdown 내보내기</button>
        </div>

        <div className="settings-section">
          <h3>JSON 복원</h3>
          <input type="file" accept="application/json,.json" onChange={(event) => void readBackup(event.target.files?.[0])} />
          {preview && (
            <div className="restore-preview">
              <p>유효한 노트 {preview.valid.length}개 · 유효하지 않은 노트 {preview.invalid.length}개</p>
              {preview.invalid.length > 0 && (
                <details>
                  <summary>검사 오류 보기</summary>
                  <ul>{preview.invalid.map((item) => <li key={item.index}>{item.index < 0 ? "파일" : `${item.index + 1}번`}: {item.errors.join(" ")}</li>)}</ul>
                </details>
              )}
              <label>
                동일 noteId 처리
                <select value={strategy} onChange={(event) => setStrategy(event.target.value as RestoreStrategy)}>
                  <option value="skip">기존 노트 건너뛰기</option>
                  <option value="newest">더 최신 updatedAt 사용</option>
                  <option value="copy">새 noteId의 복사본</option>
                </select>
              </label>
              <p>예상 Firestore 쓰기: <strong>{restoreNotes.length}회</strong></p>
              <button className="primary" disabled={busy || restoreNotes.length === 0} onClick={() => void restore()}>{busy ? "복원 중…" : "검사 결과로 복원"}</button>
            </div>
          )}
          {message && <p className="inline-message">{message}</p>}
        </div>
      </section>
    </div>
  );
}
