import React, { useState, useEffect } from 'react';
import { Post } from '../types';
import { postApi } from '../api/client';
import { PostCard } from '../components/post/PostCard';
import { Bookmark, Loader2 } from 'lucide-react';

interface BookmarksPageProps {
  onRequireAuth: () => void;
}

export const BookmarksPage: React.FC<BookmarksPageProps> = ({ onRequireAuth }) => {
  const [bookmarks, setBookmarks] = useState<Post[]>([]);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    setIsLoading(true);
    postApi.getBookmarks({ size: 30 })
      .then((res) => {
        setBookmarks(res.items || []);
      })
      .catch(() => {
        setBookmarks([]);
      })
      .finally(() => setIsLoading(false));
  }, []);

  return (
    <div className="space-y-6 md:space-y-8 animate-in fade-in duration-150">
      <div className="space-y-1.5 pb-6 hairline-b">
        <div className="flex items-center gap-2">
          <Bookmark className="w-5 h-5 text-terracotta" />
          <h1 className="font-serif text-3xl font-medium text-ink-primary dark:text-night-ink tracking-tight">
            我的收藏 · Bookmarks
          </h1>
        </div>
        <p className="font-serif italic text-sm text-ink-muted dark:text-night-muted">
          您在社区中保存的所有精彩讨论与长篇文章。
        </p>
      </div>

      {isLoading ? (
        <div className="py-24 text-center space-y-3">
          <Loader2 className="w-8 h-8 animate-spin mx-auto text-ink-subtle" />
          <p className="font-serif italic text-sm text-ink-muted dark:text-night-muted">
            正在载入收藏列表...
          </p>
        </div>
      ) : bookmarks.length === 0 ? (
        <div className="py-20 text-center space-y-3 bg-paper-50 dark:bg-night-surface border border-paper-300 dark:border-night-border rounded-[2px]">
          <p className="font-serif italic text-base text-ink-muted dark:text-night-muted">
            暂无收藏内容。
          </p>
          <p className="text-xs font-sans text-ink-subtle dark:text-night-subtle">
            在浏览帖子时点击 “收藏”，即可将其存放在此处。
          </p>
        </div>
      ) : (
        <div className="divide-y divide-paper-300 dark:divide-night-border">
          {bookmarks.map((post) => (
            <PostCard
              key={post.id}
              post={post}
              onRequireAuth={onRequireAuth}
              onVoteChange={(updated) =>
                setBookmarks((prev) =>
                  updated.bookmarked === false
                    ? prev.filter((p) => p.id !== updated.id)
                    : prev.map((p) => (p.id === updated.id ? updated : p))
                )
              }
            />
          ))}
        </div>
      )}
    </div>
  );
};
