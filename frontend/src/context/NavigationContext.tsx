import React, { createContext, useContext, useEffect, useRef } from "react";
import { useLocation, useNavigate, useNavigationType } from "react-router-dom";

const STORAGE_KEY = "luminous_navigation_entries";
interface Entry { key: string; path: string; }
interface NavigationContextValue {
  canGoBack: boolean;
  goBack: (fallback: string) => void;
}
const NavigationContext = createContext<NavigationContextValue | null>(null);

function readEntries(): Map<number, Entry> {
  try {
    const entries: unknown = JSON.parse(sessionStorage.getItem(STORAGE_KEY) || "[]");
    if (!Array.isArray(entries)) return new Map();
    return new Map(entries.filter(item => Array.isArray(item) && Number.isInteger(item[0]) && item[0] >= 0
      && typeof item[1]?.key === "string" && typeof item[1]?.path === "string"
      && item[1].path.startsWith("/") && !item[1].path.startsWith("//")));
  } catch { return new Map(); }
}

/** 只返回本标签页记录过的站内历史，直接打开页面时使用页面指定的去处。 */
export const NavigationProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const location = useLocation();
  const navigate = useNavigate();
  const type = useNavigationType();
  const entries = useRef<Map<number, Entry>>(readEntries());
  const pending = useRef(false);
  const index = Number.isInteger(window.history.state?.idx) ? window.history.state.idx : 0;
  const canGoBack = index > 0 && entries.current.has(index - 1);

  useEffect(() => {
    pending.current = false;
    if (type === "PUSH") {
      for (const key of entries.current.keys()) if (key > index) entries.current.delete(key);
    }
    entries.current.set(index, { key: location.key, path: location.pathname + location.search + location.hash });
    while (entries.current.size > 80) entries.current.delete(entries.current.keys().next().value!);
    try { sessionStorage.setItem(STORAGE_KEY, JSON.stringify([...entries.current])); } catch { /* 存储不可用时保留当前会话内的返回能力。 */ }
  }, [location, index, type]);

  return <NavigationContext.Provider value={{ canGoBack, goBack: fallback => {
    if (pending.current) return;
    pending.current = true;
    if (canGoBack) navigate(-1);
    else navigate(fallback, { replace: true });
  } }}>{children}</NavigationContext.Provider>;
};

export function useAppNavigation() {
  const context = useContext(NavigationContext);
  if (!context) throw new Error("导航组件需要 NavigationProvider");
  return context;
}
