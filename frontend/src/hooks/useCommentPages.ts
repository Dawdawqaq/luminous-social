import { useCallback, useEffect, useRef, useState } from "react";
import { CommentSlice } from "../types";

/** 按游标追加并去重，隔离切换帖子、用户或刷新后的迟到响应。 */
export function useCommentPages<T extends { id: string }>(
  key: string, loader: (cursor?: string | null) => Promise<CommentSlice<T>>, enabled: boolean,
) {
  const [items, setItems] = useState<T[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(enabled);
  const [error, setError] = useState("");
  const [hasNext, setHasNext] = useState(false);
  const [revision, setRevision] = useState(0);
  const generation = useRef(0);
  const busy = useRef(false);
  const cursor = useRef<string | null>(null);
  const loaderRef = useRef(loader); loaderRef.current = loader;
  const load = useCallback(async (initial = false) => {
    if (busy.current) return;
    busy.current = true;
    const run = generation.current;
    setLoading(true); setError("");
    try {
      const result = await loaderRef.current(initial ? null : cursor.current);
      if (run !== generation.current) return;
      setItems(previous => {
        const map = new Map((initial ? [] : previous).map(item => [item.id, item]));
        result.items.forEach(item => { if (!map.has(item.id)) map.set(item.id, item); });
        return [...map.values()];
      });
      cursor.current = result.nextCursor; setHasNext(result.hasNext); setTotal(result.total);
    } catch (exception) {
      if (run === generation.current) setError(exception instanceof Error ? exception.message : "加载失败，请重试");
    } finally {
      if (run === generation.current) { busy.current = false; setLoading(false); }
    }
  }, []);
  useEffect(() => {
    generation.current++; busy.current = false; cursor.current = null;
    setItems([]); setHasNext(false); setError(""); setTotal(0); setLoading(enabled);
    if (enabled) void load(true);
    return () => { generation.current++; };
  }, [key, enabled, revision, load]);
  return { items, setItems, total, setTotal, loading, error, hasNext,
    loadMore: () => load(!cursor.current), reload: () => setRevision(value => value + 1) };
}
