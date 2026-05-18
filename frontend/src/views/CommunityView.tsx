import { Smartphone, Share2, Gavel, Shield, Mail, Zap, Brain, Users, Settings as SettingsIcon } from 'lucide-react';
import { PostCard } from '../components/PostCard';
import { CommunitySettingsModal } from '../components/CommunitySettingsModal';
import { useEffect, useState } from 'react';
import { cn } from '../lib/utils';
import { motion } from 'motion/react';
import { useParams, Link } from 'react-router-dom';
import { api } from '../lib/api';
import { Community, Post } from '../types';
import { useAuth } from '../auth/AuthContext';

export function CommunityView() {
  const { id = 'pixel8' } = useParams();
  const { user } = useAuth();
  const [tab, setTab] = useState('hot');
  const [community, setCommunity] = useState<Community | null>(null);
  const [posts, setPosts] = useState<Post[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [isSettingsOpen, setIsSettingsOpen] = useState(false);

  useEffect(() => {
    let alive = true;
    setLoading(true);
    setError('');
    Promise.all([
      api.community(id),
      api.posts({ community: id, filter: tab === 'featured' ? 'hot' : tab, page: 1, size: 10 })
    ])
      .then(([communityData, postPage]) => {
        if (!alive) return;
        setCommunity(communityData);
        setPosts(tab === 'featured' ? postPage.items.filter(post => post.votes > 150) : postPage.items);
      })
      .catch((ex) => alive && setError(ex instanceof Error ? ex.message : '加载失败'))
      .finally(() => alive && setLoading(false));
    return () => {
      alive = false;
    };
  }, [id, tab]);

  const toggleJoin = async () => {
    if (!community) return;
    if (!user) {
      window.dispatchEvent(new CustomEvent('auth:required', {
        detail: { message: `您需要先登录，才能加入社区 c/${community.name} 哦` }
      }));
      return;
    }
    const updated = community.joined ? await api.leaveCommunity(community.name) : await api.joinCommunity(community.name);
    setCommunity(updated);
  };

  if (loading && !community) {
    return (
      <div className="flex justify-center items-center min-h-[60vh]">
        <div className="w-12 h-12 border-4 border-primary/20 border-t-primary rounded-full animate-spin" />
      </div>
    );
  }

  if (!loading && !community) {
    return (
      <div className="flex justify-center items-center min-h-[60vh] text-on-surface-variant font-bold text-lg">
        {error || '社区不存在'}
      </div>
    );
  }

  return (
    <motion.div 
      initial={{ opacity: 0, y: 10 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.3 }}
      className="flex justify-center w-full"
    >
      <div className="flex flex-col lg:flex-row gap-8 w-full max-w-[1052px]">
        <div className="flex-1 max-w-[720px] mx-auto lg:mx-0 w-full">
        {/* Banner */}
        <div className="relative w-full rounded-3xl overflow-hidden bg-surface-container-high mb-8 shadow-sm">
          <div className="h-48 w-full bg-gradient-to-br from-primary to-secondary-container relative">
            {community?.banner && (
              <img 
                src={community.banner}
                alt="Community Banner"
                className="w-full h-full object-cover mix-blend-overlay opacity-60"
              />
            )}
          </div>
          
          <div className="px-6 pb-6">
            <div className="flex items-end justify-between -mt-10 relative">
              <div className="flex items-end gap-4">
                <div className="w-24 h-24 rounded-3xl bg-white p-1 shadow-xl shrink-0">
                  <div className="w-full h-full rounded-2xl bg-primary flex items-center justify-center text-white overflow-hidden">
                    {community?.icon ? (
                      <img src={community.icon} alt={community.name} className="w-full h-full object-cover bg-surface" />
                    ) : (
                      community?.name.includes('futurism') ? <Brain size={48} /> :
                      community?.name.includes('pixel8') ? <Smartphone size={48} /> :
                      <Users size={48} />
                    )}
                  </div>
                </div>
                <div className="mb-2">
                  <h1 className="font-display text-4xl font-extrabold text-on-surface leading-tight">
                    {community?.displayName ?? '社区'}
                  </h1>
                  <p className="text-sm font-medium text-on-surface-variant">
                    c/{community?.name ?? id} • {community ? (community.membersCount / 1000).toFixed(0) : 0}k {community?.membersUnit ?? 'Members'}
                  </p>
                </div>
              </div>
              <div className="flex gap-2 mb-2">
                {user?.id === community?.ownerId && (
                  <button 
                    onClick={() => setIsSettingsOpen(true)}
                    className="px-4 py-2.5 bg-surface-variant text-on-surface-variant rounded-full font-bold shadow-sm hover:bg-surface-variant/80 active:scale-95 transition-all flex items-center gap-2"
                  >
                    <SettingsIcon size={18} /> 管理社区
                  </button>
                )}
                <button onClick={toggleJoin} className="px-8 py-2.5 bg-primary text-on-primary rounded-full font-bold shadow-lg hover:scale-[1.02] active:scale-95 transition-all">
                  {community?.joined ? '已加入' : '加入'}
                </button>
                <button className="p-2.5 bg-surface-variant/50 rounded-full text-on-surface-variant hover:bg-surface-variant transition-colors">
                  <Share2 size={20} />
                </button>
              </div>
            </div>
            <p className="mt-6 text-base font-medium text-on-surface max-w-[600px] leading-relaxed">
              {community?.description}
            </p>
          </div>

          <div className="px-6 border-t border-outline-variant pt-6 pb-4">
            <div className="flex bg-surface-container-low p-1.5 rounded-3xl w-full max-w-[400px] shadow-sm border border-outline-variant/30 relative">
              {[
                { id: 'hot', label: '推荐' },
                { id: 'new', label: '最新' },
                { id: 'featured', label: '热门' }
              ].map((t) => {
                const isActive = tab === t.id;
                return (
                  <button
                    key={t.id}
                    onClick={() => setTab(t.id)}
                    className={cn(
                      "flex-1 py-2.5 rounded-2xl font-bold flex items-center justify-center transition-all text-sm relative z-10",
                      isActive ? "text-on-primary" : "text-on-surface-variant hover:text-on-surface"
                    )}
                  >
                    <span className="relative z-20">{t.label}</span>
                    {isActive && (
                      <motion.div
                        layoutId="activeCommunityTab"
                        className="absolute inset-0 bg-primary rounded-2xl shadow-md z-10"
                        transition={{ type: "spring", stiffness: 380, damping: 30 }}
                      />
                    )}
                  </button>
                );
              })}
            </div>
          </div>
        </div>

        <div className="flex flex-col gap-6 min-h-[400px]">
          <motion.div
            key={tab}
            initial={{ opacity: 0, x: 20 }}
            animate={{ opacity: 1, x: 0 }}
            exit={{ opacity: 0, x: -20 }}
            transition={{ duration: 0.3, ease: "easeOut" }}
            className="flex flex-col gap-6"
          >
            {loading && <div className="card-luminous text-center text-on-surface-variant">正在加载社区内容...</div>}
            {error && <div className="card-luminous text-center text-red-600 font-bold">{error}</div>}
            {!loading && !error && posts.map(post => <PostCard key={post.id} post={post} />)}
            
            {tab === 'featured' && !loading && posts.length === 0 && (
              <div className="py-20 text-center bg-surface-container-low rounded-[40px] border border-dashed border-outline-variant">
                <Zap size={64} className="mx-auto mb-4 text-on-surface-variant/20" />
                <h2 className="text-xl font-bold text-on-surface mb-2">暂无精选帖子</h2>
                <p className="text-on-surface-variant mt-2">
                  该社区目前还没有达到精选标准的帖子。
                </p>
              </div>
            )}
          </motion.div>
        </div>
      </div>

      <aside className="hidden xl:flex flex-col gap-6 w-[300px] sticky top-24 h-fit shrink-0">
        <div className="card-luminous border-outline-variant bg-surface-container-low p-6">
          <h3 className="font-bold text-lg mb-6 flex items-center gap-2">
            <Gavel size={20} className="text-primary" /> 吧规
          </h3>
          <ul className="flex flex-col gap-4">
            {(community?.rules?.length ? community.rules : ['禁止发布违法违规内容', '友善交流，互相尊重']).map((rule, idx) => (
              <li key={idx} className="flex gap-3 items-start border-b border-outline-variant/30 pb-4 last:border-0 last:pb-0">
                <span className="text-sm font-bold text-primary">{idx + 1}.</span>
                <p className="text-sm text-on-surface-variant font-medium">{rule}</p>
              </li>
            ))}
          </ul>
        </div>

        <div className="card-luminous border-outline-variant bg-surface-container-low p-6">
          <h3 className="font-bold text-lg mb-6 flex items-center gap-2">
            <Shield size={20} className="text-primary" /> 版主
          </h3>
          <div className="flex flex-col gap-4">
            {(community?.moderators ?? []).map((mod) => (
              <Link 
                key={mod.id} 
                to={user && mod.username === user?.username ? "/settings" : `/user/${mod.username}`}
                className="flex items-center justify-between group cursor-pointer hover:bg-surface-variant/30 p-2 -mx-2 rounded-xl transition-all"
              >
                <div className="flex items-center gap-3">
                  <div className="w-8 h-8 rounded-full bg-primary-container text-on-primary-container flex items-center justify-center font-bold text-xs shrink-0">
                    {mod.initials}
                  </div>
                  <span className="text-sm font-bold group-hover:text-primary transition-colors line-clamp-1">
                    {mod.displayName || mod.username}
                  </span>
                </div>
                <Mail size={18} className="text-on-surface-variant group-hover:text-primary transition-colors shrink-0" />
              </Link>
            ))}
          </div>
          <button className="mt-6 w-full py-2 bg-surface-variant/50 text-primary font-bold rounded-full text-xs hover:bg-surface-variant transition-colors">
            查看所有版主
          </button>
        </div>
      </aside>
      </div>

      <CommunitySettingsModal
        community={community!}
        isOpen={isSettingsOpen && !!community}
        onClose={() => setIsSettingsOpen(false)}
        onUpdate={(updated) => setCommunity(updated)}
      />
    </motion.div>
  );
}
