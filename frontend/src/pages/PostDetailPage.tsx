import React, { useState, useEffect } from 'react';
import { useParams, Link, useNavigate } from 'react-router-dom';
import { Post, Comment } from '../types';
import { postApi } from '../api/client';
import { Avatar } from '../components/ui/Avatar';
import { Button } from '../components/ui/Button';
import { MarkdownRenderer } from '../components/ui/MarkdownRenderer';
import { MarginaliaSidebar } from '../components/post/MarginaliaSidebar';
import { CommentThread } from '../components/post/CommentThread';
import { formatTimeAgo, estimateReadingTime, formatEchoes } from '../utils';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';
import { ArrowLeft, Trash2, Bookmark, Share2, Loader2, ArrowBigUp, Eye } from 'lucide-react';

interface PostDetailPageProps {
  onRequireAuth: () => void;
}

export const PostDetailPage: React.FC<PostDetailPageProps> = ({ onRequireAuth }) => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { user, isAuthenticated } = useAuth();
  const { showToast } = useToast();

  const [post, setPost] = useState<Post | null>(null);
  const [comments, setComments] = useState<Comment[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isVoting, setIsVoting] = useState(false);
  const [isBookmarking, setIsBookmarking] = useState(false);
  const [readingProgress, setReadingProgress] = useState(0);

  useEffect(() => {
    if (!id) return;
    setIsLoading(true);
    if (!window.location.hash) {
      setReadingProgress(0);
      window.scrollTo({ top: 0, left: 0, behavior: 'instant' });
    }

    Promise.allSettled([
      postApi.getById(id),
      postApi.getComments(id),
    ]).then(([postRes, commentsRes]) => {
      if (postRes.status === 'fulfilled') {
        setPost(postRes.value);
      }
      if (commentsRes.status === 'fulfilled') {
        setComments(commentsRes.value || []);
      }
      setIsLoading(false);
    });
  }, [id]);

  useEffect(() => {
    const handleScroll = () => {
      const totalHeight = document.documentElement.scrollHeight - window.innerHeight;
      if (totalHeight > 0) {
        setReadingProgress(Math.min(100, Math.max(0, (window.scrollY / totalHeight) * 100)));
      }
    };
    window.addEventListener('scroll', handleScroll);
    return () => window.removeEventListener('scroll', handleScroll);
  }, []);

  const handleVote = async () => {
    if (!isAuthenticated) {
      onRequireAuth();
      return;
    }
    if (!post || isVoting) return;

    try {
      setIsVoting(true);
      const newVoteValue = post.userVote === 1 ? 0 : 1;
      const updated = await postApi.vote(post.id, newVoteValue);
      setPost(updated);
      showToast(newVoteValue === 1 ? '已送出赞同与共鸣' : '已取消赞同', 'info');
    } catch (err: any) {
      showToast(err.message || '操作失败', 'error');
    } finally {
      setIsVoting(false);
    }
  };

  const handleBookmark = async () => {
    if (!isAuthenticated) {
      onRequireAuth();
      return;
    }
    if (!post || isBookmarking) return;

    try {
      setIsBookmarking(true);
      if (post.bookmarked) {
        await postApi.removeBookmark(post.id);
        setPost({ ...post, bookmarked: false });
        showToast('已从收藏中移除', 'info');
      } else {
        const updated = await postApi.bookmark(post.id);
        setPost(updated);
        showToast('已加入我的收藏', 'success');
      }
    } catch (err: any) {
      showToast(err.message || '操作失败', 'error');
    } finally {
      setIsBookmarking(false);
    }
  };

  const handleShare = () => {
    navigator.clipboard.writeText(window.location.href);
    showToast('帖子链接已复制到剪贴板', 'success');
  };

  const handleDelete = async () => {
    if (!post || !confirm('确认删除这篇帖子吗？')) return;
    try {
      await postApi.delete(post.id);
      showToast('帖子已删除', 'info');
      navigate('/');
    } catch (err: any) {
      showToast(err.message || '删除失败', 'error');
    }
  };

  if (isLoading) {
    return (
      <div className="py-32 text-center space-y-3">
        <Loader2 className="w-8 h-8 animate-spin mx-auto text-ink-subtle" />
        <p className="font-serif italic text-sm text-ink-muted dark:text-night-muted">
          正在载入帖子正文...
        </p>
      </div>
    );
  }

  if (!post) {
    return (
      <div className="py-24 text-center space-y-4">
        <h2 className="font-serif text-2xl font-medium text-ink-primary dark:text-night-ink">
          帖子不存在或已被删除
        </h2>
        <Link to="/" className="inline-block">
          <Button variant="outline" size="sm">
            返回社区首页
          </Button>
        </Link>
      </div>
    );
  }

  const isAuthor = user?.username === post.author.username;
  const readingTime = estimateReadingTime(post.content);
  const communitySlug = (post.community?.name || 'Craft').replace(/^c\//, '');

  return (
    <div className="space-y-6 md:space-y-8 animate-in fade-in duration-150">
      <div
        className="fixed top-0 left-0 h-[2px] bg-terracotta dark:bg-terracotta-dark z-50 transition-all duration-75"
        style={{ width: `${readingProgress}%` }}
      />

      <div className="flex items-center justify-between text-xs font-sans text-ink-muted dark:text-night-muted pb-1">
        <div className="flex items-center gap-3">
          <button
            onClick={() => navigate(-1)}
            className="inline-flex items-center gap-1.5 hover:text-ink-primary dark:hover:text-night-ink transition-colors cursor-pointer"
            title="返回上一级页面"
          >
            <ArrowLeft className="w-3.5 h-3.5" />
            <span>返回上一页</span>
          </button>
          <span className="text-ink-subtle">·</span>
          <Link
            to={`/s/${communitySlug}`}
            className="hover:text-ink-primary dark:hover:text-night-ink transition-colors text-terracotta font-medium"
          >
            c/{communitySlug} 社区
          </Link>
        </div>

        <div className="flex items-center gap-3 text-[11px] uppercase tracking-wider text-terracotta font-medium">
          {post.viewCount !== undefined && post.viewCount > 0 && (
            <span className="inline-flex items-center gap-1 text-ink-muted dark:text-night-muted font-mono font-normal">
              <Eye className="w-3.5 h-3.5 text-ink-subtle" />
              {post.viewCount} 次独立访客浏览
            </span>
          )}
          <span>{post.type === 'ESSAY' ? '深度长文' : '社区探讨'} · {readingTime} 分钟阅读</span>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 lg:gap-12 items-start">
        <div className="lg:col-span-8 space-y-6">
          <header className="space-y-4 pb-6 hairline-b">
            <h1 className="font-serif text-2xl sm:text-3xl md:text-4xl font-medium text-ink-primary dark:text-night-ink leading-[1.25] tracking-tight">
              {post.title}
            </h1>

            <div className="flex flex-wrap items-center justify-between gap-4 pt-1 text-xs font-sans text-ink-muted dark:text-night-muted">
              <Link
                to={`/u/${post.author.username}`}
                className="flex items-center gap-2.5 hover:text-ink-primary dark:hover:text-night-ink"
              >
                <Avatar
                  src={post.author.avatar}
                  name={post.author.displayName || post.author.username}
                  size="md"
                />
                <div>
                  <p className="font-medium text-ink-primary dark:text-night-ink">
                    {post.author.displayName || post.author.username}
                  </p>
                  <p className="text-[11px] text-ink-subtle dark:text-night-subtle">
                    发布于 {formatTimeAgo(post.timestamp)}
                  </p>
                </div>
              </Link>

              <div className="flex items-center gap-2">
                {isAuthor && (
                  <button
                    onClick={handleDelete}
                    className="p-1.5 text-ink-subtle hover:text-terracotta transition-colors cursor-pointer"
                    title="删除帖子"
                  >
                    <Trash2 className="w-4 h-4" />
                  </button>
                )}
                <button
                  onClick={handleShare}
                  className="p-1.5 text-ink-muted hover:text-ink-primary dark:text-night-muted dark:hover:text-night-ink transition-colors cursor-pointer"
                  title="分享帖子"
                >
                  <Share2 className="w-4 h-4" />
                </button>
              </div>
            </div>
          </header>

          {post.image && (
            <div className="w-full max-h-[420px] overflow-hidden rounded-[2px] border border-paper-300 dark:border-night-border my-4 shadow-tactile">
              <img
                src={post.image.startsWith('/') ? post.image : post.image.startsWith('http') ? post.image : `/${post.image}`}
                alt={post.title}
                className="w-full h-full object-cover"
              />
            </div>
          )}

          <main className="py-2">
            <MarkdownRenderer content={post.content} />
          </main>

          <div className="flex flex-wrap items-center justify-between gap-4 py-4 hairline-t hairline-b text-xs font-sans text-ink-muted dark:text-night-muted">
            <div className="flex items-center gap-4">
              <button
                onClick={handleVote}
                className={`flex items-center gap-1.5 py-1.5 px-3 rounded-[2px] border border-paper-300 dark:border-night-border transition-colors cursor-pointer ${
                  post.userVote === 1
                    ? 'bg-terracotta/10 border-terracotta text-terracotta dark:text-terracotta-dark font-medium'
                    : 'hover:bg-paper-200/60 dark:hover:bg-night-surface hover:text-ink-primary dark:hover:text-night-ink'
                }`}
              >
                <ArrowBigUp className={`w-4 h-4 ${post.userVote === 1 ? 'fill-current' : ''}`} />
                <span>{formatEchoes(post.votes)} 赞同 (Echoes)</span>
              </button>

              <button
                onClick={handleBookmark}
                className={`flex items-center gap-1.5 py-1.5 px-3 rounded-[2px] border border-paper-300 dark:border-night-border transition-colors cursor-pointer ${
                  post.bookmarked
                    ? 'bg-terracotta/10 border-terracotta text-terracotta dark:text-terracotta-dark font-medium'
                    : 'hover:bg-paper-200/60 dark:hover:bg-night-surface hover:text-ink-primary dark:hover:text-night-ink'
                }`}
              >
                <Bookmark className={`w-3.5 h-3.5 ${post.bookmarked ? 'fill-current' : ''}`} />
                <span>{post.bookmarked ? '已收藏' : '收藏帖子'}</span>
              </button>
            </div>

            <button
              onClick={handleShare}
              className="text-xs hover:text-ink-primary dark:hover:text-night-ink transition-colors cursor-pointer"
            >
              复制分享链接 ↗
            </button>
          </div>

          <CommentThread
            postId={post.id}
            comments={comments}
            onCommentAdded={(newC) => setComments((prev) => [newC, ...prev])}
            onCommentDeleted={(delId) => setComments((prev) => prev.filter((c) => c.id !== delId))}
            onRequireAuth={onRequireAuth}
          />
        </div>

        <div className="lg:col-span-4 hidden lg:block sticky top-24">
          <MarginaliaSidebar
            comments={comments}
            onScrollToDiscussion={() => {
              const el = document.getElementById('responses');
              el?.scrollIntoView({ behavior: 'smooth' });
            }}
          />
        </div>
      </div>
    </div>
  );
};
