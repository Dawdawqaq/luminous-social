import { PostCard } from '../components/PostCard';
import { Edit2, MoreHorizontal, FileText, MessageCircle, Bookmark, Users, ChevronRight, Share2, Sparkles } from 'lucide-react';
import { useEffect, useState } from 'react';
import { cn } from '../lib/utils';
import { motion, AnimatePresence } from 'motion/react';
import { Link } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { api } from '../lib/api';
import { Community, Post } from '../types';
import { EditProfileModal } from '../components/EditProfileModal';

export function ProfileView() {
  const { user } = useAuth();
  const [tab, setTab] = useState('posts');
  const [posts, setPosts] = useState<Post[]>([]);
  const [communities, setCommunities] = useState<Community[]>([]);
  const [bookmarks, setBookmarks] = useState<Post[]>([]);
  const [isEditing, setIsEditing] = useState(false);

  useEffect(() => {
    if (!user) return;
    api.posts({ page: 1, size: 30 }).then(page => setPosts(page.items.filter(post => post.author.username === user.username))).catch(() => undefined);
    api.communities().then(setCommunities).catch(() => undefined);
    api.bookmarks().then(page => setBookmarks(page.items)).catch(() => undefined);
  }, [user]);

  if (!user) {
    return (
      <motion.div 
        initial={{ opacity: 0, y: 15 }}
        animate={{ opacity: 1, y: 0 }}
        className="max-w-[560px] mx-auto py-16 px-8 text-center bg-gradient-to-br from-surface-container-low via-surface-container-lowest to-surface-container-low border border-outline-variant/60 rounded-[2.5rem] shadow-xl backdrop-blur-lg mt-8"
      >
        <div className="relative w-24 h-24 mx-auto mb-6 bg-primary/10 rounded-full flex items-center justify-center text-primary shadow-inner">
          <motion.div 
            animate={{ rotate: [0, 10, -10, 0] }}
            transition={{ repeat: Infinity, duration: 4, ease: "easeInOut" }}
          >
            <Sparkles size={40} className="text-primary animate-pulse" />
          </motion.div>
          <div className="absolute -top-1 -right-1 w-4 h-4 bg-[#EA4335] rounded-full animate-ping" />
        </div>
        <h2 className="text-2xl font-display font-black text-on-surface mb-3">
          尚未登录个人空间
        </h2>
        <p className="text-on-surface-variant font-medium text-sm leading-relaxed max-w-[380px] mx-auto mb-8">
          登录后即可探索您的专属个人资料，查看您的帖子、所加入的社区以及珍藏的书签，开启精彩的交流之旅。
        </p>
        <div className="flex flex-col sm:flex-row justify-center gap-4 max-w-[320px] mx-auto">
          <button 
            onClick={() => window.dispatchEvent(new CustomEvent('auth:required', {
              detail: { message: '您需要先登录，才能进入您的个人空间哦' }
            }))}
            className="flex-1 px-8 py-3 bg-primary text-on-primary font-bold rounded-full shadow-lg shadow-primary/20 hover:scale-[1.02] active:scale-95 transition-all text-sm"
          >
            立即登录
          </button>
          <button 
            onClick={() => window.dispatchEvent(new CustomEvent('auth:required', {
              detail: { message: '注册一个 Luminous 账号，开启您的个人天地吧' }
            }))}
            className="flex-1 px-8 py-3 bg-surface-variant text-on-surface-variant font-bold rounded-full border border-outline-variant hover:bg-surface-variant/80 active:scale-95 transition-all text-sm"
          >
            注册账号
          </button>
        </div>
      </motion.div>
    );
  }

  return (
    <div className="max-w-[720px] mx-auto w-full py-4">
      {/* User Profile Header Card */}
      <div className="bg-surface-container-lowest rounded-[2rem] overflow-hidden mb-8 border border-outline-variant shadow-sm">
        {/* Banner */}
        <div className="h-48 w-full bg-primary-container relative">
          <img 
            src={user.banner ?? 'https://images.unsplash.com/photo-1497366754035-f200968a6e72?auto=format&fit=crop&w=1200&q=80'} 
            alt="User Banner" 
            className="w-full h-full object-cover opacity-80"
          />
          <button onClick={() => setIsEditing(true)} className="absolute top-4 right-4 bg-surface/40 backdrop-blur-md p-2.5 rounded-full text-on-surface hover:bg-surface/60 transition-colors">
            <Edit2 size={18} />
          </button>
        </div>

        {/* Avatar & Basic Info */}
        <div className="px-8 pb-8 relative">
          <div className="flex flex-col md:flex-row md:items-end gap-6 -mt-12 mb-4">
            <div className="relative inline-block">
              {user.avatar ? (
                <img 
                  src={user.avatar} 
                  alt={user.username} 
                  className="w-24 h-24 md:w-32 md:h-32 rounded-[2rem] border-4 border-surface-container-lowest shadow-xl bg-surface object-cover"
                />
              ) : (
                <div className="w-24 h-24 md:w-32 md:h-32 rounded-[2rem] border-4 border-surface-container-lowest shadow-xl bg-primary-container text-on-primary-container flex items-center justify-center font-bold text-4xl">
                  {user.username.slice(0, 2).toUpperCase()}
                </div>
              )}
              <div className="absolute bottom-1 right-1 w-6 h-6 bg-secondary rounded-full border-2 border-surface-container-lowest" />
            </div>
            <div className="flex-1 pb-2">
              <h1 className="font-display text-4xl font-bold text-on-surface">{user.displayName}</h1>
              <p className="text-on-surface-variant font-medium">@{user.username} • {user.joinDate}加入</p>
            </div>
          </div>

          <div className="mt-6 flex flex-col gap-4">
            <div className="flex items-center gap-2 text-primary font-bold text-sm uppercase">
              <FileText size={16} />
              <span>个人简介</span>
            </div>
            <div className="bg-surface-container-low p-6 rounded-3xl border border-outline-variant/30">
              <p className="text-base font-medium text-on-surface leading-relaxed">
                {user.bio}
              </p>
            </div>
          </div>

          {/* Profile Stats */}
          <div className="flex gap-12 mt-8 py-6 border-t border-outline-variant">
            <div>
              <span className="block font-display text-2xl font-bold text-on-surface">{user.stats.posts}</span>
              <span className="text-xs font-bold text-on-surface-variant uppercase tracking-wider">帖子</span>
            </div>
            <div>
              <span className="block font-display text-2xl font-bold text-on-surface">{user.stats.followers}</span>
              <span className="text-xs font-bold text-on-surface-variant uppercase tracking-wider">粉丝</span>
            </div>
            <div>
              <span className="block font-display text-2xl font-bold text-on-surface">{user.stats.following}</span>
              <span className="text-xs font-bold text-on-surface-variant uppercase tracking-wider">关注中</span>
            </div>
          </div>
        </div>
      </div>

      {/* Tabbed Interface */}
      <div className="flex bg-surface-container-low p-1.5 rounded-3xl mb-8">
        {[
          { id: 'posts', label: '我的帖子', icon: FileText },
          { id: 'communities', label: '我的社区', icon: Users },
          { id: 'comments', label: '评论', icon: MessageCircle },
          { id: 'bookmarks', label: '已收藏', icon: Bookmark },
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
                {posts.length === 0 && <div className="card-luminous text-center text-on-surface-variant">你还没有发布帖子。</div>}
              </div>
            )}

            {tab === 'communities' && (
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                {communities.filter(community => community.joined).map((community) => (
                  <Link 
                    key={community.id}
                    to={`/community/${community.name}`}
                    className="group bg-surface-container-lowest p-6 rounded-[2rem] border border-outline-variant hover:border-primary/50 hover:shadow-lg transition-all flex items-center justify-between"
                  >
                    <div className="flex items-center gap-4">
                      <div className="w-14 h-14 rounded-2xl bg-secondary-container flex items-center justify-center text-on-secondary-container group-hover:scale-110 transition-transform overflow-hidden shrink-0">
                        {community.icon ? (
                          <img src={community.icon} alt={community.name} className="w-full h-full object-cover" />
                        ) : (
                          <Users size={28} />
                        )}
                      </div>
                      <div>
                        <h3 className="font-display font-bold text-lg text-on-surface group-hover:text-primary transition-colors">
                          {community.displayName || `c/${community.name}`}
                        </h3>
                        <p className="text-sm text-on-surface-variant font-medium">
                          {community.membersCount} {community.membersUnit}
                        </p>
                      </div>
                    </div>
                    <div className="w-10 h-10 rounded-full bg-surface-container-high flex items-center justify-center text-on-surface-variant group-hover:bg-primary group-hover:text-on-primary transition-all">
                      <ChevronRight size={20} />
                    </div>
                  </Link>
                ))}
                {communities.filter(community => community.joined).length === 0 && <div className="card-luminous text-center text-on-surface-variant">还没有加入社区。</div>}
              </div>
            )}
            
            {tab === 'comments' && (
              <div className="py-20 text-center bg-surface-container-low rounded-[2rem] border border-dashed border-outline-variant">
                <MessageCircle size={48} className="mx-auto text-on-surface-variant/20" />
              </div>
            )}

            {tab === 'bookmarks' && (
              <div className="flex flex-col gap-6">
                {bookmarks.map(post => <PostCard key={post.id} post={post} />)}
                {bookmarks.length === 0 && (
                  <div className="py-20 text-center bg-surface-container-low rounded-[2rem] border border-dashed border-outline-variant">
                    <Bookmark size={48} className="mx-auto text-on-surface-variant/20" />
                  </div>
                )}
              </div>
            )}
          </motion.div>
        </AnimatePresence>
      </div>

      {isEditing && <EditProfileModal onClose={() => setIsEditing(false)} />}
    </div>
  );
}
