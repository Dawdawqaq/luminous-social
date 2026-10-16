import { useEffect, useState, useSyncExternalStore } from "react";
import { useAuth } from "../context/AuthContext";

const deadlines = new Map<string, number>();
const listeners = new Set<() => void>();
const subscribe = (listener: () => void) => { listeners.add(listener); return () => { listeners.delete(listener); }; };

/** 当前用户的主评论和楼内输入框共用提示时间，最终是否允许发布仍由后端判断。 */
export function useCommentCooldown() {
  const { user } = useAuth();
  const key = user?.id || "guest";
  const deadline = useSyncExternalStore(subscribe, () => deadlines.get(key) || 0, () => 0);
  const [now, setNow] = useState(Date.now());
  useEffect(() => {
    setNow(Date.now());
    if (deadline <= Date.now()) return;
    const timer = window.setInterval(() => {
      const current = Date.now(); setNow(current);
      if (current >= deadline) window.clearInterval(timer);
    }, 250);
    return () => window.clearInterval(timer);
  }, [deadline]);
  const impose = (seconds: number) => {
    if (!Number.isFinite(seconds) || seconds <= 0) return;
    deadlines.set(key, Math.max(deadlines.get(key) || 0, Date.now() + seconds * 1000));
    listeners.forEach(listener => listener());
  };
  return { remaining: Math.max(0, Math.ceil((deadline - now) / 1000)), impose };
}
