import React, { useEffect, useId, useRef, useState } from "react";
import { Smile } from "lucide-react";
import { CAT_EMOTES, CatEmote } from "../../emotes/catEmotes";

export const CatEmotePicker: React.FC<{ onSelect: (emote: CatEmote) => void; disabled?: boolean }> = ({ onSelect, disabled }) => {
  const id = useId();
  const root = useRef<HTMLDivElement>(null);
  const trigger = useRef<HTMLButtonElement>(null);
  const popup = useRef<HTMLDivElement>(null);
  const [position, setPosition] = useState<{ left: number; top: number; width: number; maxHeight: number } | null>(null);
  const open = !!position;

  const locate = () => {
    const rect = trigger.current!.getBoundingClientRect();
    const width = Math.min(280, window.innerWidth - 32);
    const height = 202;
    const above = rect.top >= height + 16;
    return { left: Math.max(16, Math.min(rect.left, window.innerWidth - width - 16)),
      top: above ? rect.top - height - 8 : rect.bottom + 8, width,
      maxHeight: above ? height : Math.max(80, window.innerHeight - rect.bottom - 24) };
  };
  const toggle = () => setPosition(open ? null : locate());

  useEffect(() => { if (disabled) setPosition(null); }, [disabled]);
  useEffect(() => {
    if (!open) return;
    const frame = requestAnimationFrame(() => popup.current?.querySelector<HTMLButtonElement>("button")?.focus({ preventScroll: true }));
    const outside = (event: PointerEvent) => { if (!root.current?.contains(event.target as Node)) setPosition(null); };
    const escape = (event: KeyboardEvent) => {
      if (event.key !== "Escape") return;
      event.preventDefault(); event.stopPropagation();
      setPosition(null); trigger.current?.focus({ preventScroll: true });
    };
    const move = () => {
      const rect = trigger.current?.getBoundingClientRect();
      if (!rect || rect.bottom <= 0 || rect.top >= window.innerHeight) setPosition(null);
      else setPosition(locate());
    };
    const scroll = (event: Event) => { if (!popup.current?.contains(event.target as Node)) move(); };
    document.addEventListener("pointerdown", outside);
    window.addEventListener("keydown", escape, true);
    window.addEventListener("resize", move);
    window.addEventListener("scroll", scroll, true);
    return () => {
      cancelAnimationFrame(frame);
      document.removeEventListener("pointerdown", outside);
      window.removeEventListener("keydown", escape, true);
      window.removeEventListener("resize", move);
      window.removeEventListener("scroll", scroll, true);
    };
  }, [open]);

  const navigateGrid = (event: React.KeyboardEvent) => {
    const buttons = Array.from(popup.current?.querySelectorAll<HTMLButtonElement>("button") || []);
    const current = buttons.indexOf(document.activeElement as HTMLButtonElement);
    const offset = { ArrowRight: 1, ArrowLeft: -1, ArrowDown: 3, ArrowUp: -3 }[event.key];
    if (offset === undefined) return;
    event.preventDefault(); buttons[(current + offset + buttons.length) % buttons.length]?.focus();
  };

  return (
    <div ref={root} className="inline-flex" onBlur={event => {
      if (event.relatedTarget && !event.currentTarget.contains(event.relatedTarget as Node)) setPosition(null);
    }}>
      <button ref={trigger} type="button" className="post-action min-h-10" disabled={disabled}
        aria-label="选择猫猫表情" aria-haspopup="dialog" aria-expanded={open} aria-controls={open ? id : undefined} onClick={toggle}>
        <Smile size={18} aria-hidden="true" />表情
      </button>
      {position && <div ref={popup} id={id} role="dialog" aria-label="猫猫表情" onKeyDown={navigateGrid}
        className="cat-emote-picker" style={position}>
        <p className="text-sm font-bold mb-2">猫猫表情</p>
        <div className="grid grid-cols-3 gap-1">
          {CAT_EMOTES.map(emote => <button key={emote.id} type="button" className="cat-emote-choice" aria-label={emote.label}
            onClick={() => { setPosition(null); onSelect(emote); }}>
            <img src={emote.src} alt="" width={40} height={40} draggable={false} />
            <span>{emote.label}</span>
          </button>)}
        </div>
      </div>}
    </div>
  );
};
