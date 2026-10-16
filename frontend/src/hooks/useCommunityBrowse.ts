import { useEffect, useRef, useState } from "react";
import { communityApi } from "../api/client";
import { Community } from "../types";

/** 搜索防抖与代次隔离共用；旧请求不能覆盖新筛选，续页失败保留已读内容。 */
export function useCommunityBrowse(search: string, scope = "all", category = "", enabled = true, revision: string | number = "") {
  const [items, setItems] = useState<Community[]>([]);
  const [cursor, setCursor] = useState<string | null>(null);
  const [hasNext, setHasNext] = useState(false);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState("");
  const [moreError, setMoreError] = useState("");
  const [refreshVersion, setRefreshVersion] = useState(0);
  const generation = useRef(0);
  const pending = useRef(false);
  useEffect(() => {
    const version = ++generation.current;
    setItems([]); setCursor(null); setHasNext(false); setError(""); setMoreError(""); setLoadingMore(false); pending.current = false;
    setLoading(enabled);
    if (!enabled) return;
    const timer = window.setTimeout(() => {
      communityApi.browse({ search: search.trim() || undefined, scope, category: category || undefined, size: 12 })
        .then(result => { if (version === generation.current) { setItems(result.items); setCursor(result.nextCursor); setHasNext(result.hasNext); } })
        .catch(err => { if (version === generation.current) setError(err instanceof Error ? err.message : "社区加载失败"); })
        .finally(() => { if (version === generation.current) setLoading(false); });
    }, search ? 250 : 0);
    return () => { window.clearTimeout(timer); ++generation.current; };
  }, [search, scope, category, enabled, revision, refreshVersion]);
  const loadMore = async () => {
    if (pending.current || !cursor || !hasNext) return;
    const version = generation.current;
    pending.current = true; setLoadingMore(true); setMoreError("");
    try {
      const result = await communityApi.browse({ search: search.trim() || undefined, scope, category: category || undefined, size: 12, cursor });
      if (version !== generation.current) return;
      setItems(previous => { const seen = new Set(previous.map(item => item.id)); return [...previous, ...result.items.filter(item => !seen.has(item.id))]; });
      setCursor(result.nextCursor); setHasNext(result.hasNext);
    } catch (err) { if (version === generation.current) setMoreError(err instanceof Error ? err.message : "加载失败，请重试"); }
    finally { if (version === generation.current) { pending.current = false; setLoadingMore(false); } }
  };
  return { items, setItems, hasNext, loading, loadingMore, error, moreError, loadMore, refresh: () => setRefreshVersion(value => value + 1) };
}
