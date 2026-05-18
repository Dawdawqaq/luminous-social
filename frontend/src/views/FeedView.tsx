import { PostCard } from '../components/PostCard';
import { Palette, Code, SquareSlash, Zap } from 'lucide-react';
import { motion } from 'motion/react';
import { useState, useEffect } from 'react';
import { useLocation, Link } from 'react-router-dom';
import { cn } from '../lib/utils';
import { api } from '../lib/api';
import { Post } from '../types';
import { useAuth } from '../auth/AuthContext';

export function FeedView() {
  const { user } = useAuth();
  const location = useLocation();
  const [filter, setFilter] = useState(() => {
    const path = window.location.pathname;
    if (path === '/trending') return 'trending';
    if (path === '/new') return 'new';
    return 'hot';
  });
  const [posts, setPosts] = useState<Post[]>([]);
  const [recommendedCommunities, setRecommendedCommunities] = useState<Array<{ id: string; name: string; members: string; category: string; icon: string; isJoined: boolean }>>([]);
  const [joinedCommunities, setJoinedCommunities] = useState<Set<string>>(new Set());
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const toggleJoin = async (communityName: string) => {
    if (!user) {
      window.dispatchEvent(new CustomEvent('auth:required', {
        detail: { message: `您需要先登录，才能关注社区 ${communityName} 哦` }
      }));
      return;
    }
    const slug = communityName.replace(/^c\//, '');
    try {
      if (joinedCommunities.has(slug)) {
        await api.leaveCommunity(slug);
        setJoinedCommunities(prev => { const n = new Set(prev); n.delete(slug); return n; });
      } else {
        await api.joinCommunity(slug);
        setJoinedCommunities(prev => { const n = new Set(prev); n.add(slug); return n; });
      }
    } catch (err) {
      console.error(err);
    }
  };

  useEffect(() => {
    if (location.pathname === '/trending') setFilter('trending');
    else if (location.pathname === '/new') setFilter('new');
    else setFilter('hot');
  }, [location.pathname]);

  useEffect(() => {
    let alive = true;
    setLoading(true);
    setError('');
    Promise.all([
      api.posts({ filter: filter === 'trending' ? 'hot' : filter, page: 1, size: 10 }),
      api.recommendedCommunities()
    ])
      .then(([postPage, communities]) => {
        if (!alive) return;
        setPosts(postPage.items);
        setRecommendedCommunities(communities);
        setJoinedCommunities(new Set(communities.filter(c => c.isJoined).map(c => c.name.replace(/^c\//, ''))));
      })
      .catch((ex) => alive && setError(ex instanceof Error ? ex.message : '加载失败'))
      .finally(() => alive && setLoading(false));
    const refresh = () => api.posts({ filter, page: 1, size: 10 }).then(page => setPosts(page.items)).catch(() => undefined);
    window.addEventListener('luminous:post-created', refresh);
    return () => {
      alive = false;
      window.removeEventListener('luminous:post-created', refresh);
    };
  }, [filter]);

  return (
    <div className="flex justify-center w-full">
      <div className="flex flex-col lg:flex-row gap-8 w-full max-w-[1052px]">
        <div className="flex-1 max-w-[720px] mx-auto lg:mx-0 w-full">
          <div className="sticky top-16 z-40 bg-surface/80 backdrop-blur-md pt-4 pb-6 w-full -mt-4 mb-2">
            <div className="flex bg-surface-container-low p-1.5 rounded-3xl w-full shadow-sm border border-outline-variant/30 relative">
              {[
                { id: 'hot', label: '推荐', path: '/' },
                { id: 'trending', label: '热门', path: '/trending' },
                { id: 'new', label: '最新', path: '/new' }
              ].map((f) => {
                const isActive = filter === f.id;
                return (
                  <Link
                    key={f.id}
                    to={f.path}
                    className={cn(
                      "flex-1 py-3 rounded-2xl font-bold flex items-center justify-center transition-all text-sm relative z-10",
                      isActive ? "text-on-primary" : "text-on-surface-variant hover:text-on-surface"
                    )}
                  >
                    <span className="relative z-20">{f.label}</span>
                    {isActive && (
                      <motion.div
                        layoutId="activeFeedTab"
                        className="absolute inset-0 bg-primary rounded-2xl shadow-md z-10"
                        transition={{ type: "spring", stiffness: 380, damping: 30 }}
                      />
                    )}
                  </Link>
                );
              })}
            </div>
          </div>

        <div className="flex flex-col gap-6 w-full min-h-[500px]">
          <motion.div
            key={filter}
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -10 }}
            transition={{ duration: 0.3, ease: "easeOut" }}
            className="flex flex-col gap-6"
          >
            {filter === 'trending' && (
              <article className="bg-surface-container-low rounded-3xl border border-outline-variant p-6 stagger-in">
                <div className="flex items-center gap-2 mb-4">
                  <div className="w-8 h-8 rounded-full bg-secondary-container flex items-center justify-center">
                    <Zap size={18} className="text-on-secondary-container" />
                  </div>
                  <h3 className="font-bold text-secondary">趋势洞察</h3>
                </div>
                <div className="grid grid-cols-2 gap-4">
                  <div className="bg-surface-container-lowest p-6 rounded-2xl col-span-2 md:col-span-1 shadow-sm">
                    <h4 className="font-bold text-on-surface mb-1">Web3 采用</h4>
                    <p className="text-sm text-on-surface-variant">Surge in decentralized social protocols across APAC regions.</p>
                  </div>
                  <div className="bg-surface-container-lowest p-6 rounded-2xl col-span-2 md:col-span-1 border-l-4 border-primary shadow-sm">
                    <h4 className="font-bold text-on-surface mb-1">社区参与</h4>
                    <p className="text-sm text-on-surface-variant">4.2k users currently debating the future of AI in Reddit-style forums.</p>
                  </div>
                </div>
              </article>
            )}

            {loading && <div className="card-luminous text-center text-on-surface-variant">正在加载社区动态...</div>}
            {error && <div className="card-luminous text-center text-red-600 font-bold">{error}</div>}
            {!loading && !error && posts.length === 0 && (
              <div className="card-luminous text-center text-on-surface-variant">暂时没有帖子，发布第一条动态吧。</div>
            )}
            {!loading && !error && posts.map((post) => <PostCard key={post.id} post={post} />)}
          </motion.div>
        </div>
      </div>

      <aside className="hidden xl:flex flex-col gap-6 sticky top-24 h-fit w-[300px] shrink-0">
        <section className="bg-surface-container-low rounded-3xl p-6 border border-outline-variant">
          <h3 className="font-bold text-lg text-on-surface mb-6">推荐社区</h3>
          <div className="flex flex-col gap-6">
            {recommendedCommunities.map((community) => {
              const slug = community.name.replace(/^c\//, '');
              const isJoined = joinedCommunities.has(slug);
              return (
              <div key={community.id} className="flex items-center justify-between">
                <Link to={`/community/${slug}`} className="flex items-center gap-3 hover:opacity-80 transition-opacity">
                  <div className={cn(
                    "w-10 h-10 rounded-xl flex items-center justify-center overflow-hidden",
                    community.category === 'brain' ? "bg-primary-container text-on-primary-container" :
                    community.category === 'phone' ? "bg-secondary-container text-on-secondary-container" :
                    "bg-tertiary-container text-on-tertiary-container"
                  )}>
                    {community.icon ? <img src={community.icon} alt={community.name} className="w-full h-full object-cover" /> :
                     community.category === 'brain' ? <Palette size={20} /> :
                     community.category === 'phone' ? <Code size={20} /> :
                     <SquareSlash size={20} />}
                  </div>
                  <div>
                    <p className="font-bold text-sm leading-tight mb-0.5 hover:underline text-on-surface">{community.name}</p>
                    <p className="text-xs text-on-surface-variant">{community.members}</p>
                  </div>
                </Link>
                <button 
                  onClick={() => toggleJoin(community.name)}
                  className={cn(
                    "px-4 py-1.5 rounded-full text-xs font-bold transition-all",
                    isJoined ? "bg-surface-variant text-on-surface-variant hover:bg-error hover:text-error-container" : "bg-secondary text-on-secondary hover:brightness-110"
                  )}
                >
                  {isJoined ? '已关注' : '关注'}
                </button>
              </div>
            )})}
          </div>
          <Link to="/communities" className="block text-center w-full mt-6 text-primary font-bold text-sm hover:bg-primary/5 py-2 rounded-xl transition-colors">
            查看全部
          </Link>
        </section>


      </aside>
    </div>
    </div>
  );
}
