import React, { useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { CheckCheck, RefreshCw, Trash2 } from "lucide-react";
import { Notification } from "../../types";
import { notificationApi } from "../../api/client";
import { useAuth } from "../../context/AuthContext";
import { useToast } from "../../context/ToastContext";
import { useNotifications } from "../../context/NotificationContext";
import { useNotificationInbox } from "../../hooks/useNotificationInbox";
import { DeleteConfirmModal } from "../ui/DeleteConfirmModal";
import { Modal } from "../ui/Modal";
import { Avatar } from "../ui/Avatar";
import { Button } from "../ui/Button";
import { ContentState } from "../common/ContentState";
import { CatEmoteText } from "../emotes/CatEmoteText";
import { formatTimeAgo } from "../../utils";

interface Props { isOpen: boolean; onClose: () => void }
const categories = [{key:"ALL",label:"全部"},{key:"COMMENT",label:"评论"},{key:"LIKE",label:"赞同"},{key:"FOLLOW",label:"关注"},{key:"SYSTEM",label:"系统"}];
const actionText = (type: string) => type === "LIKE_COMMENT" ? "赞同了你的评论" : type === "LIKE_POST" || type === "VOTE" ? "赞同了你的帖子"
  : type === "REPLY_COMMENT" ? "回复了你的评论" : type === "COMMENT_POST" || type === "COMMENT" ? "评论了你的帖子"
  : type === "FOLLOW_USER" || type === "FOLLOW" ? "关注了你" : "发来一条通知";

export const NotificationDrawer: React.FC<Props> = ({isOpen,onClose}) => {
  const {user,isAuthenticated} = useAuth();
  const {showToast} = useToast();
  const {refresh,unreadCount} = useNotifications();
  const navigate = useNavigate();
  const [type,setType] = useState("ALL");
  const inbox = useNotificationInbox(String(user?.id || ""),isOpen && isAuthenticated,type);
  const [marking,setMarking] = useState(false);
  const [deleting,setDeleting] = useState<Notification | null>(null);
  const [deleteBusy,setDeleteBusy] = useState(false);
  const locks = useRef(new Set<string>());
  const identity = useRef(user?.id); identity.current=user?.id;
  const scroller = useRef<HTMLDivElement>(null);
  const sentinel = useRef<HTMLDivElement>(null);
  const more = useRef(inbox.loadMore); more.current = inbox.loadMore;
  const {page} = inbox;
  useEffect(()=> { if(!isOpen) setType("ALL"); setDeleting(null); },[isOpen,user?.id]);
  useEffect(()=> { if(scroller.current) scroller.current.scrollTop=page.scrollTop; },[type,isOpen]);
  useEffect(()=> {
    if(!isOpen || !page.hasNext || page.loading || page.error || !sentinel.current) return;
    const observer = new IntersectionObserver(entries=> { if(entries.some(entry=>entry.isIntersecting)) void more.current(); },{root:scroller.current,rootMargin:"100px"});
    observer.observe(sentinel.current); return ()=>observer.disconnect();
  },[isOpen,type,page.hasNext,page.loading,page.error]);
  const report = (error: unknown) => showToast(error instanceof Error ? error.message : "操作失败，请重试","error");
  const mark = async (notice: Notification) => {
    if(notice.isRead || locks.current.has(notice.id)) return;
    locks.current.add(notice.id);
    const account=identity.current;
    try {
      await notificationApi.markAsRead(notice.id,notice.interactionCount || 1);
      if(identity.current!==account) return;
      inbox.patch(items=>items.map(item=>item.id===notice.id && (item.interactionCount || 1)<=(notice.interactionCount || 1) ? {...item,isRead:true} : item));
      await inbox.refreshSummary(); void refresh();
    } catch(error) { report(error); }
    finally { locks.current.delete(notice.id); }
  };
  const open = (notice: Notification) => {
    void mark(notice);
    const target = notice.target?.targetUrl || (notice.target?.type === "POST" ? `/post/${notice.target.id}` : notice.actor.username !== "已注销用户" ? `/u/${encodeURIComponent(notice.actor.username)}` : null);
    if(!target || !target.startsWith("/") || target.startsWith("//")) { showToast("相关内容暂时无法打开","info"); return; }
    onClose(); navigate(target);
  };
  const markAll = async () => {
    if(locks.current.has("all")) return;
    locks.current.add("all"); setMarking(true);
    const account=identity.current;
    try {
      await notificationApi.markAllAsRead();
      if(identity.current!==account) return;
      // 从数据库重新取得当前版本，避免本地把操作期间到达的新消息全部设为已读。
      await inbox.reload(); void refresh();
    } catch(error) { report(error); }
    finally { locks.current.delete("all"); setMarking(false); }
  };
  const remove = async () => {
    if(!deleting || deleteBusy) return;
    setDeleteBusy(true);
    const account=identity.current;
    try {
      await notificationApi.delete(deleting.id);
      if(identity.current!==account) return;
      inbox.patch(items=>items.filter(item=>item.id!==deleting.id));
      await inbox.refreshSummary(true); void refresh(); setDeleting(null);
    } finally { setDeleteBusy(false); }
  };
  return <>
    <Modal isOpen={isOpen && isAuthenticated} onClose={onClose} dismissible={!deleting} title="通知" subtitle="评论、赞同和新朋友，慢慢看。" maxWidth="xl">
      <div className="flex flex-wrap items-center justify-between gap-3 pb-3 hairline-b">
        <div role="tablist" aria-label="通知分类" className="flex gap-1 overflow-x-auto max-w-full">
          {categories.map(category=> <button key={category.key} type="button" role="tab" aria-selected={type===category.key}
            tabIndex={type===category.key ? 0 : -1} onKeyDown={event=> {
              if(!["ArrowLeft","ArrowRight","Home","End"].includes(event.key)) return;
              event.preventDefault(); const index=categories.findIndex(item=>item.key===category.key);
              const next=event.key==="Home" ? 0 : event.key==="End" ? categories.length-1 : (index+(event.key==="ArrowRight" ? 1 : -1)+categories.length)%categories.length;
              setType(categories[next].key); document.getElementById(`notification-tab-${categories[next].key}`)?.focus();
            }}
            aria-controls="notification-list" id={`notification-tab-${category.key}`} onClick={()=>setType(category.key)}
            className={"min-h-10 shrink-0 px-2 sm:px-3 rounded-md text-sm font-bold " + (type===category.key ? "bg-terracotta-light text-terracotta dark:bg-night-surfaceHover" : "text-ink-muted hover:bg-paper-100 dark:text-night-muted dark:hover:bg-night-surfaceHover")}>
            {category.label}{!!inbox.summary?.unread[category.key] && <span className="ml-1 text-xs tabular-nums">{inbox.summary.unread[category.key]>99 ? "99+" : inbox.summary.unread[category.key]}</span>}
          </button>)}
        </div>
        <div className="flex gap-2 items-center ml-auto">
          <button type="button" className="icon-button" aria-label="刷新通知" disabled={page.loading || marking} onClick={()=>void inbox.reload().catch(report)}><RefreshCw size={17}/></button>
          <Button size="sm" variant="outline" onClick={markAll} isLoading={marking} disabled={marking || (inbox.summary?.unreadCount ?? unreadCount)===0}>
            <CheckCheck size={17}/>全部已读
          </Button>
        </div>
      </div>
      {inbox.hasUpdates && <button type="button" className="w-full py-2 mt-2 text-sm text-terracotta bg-terracotta-light rounded-md" onClick={()=>void inbox.reload().catch(report)}>有新通知，点击更新</button>}
      <div ref={scroller} id="notification-list" role="tabpanel" aria-labelledby={`notification-tab-${type}`} tabIndex={0}
        className="max-h-[55dvh] sm:max-h-[60dvh] overflow-y-auto overscroll-contain mt-2" onScroll={event=>inbox.saveScroll(event.currentTarget.scrollTop)}>
        {page.items.map(notice=> <article key={notice.id} data-notification-id={notice.id} className={"relative hairline-b last:border-b-0 py-1 " + (!notice.isRead ? "bg-terracotta-light/60 dark:bg-night-surfaceHover" : "")}>
          <div className="flex items-start gap-1">
            <button type="button" onClick={()=>open(notice)} className="flex flex-1 min-w-0 gap-3 p-3 text-left rounded-md hover:bg-paper-100 dark:hover:bg-night-canvas focus-visible:outline-terracotta">
              <Avatar src={notice.actor.avatar} name={notice.actor.displayName || notice.actor.username} size="sm"/>
              <div className="flex-1 min-w-0">
                <p className="text-sm leading-relaxed break-words"><strong>{notice.actor.displayName || notice.actor.username}</strong>
                  {(notice.interactionCount || 1)>1 && <span> 等 {notice.interactionCount} 人</span>} {actionText(notice.type)}</p>
                {notice.contentPreview && notice.type!=="FOLLOW_USER" && <p className="mt-1 text-sm text-ink-muted dark:text-night-muted line-clamp-2 break-words"><CatEmoteText text={notice.contentPreview} size="compact"/></p>}
                <div className="mt-2 text-xs text-ink-muted dark:text-night-muted">
                  <time dateTime={notice.updatedAt || notice.createdAt}>{formatTimeAgo(notice.updatedAt || notice.createdAt)}</time>
                </div>
              </div>
            </button>
            <div className="flex flex-col items-center pt-2 pr-1">
              <span className={"w-2 h-2 rounded-full my-2 " + (notice.isRead ? "bg-transparent" : "bg-terracotta")} aria-label={notice.isRead ? "已读" : "未读"}/>
              <button type="button" aria-label="删除这条通知" className="icon-button text-ink-muted dark:text-night-muted" onClick={()=>setDeleting(notice)}><Trash2 size={15}/></button>
            </div>
          </div>
        </article>)}
        {page.loading && <ContentState title="正在加载通知" loading/>}
        {page.error && <ContentState title="通知加载失败" description={page.error} onRetry={()=>void inbox.loadMore()}/>}
        {page.loaded && !page.loading && !page.error && !page.items.length && <ContentState title={type==="ALL" ? "还没有通知" : `暂无${categories.find(item=>item.key===type)?.label}通知`} description="收到新的互动后，会出现在这里。"/>}
        <div ref={sentinel} className="py-2 text-center">
          {page.hasNext && !page.loading && !page.error && <button type="button" className="post-action" onClick={()=>void inbox.loadMore()}>加载更多通知</button>}
          {page.loaded && page.items.length>0 && !page.hasNext && !page.loading && <p className="py-2 text-xs text-ink-muted dark:text-night-muted">已经看到最后一条了</p>}
        </div>
      </div>
    </Modal>
    <DeleteConfirmModal isOpen={!!deleting && isOpen} onClose={()=>setDeleting(null)} onConfirm={remove} kind="notification" preview={deleting?.contentPreview}/>

  </>;
};
