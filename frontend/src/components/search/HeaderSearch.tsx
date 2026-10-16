import React, { useEffect, useId, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { Search, X, FileText, Compass, UserRound, Loader2 } from "lucide-react";
import { searchApi } from "../../api/client";
import { SearchGroup, SearchHit } from "../../types";
import { SearchHighlight } from "./SearchHighlight";

const names = { posts: "帖子", communities: "社区", users: "用户" };
const icons = { posts: FileText, communities: Compass, users: UserRound };
export const HeaderSearch: React.FC = () => {
  const [query, setQuery] = useState("");
  const [open, setOpen] = useState(false);
  const [loading, setLoading] = useState(false);
  const [composing, setComposing] = useState(false);
  const [groups, setGroups] = useState<SearchGroup[]>([]);
  const [hint, setHint] = useState("");
  const [error, setError] = useState("");
  const [selected, setSelected] = useState(-1);
  const [revision, setRevision] = useState(0);
  const root = useRef<HTMLDivElement>(null), input = useRef<HTMLInputElement>(null);
  const navigate = useNavigate(), listId = useId();
  const results = groups.flatMap(group => group.items);
  useEffect(() => {
    if (selected >= 0) document.getElementById(`${listId}-${selected}`)?.scrollIntoView({ block: "nearest" });
  }, [selected, listId]);
  useEffect(() => {
    const shortcut = (event: KeyboardEvent) => {
      if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === "k") { event.preventDefault(); input.current?.focus(); setOpen(true); }
      if (event.key === "Escape" && !event.isComposing) setOpen(false);
    };
    const outside = (event: PointerEvent) => { if (!root.current?.contains(event.target as Node)) setOpen(false); };
    window.addEventListener("keydown", shortcut); document.addEventListener("pointerdown", outside);
    return () => { window.removeEventListener("keydown", shortcut); document.removeEventListener("pointerdown", outside); };
  }, []);
  useEffect(() => {
    const controller = new AbortController();
    setSelected(-1); setGroups([]); setError(""); setHint("");
    const term = query.trim();
    if (!term || !open || composing) { setLoading(false); return; }
    setLoading(true);
    const timer = window.setTimeout(() => {
      searchApi.suggest(term, controller.signal).then(result => {
        if (!controller.signal.aborted) { setGroups(result.groups); setHint(result.hint); }
      }).catch(err => { if (!controller.signal.aborted) setError(err instanceof Error ? err.message : "搜索暂不可用，请稍后重试"); })
        .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    }, 240);
    return () => { window.clearTimeout(timer); controller.abort(); };
  }, [query, open, composing, revision]);
  const select = (hit: SearchHit) => { setOpen(false); setQuery(""); navigate(hit.url); };
  const more = (kind?: string) => { const term = query.trim(); if (!term) return; setOpen(false); navigate(`/search?q=${encodeURIComponent(term)}${kind ? `&type=${kind}` : ""}`); };
  return <div ref={root} className="relative">
    <div className="search-field"><Search size={20} aria-hidden="true" />
      <input ref={input} type="search" className="search-input header-search-input" maxLength={50} placeholder="搜索帖子、社区、用户" aria-label="搜索帖子、社区、用户" role="combobox" aria-autocomplete="list" aria-expanded={open && !!query.trim()} aria-controls={open && !!query.trim() ? listId : undefined} aria-activedescendant={selected >= 0 ? `${listId}-${selected}` : undefined} value={query}
        onChange={event => { setQuery(event.target.value); setOpen(true); }} onFocus={() => setOpen(true)}
        onCompositionStart={() => setComposing(true)} onCompositionEnd={event => { setComposing(false); setQuery(event.currentTarget.value); }}
        onKeyDown={event => {
          if (event.nativeEvent.isComposing || event.nativeEvent.keyCode === 229) return;
          if (event.key === "ArrowDown" && results.length) { event.preventDefault(); setOpen(true); setSelected(value => (value + 1) % results.length); }
          if (event.key === "ArrowUp" && results.length) { event.preventDefault(); setSelected(value => value < 0 ? results.length - 1 : (value - 1 + results.length) % results.length); }
          if (event.key === "Enter") { event.preventDefault(); if (selected >= 0 && results[selected]) select(results[selected]); else more(); }
          if (event.key === "Escape") setOpen(false);
        }} />
      {query && <button type="button" className="icon-button" aria-label="清空搜索" onClick={() => { setQuery(""); setOpen(false); input.current?.focus(); }}><X size={18} /></button>}
    </div>
    {open && query.trim() && <div className="panel search-dropdown flex flex-col">
      <div className="min-h-0 overflow-y-auto" role="listbox" id={listId} aria-label="搜索结果">
        {loading ? <p className="search-message flex gap-2 items-center" role="status"><Loader2 size={17} className="animate-spin" />正在搜索…</p> : composing ? <p className="search-message">输入完成后开始搜索。</p> : groups.map(group => {
          const Icon = icons[group.kind];
          return <div className="hairline-b last:border-0" key={group.kind} role="group" aria-label={names[group.kind]}>
            <p className="px-4 pt-3 pb-1 text-xs font-bold text-ink-muted dark:text-night-muted">{names[group.kind]}</p>
            {group.unavailable ? <p className="search-message text-sm">这类结果暂时无法加载。</p> : group.items.length ? group.items.map(hit => {
              const index = results.findIndex(result => result.kind === hit.kind && result.id === hit.id);
              return <button type="button" key={hit.id} id={`${listId}-${index}`} role="option" aria-selected={index === selected} className="search-result" onClick={() => select(hit)}><Icon size={20} className="text-terracotta shrink-0" /><div className="min-w-0"><strong><SearchHighlight text={hit.title} query={query} /></strong><span>{hit.subtitle}</span></div></button>;
            }) : <p className="px-4 pb-2 text-xs text-ink-muted dark:text-night-muted">暂无匹配结果</p>}
          </div>;
        })}
      </div>
      {hint && <p className="search-message text-xs">{hint}</p>}
      {error && <div className="search-message" role="status">{error}<button type="button" className="ml-3 underline text-terracotta dark:text-terracotta-dark" onClick={() => setRevision(value => value + 1)}>重试搜索</button></div>}
      {!loading && !composing && !error && <div className="p-3 hairline-t flex shrink-0 flex-wrap gap-2">{groups.filter(group => group.hasMore).map(group => <button key={group.kind} type="button" className="text-sm text-terracotta dark:text-terracotta-dark px-2 py-1" onClick={() => more(group.kind)}>更多{names[group.kind]}</button>)}<button type="button" className="text-sm font-semibold text-terracotta dark:text-terracotta-dark px-2 py-1" onClick={() => more()}>查看全部结果</button></div>}
    </div>}
  </div>;
};
