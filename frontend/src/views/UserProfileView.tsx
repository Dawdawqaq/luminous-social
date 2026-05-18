import { PostCard } from '../components/PostCard';
import { MoreHorizontal, FileText, MessageCircle, Bookmark, Users } from 'lucide-react';
import { useEffect, useState } from 'react';
import { cn } from '../lib/utils';
import { motion, AnimatePresence } from 'motion/react';
import { useParams } from 'react-router-dom';
import { api } from '../lib/api';
import { Post, User } from '../types';
import { useAuth } from '../auth/AuthContext';

export function UserProfileView() {
  const { username } = useParams();
  const { user: currentUser, refreshUser } = useAuth();
  const [profileUser, setProfileUser] = useState<User | null>(null);
  const [tab, setTab] = useState('posts');
  const [posts, setPosts] = useState<Post[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!username) return;
    setLoading(true);
    setError('');
    
    api.userProfile(username)
      .then(u => {
        setProfileUser(u);
        return api.posts({ page: 1, size: 50 }).then(page => {
          setPosts(page.items.filter(post => post.author.username === username));
        });
      })
      .catch((ex) => setError(ex instanceof Error ? ex.message : '加载用户失败'))
      .finally(() => setLoading(false));
  }, [username]);

  if (loading) return <div className="max-w-[720px] mx-auto card-luminous text-center text-on-surface-variant">正在加载用户资料...</div>;
  if (error || !profileUser) return <div className="max-w-[720px] mx-auto card-luminous text-center text-red-600 font-bold">{error || '用户不存在'}</div>;

  const handleFollowToggle = async () => {
    if (!profileUser) return;
    if (!currentUser) {
      window.dispatchEvent(new CustomEvent('auth:required', {
        detail: { message: `您需要先登录，才能关注用户 @${profileUser.username}` }
      }));
      return;
    }
    try {
      const updatedUser = profileUser.isFollowing 
        ? await api.unfollowUser(profileUser.username)
        : await api.followUser(profileUser.username);
      setProfileUser(updatedUser);
      await refreshUser();
    } catch (err) {
      alert(err instanceof Error ? err.message : '操作失败');
    }
  };

  return (
    <div className="max-w-[720px] mx-auto w-full py-4">
      {/* User Profile Header Card */}
      <div className="bg-surface-container-lowest rounded-[2rem] overflow-hidden mb-8 border border-outline-variant shadow-sm">
        {/* Banner */}
        <div className="h-48 w-full bg-primary-container relative">
          <img 
            src={profileUser.banner ?? 'https://images.unsplash.com/photo-1497366754035-f200968a6e72?auto=format&fit=crop&w=1200&q=80'} 
            alt="User Banner" 
            className="w-full h-full object-cover opacity-80"
          />
        </div>

        {/* Avatar & Basic Info */}
        <div className="px-8 pb-8 relative">
          <div className="flex flex-col md:flex-row md:items-end gap-6 -mt-12 mb-4">
            <div className="relative inline-block">
              {profileUser.avatar ? (
                <img 
                  src={profileUser.avatar} 
                  alt={profileUser.username} 
                  className="w-24 h-24 md:w-32 md:h-32 rounded-[2rem] border-4 border-surface-container-lowest shadow-xl bg-surface object-cover"
                />
              ) : (
                <div className="w-24 h-24 md:w-32 md:h-32 rounded-[2rem] border-4 border-surface-container-lowest shadow-xl bg-primary-container text-on-primary-container flex items-center justify-center font-bold text-4xl">
                  {profileUser.username.slice(0, 2).toUpperCase()}
                </div>
              )}
              <div className="absolute bottom-1 right-1 w-6 h-6 bg-secondary rounded-full border-2 border-surface-container-lowest" />
            </div>
            <div className="flex-1 pb-2">
              <h1 className="font-display text-4xl font-bold text-on-surface">{profileUser.displayName}</h1>
              <p className="text-on-surface-variant font-medium">@{profileUser.username} • {profileUser.joinDate}加入</p>
            </div>
            <div className="flex gap-2 pb-2">
              {profileUser.username !== currentUser?.username && (
                <button 
                  onClick={handleFollowToggle}
                  className={cn(
                    "px-8 py-2.5 rounded-full font-bold hover:shadow-lg transition-all active:scale-95",
                    profileUser.isFollowing 
                      ? "bg-surface-variant text-on-surface-variant border border-outline-variant"
                      : "bg-primary text-on-primary"
                  )}
                >
                  {profileUser.isFollowing ? '已关注' : '关注'}
                </button>
              )}
              <button className="p-2.5 border border-outline rounded-full text-on-surface-variant hover:bg-surface-variant/20 transition-all">
                <MoreHorizontal size={20} />
              </button>
            </div>
          </div>

          <div className="mt-6 flex flex-col gap-4">
            <div className="flex items-center gap-2 text-primary font-bold text-sm uppercase">
              <FileText size={16} />
              <span>个人简介</span>
            </div>
            <div className="bg-surface-container-low p-6 rounded-3xl border border-outline-variant/30">
              <p className="text-base font-medium text-on-surface leading-relaxed">
                {profileUser.bio}
              </p>
            </div>
          </div>

          {/* Profile Stats */}
          <div className="flex gap-12 mt-8 py-6 border-t border-outline-variant">
            <div>
              <span className="block font-display text-2xl font-bold text-on-surface">{profileUser.stats.posts}</span>
              <span className="text-xs font-bold text-on-surface-variant uppercase tracking-wider">帖子</span>
            </div>
            <div>
              <span className="block font-display text-2xl font-bold text-on-surface">{profileUser.stats.followers}</span>
              <span className="text-xs font-bold text-on-surface-variant uppercase tracking-wider">粉丝</span>
            </div>
            <div>
              <span className="block font-display text-2xl font-bold text-on-surface">{profileUser.stats.following}</span>
              <span className="text-xs font-bold text-on-surface-variant uppercase tracking-wider">关注中</span>
            </div>
          </div>
        </div>
      </div>

      {/* Tabbed Interface */}
      <div className="flex bg-surface-container-low p-1.5 rounded-3xl mb-8">
        {[
          { id: 'posts', label: 'Ta的帖子', icon: FileText },
        ].map((t) => (
          <button
            key={t.id}
            onClick={() => setTab(t.id)}
            className={cn(
              "flex-1 py-3.5 rounded-2xl font-bold flex items-center justify-center gap-2.5 transition-all text-sm",
              tab === t.id 
                ? "bg-primary text-on-primary shadow-md" 
                : "text-on-surface-variant hover:bg-surface-variant/30"
            )}
          >
            <t.icon size={18} />
            {t.label}
          </button>
        ))}
      </div>

      {/* Content Feed */}
      <div className="flex flex-col gap-6 min-h-[400px]">
        <AnimatePresence mode="wait">
          <motion.div
            key={tab}
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -10 }}
            transition={{ duration: 0.2 }}
          >
            {tab === 'posts' && (
              <div className="flex flex-col gap-6">
                {posts.map(post => (
                  <PostCard key={post.id} post={post} />
                ))}
                {posts.length === 0 && <div className="card-luminous text-center text-on-surface-variant">Ta还没有发布任何帖子。</div>}
              </div>
            )}
          </motion.div>
        </AnimatePresence>
      </div>
    </div>
  );
}
