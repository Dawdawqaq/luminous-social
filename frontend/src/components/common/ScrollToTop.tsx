import React, { useEffect, useLayoutEffect, useRef } from "react";
import { useLocation, useNavigationType } from "react-router-dom";

const STORAGE_KEY = "luminous_scroll_positions";
function readPositions(): Map<string, number> {
  try {
    const values: unknown = JSON.parse(sessionStorage.getItem(STORAGE_KEY) || "[]");
    if (!Array.isArray(values)) return new Map();
    return new Map(values.filter(item => Array.isArray(item) && typeof item[0] === "string"
      && Number.isFinite(item[1]) && item[1] >= 0));
  } catch { return new Map(); }
}
const scrollPositions = readPositions();
function anchorId(hash: string): string {
  try { return decodeURIComponent(hash.slice(1)); } catch { return hash.slice(1); }
}
function persistPositions() {
  while (scrollPositions.size > 80) scrollPositions.delete(scrollPositions.keys().next().value!);
  try { sessionStorage.setItem(STORAGE_KEY, JSON.stringify([...scrollPositions])); } catch { /* 存储不可用时仍在内存中恢复滚动位置。 */ }
}

export const ScrollToTop: React.FC = () => {
  const location = useLocation();
  const navigationType = useNavigationType();
  const previous = useRef({ key: location.key, pathname: location.pathname });
  const currentKey = useRef(location.key);
  const restoring = useRef(false);

  useEffect(() => {
    const original = window.history.scrollRestoration;
    window.history.scrollRestoration = "manual";
    const record = () => {
      if (!restoring.current) scrollPositions.set(currentKey.current, window.scrollY);
    };
    const hide = () => { record(); persistPositions(); };
    window.addEventListener("scroll", record, { passive: true });
    window.addEventListener("pagehide", hide);
    return () => {
      window.history.scrollRestoration = original;
      window.removeEventListener("scroll", record);
      window.removeEventListener("pagehide", hide);
    };
  }, []);

  useLayoutEffect(() => {
    const from = previous.current;
    const preservePage = navigationType === "REPLACE" && from.pathname === location.pathname;
    const saved = navigationType === "POP" ? scrollPositions.get(location.key)
      : preservePage ? scrollPositions.get(from.key) : undefined;
    const anchor = saved === undefined && location.hash ? anchorId(location.hash) : "";
    const desired = saved ?? 0;
    currentKey.current = location.key;
    previous.current = { key: location.key, pathname: location.pathname };
    restoring.current = true;
    let stopped = false, frame = 0;
    let mutation: MutationObserver | undefined;
    let resize: ResizeObserver | undefined;
    let timer: ReturnType<typeof setTimeout> | undefined;

    const stop = () => {
      if (stopped) return;
      stopped = true;
      restoring.current = false;
      cancelAnimationFrame(frame);
      mutation?.disconnect();
      resize?.disconnect();
      if (timer) clearTimeout(timer);
      window.removeEventListener("wheel", cancel);
      window.removeEventListener("touchstart", cancel);
      window.removeEventListener("keydown", onKey);
    };
    const cancel = () => { stop(); scrollPositions.set(location.key, window.scrollY); };
    const onKey = (event: KeyboardEvent) => {
      if (["ArrowDown", "ArrowUp", "PageDown", "PageUp", "Home", "End", " "].includes(event.key)) cancel();
    };
    const restore = () => {
      if (stopped || document.querySelector('#main-content [aria-busy="true"]')) return;
      if (anchor) {
        const element = document.getElementById(anchor);
        if (!element) return;
        element.scrollIntoView({ behavior: "instant", block: "start" });
      } else {
        // 等内容加载到足够高度再恢复，避免先被加载占位区域截断。
        const maximum = Math.max(0, document.documentElement.scrollHeight - window.innerHeight);
        if (maximum + 1 < desired) return;
        window.scrollTo({ top: desired, left: 0, behavior: "instant" });
      }
      scrollPositions.set(location.key, window.scrollY);
      stop();
    };
    const schedule = () => { cancelAnimationFrame(frame); frame = requestAnimationFrame(restore); };

    if (!preservePage) window.scrollTo({ top: 0, left: 0, behavior: "instant" });
    if (!anchor && desired === 0) {
      scrollPositions.set(location.key, 0);
      stop();
    } else {
      mutation = new MutationObserver(schedule);
      mutation.observe(document.getElementById("main-content") || document.body, { childList: true, subtree: true, attributes: true, attributeFilter: ["aria-busy"] });
      resize = new ResizeObserver(schedule);
      resize.observe(document.body);
      window.addEventListener("wheel", cancel, { passive: true });
      window.addEventListener("touchstart", cancel, { passive: true });
      window.addEventListener("keydown", onKey);
      // 页面内容变少或请求失败时恢复到现有范围，不持续抢夺用户的滚动控制。
      timer = setTimeout(() => {
        if (stopped) return;
        if (anchor) document.getElementById(anchor)?.scrollIntoView({ behavior: "instant", block: "start" });
        else window.scrollTo({ top: desired, left: 0, behavior: "instant" });
        cancel();
      }, 5000);
      schedule();
    }
    return () => { stop(); persistPositions(); };
  }, [location.key, location.pathname, location.search, location.hash, navigationType]);

  return null;
};
