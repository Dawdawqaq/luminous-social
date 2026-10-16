import { useCallback, useEffect, useRef, useState } from "react";
import { lumiChatApi, LumiChatPage, LumiChatTurn } from "../api/client";

const running = (state: string) => ["PENDING", "RUNNING", "STOPPING"].includes(state);
function merge(old: LumiChatTurn[], next: LumiChatTurn[]) {
  const rows = new Map(old.map(turn => [turn.id, turn]));
  next.forEach(turn => { const previous = rows.get(turn.id); if (!previous || BigInt(turn.revision) >= BigInt(previous.revision)) rows.set(turn.id, turn); });
  return Array.from(rows.values()).sort((a, b) => BigInt(a.id) < BigInt(b.id) ? -1 : 1);
}

/** 页面选择与后台任务独立；旧会话回调不能写入新会话，缓存只保留本人最近十段已加载记录。 */
export function useLumiChat(userId?: string, sessionId?: string) {
  const [page, setPage] = useState<LumiChatPage | null>(null);
  const [owner, setOwner] = useState<string>();
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);
  const [olderLoading, setOlderLoading] = useState(false);
  const [remaining, setRemaining] = useState(0);
  const [live, setLive] = useState(false);
  const [visible, setVisible] = useState(document.visibilityState === "visible");
  const epoch = useRef(0), loadEpoch = useRef(0), busy = useRef(false);
  const metadataEpoch = useRef(0);
  const cache = useRef(new Map<string, LumiChatPage>());
  const cacheOwner = useRef(userId);
  const current = useRef(page); current.current = page;
  useEffect(() => { if (owner === userId && page) { cache.current.set(page.sessionId, page); while (cache.current.size > 10) cache.current.delete(cache.current.keys().next().value!); } }, [page, owner, userId]);
  const refresh = useCallback(async (before?: string, signal?: AbortSignal) => {
    if (!userId || busy.current) return;
    const version = epoch.current, readVersion = loadEpoch.current, metadataVersion = metadataEpoch.current; busy.current = true;
    if (before) setOlderLoading(true);
    try {
      const next = await lumiChatApi.read(before, signal, sessionId);
      if (version !== epoch.current || readVersion !== loadEpoch.current || signal?.aborted) return;
      setOwner(userId); setError("");
      setPage(previous => {
        if (!previous || previous.sessionId !== next.sessionId) return next;
        const preserveCursor = !before && previous.turns.length > 20;
        return { ...next, ...(metadataVersion !== metadataEpoch.current ? { busy: previous.busy, activeSessionId: previous.activeSessionId, activeTurnId: previous.activeTurnId } : {}), turns: merge(previous.turns, next.turns), hasMore: preserveCursor ? previous.hasMore : next.hasMore, nextCursor: preserveCursor ? previous.nextCursor : next.nextCursor };
      });
      setRemaining(next.cooldownRemaining);
    } catch (err) {
      if (version === epoch.current && readVersion === loadEpoch.current && !signal?.aborted) {
        setError(err instanceof Error ? err.message : "对话加载失败，请重试");
        if ((err as { status?: number }).status === 404) { if (sessionId) cache.current.delete(sessionId); setPage(null); }
      }
    } finally { if (version === epoch.current && readVersion === loadEpoch.current) { busy.current = false; setLoading(false); setOlderLoading(false); } }
  }, [userId, sessionId]);
  useEffect(() => {
    epoch.current++; loadEpoch.current++; busy.current = false; setError(""); setLive(false); setOlderLoading(false);
    if (cacheOwner.current !== userId) { cache.current.clear(); cacheOwner.current = userId; }
    const cached = sessionId ? cache.current.get(sessionId) : undefined;
    const same = owner === userId && (!sessionId || current.current?.sessionId === sessionId) ? current.current : null;
    const base = cached || same || null, shared = owner === userId ? current.current : null;
    setPage(base && shared ? { ...base, busy: shared.busy, activeSessionId: shared.activeSessionId, activeTurnId: shared.activeTurnId } : base);
    setOwner(userId); setLoading(!!userId && !cached && !same); setRemaining(value => owner === userId ? value : 0);
    const controller = new AbortController(); if (userId) void refresh(undefined, controller.signal);
    return () => { epoch.current++; controller.abort(); };
  }, [userId, sessionId, refresh]);
  const update = useCallback((turn: LumiChatTurn) => {
    metadataEpoch.current++;
    setPage(previous => {
      if (!previous) return previous;
      const active = running(turn.status), old = previous.turns.find(row => row.id === turn.id);
      const stale = old && BigInt(old.revision) > BigInt(turn.revision) || previous.activeTurnId && BigInt(previous.activeTurnId) > BigInt(turn.id);
      return { ...previous, turns: previous.sessionId === turn.sessionId ? merge(previous.turns, [turn]) : previous.turns,
        ...(stale ? {} : { busy: active, activeSessionId: active ? turn.sessionId : null, activeTurnId: active ? turn.id : null }) };
    });
  }, []);
  const shown = owner === userId && (!sessionId || page?.sessionId === sessionId) ? page : null;
  const activeId = shown?.activeTurnId;
  const activeTurn = shown?.turns.find(turn => turn.id === activeId && running(turn.status));
  useEffect(() => {
    if (!activeId || !userId || !visible) { setLive(false); return; }
    const version = epoch.current, controller = new AbortController(); let timer: number | undefined;
    const connect = async () => {
      try {
        await lumiChatApi.watch(activeId, turn => {
          if (version !== epoch.current || controller.signal.aborted) return;
          setLive(true); setError(""); update(turn);
          if (!running(turn.status)) void refresh();
        }, controller.signal);
      } catch { /* 订阅失败只降级读取，不重发问题或调用模型。 */ }
      if (!controller.signal.aborted && version === epoch.current) { setLive(false); void refresh(); timer = window.setTimeout(() => void connect(), 3000); }
    };
    void connect(); return () => { controller.abort(); clearTimeout(timer); };
  }, [activeId, userId, visible, refresh, update]);
  useEffect(() => {
    if (!userId) return;
    const change = () => { const shown = document.visibilityState === "visible"; setVisible(shown); if (shown) void refresh(); };
    const timer = shown?.busy && !live ? window.setInterval(() => { if (document.visibilityState === "visible") void refresh(); }, 3000) : undefined;
    document.addEventListener("visibilitychange", change);
    return () => { clearInterval(timer); document.removeEventListener("visibilitychange", change); };
  }, [shown?.busy, userId, refresh, live]);
  useEffect(() => { if (remaining <= 0) return; const timer = window.setTimeout(() => setRemaining(value => Math.max(0, value - 1)), 1000); return () => clearTimeout(timer); }, [remaining]);
  return { page: shown, error, loading, olderLoading, remaining, active: !!activeTurn, busy: shown?.busy ?? false, activeTurn,
    refresh, loadOlder: () => shown?.nextCursor && refresh(shown.nextCursor), update,
    accept: (turn: LumiChatTurn) => { loadEpoch.current++; busy.current = false; update(turn); setRemaining(5); },
    replace: (next: LumiChatPage) => { loadEpoch.current++; busy.current = false; cache.current.set(next.sessionId, next); setOwner(userId); setPage(next); setRemaining(next.cooldownRemaining); setError(""); setLoading(false); setOlderLoading(false); },
    forget: (id: string) => cache.current.delete(id), guard: () => epoch.current, isCurrent: (version: number) => version === epoch.current };
}
