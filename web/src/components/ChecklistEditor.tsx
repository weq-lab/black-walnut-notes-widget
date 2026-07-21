import type { ChecklistItem } from "../models/note";

interface Props {
  items: ChecklistItem[];
  onChange(items: ChecklistItem[]): void;
}

const reindex = (items: ChecklistItem[]) => items.map((item, position) => ({ ...item, position }));

export function ChecklistEditor({ items, onChange }: Props) {
  const update = (index: number, patch: Partial<ChecklistItem>) => onChange(reindex(items.map((item, position) => position === index ? { ...item, ...patch } : item)));
  const remove = (index: number) => onChange(reindex(items.filter((_, position) => position !== index)));
  const move = (index: number, direction: -1 | 1) => {
    const target = index + direction;
    if (target < 0 || target >= items.length) return;
    const next = [...items];
    [next[index], next[target]] = [next[target], next[index]];
    onChange(reindex(next));
  };

  return (
    <section className="checklist-editor" aria-labelledby="checklist-heading">
      <div className="section-heading">
        <h3 id="checklist-heading">체크리스트</h3>
        <button className="text-button" onClick={() => onChange([...items, { text: "", checked: false, position: items.length }])}>＋ 항목</button>
      </div>
      <div className="checklist-items">
        {items.map((item, index) => (
          <div className="check-row" key={`${index}-${item.position}`}>
            <input type="checkbox" checked={item.checked} onChange={(event) => update(index, { checked: event.target.checked })} aria-label={`${index + 1}번 항목 체크`} />
            <input
              className={item.checked ? "checked-text" : ""}
              value={item.text}
              placeholder="체크리스트 항목"
              onChange={(event) => update(index, { text: event.target.value })}
              onKeyDown={(event) => {
                if (event.altKey && event.key === "ArrowUp") { event.preventDefault(); move(index, -1); }
                if (event.altKey && event.key === "ArrowDown") { event.preventDefault(); move(index, 1); }
              }}
            />
            <div className="row-actions">
              <button onClick={() => move(index, -1)} disabled={index === 0} aria-label="항목 위로 이동">↑</button>
              <button onClick={() => move(index, 1)} disabled={index === items.length - 1} aria-label="항목 아래로 이동">↓</button>
              <button onClick={() => remove(index)} aria-label="항목 삭제">×</button>
            </div>
          </div>
        ))}
      </div>
    </section>
  );
}
