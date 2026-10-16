import { useLayoutEffect, useRef, useState } from "react";

type Pending = { session: string; content: string; key: string };
type Draft = { text: string; pending?: Pending };

/** 草稿只留在当前浏览器标签页，并按账号和会话隔离；存储不可用时仍可输入和发送。 */
export function useLumiDraft(user?: string, session?: string) {
  const key = user && session ? `luminous:lumi-draft:${user}:${session}` : undefined;
  const [value, setValue] = useState<Draft>({ text: "" }); const owner = useRef<string>();
  useLayoutEffect(() => {
    owner.current = key; let next: Draft = { text: "" };
    if (key) try {
      const saved = JSON.parse(sessionStorage.getItem(key) || "null");
      if (saved && typeof saved.text === "string") {
        next.text = saved.text.slice(0, 1000);
        if (saved.pending?.session === session && typeof saved.pending.content === "string" && /^[A-Za-z0-9-]{16,64}$/.test(saved.pending.key)) next.pending = saved.pending;
      }
    } catch { /* 草稿恢复失败不影响聊天。 */ }
    setValue(next);
  }, [key, session, user]);
  const save = (next: Draft) => {
    setValue(next);
    if (key && owner.current === key) try { if (next.text || next.pending) sessionStorage.setItem(key, JSON.stringify(next)); else sessionStorage.removeItem(key); } catch { /* 浏览器禁用存储时仅保留内存草稿。 */ }
  };
  return { draft: owner.current === key ? value.text : "", pending: owner.current === key ? value.pending : undefined,
    edit: (text: string) => save({ text, pending: value.pending?.content === text.trim() ? value.pending : undefined }),
    preparing: (pending: Pending) => save({ text: value.text, pending }), clear: () => save({ text: "" }) };
}
