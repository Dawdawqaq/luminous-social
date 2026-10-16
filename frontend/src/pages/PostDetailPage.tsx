import React, { useEffect, useRef, useState } from "react";
import { Link, useParams, useNavigate } from "react-router-dom";
import {
  Bookmark,
  Share2,
  ThumbsUp,
  Trash2,
  Eye,
} from "lucide-react";
import { Post } from "../types";
import { postApi } from "../api/client";
import { useAuth } from "../context/AuthContext";
import { useToast } from "../context/ToastContext";
import { useCommunities } from "../context/CommunityContext";
import { useRemoteData } from "../hooks/useRemoteData";
import { Avatar } from "../components/ui/Avatar";
import { LevelBadge } from "../components/ui/LevelBadge";
import { MarkdownRenderer } from "../components/ui/MarkdownRenderer";
import { CommentThread } from "../components/post/CommentThread";
import { ContentState } from "../components/common/ContentState";
import { PageMascot } from "../components/common/PageMascot";
import { DeleteConfirmModal } from "../components/ui/DeleteConfirmModal";
import { PageBackButton } from "../components/common/PageBackButton";
import { formatTimeAgo } from "../utils";
export const PostDetailPage: React.FC<{ onRequireAuth: () => void }> = ({
  onRequireAuth,
}) => {
  const { id = "" } = useParams();
  const navigate = useNavigate();
  const { user, isAuthenticated, isLoading: authLoading } = useAuth();
  const { showToast } = useToast();
  const { communities } = useCommunities();
  const post = useRemoteData<Post | null>(
    () => postApi.getById(id),
    [id, user?.id],
    null,
    !authLoading,
  );
  const [pending, setPending] = useState("");
  const [deleteTarget, setDeleteTarget] = useState<Post | null>(null);
  const busy = useRef(false);
  useEffect(() => {
    setDeleteTarget(null);
  }, [id]);
  const act = async (kind: "vote" | "bookmark") => {
    if (!isAuthenticated) {
      onRequireAuth();
      return;
    }
    if (!post.data || busy.current) return;
    const current = post.data;
    busy.current = true;
    setPending(kind);
    try {
      if (kind === "vote")
        post.setData(
          await postApi.vote(current.id, current.userVote === 1 ? 0 : 1),
        );
      else if (kind === "bookmark") {
        if (current.bookmarked) {
          await postApi.removeBookmark(current.id);
          post.setData({ ...current, bookmarked: false });
        } else post.setData(await postApi.bookmark(current.id));
      }
    } catch (err) {
      showToast(err instanceof Error ? err.message : "操作失败", "error");
    } finally {
      busy.current = false;
      setPending("");
    }
  };
  const deletePost = async () => {
    if (!deleteTarget || !isAuthenticated) throw new Error("请重新登录后再删除帖子。");
    if (busy.current) throw new Error("帖子正在处理中，请稍后再试。");
    busy.current = true;
    setPending("delete");
    try {
      await postApi.delete(deleteTarget.id);
      showToast("帖子已删除", "success");
      navigate("/");
    } finally {
      busy.current = false;
      setPending("");
    }
  };
  const share = async () => {
    try {
      await navigator.clipboard.writeText(location.origin + "/post/" + id);
      showToast("链接已复制", "success");
    } catch {
      showToast("复制失败，请复制地址栏中的链接", "error");
    }
  };
  if (post.loading || authLoading)
    return (
      <div className="max-w-4xl mx-auto space-y-5">
        <PageBackButton />
        <div className="panel">
        <ContentState title="正在加载帖子" loading />
        </div>
      </div>
    );
  if (post.error || !post.data)
    return (
      <div className="max-w-4xl mx-auto space-y-5">
        <PageBackButton />
        <div className="panel">
        <ContentState
          title="帖子暂时无法打开"
          description={post.error || "帖子不存在或已删除。"}
          onRetry={post.reload}
        />
        </div>
      </div>
    );
  const p = post.data;
  const slug = (p.community?.name || "").replace(/^c\//, "");
  const name =
    communities.find((c) => c.name.replace(/^c\//, "") === slug)?.displayName ||
    slug;
  return (
    <div className="max-w-4xl mx-auto space-y-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <PageBackButton fallback={slug ? "/s/" + encodeURIComponent(slug) : "/"} fallbackLabel={slug ? "返回社区" : "返回首页"} />
        {slug && <Link className="text-sm text-ink-muted hover:text-terracotta dark:text-night-muted dark:hover:text-terracotta-dark" to={"/s/" + encodeURIComponent(slug)}>{name}</Link>}
      </div>
      <article className="panel p-5 sm:p-8">
        <header className="pb-5 hairline-b">
          <h1 className="text-2xl sm:text-3xl font-extrabold leading-snug break-words">
            {p.title}
          </h1>
          <div className="flex gap-3 flex-wrap items-center mt-5 text-sm text-ink-muted dark:text-night-muted">
            <Link
              className="flex items-center gap-2"
              to={"/u/" + encodeURIComponent(p.author.username)}
            >
              <Avatar
                src={p.author.avatar}
                name={p.author.displayName || p.author.username}
                size="sm"
              />
              {p.author.displayName || p.author.username}
              <LevelBadge level={p.author.communityGrowth?.level} scope="community" />
            </Link>
            <time dateTime={p.timestamp}>{formatTimeAgo(p.timestamp)}</time>
            {p.viewCount !== undefined && (
              <span className="flex items-center gap-1">
                <Eye size={16} />
                {p.viewCount}
              </span>
            )}
          </div>
        </header>
        {p.image && (
          <img
            className="w-full max-h-[480px] object-contain rounded-md my-6 bg-paper-100 dark:bg-night-canvas"
            src={
              p.image.startsWith("/") || p.image.startsWith("http")
                ? p.image
                : "/" + p.image
            }
            alt={p.title}
          />
        )}
        <div className="py-4">
          <MarkdownRenderer content={p.content} />
        </div>
        <div className="reading-mascot-slot">
          <PageMascot pose="reading" />
        </div>
        <div className="post-actions py-5 hairline-t">
          <button
            className={"post-action" + (p.userVote === 1 ? " selected" : "")}
            aria-pressed={p.userVote === 1}
            disabled={!!pending}
            onClick={() => act("vote")}
          >
            <ThumbsUp size={20} />
            {p.votes} 赞同
          </button>
          <button
            className={"post-action" + (p.bookmarked ? " selected" : "")}
            aria-pressed={!!p.bookmarked}
            disabled={!!pending}
            onClick={() => act("bookmark")}
          >
            <Bookmark size={20} />
            {p.bookmarked ? "已收藏" : "收藏"}
          </button>
          <button className="post-action" onClick={share}>
            <Share2 size={18} />
            分享
          </button>
          {user?.username === p.author.username && (
            <button
              className="post-action ml-auto"
              disabled={!!pending}
              onClick={() => setDeleteTarget(p)}
            >
              <Trash2 size={18} />
              删除
            </button>
          )}
        </div>
        <CommentThread
          key={String(p.id) + ":" + (user?.id || "guest")}
          postId={p.id}
          onCountChange={delta => post.setData(previous => previous ? { ...previous, comments: Math.max(0, previous.comments + delta) } : previous)}
          onRequireAuth={onRequireAuth}
        />
      </article>
      <DeleteConfirmModal
        isOpen={!!deleteTarget}
        onClose={() => setDeleteTarget(null)}
        onConfirm={deletePost}
        kind="post"
        preview={deleteTarget?.title}
      />
    </div>
  );
};
