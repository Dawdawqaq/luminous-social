import React, { useEffect, useLayoutEffect, useRef, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { Send, Plus, RotateCcw, Square, PanelLeft } from "lucide-react";
import { ApiError, lumiChatApi, LumiConversation } from "../api/client";
import { randomOperationKey } from "../api/browserCrypto";
import { useAuth } from "../context/AuthContext";
import { useLumiChat } from "../hooks/useLumiChat";
import { useLumiConversations } from "../hooks/useLumiConversations";
import { useLumiDraft } from "../hooks/useLumiDraft";
import { useChatViewport } from "../hooks/useChatViewport";
import { ChatMarkdown, ChatCopyButton } from "../components/lumi/ChatMarkdown";
import { ConversationList } from "../components/lumi/ConversationList";
import { Avatar } from "../components/ui/Avatar";
import { Button } from "../components/ui/Button";
import { Modal } from "../components/ui/Modal";
import { DeleteConfirmModal } from "../components/ui/DeleteConfirmModal";
import { ContentState } from "../components/common/ContentState";
import "./LumiChatPage.css";

function remembered(user?: string) { try { return user ? localStorage.getItem(`luminous:lumi-selected:${user}`) || undefined : undefined; } catch { return undefined; } }
function removeDraft(user: string, id: string) { try { sessionStorage.removeItem(`luminous:lumi-draft:${user}:${id}`); } catch { /* 存储不可用时不阻断会话操作。 */ } }

export const LumiChatPage: React.FC<{ onRequireAuth: () => void }> = ({ onRequireAuth }) => {
  const { user, isLoading: authLoading } = useAuth();
  const [params, setParams] = useSearchParams();
  const requested = params.get("chat") || remembered(user?.id);
  const chat = useLumiChat(user?.id, requested);
  const conversations = useLumiConversations(user?.id);
  const saved = useLumiDraft(user?.id, chat.page?.sessionId);
  const [actionError, setActionError] = useState("");
  const [sending, setSending] = useState(false), [creating, setCreating] = useState(false), [stopping, setStopping] = useState(false);
  const [retrying, setRetrying] = useState<string>(), [drawer, setDrawer] = useState(false);
  const [rename, setRename] = useState<LumiConversation>(), [name, setName] = useState("");
  const [renaming, setRenaming] = useState(false), [deleting, setDeleting] = useState<LumiConversation>();
  const panel = useRef<HTMLElement>(null), region = useRef<HTMLDivElement>(null), input = useRef<HTMLTextAreaElement>(null);
  const renameInput = useRef<HTMLInputElement>(null);
  const lock = useRef(false), stick = useRef(true), createKey = useRef<string>();
  const positions = useRef(new Map<string, { top: number; stick: boolean }>());
  const restored = useRef<string>(), selection = useRef<string>();
  const previousAccount = useRef(user?.id);
  const page = chat.page, draft = saved.draft; selection.current = page?.sessionId;
  useChatViewport(panel);
  const select = (id: string, replace = false) => { setParams({ chat: id }, { replace }); setDrawer(false); };
  useEffect(() => { if (user && page) { try { localStorage.setItem(`luminous:lumi-selected:${user.id}`, page.sessionId); } catch {} if (!params.get("chat")) select(page.sessionId, true); } }, [user?.id, page?.sessionId]);
  useEffect(() => { setActionError(""); lock.current = false; setSending(false); setCreating(false); setStopping(false); setRetrying(undefined); }, [user?.id, requested]);
  useEffect(() => { if (previousAccount.current && previousAccount.current !== user?.id) setParams({}, { replace: true }); previousAccount.current = user?.id; positions.current.clear(); restored.current = undefined; setDrawer(false); setRename(undefined); setRenaming(false); setDeleting(undefined); createKey.current = undefined; }, [user?.id]);
  useEffect(() => { if (user && page) void conversations.refresh(); }, [user?.id, page?.sessionId, page?.activeTurnId, page?.busy]);
  useLayoutEffect(() => {
    const el = region.current; if (!el || !page) return;
    if (restored.current !== page.sessionId) { const old = positions.current.get(page.sessionId); stick.current = old?.stick ?? true; el.scrollTop = old && !old.stick ? old.top : el.scrollHeight; restored.current = page.sessionId; }
    else if (stick.current) el.scrollTop = el.scrollHeight;
  }, [page?.sessionId, page?.turns.at(-1)?.revision, page?.turns.length, chat.loading]);
  useLayoutEffect(() => { const el = input.current; if (el) { el.style.height = "auto"; el.style.height = `${Math.min(160, Math.max(72, el.scrollHeight))}px`; } }, [draft, page?.sessionId, chat.loading]);
  const submit = async (event: React.FormEvent) => {
    event.preventDefault(); if (lock.current || !page || !user || chat.busy || chat.remaining > 0 || !draft.trim() || !page.enabled || draft.length > 1000) return;
    lock.current = true; setSending(true); setActionError(""); const version = chat.guard(), account = conversations.guard();
    const content = draft.trim(), operation = saved.pending?.content === content ? saved.pending : { session: page.sessionId, content, key: randomOperationKey() };
    try {
      saved.preparing(operation); const turn = await lumiChatApi.send(page.sessionId, operation.key, content);
      if (!conversations.isCurrent(account)) return;
      void conversations.refresh();
      if (!chat.isCurrent(version)) { try { const stored = JSON.parse(sessionStorage.getItem(`luminous:lumi-draft:${user.id}:${page.sessionId}`) || "null"); if (stored?.pending?.key === operation.key) removeDraft(user.id, page.sessionId); } catch {} return; }
      stick.current = true; chat.accept(turn); saved.clear(); input.current?.focus();
    } catch (err) { if (chat.isCurrent(version)) setActionError(err instanceof Error ? err.message : "发送失败，请重试"); }
    finally { if (chat.isCurrent(version)) { lock.current = false; setSending(false); } }
  };
  const stop = async () => {
    if (!chat.activeTurn || stopping || lock.current) return; const version = chat.guard(); setStopping(true); setActionError("");
    try { const turn = await lumiChatApi.stop(chat.activeTurn.id); if (chat.isCurrent(version)) chat.update(turn); }
    catch (err) { if (chat.isCurrent(version)) setActionError(err instanceof Error ? err.message : "停止失败，请重试"); }
    finally { if (chat.isCurrent(version)) setStopping(false); }
  };
  const retry = async (id: string) => {
    if (lock.current) return; lock.current = true; setRetrying(id); setActionError(""); const version = chat.guard();
    try { const turn = await lumiChatApi.retry(id); if (chat.isCurrent(version)) { stick.current = true; chat.accept(turn); } }
    catch (err) { if (chat.isCurrent(version)) setActionError(err instanceof Error ? err.message : "重试失败"); }
    finally { if (chat.isCurrent(version)) { lock.current = false; setRetrying(undefined); } }
  };
  const create = async () => {
    if (!user || lock.current) return; lock.current = true; setCreating(true); setActionError(""); const version = chat.guard(), account = conversations.guard();
    createKey.current ||= randomOperationKey();
    try { const next = await lumiChatApi.create(createKey.current); if (!conversations.isCurrent(account)) return; createKey.current = undefined; void conversations.refresh(); if (chat.isCurrent(version)) { chat.replace(next); select(next.sessionId); } }
    catch (err) { if (!conversations.isCurrent(account)) return; if (err instanceof ApiError && err.status && err.status < 500) createKey.current = undefined; if (chat.isCurrent(version)) setActionError(err instanceof Error ? err.message : "新建失败，请重试"); }
    finally { if (chat.isCurrent(version)) { lock.current = false; setCreating(false); } }
  };
  const renameConversation = async (event: React.FormEvent) => {
    event.preventDefault(); if (!rename || !name.trim() || renaming) return; const account = conversations.guard(); setRenaming(true); setActionError("");
    try { await lumiChatApi.rename(rename.id, name.trim()); if (conversations.isCurrent(account)) { setRename(undefined); void conversations.refresh(); if (selection.current === rename.id) void chat.refresh(); } }
    catch (err) { if (conversations.isCurrent(account)) setActionError(err instanceof Error ? err.message : "重命名失败"); }
    finally { if (conversations.isCurrent(account)) setRenaming(false); }
  };
  const deleteConversation = async () => {
    if (!deleting || !user) return; const id = deleting.id, account = conversations.guard(); await lumiChatApi.delete(id);
    if (!conversations.isCurrent(account)) return;
    removeDraft(user.id, id); chat.forget(id); positions.current.delete(id); conversations.remove(id);
    if (selection.current === id || requested === id) {
      try { localStorage.removeItem(`luminous:lumi-selected:${user.id}`); } catch {}
      const next = await lumiChatApi.read(); if (!conversations.isCurrent(account)) return; chat.replace(next); select(next.sessionId, true);
    }
    void conversations.refresh();
  };
  const list = <ConversationList data={conversations.value} selected={page?.sessionId || requested} loading={conversations.loading} error={conversations.error} onSelect={id => select(id)} onRename={row => { setDrawer(false); setName(row.title); setActionError(""); setRename(row); }} onDelete={row => { setDrawer(false); setDeleting(row); }} onReload={() => void conversations.refresh()} onMore={() => void conversations.refresh(true)} />;
  return <>
    <section ref={panel} className="panel lumi-chat lumi-chat--multi" aria-labelledby="lumi-chat-title">
      {user && <aside className="lumi-conversations-desktop" aria-label="聊天会话列表"><div className="lumi-conversations-heading"><h2>对话</h2><span>最多{conversations.value?.maxConversations || 50}段</span></div>{list}</aside>}
      <div className="lumi-chat-main">
        <header className="lumi-chat-header"><div className="flex items-center gap-3 min-w-0">{user && <button type="button" className="lumi-chat-history-toggle icon-button" aria-label="打开对话列表" onClick={() => { setDrawer(true); void conversations.refresh(); }}><PanelLeft size={20} aria-hidden="true" /></button>}<Avatar src="/emotes/cats/happy.webp" name="Lumi" size="md" /><div className="min-w-0"><h1 id="lumi-chat-title">Lumi AI</h1><p className="lumi-chat-current-title" title={page?.title}>{page?.title || "和 Lumi 聊聊想法"}</p></div></div>{user && <Button size="sm" variant="outline" disabled={creating || sending} isLoading={creating} onClick={() => void create()}><Plus size={16} aria-hidden="true" />新对话</Button>}</header>
        {page?.busy && !chat.active && <div className="lumi-chat-background-task" role="status"><span>Lumi 正在处理你的另一条请求，可以先写草稿。</span>{page.activeSessionId && <button type="button" onClick={() => select(page.activeSessionId!)}>返回正在回答的对话</button>}</div>}
        {authLoading || chat.loading ? <ContentState title="正在加载对话" loading /> : !user ? <div className="lumi-chat-empty"><img src="/emotes/cats/happy.webp" alt="" width={72} height={72} /><h2>登录后，和 Lumi 聊聊</h2><p>聊天记录会为你保存，下次回来接着聊。</p><Button onClick={onRequireAuth}>登录</Button></div> : <>
          <div key={page?.sessionId || "unavailable"} ref={region} className="lumi-chat-messages" role="region" aria-label="当前聊天记录" onScroll={() => { const el = region.current; if (el && page) { stick.current = el.scrollHeight - el.scrollTop - el.clientHeight < 100; positions.current.set(page.sessionId, { top: el.scrollTop, stick: stick.current }); } }}>
            {page?.hasMore && <div className="text-center mb-5"><Button variant="ghost" size="sm" isLoading={chat.olderLoading} onClick={async () => { const el = region.current, oldHeight = el?.scrollHeight || 0; const version = chat.guard(); stick.current = false; await chat.loadOlder(); requestAnimationFrame(() => { if (el && chat.isCurrent(version)) el.scrollTop += el.scrollHeight - oldHeight; }); }}>查看更早的消息</Button></div>}
            {page && !page.turns.length && <div className="lumi-chat-empty"><img src="/emotes/cats/happy.webp" alt="" width={72} height={72} /><h2>有什么想和我聊聊的？</h2><p>每段对话独立记忆，从这里开始一个新话题。</p></div>}
            {!page && !chat.error && <div className="lumi-chat-empty"><h2>开始一段新对话</h2><p>选择左侧记录，或新建一个话题。</p></div>}
            {page?.turns.map(turn => <article key={turn.id} className="lumi-chat-turn" data-chat-turn={turn.id}><div className="lumi-chat-question"><span className="sr-only">你：</span><p>{turn.question}</p></div><div className="lumi-chat-answer"><Avatar src="/emotes/cats/happy.webp" name="Lumi" size="sm" /><div className="min-w-0 flex-1"><div className="flex items-center gap-2 mb-2"><strong>Lumi</strong><span className="text-xs text-terracotta dark:text-terracotta-dark">AI</span></div>{turn.answer && <ChatMarkdown content={turn.answer} />}<div className="lumi-chat-turn-status" role={turn.status === "FAILED" ? "alert" : "status"}>{turn.status !== "SUCCEEDED" && <p>{turn.message}</p>}<div className="lumi-chat-answer-actions">{turn.answer && !["RUNNING", "STOPPING"].includes(turn.status) && <ChatCopyButton text={turn.answer} />}{turn.retryable && <button type="button" className="post-action" disabled={!!retrying || sending || chat.busy || chat.remaining > 0} onClick={() => void retry(turn.id)}><RotateCcw size={16} aria-hidden="true" />{retrying === turn.id ? "正在重试…" : "重试回答"}</button>}</div></div></div></div></article>)}
          </div>
          {(chat.error || actionError) && <div className="lumi-chat-error" role="alert"><span>{actionError || chat.error}</span>{chat.error && <button type="button" className="post-action" onClick={() => void chat.refresh()}>重新加载</button>}</div>}
          <form className="lumi-chat-composer" onSubmit={submit} aria-label="发送聊天消息"><label htmlFor="lumi-chat-input" className="sr-only">发给 Lumi 的消息</label><textarea ref={input} id="lumi-chat-input" value={draft} rows={2} maxLength={1000} placeholder="和 Lumi 说点什么…" disabled={!page || !page.enabled || sending} onChange={event => { saved.edit(event.target.value); setActionError(""); }} onKeyDown={event => { if (event.key === "Enter" && (event.ctrlKey || event.metaKey) && !event.nativeEvent.isComposing) { event.preventDefault(); event.currentTarget.form?.requestSubmit(); } }} /><div className="lumi-chat-composer-bottom"><p>{page && !page.enabled ? "Lumi 暂时不可用" : "文字聊天 · 暂不联网"}<span className="hidden sm:inline"> · Ctrl+Enter 发送</span></p><div className="flex items-center gap-3"><span className="text-xs text-ink-muted dark:text-night-muted">{draft.length}/1000</span>{chat.active ? <Button type="button" size="sm" variant="outline" disabled={stopping || chat.activeTurn?.status === "STOPPING"} onClick={() => void stop()}><Square size={14} aria-hidden="true" />{stopping || chat.activeTurn?.status === "STOPPING" ? "正在停止…" : "停止生成"}</Button> : <Button type="submit" size="sm" isLoading={sending} disabled={!page?.enabled || !draft.trim() || chat.busy || chat.remaining > 0}><Send size={16} aria-hidden="true" />{chat.busy ? "等待其他回答" : chat.remaining > 0 ? `${chat.remaining}秒后发送` : "发送"}</Button>}</div></div></form>
        </>}
      </div>
    </section>
    <Modal isOpen={drawer} onClose={() => setDrawer(false)} title="对话" maxWidth="sm" placement="drawer">{list}</Modal>
    <Modal isOpen={!!rename} onClose={() => !renaming && setRename(undefined)} title="重命名对话" maxWidth="sm" dismissible={!renaming} initialFocusRef={renameInput}><form onSubmit={renameConversation}><label htmlFor="lumi-conversation-name" className="block mb-2">对话名称</label><input ref={renameInput} id="lumi-conversation-name" className="lumi-conversation-name-input" value={name} onChange={event => setName(event.target.value)} maxLength={40} autoFocus /><p className="lumi-conversations-hint">{name.length}/40</p>{actionError && <p role="alert" className="lumi-conversations-error">{actionError}</p>}<div className="flex justify-end gap-3 mt-5"><Button variant="outline" onClick={() => setRename(undefined)} disabled={renaming}>取消</Button><Button type="submit" disabled={!name.trim()} isLoading={renaming}>保存名称</Button></div></form></Modal>
    <DeleteConfirmModal isOpen={!!deleting} onClose={() => setDeleting(undefined)} onConfirm={deleteConversation} kind="conversation" preview={deleting?.title} />
  </>;
};
