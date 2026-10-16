import { useEffect, useRef, useState } from "react";
import { ApiError, communityApi, postApi } from "../api/client";
import { Post } from "../types";

export function useCommunityPosts(slug: string, order: string, enabled: boolean, viewer?: string) {
  const [data, setData] = useState<Post[]>([]);
  const [cursor, setCursor] = useState<string | null>(null);
  const [hasMore, setHasMore] = useState(false);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState("");
  const [moreError, setMoreError] = useState("");
  const [expired, setExpired] = useState(false);
  const [version, setVersion] = useState(0);
  const generation = useRef(0), pending = useRef(false);
  const read = (next?: string) => order === "hot" ? postApi.ranking({ community: slug, type: "hot", period: "week", size: 10, cursor: next }) : communityApi.posts(slug, next);
  useEffect(() => {
    const current = ++generation.current;
    setData([]); setCursor(null); setHasMore(false); setError(""); setMoreError(""); setExpired(false); setLoading(enabled); setLoadingMore(false); pending.current = false;
    if (!enabled) return;
    read().then(result => { if (current === generation.current) { setData(result.items); setCursor(result.nextCursor); setHasMore(result.hasMore); } })
      .catch(err => { if (current === generation.current) setError(err instanceof Error ? err.message : "帖子加载失败"); })
      .finally(() => { if (current === generation.current) setLoading(false); });
    return () => { ++generation.current; };
  }, [slug, order, enabled, viewer, version]);
  const loadMore = async () => {
    if (!cursor || !hasMore || pending.current || expired) return;
    const current = generation.current; pending.current = true; setLoadingMore(true); setMoreError("");
    try {
      const result = await read(cursor);
      if (current !== generation.current) return;
      setData(previous => { const seen = new Set(previous.map(post => post.id)); return [...previous, ...result.items.filter(post => !seen.has(post.id))]; });
      setCursor(result.nextCursor); setHasMore(result.hasMore);
    } catch (err) {
      if (current !== generation.current) return;
      if (err instanceof ApiError && err.status === 410) setExpired(true);
      setMoreError(err instanceof Error ? err.message : "加载失败，请重试");
    } finally { if (current === generation.current) { pending.current = false; setLoadingMore(false); } }
  };
  return { data, setData, loading, loadingMore, hasMore, error, moreError, expired, loadMore, reload: () => setVersion(value => value + 1) };
}
