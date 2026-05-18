import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../lib/api';
import { Community } from '../types';
import { Users, LayoutGrid, Search, CheckCircle2 } from 'lucide-react';
import { motion } from 'motion/react';
import { cn } from '../lib/utils';
import { useAuth } from '../auth/AuthContext';

export function CommunitiesView() {
  const { user } = useAuth();
  const [communities, setCommunities] = useState<Community[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const toggleJoin = async (e: React.MouseEvent, slug: string, isJoined: boolean, communityName: string) => {
    e.preventDefault();
    e.stopPropagation();
    if (!user) {
      window.dispatchEvent(new CustomEvent('auth:required', {
        detail: { message: `您需要先登录，才能关注社区 ${communityName} 哦` }
      }));
      return;
    }
    try {
      if (isJoined) {
        await api.leaveCommunity(slug);
        setCommunities(communities.map(c => c.name.replace(/^c\//, '') === slug ? { ...c, joined: false } : c));
      } else {
        await api.joinCommunity(slug);
        setCommunities(communities.map(c => c.name.replace(/^c\//, '') === slug ? { ...c, joined: true } : c));
      }
    } catch (err) {
      console.error(err);
    }
  };

  useEffect(() => {
    api.communities()
      .then(setCommunities)
      .catch((err) => setError(err.message || '加载社区失败'))
      .finally(() => setLoading(false));
  }, []);

  return (
    <div className="max-w-[1000px] mx-auto pb-20">
      <div className="flex items-center justify-between gap-6 mb-8">
        <div>
          <h1 className="text-3xl font-display font-black text-on-surface mb-2">探索社区</h1>
          <p className="text-on-surface-variant text-sm">发现充满活力的新天地，与志同道合的人一起交流。</p>
        </div>
      </div>

      {loading && <div className="card-luminous text-center text-on-surface-variant py-12">正在加载社区列表...</div>}
      {error && <div className="card-luminous text-center text-red-600 font-bold py-12">{error}</div>}

      {!loading && !error && (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          {communities.map((community, i) => {
            const slug = community.name.replace(/^c\//, '');
            return (
              <motion.div 
                key={community.id}
                initial={{ opacity: 0, y: 20 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ delay: i * 0.05 }}
              >
                <Link to={`/community/${slug}`} className="block card-luminous !p-0 overflow-hidden hover:shadow-xl hover:-translate-y-1 transition-all group">
                  <div className="h-24 bg-surface-container-highest relative overflow-hidden">
                    {community.banner && <img src={community.banner} alt="Banner" className="w-full h-full object-cover opacity-80 group-hover:opacity-100 transition-opacity" />}
                    <div className="absolute inset-0 bg-gradient-to-t from-black/50 to-transparent" />
                    {community.joined && (
                      <div className="absolute top-3 right-3 bg-primary text-on-primary px-3 py-1 rounded-full text-xs font-bold flex items-center gap-1.5 shadow-lg backdrop-blur-md bg-opacity-90">
                        <CheckCircle2 size={14} /> 已关注
                      </div>
                    )}
                  </div>
                  <div className="p-5 relative">
                    <div className="absolute -top-10 left-5 w-16 h-16 rounded-2xl bg-surface p-1 shadow-lg">
                      {community.icon ? (
                        <img src={community.icon} alt="Icon" className="w-full h-full rounded-xl object-cover" />
                      ) : (
                        <div className="w-full h-full rounded-xl bg-primary-container text-on-primary-container flex items-center justify-center">
                          <LayoutGrid size={24} />
                        </div>
                      )}
                    </div>
                    <div className="mt-8">
                      <div className="flex items-start justify-between gap-4">
                        <div>
                          <h3 className="font-bold text-lg text-on-surface group-hover:text-primary transition-colors">{community.displayName || community.name}</h3>
                          <p className="text-sm font-medium text-primary mb-2">{community.name}</p>
                        </div>
                        <button 
                          onClick={(e) => toggleJoin(e, slug, !!community.joined, community.displayName || community.name)}
                          className={cn(
                            "px-4 py-1.5 rounded-full text-xs font-bold transition-all shrink-0",
                            community.joined ? "bg-surface-variant text-on-surface-variant hover:bg-error hover:text-error-container" : "bg-secondary text-on-secondary hover:brightness-110"
                          )}
                        >
                          {community.joined ? '已关注' : '关注'}
                        </button>
                      </div>
                      <p className="text-sm text-on-surface-variant line-clamp-2 mb-4 h-10">
                        {community.description || '一个充满活力的新社区。'}
                      </p>
                      <div className="flex items-center gap-2 text-xs font-bold text-on-surface-variant bg-surface-container-high w-fit px-3 py-1.5 rounded-full">
                        <Users size={14} />
                        {community.membersCount} {community.membersUnit || '成员'}
                      </div>
                    </div>
                  </div>
                </Link>
              </motion.div>
            );
          })}
        </div>
      )}
    </div>
  );
}
