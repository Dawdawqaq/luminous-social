import { PostCard } from '../components/PostCard';
import { Bookmark, Search } from 'lucide-react';
import { motion } from 'motion/react';
import { useEffect, useState } from 'react';
import { api } from '../lib/api';
import { Post } from '../types';
import { useAuth } from '../auth/AuthContext';

export function BookmarkView() {
  const { user } = useAuth();
  const [bookmarkedPosts, setBookmarkedPosts] = useState<Post[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!user) return;
    setLoading(true);
    setError('');
    api.bookmarks()
      .then(page => setBookmarkedPosts(page.items))
      .catch((ex) => setError(ex instanceof Error ? ex.message : '加载收藏失败'))
      .finally(() => setLoading(false));
  }, [user]);

  return (
    <motion.div 
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      exit={{ opacity: 0, y: -20 }}
      className="max-w-[720px] mx-auto"
    >
      <div className="flex flex-col gap-8">
        <header className="flex items-center justify-between mb-2">
          <div>
            <h1 className="text-3xl font-display font-bold text-on-surface flex items-center gap-3">
              <Bookmark className="text-primary" size={32} />
              我的收藏
            </h1>
            <p className="text-on-surface-variant mt-1">您保存的所有帖子都将显示在这里</p>
          </div>
          <button className="p-3 bg-surface-container-high rounded-full text-on-surface-variant hover:bg-surface-variant transition-colors">
            <Search size={24} />
          </button>
        </header>

        {!user && <div className="card-luminous text-center text-on-surface-variant">登录后可以查看你的收藏夹。</div>}
        {loading && <div className="card-luminous text-center text-on-surface-variant">正在加载收藏...</div>}
        {error && <div className="card-luminous text-center text-red-600 font-bold">{error}</div>}
        {user && !loading && !error && bookmarkedPosts.length > 0 ? (
          <div className="flex flex-col gap-6">
            {bookmarkedPosts.map((post) => (
              <PostCard key={post.id} post={post} />
            ))}
          </div>
        ) : user && !loading && !error ? (
          <div className="py-20 text-center bg-surface-container-low rounded-[40px] border border-dashed border-outline-variant">
            <Bookmark size={64} className="mx-auto mb-4 text-on-surface-variant/20" />
            <h2 className="text-xl font-bold text-on-surface mb-2">空空如也</h2>
            <p className="text-on-surface-variant max-w-[320px] mx-auto">
              您还没有收藏任何帖子。快去探索社区，把感兴趣的内容存下来吧！
            </p>
          </div>
        ) : null}
      </div>
    </motion.div>
  );
}
