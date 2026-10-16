import React, { useEffect, useRef, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { ThumbsUp, Reply, Trash2 } from "lucide-react";
import { Comment, CommentFloorSummary } from "../../types";
import { lumiApi, postApi } from "../../api/client";
import { useAuth } from "../../context/AuthContext";
import { useToast } from "../../context/ToastContext";
import { Avatar } from "../ui/Avatar";
import { LevelBadge } from "../ui/LevelBadge";
import { Button } from "../ui/Button";
import { DeleteConfirmModal } from "../ui/DeleteConfirmModal";
import { Modal } from "../ui/Modal";
import { CommentComposer } from "./CommentComposer";
import { CommentContent } from "./CommentContent";
import { useCommentPages } from "../../hooks/useCommentPages";
import { ContentState } from "../common/ContentState";
import { formatTimeAgo } from "../../utils";
import { useLumiTasks } from "../../hooks/useLumiTasks";

interface Props {
  postId: string | number;
  onCountChange: (delta: number) => void;
  onRequireAuth: () => void;
}

interface FloorProps {
  notificationTarget?: Comment | null;
  floor: CommentFloorSummary;
  postId: string | number;
  isAuthenticated: boolean;
  onRequireAuth: () => void;
  onAdded: (comment: Comment) => void;
  onUpdated: (comment: Comment) => void;
  renderItem: (comment: Comment, onReply: () => void, busy: boolean, onDelete?: (id: string) => void, onUpdate?: (comment: Comment) => void) => React.ReactNode;
}

const CommentFloor: React.FC<FloorProps> = ({ floor, postId, isAuthenticated, onRequireAuth, onAdded, onUpdated, renderItem, notificationTarget }) => {
  const [localReplies, setLocalReplies] = useState<Comment[]>([]);
  const [expanded, setExpanded] = useState(false);
  const [activated, setActivated] = useState(false);
  const replies = useCommentPages<Comment>(`${postId}:${floor.id}`, cursor => postApi.getCommentReplies(postId, floor.id, cursor), activated);
  const localIds = new Set(localReplies.map(comment => comment.id));
  const visibleReplies = [...localReplies, ...replies.items.filter(comment => !localIds.has(comment.id))];
  const [target, setTarget] = useState<Comment | null>(null);
  const [busy, setBusy] = useState(false);
  const reply = (comment: Comment) => {
    if (!isAuthenticated) { onRequireAuth(); return; }
    if (!busy) setTarget(comment);
  };
  useEffect(()=> { if(notificationTarget?.parentId) { setExpanded(true); setActivated(true); } },[notificationTarget?.id]);
  return (
    <article id={`comment-${floor.id}`} data-comment-floor={floor.id} className="py-4 hairline-b scroll-mt-36">
      {floor.root ? renderItem(floor.root, () => reply(floor.root!), busy) : (
        <p className="py-2 text-sm text-ink-muted dark:text-night-muted">原评论已删除，楼内回复已保留。</p>
      )}
      {notificationTarget?.parentId && !visibleReplies.some(item=>item.id===notificationTarget.id) && <div className="mt-3 rounded-md bg-terracotta-light p-3 sm:ml-10 dark:bg-night-surfaceHover">
        <p className="text-xs text-ink-muted dark:text-night-muted mb-2">通知中的回复</p>
        {renderItem(notificationTarget,()=>reply(notificationTarget),busy)}
      </div>}
      {floor.replyCount > 0 && <button type="button" className="post-action mt-2 sm:ml-10" aria-expanded={expanded}
        onClick={() => { setExpanded(previous => !previous); setActivated(true); }}>
        {expanded ? "收起回复" : `查看 ${floor.replyCount} 条回复`}
      </button>}
      {(expanded || target) && <div className="mt-3 space-y-4 rounded-md border border-ink-rule bg-paper-100 p-3 sm:ml-10 sm:p-4 dark:border-night-border dark:bg-night-canvas" aria-label="楼内讨论">
        {expanded && <>
          {visibleReplies.map(comment => <div key={comment.id} className="hairline-b pb-3 last:border-b-0">
            {renderItem(comment, () => reply(comment), busy, id => { setLocalReplies(previous => previous.filter(item => item.id !== id)); replies.setItems(previous => previous.filter(item => item.id !== id)); }, updated => { setLocalReplies(previous => previous.map(item => item.id === updated.id ? updated : item)); replies.setItems(previous => previous.map(item => item.id === updated.id ? updated : item)); onUpdated(updated); })}
          </div>)}
          {replies.loading && <p role="status" className="text-sm text-ink-muted dark:text-night-muted">正在加载回复…</p>}
          {replies.error && <p role="alert" className="text-sm text-red-600">{replies.error}</p>}
          {!replies.loading && (replies.hasNext || replies.error) && <button type="button" className="post-action" onClick={() => void replies.loadMore()}>
            {replies.error ? "重试加载回复" : "查看更多回复"}
          </button>}
          {!replies.loading && !replies.error && visibleReplies.length === 0 && <p className="text-sm text-ink-muted dark:text-night-muted">暂无楼内回复。</p>}
        </>}
        {target && <CommentComposer postId={postId} target={target} onBusyChange={setBusy} onCancel={() => setTarget(null)}
          onAdded={comment => { setLocalReplies(previous => [comment, ...previous.filter(item => item.id !== comment.id)]); onAdded(comment); setTarget(null); setExpanded(true); setActivated(true); }} />}
      </div>}

    </article>
  );
};

export const CommentThread: React.FC<Props> = ({ postId, onCountChange, onRequireAuth }) => {
  const { user, isAuthenticated } = useAuth();
  const lumi = useLumiTasks(postId, user?.id);
  const [lumiRetrying, setLumiRetrying] = useState<string | null>(null);
  const [search] = useSearchParams();
  const focusId = search.get("comment");
  const [notificationTarget,setNotificationTarget] = useState<Comment | null>(null);
  const [focusError,setFocusError] = useState("");
  const floors = useCommentPages<CommentFloorSummary>(String(postId), cursor => postApi.getCommentFloors(postId, cursor), true);
  const [localFloors, setLocalFloors] = useState<CommentFloorSummary[]>([]);
  useEffect(()=> {
    let cancelled=false; setNotificationTarget(null); setFocusError("");
    if(focusId && /^\d+$/.test(focusId)) void postApi.getCommentContext(postId,focusId).then(result=> {
      if(cancelled) return;
      setLocalFloors(previous=>[result.floor,...previous.filter(item=>item.id!==result.floor.id)]);
      setNotificationTarget(result.target);
    }).catch(error=> { if(!cancelled) setFocusError(error instanceof Error ? error.message : "通知对应的评论暂时不可查看"); });
    return ()=> {cancelled=true;};
  },[postId,focusId]);
  useEffect(()=> {
    if(!notificationTarget) return;
    const frame=requestAnimationFrame(()=>document.getElementById(`comment-item-${notificationTarget.id}`)?.scrollIntoView({block:"center"}));
    return ()=>cancelAnimationFrame(frame);
  },[notificationTarget]);
  const locallyAdded = useRef(new Set<string>());
  const pendingLumi = useRef(new Set<string>());
  useEffect(() => { pendingLumi.current.clear(); }, [postId,user?.id]);
  const localFloorIds = new Set(localFloors.map(floor => floor.id));
  const visibleFloors = [...localFloors, ...floors.items.filter(floor => !localFloorIds.has(floor.id))];
  const updateFloors = (update: (previous: CommentFloorSummary[]) => CommentFloorSummary[]) => {
    floors.setItems(update); setLocalFloors(update);
  };
  const sentinel = useRef<HTMLDivElement>(null);
  const loadRef = useRef(floors.loadMore); loadRef.current = floors.loadMore;
  useEffect(() => {
    if (!floors.hasNext || floors.loading || floors.error || !sentinel.current) return;
    const observer = new IntersectionObserver(entries => {
      if (entries.some(entry => entry.isIntersecting)) void loadRef.current();
    }, { rootMargin: "160px" });
    observer.observe(sentinel.current);
    return () => observer.disconnect();
  }, [floors.hasNext, floors.loading, floors.error]);
  const onCommentAdded = (comment: Comment) => {
    if (/@lumi\b/i.test(comment.content)) { pendingLumi.current.add(comment.id); lumi.refresh(); }
    if (locallyAdded.current.has(comment.id)) return;
    locallyAdded.current.add(comment.id);
    floors.setTotal(previous => previous + 1); onCountChange(1);
    if (comment.parentId) updateFloors(previous => previous.map(floor => floor.id === comment.parentId ? { ...floor, replyCount: floor.replyCount + 1 } : floor));
    else setLocalFloors(previous => [{id:comment.id,root:comment,replyCount:0}, ...previous.filter(floor => floor.id !== comment.id)]);
    requestAnimationFrame(() => document.getElementById(`comment-item-${comment.id}`)?.scrollIntoView({ block: "nearest" }));
  };
  useEffect(() => {
    for (const task of lumi.tasks) {
      if (task.status === "PENDING" || task.status === "RUNNING") pendingLumi.current.add(task.commentId);
      if (["FAILED","CANCELLED","REJECTED"].includes(task.status)) pendingLumi.current.delete(task.commentId);
      if (task.status === "SUCCEEDED" && task.replyId && pendingLumi.current.delete(task.commentId) && !locallyAdded.current.has(task.replyId)) {
        locallyAdded.current.add(task.replyId);
        floors.setTotal(value => value + 1); onCountChange(1);
        void postApi.getCommentContext(postId,task.replyId).then(context => updateFloors(previous => previous.map(floor => floor.id === context.floor.id ? context.floor : floor))).catch(() => {});
      }
    }
  }, [lumi.tasks, postId]);
  const onCommentUpdated = (comment: Comment) => {
    setNotificationTarget(previous=>previous?.id===comment.id ? comment : previous);
    updateFloors(previous => previous.map(floor => floor.root?.id === comment.id ? { ...floor, root: comment } : floor));
  };

  const { showToast } = useToast();
  const retryLumi = async (id: string) => {
    if (lumiRetrying) return;
    setLumiRetrying(id);
    try { await lumiApi.retry(id); lumi.refresh(); }
    catch (err) { showToast(err instanceof Error ? err.message : "暂时无法重试", "error"); }
    finally { setLumiRetrying(null); }
  };
  const [pending, setPending] = useState<string[]>([]);
  const [deleteTarget, setDeleteTarget] = useState<Comment | null>(null);
  const deleteCallback = useRef<((id: string) => void) | undefined>();
  const [viewImage, setViewImage] = useState<string | null>(null);
  const locks = useRef(new Set<string>());

  const act = async (comment: Comment, onUpdate = onCommentUpdated) => {
    if (!isAuthenticated) { onRequireAuth(); return; }
    if (locks.current.has(comment.id)) return;
    locks.current.add(comment.id);
    setPending([...locks.current]);
    try {
      const updated = await postApi.voteComment(comment.id, comment.userVote === 1 ? 0 : 1);
      onUpdate({ ...updated, timestamp: comment.timestamp });
    } catch (err) {
      showToast(err instanceof Error ? err.message : "操作失败", "error");
    } finally {
      locks.current.delete(comment.id);
      setPending([...locks.current]);
    }
  };

  const deleteComment = async () => {
    if (!deleteTarget || !isAuthenticated) throw new Error("请重新登录后再删除评论。");
    const commentId = deleteTarget.id;
    if (locks.current.has(commentId)) throw new Error("评论正在处理中，请稍后再试。");
    locks.current.add(commentId);
    setPending([...locks.current]);
    try {
      await postApi.deleteComment(commentId);
      setNotificationTarget(previous=>previous?.id===commentId ? null : previous);
      floors.setTotal(previous => Math.max(0, previous - 1)); onCountChange(-1);
      if (deleteTarget.parentId) {
        updateFloors(previous => previous.map(floor => floor.id === deleteTarget.parentId ? { ...floor, replyCount: Math.max(0, floor.replyCount - 1) } : floor)
          .filter(floor => floor.root || floor.replyCount > 0));
        deleteCallback.current?.(commentId);
      } else updateFloors(previous => previous.map(floor => floor.id === commentId ? { ...floor, root: null } : floor).filter(floor => floor.root || floor.replyCount > 0));
      showToast("评论已删除", "success");
    } finally {
      locks.current.delete(commentId);
      setPending([...locks.current]);
    }
  };

  const renderItem = (comment: Comment, onReply: () => void, busy: boolean, onDelete?: (id: string) => void, onUpdate?: (comment: Comment) => void) => (
    <div id={`comment-item-${comment.id}`} data-comment-id={comment.id}>
      <div className="flex flex-wrap items-center gap-x-2 gap-y-1">
        <Avatar src={comment.author.avatar} name={comment.author.displayName || comment.author.username} size="sm" />
        <Link to={"/u/" + encodeURIComponent(comment.author.username)} className="text-sm font-bold break-all">
          {comment.author.displayName || comment.author.username}
        </Link>
        {comment.author.accountType === "BOT" ? <span className="text-xs font-semibold text-terracotta dark:text-terracotta-dark">AI</span> : <LevelBadge level={comment.author.communityGrowth?.level} scope="community" />}
        <time className="text-xs text-ink-muted dark:text-night-muted">{formatTimeAgo(comment.timestamp)}</time>
      </div>
      {comment.content && <CommentContent comment={comment} />}
      {lumi.tasks.filter(task => task.commentId === comment.id).map(task => <div key={task.id} role="status" className="mt-2 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-ink-muted dark:text-night-muted" data-lumi-task={task.status}>
        <span>{task.message}</span>
        {task.replyId && <Link className="font-semibold text-terracotta dark:text-terracotta-dark hover:underline" to={`/post/${postId}?comment=${task.replyId}`}>查看 Lumi 回复</Link>}
        {task.retryable && <button type="button" className="font-semibold text-terracotta dark:text-terracotta-dark hover:underline disabled:opacity-50" disabled={!!lumiRetrying} onClick={() => void retryLumi(task.id)}>{lumiRetrying === task.id ? "正在重试…" : "重试 Lumi"}</button>}
      </div>)}
      {!comment.parentId && comment.image && <button type="button" aria-label="查看评论图片" onClick={() => setViewImage(comment.image!)} className="mt-3 block max-w-full rounded-md overflow-hidden">
        <img src={comment.image} alt={`${comment.author.displayName || comment.author.username}的评论图片`} loading="lazy" className="max-h-60 max-w-full sm:max-w-80 object-contain" />
      </button>}
      <div className="post-actions">
        <button className={"post-action" + (comment.userVote === 1 ? " selected" : "")} disabled={pending.includes(comment.id)} aria-pressed={comment.userVote === 1} onClick={() => act(comment, onUpdate)}>
          <ThumbsUp size={17} />{comment.votes}
        </button>
        <button className="post-action" disabled={busy || pending.includes(comment.id)} onClick={onReply}>
          <Reply size={17} />回复
        </button>
        {user?.username === comment.author.username && <button className="post-action ml-auto" aria-label="删除评论" disabled={pending.includes(comment.id)} onClick={() => { deleteCallback.current = onDelete; setDeleteTarget(comment); }}>
          <Trash2 size={17} />
        </button>}
      </div>
    </div>
  );

  return (
    <section id="responses" className="pt-6 hairline-t space-y-5">
      <h2 className="font-extrabold text-xl">评论<span className="text-base font-medium tabular-nums text-ink-muted dark:text-night-muted">（{floors.total}）</span></h2>
      {isAuthenticated ? <CommentComposer postId={postId} onAdded={onCommentAdded} /> : (
        <div className="flex gap-3 items-center justify-between bg-paper-100 dark:bg-night-canvas p-4 rounded-md">
          <p className="text-sm text-ink-muted dark:text-night-muted">登录后，和大家一起聊聊。</p>
          <Button size="sm" onClick={onRequireAuth}>登录</Button>
        </div>
      )}
      {lumi.error && <p role="status" className="text-sm text-ink-muted dark:text-night-muted">Lumi 状态暂时无法更新。<button type="button" className="ml-2 underline text-terracotta dark:text-terracotta-dark" onClick={lumi.refresh}>重试更新</button></p>}
      {visibleFloors.map(floor => <CommentFloor key={floor.id} floor={floor} postId={postId} isAuthenticated={isAuthenticated}
        notificationTarget={notificationTarget && (notificationTarget.parentId || notificationTarget.id)===floor.id ? notificationTarget : null}
        onRequireAuth={onRequireAuth} onAdded={onCommentAdded} onUpdated={onCommentUpdated} renderItem={renderItem} />)}
      {floors.loading && <ContentState title="正在加载评论" loading />}
      {focusError && <p role="status" className="text-sm text-ink-muted dark:text-night-muted">{focusError}</p>}
      {floors.error && <ContentState title="评论加载失败" description={floors.error} onRetry={() => void floors.loadMore()} />}
      {!floors.loading && !floors.error && !visibleFloors.length && <p className="py-6 text-sm text-center text-ink-muted dark:text-night-muted">还没有评论，说说你的看法吧。</p>}
      <div ref={sentinel}>
        {floors.hasNext && !floors.loading && !floors.error && <button type="button" className="post-action mx-auto" onClick={() => void floors.loadMore()}>加载更多评论</button>}
      </div>
      <DeleteConfirmModal
        isOpen={!!deleteTarget}
        onClose={() => setDeleteTarget(null)}
        onConfirm={deleteComment}
        kind="comment"
        preserveReplies={!!deleteTarget && visibleFloors.some(floor => floor.id === deleteTarget.id && floor.replyCount > 0)}
        preview={deleteTarget?.content || (deleteTarget?.image ? "图片评论" : undefined)}
      />
      <Modal isOpen={!!viewImage} onClose={() => setViewImage(null)} title="评论图片" maxWidth="2xl">
        {viewImage && <img src={viewImage} alt="评论图片大图" className="mx-auto max-h-[70vh] max-w-full object-contain" />}
      </Modal>
    </section>
  );
};
