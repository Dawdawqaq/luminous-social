import React, { useEffect, useRef, useState } from "react";
import { Link } from "react-router-dom";
import { ThumbsUp, MessageSquare, Bookmark, Share2 } from "lucide-react";
import { Post } from "../../types";
import { Avatar } from "../ui/Avatar";
import { LevelBadge } from "../ui/LevelBadge";
import { postApi } from "../../api/client";
import { useAuth } from "../../context/AuthContext";
import { useToast } from "../../context/ToastContext";
import { useCommunities } from "../../context/CommunityContext";
import { formatTimeAgo, formatEchoes } from "../../utils";
import { CatEmoteText } from "../emotes/CatEmoteText";
interface PostCardProps {
  post: Post;
  onVoteChange?: (post: Post) => void;
  onRequireAuth?: () => void;
}
export const PostCard: React.FC<PostCardProps> = ({
  post,
  onVoteChange,
  onRequireAuth,
}) => {
  const { isAuthenticated } = useAuth();
  const { showToast } = useToast();
  const { communities } = useCommunities();
  const [current, setCurrent] = useState(post);
  const [voting, setVoting] = useState(false);
  const [bookmarking, setBookmarking] = useState(false);
  const votingRef = useRef(false);
  const bookmarkRef = useRef(false);
  useEffect(() => {
    setCurrent(post);
  }, [post]);
  const slug = (current.community?.name || "").replace(/^c\//, "");
  const communityName =
    communities.find((c) => c.name.replace(/^c\//, "") === slug)?.displayName ||
    slug;
  const vote = async () => {
    if (!isAuthenticated) {
      onRequireAuth?.();
      return;
    }
    if (votingRef.current) return;
    votingRef.current = true;
    setVoting(true);
    try {
      const updated = await postApi.vote(
        current.id,
        current.userVote === 1 ? 0 : 1,
      );
      setCurrent(updated);
      onVoteChange?.(updated);
    } catch (err) {
      showToast(
        err instanceof Error ? err.message : "赞同失败，请重试",
        "error",
      );
    } finally {
      votingRef.current = false;
      setVoting(false);
    }
  };
  const bookmark = async () => {
    if (!isAuthenticated) {
      onRequireAuth?.();
      return;
    }
    if (bookmarkRef.current) return;
    bookmarkRef.current = true;
    setBookmarking(true);
    try {
      let updated: Post;
      if (current.bookmarked) {
        await postApi.removeBookmark(current.id);
        updated = { ...current, bookmarked: false };
      } else {
        updated = await postApi.bookmark(current.id);
      }
      setCurrent(updated);
      onVoteChange?.(updated);
      showToast(updated.bookmarked ? "已加入收藏" : "已取消收藏", "success");
    } catch (err) {
      showToast(
        err instanceof Error ? err.message : "收藏失败，请重试",
        "error",
      );
    } finally {
      bookmarkRef.current = false;
      setBookmarking(false);
    }
  };
  const share = async () => {
    try {
      await navigator.clipboard.writeText(
        window.location.origin + "/post/" + current.id,
      );
      showToast("帖子链接已复制", "success");
    } catch {
      showToast("暂时无法复制，请复制地址栏中的帖子链接", "error");
    }
  };
  return (
    <article className="post-row">
      <div className="post-meta">
        <Link className="post-community" to={"/s/" + encodeURIComponent(slug)}>
          {communityName}
        </Link>
        <span>·</span>
        <Link
          to={"/u/" + encodeURIComponent(current.author.username)}
          className="flex items-center gap-1.5"
        >
          <Avatar
            src={current.author.avatar}
            name={current.author.displayName || current.author.username}
            size="xs"
          />
          {current.author.displayName || current.author.username}
          <LevelBadge level={current.author.communityGrowth?.level} scope="community" />
        </Link>
        <span>·</span>
        <time dateTime={current.timestamp}>
          {formatTimeAgo(current.timestamp)}
        </time>
      </div>
      <div className="post-preview">
        <div className="post-copy">
          <h2 className="post-title">
            <Link to={"/post/" + current.id}>{current.title}</Link>
          </h2>
          <p className="post-excerpt">
            <CatEmoteText text={current.content.replace(/[#*`>]/g, "")} size="compact" />
          </p>
        </div>
        {current.image && (
          <Link
            className="post-thumbnail"
            to={"/post/" + current.id}
            tabIndex={-1}
            aria-hidden="true"
          >
            <img
              src={
                current.image.startsWith("/") ||
                current.image.startsWith("http")
                  ? current.image
                  : "/" + current.image
              }
              alt=""
              loading="lazy"
              width="166"
              height="105"
            />
          </Link>
        )}
      </div>
      <div className="post-actions">
        <button
          className={
            "post-action" + (current.userVote === 1 ? " selected" : "")
          }
          onClick={vote}
          disabled={voting}
          aria-pressed={current.userVote === 1}
          aria-label={"赞同 " + current.title}
        >
          <ThumbsUp
            size={18}
            fill={current.userVote === 1 ? "currentColor" : "none"}
          />
          <span>{formatEchoes(current.votes)}</span>
        </button>
        <Link
          className="post-action"
          to={"/post/" + current.id + "#responses"}
          aria-label={"评论 " + current.title}
        >
          <MessageSquare size={18} />
          {current.comments}
        </Link>
        <button
          className={"post-action" + (current.bookmarked ? " selected" : "")}
          onClick={bookmark}
          disabled={bookmarking}
          aria-pressed={!!current.bookmarked}
        >
          <Bookmark
            size={18}
            fill={current.bookmarked ? "currentColor" : "none"}
          />
          {current.bookmarked ? "已收藏" : "收藏"}
        </button>
        <button
          className="post-action post-share ml-auto"
          onClick={share}
          aria-label={"分享 " + current.title}
        >
          <Share2 size={16} />
          <span className="hidden sm:inline">分享</span>
        </button>
      </div>
    </article>
  );
};
