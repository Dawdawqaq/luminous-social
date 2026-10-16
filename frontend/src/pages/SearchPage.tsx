import React, { useEffect, useRef, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { Search } from "lucide-react";
import { ApiError, searchApi } from "../api/client";
import { SearchHit, SearchKind } from "../types";
import { SearchHighlight } from "../components/search/SearchHighlight";
import { PageBackButton } from "../components/common/PageBackButton";
import { ContentState } from "../components/common/ContentState";
import { Button } from "../components/ui/Button";

const names: Record<SearchKind, string> = { posts: "帖子", communities: "社区", users: "用户" };
export const SearchPage: React.FC = () => {
  const [params, setParams] = useSearchParams();
  const query = params.get("q") || "";
  const type: SearchKind = query.startsWith("@") || query.startsWith("id:") ? "users" : query.startsWith("c/") ? "communities" : params.get("type") === "users" ? "users" : params.get("type") === "communities" ? "communities" : "posts";
  const [input, setInput] = useState(query);
  const [items, setItems] = useState<SearchHit[]>([]);
  const [cursor, setCursor] = useState<string | null>(null);
  const [hasMore, setHasMore] = useState(false);
  const [limited, setLimited] = useState(false);
  const [hint, setHint] = useState("");
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState("");
  const [moreError, setMoreError] = useState("");
  const [expired, setExpired] = useState(false);
  const [revision, setRevision] = useState(0);
  const composing = useRef(false);
  const generation = useRef(0), moreAbort = useRef<AbortController | null>(null), pending = useRef(false);
  useEffect(() => { setInput(query); }, [query]);
  useEffect(() => {
    const current = ++generation.current, controller = new AbortController();
    moreAbort.current?.abort(); pending.current = false;
    setItems([]); setCursor(null); setHasMore(false); setLimited(false); setHint(""); setError(""); setMoreError(""); setExpired(false); setLoadingMore(false);
    if (!query.trim()) { setLoading(false); return; }
    setLoading(true);
    searchApi.list(query, type, undefined, controller.signal).then(result => {
      if (current === generation.current) { setItems(result.items); setCursor(result.nextCursor); setHasMore(result.hasMore); setLimited(result.windowLimited); setHint(result.hint); }
    }).catch(err => { if (!controller.signal.aborted) setError(err instanceof Error ? err.message : "搜索失败，请重试"); })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => { ++generation.current; controller.abort(); moreAbort.current?.abort(); };
  }, [query, type, revision]);
  const loadMore = async () => {
    if (pending.current || !cursor || !hasMore || expired) return;
    const current = generation.current, controller = new AbortController(); moreAbort.current = controller;
    pending.current = true; setLoadingMore(true); setMoreError("");
    try {
      const result = await searchApi.list(query, type, cursor, controller.signal);
      if (current !== generation.current) return;
      setItems(previous => { const seen = new Set(previous.map(hit => hit.id)); return [...previous, ...result.items.filter(hit => !seen.has(hit.id))]; });
      setCursor(result.nextCursor); setHasMore(result.hasMore);
    } catch (err) {
      if (controller.signal.aborted) return;
      setExpired(err instanceof ApiError && err.status === 410); setMoreError(err instanceof Error ? err.message : "加载失败，请重试");
    } finally { if (current === generation.current) { pending.current = false; setLoadingMore(false); } }
  };
  return <div className="max-w-5xl mx-auto space-y-5">
    <PageBackButton />
    <header className="panel p-5 sm:p-7 space-y-4"><h1 className="text-2xl font-extrabold">搜索</h1>
      <form className="flex items-center gap-3" onSubmit={event => { event.preventDefault(); if (composing.current) return; setParams({ q: input.trim(), type }); if (input.trim() === query) setRevision(value => value + 1); }}>
        <div className="search-field min-w-0 flex-1"><Search size={20} /><input className="search-input" type="search" maxLength={50} aria-label="搜索关键词" placeholder="输入关键词，或使用 @用户名、c/社区短名" value={input} onChange={event => setInput(event.target.value)} onCompositionStart={() => { composing.current = true; }} onCompositionEnd={() => { composing.current = false; }} /></div><Button type="submit" size="sm" disabled={!input.trim()}>搜索</Button>
      </form>
      <div className="flex gap-2" aria-label="搜索分类">{(Object.keys(names) as SearchKind[]).map(kind => <Button key={kind} size="sm" variant={kind === type ? "primary" : "ghost"} aria-pressed={kind === type} onClick={() => setParams({ q: query.replace(/^(@|c\/|id:)/, ""), type: kind })}>{names[kind]}</Button>)}</div>
    </header>
    <section className="panel overflow-hidden" aria-label={`${names[type]}搜索结果`}>
      {loading ? <ContentState title="正在搜索" loading /> : error ? <ContentState title="搜索暂时无法完成" description={error} onRetry={() => setRevision(value => value + 1)} /> : !query.trim() ? <ContentState title="想找些什么？" description="输入帖子关键词、社区名称或用户名。" /> : <>
        <div className="px-5 py-3 hairline-b text-sm text-ink-muted dark:text-night-muted break-words">“{query}”的{names[type]}结果，按匹配程度排序。{hint && <p className="mt-2">{hint}</p>}</div>
        {items.length ? <div className="px-5">{items.map(hit => <Link key={hit.id} to={hit.url} className="block py-5 hairline-b last:border-0 hover:text-terracotta dark:hover:text-terracotta-dark"><p className="text-xs text-ink-muted dark:text-night-muted break-words">{hit.subtitle}</p><h2 className="mt-1 font-bold text-lg break-words"><SearchHighlight text={hit.title} query={query} /></h2>{hit.snippet && <p className="mt-2 text-sm leading-relaxed text-ink-muted dark:text-night-muted line-clamp-3 break-words"><SearchHighlight text={hit.snippet} query={query} /></p>}</Link>)}</div> : <ContentState title="没有找到匹配内容" description="尝试更具体的名称，或者切换搜索分类。" />}
        {moreError && <p role="alert" className="px-5 pt-3 text-sm">{moreError}</p>}
        {expired ? <div className="p-5 text-center"><Button size="sm" onClick={() => setRevision(value => value + 1)}>刷新结果</Button></div> : hasMore && <div className="p-5 text-center"><Button size="sm" variant="outline" onClick={loadMore} isLoading={loadingMore}>{moreError ? "重试加载" : "加载更多结果"}</Button></div>}
        {limited && !hasMore && <p className="px-5 pb-5 text-sm text-ink-muted dark:text-night-muted">本次最多展示200条结果，可补充关键词缩小范围。</p>}
      </>}
    </section>
  </div>;
};
