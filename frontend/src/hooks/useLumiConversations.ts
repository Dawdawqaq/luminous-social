import { useCallback, useEffect, useRef, useState } from "react";
import { lumiChatApi, LumiConversations } from "../api/client";

/** 列表独立分页，状态刷新保留已续载记录；账号切换丢弃全部旧请求。 */
export function useLumiConversations(user?: string) {
  const [value, setValue] = useState<LumiConversations>();
  const [owner, setOwner] = useState<string>();
  const [error, setError] = useState(""); const [loading, setLoading] = useState(false);
  const epoch = useRef(0), busy = useRef(false); const current = useRef(value); current.current = value;
  const queued = useRef(false), removed = useRef(new Set<string>());
  const refresh = useCallback(async (older = false) => {
    if (!user) return; if (busy.current) { if (!older) queued.current = true; return; } const cursor = older ? current.current?.nextCursor : undefined; if (older && !cursor) return;
    const version = epoch.current; busy.current = true; setLoading(true);
    try {
      const next = await lumiChatApi.conversations(cursor || undefined);
      if (version !== epoch.current) return;
      next.items = next.items.filter(row => !removed.current.has(row.id));
      setOwner(user); setError("");
      setValue(previous => {
        if (!previous || !older && previous.items.length <= 20) return next;
        const items = Array.from(new Map([...(previous?.items || []), ...next.items].map(row => [row.id, row])).values()).sort((a,b) => Date.parse(b.activityAt) - Date.parse(a.activityAt) || (BigInt(a.id) > BigInt(b.id) ? -1 : 1));
        return { ...next, items, hasMore: older ? next.hasMore : previous.hasMore, nextCursor: older ? next.nextCursor : previous.nextCursor };
      });
    } catch (err) { if (version === epoch.current) setError(err instanceof Error ? err.message : "会话列表加载失败"); }
    finally { if (version === epoch.current) { busy.current = false; setLoading(false); if (queued.current) { queued.current = false; queueMicrotask(() => void refresh()); } } }
  }, [user]);
  useEffect(() => { epoch.current++; busy.current = false; queued.current = false; removed.current.clear(); setValue(undefined); setOwner(undefined); setError(""); if (user) void refresh(); return () => { epoch.current++; }; }, [user, refresh]);
  useEffect(() => {
    if (!user) return; const visible = () => { if (document.visibilityState === "visible") void refresh(); };
    const timer = value?.busy ? window.setInterval(visible, 3000) : undefined;
    document.addEventListener("visibilitychange", visible); return () => { clearInterval(timer); document.removeEventListener("visibilitychange", visible); };
  }, [value?.busy, user, refresh]);
  return { value: owner === user ? value : undefined, loading, error, refresh,
    remove: (id: string) => { removed.current.add(id); setValue(previous => previous ? { ...previous, items: previous.items.filter(row => row.id !== id) } : previous); },
    guard: () => epoch.current, isCurrent: (version: number) => epoch.current === version };
}
