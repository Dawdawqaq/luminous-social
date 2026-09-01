import React, { useState } from 'react';
import { Link } from 'react-router-dom';
import { Post } from '../../types';
import { Avatar } from '../ui/Avatar';
import { formatTimeAgo, estimateReadingTime, formatEchoes } from '../../utils';
import { postApi } from '../../api/client';
import { useAuth } from '../../context/AuthContext';
import { useToast } from '../../context/ToastContext';
import { MessageSquare, Bookmark, Share2, ArrowBigUp } from 'lucide-react';

interface PostCardProps {
  post: Post;
  onVoteChange?: (updatedPost: Post) => void;
  onRequireAuth?: () => void;
}

export const PostCard: React.FC<PostCardProps> = ({ post, onVoteChange, onRequireAuth }) => {
  const { isAuthenticated } = useAuth();
  const { showToast } = useToast();
  const [isVoting, setIsVoting] = useState(false);
  const [isBookmarking, setIsBookmarking] = useState(false);
  const [currentPost, setCurrentPost] = useState<Post>(post);

  const readingTime = estimateReadingTime(currentPost.content);

  const handleVote = async (e: React.MouseEvent) => {
    e.preventDefault();
    e.stopPropagation();
    if (!isAuthenticated) {
      onRequireAuth?.();
      return;
    }
    if (isVoting) return;

    try {
      setIsVoting(true);
      const newVoteValue = currentPost.userVote === 1 ? 0 : 1;
      
      const updated = await postApi.vote(currentPost.id, newVoteValue);
      setCurrentPost(updated);
      onVoteChange?.(updated);
      showToast(newVoteValue === 1 ? '已送出赞同与共鸣' : '已取消赞同', 'info');
    } catch (err: any) {
      showToast(err.message || '操作失败', 'error');
    } finally {
      setIsVoting(false);
    }
  };

  const handleBookmark = async (e: React.MouseEvent) => {
    e.preventDefault();
    e.stopPropagation();
    if (!isAuthenticated) {
      onRequireAuth?.();
      return;
    }
    if (isBookmarking) return;

    try {
      setIsBookmarking(true);
      let updated: Post;

      if (currentPost.bookmarked) {
        await postApi.removeBookmark(currentPost.id);
        updated = { ...currentPost, bookmarked: false };
        showToast('已从收藏中移除', 'info');
      } else {
        updated = await postApi.bookmark(currentPost.id);
        showToast('已加入我的收藏', 'success');
      }
      setCurrentPost(updated);
      onVoteChange?.(updated);
    } catch (err: any) {
      showToast(err.message || '操作失败', 'error');
    } finally {
      setIsBookmarking(false);
    }
  };

  const handleShare = (e: React.MouseEvent) => {
    e.preventDefault();
    e.stopPropagation();
    const url = `${window.location.origin}/post/${currentPost.id}`;
    navigator.clipboard.writeText(url);
    showToast('帖子链接已复制到剪贴板', 'success');
  };

  const postTypeLabel =
    currentPost.type === 'ESSAY'
      ? '深度长文'
      : currentPost.type === 'DISCUSSION'
      ? '主题探讨'
      : currentPost.type === 'FIELD_NOTE'
      ? '实践手记'
      : readingTime >= 4
      ? '长文'
      : '讨论';

  const commSlug = (currentPost.community?.name || 'Craft').replace(/^c\//, '');

  return (
    <article className="group py-5 md:py-6 hairline-b transition-colors">
      <div className="flex flex-col gap-2">
        
        {/* Meta Line: Community · Author · Time · Reading Type */}
        <div className="flex flex-wrap items-center gap-2 text-xs font-sans text-ink-muted dark:text-night-muted">
          <Link
            to={`/s/${commSlug}`}
            className="font-medium text-ink-primary dark:text-night-ink hover:text-terracotta transition-colors bg-paper-200/70 dark:bg-night-surface px-1.5 py-0.5 rounded-[2px]"
          >
            c/{commSlug}
          </Link>
          <span className="text-ink-subtle">·</span>
          
          <Link
            to={`/u/${currentPost.author.username}`}
            className="flex items-center gap-1.5 hover:text-ink-primary dark:hover:text-night-ink"
          >
            <Avatar
              src={currentPost.author.avatar}
              name={currentPost.author.displayName || currentPost.author.username}
              size="xs"
            />
            <span className="font-medium">
              {currentPost.author.displayName || currentPost.author.username}
            </span>
          </Link>

          <span className="text-ink-subtle">·</span>
          <span className="text-ink-subtle">{formatTimeAgo(currentPost.timestamp)}</span>
          
          <span className="text-ink-subtle hidden sm:inline">·</span>
          <span className="text-[11px] text-terracotta dark:text-terracotta-dark hidden sm:inline">
            {postTypeLabel} ({readingTime} 分钟阅读)
          </span>
        </div>

        {/* Post Title & Image Preview */}
        <div className="flex flex-col sm:flex-row gap-4 justify-between items-start pt-0.5">
          <div className="flex-1 space-y-1.5">
            <Link to={`/post/${currentPost.id}`} className="block group-hover:text-terracotta-hover transition-colors">
              <h2 className="font-serif text-lg sm:text-xl font-medium text-ink-primary dark:text-night-ink leading-snug tracking-tight">
                {currentPost.title}
              </h2>
            </Link>
            
            {/* Excerpt */}
            {currentPost.content && (
              <p className="font-serif text-sm text-ink-muted dark:text-night-muted line-clamp-2 leading-relaxed">
                {currentPost.content.replace(/[#*`>]/g, '')}
              </p>
            )}
          </div>

          {/* Image Preview */}
          {currentPost.image && (
            <Link
              to={`/post/${currentPost.id}`}
              className="shrink-0 w-full sm:w-32 h-20 overflow-hidden rounded-[2px] border border-paper-300 dark:border-night-border mt-1 sm:mt-0"
            >
              <img
                src={currentPost.image.startsWith('/') ? currentPost.image : currentPost.image.startsWith('http') ? currentPost.image : `/${currentPost.image}`}
                alt={currentPost.title}
                className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-300"
              />
            </Link>
          )}
        </div>

        {/* Action Bar */}
        <div className="flex items-center gap-5 pt-2 text-xs font-sans text-ink-muted dark:text-night-muted">
          
          {/* Echo / Upvote Button */}
          <button
            onClick={handleVote}
            className={`flex items-center gap-1.5 py-1 px-2 rounded-[2px] transition-colors cursor-pointer border ${
              currentPost.userVote === 1
                ? 'bg-terracotta/10 border-terracotta text-terracotta dark:text-terracotta-dark font-medium'
                : 'border-paper-300 dark:border-night-border hover:bg-paper-200/60 dark:hover:bg-night-surface hover:text-ink-primary dark:hover:text-night-ink'
            }`}
            title="赞同 / Echo"
          >
            <ArrowBigUp className={`w-3.5 h-3.5 ${currentPost.userVote === 1 ? 'fill-current' : ''}`} />
            <span>{formatEchoes(currentPost.votes)} 赞同</span>
          </button>

          {/* Comments Button */}
          <Link
            to={`/post/${currentPost.id}#responses`}
            className="flex items-center gap-1.5 hover:text-ink-primary dark:hover:text-night-ink transition-colors"
          >
            <MessageSquare className="w-3.5 h-3.5" />
            <span>{currentPost.comments} 评论</span>
          </Link>

          {/* Bookmark Button */}
          <button
            onClick={handleBookmark}
            className={`flex items-center gap-1.5 hover:text-ink-primary dark:hover:text-night-ink transition-colors cursor-pointer ${
              currentPost.bookmarked ? 'text-terracotta dark:text-terracotta-dark font-medium' : ''
            }`}
          >
            <Bookmark className={`w-3.5 h-3.5 ${currentPost.bookmarked ? 'fill-current' : ''}`} />
            <span>{currentPost.bookmarked ? '已收藏' : '收藏'}</span>
          </button>

          {/* Share Button */}
          <button
            onClick={handleShare}
            className="flex items-center gap-1 hover:text-ink-primary dark:hover:text-night-ink transition-colors cursor-pointer hidden sm:inline-flex ml-auto"
          >
            <Share2 className="w-3.5 h-3.5" />
            <span>分享</span>
          </button>

        </div>

      </div>
    </article>
  );
};
