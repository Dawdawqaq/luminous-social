import { useEffect, useState } from 'react';
import { useSearchParams, Link } from 'react-router-dom';
import { api } from '../lib/api';
import { Post, Community, User } from '../types';
import { PostCard } from '../components/PostCard';
import { motion, AnimatePresence } from 'motion/react';
import { Search, Sparkles, Users, LayoutGrid, CheckCircle2 } from 'lucide-react';
import { cn } from '../lib/utils';
import { useAuth } from '../auth/AuthContext';

export function SearchView() {
  const [searchParams] = useSearchParams();
  const query = searchParams.get('q') || '';
  const { user } = useAuth();

  const [activeTab, setActiveTab] = useState<'posts' | 'communities' | 'users'>('posts');
  const [posts, setPosts] = useState<Post[]>([]);
  const [communities, setCommunities] = useState<Community[]>([]);
  const [users, setUsers] = useState<User[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!query.trim()) {
      setPosts([]);
      setCommunities([]);
      setUsers([]);
      setLoading(false);
      return;
    }

    let alive = true;
    setLoading(true);
    setError('');

    Promise.all([
      api.posts({ search: query }),
      api.communities(query),
      api.searchUsers(query)
    ])
      .then(([postPage, communityList, userList]) => {
        if (!alive) return;
        setPosts(postPage.items || []);
        setCommunities(communityList || []);
        setUsers(userList || []);
      })
      .catch((err) => {
        if (alive) setError(err.message || '搜索失败');
      })
      .finally(() => {
        if (alive) setLoading(false);
      });

    return () => {
      alive = false;
    };
  }, [query]);

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

  const toggleFollow = async (e: React.MouseEvent, targetUsername: string, isFollowing: boolean) => {
    e.preventDefault();
    e.stopPropagation();
    if (!user) {
      window.dispatchEvent(new CustomEvent('auth:required', {
        detail: { message: `您需要先登录，才能关注用户 @${targetUsername} 哦` }
      }));
      return;
    }
    try {
      if (isFollowing) {
        await api.unfollowUser(targetUsername);
        setUsers(users.map(u => u.username === targetUsername ? { ...u, isFollowing: false, stats: { ...u.stats, followers: Math.max(0, u.stats.followers - 1) } } : u));
      } else {
        await api.followUser(targetUsername);
        setUsers(users.map(u => u.username === targetUsername ? { ...u, isFollowing: true, stats: { ...u.stats, followers: u.stats.followers + 1 } } : u));
      }
    } catch (err) {
      console.error(err);
    }
  };

  return (
    <div className="max-w-[1000px] mx-auto pb-20">
      {/* Search Header Banner */}
      <div className="relative w-full rounded-3xl overflow-hidden bg-gradient-to-r from-primary/10 via-[#EA4335]/5 to-tertiary/10 border border-outline-variant p-8 mb-8 backdrop-blur-md">
        <div className="flex items-center gap-3 mb-2">
          <Sparkles className="text-primary animate-pulse" size={20} />
          <span className="text-xs font-bold text-primary tracking-wider uppercase">搜索结果</span>
        </div>
        <h1 className="text-3xl font-display font-black text-on-surface">
          为您找到关于 <span className="text-primary italic">“{query}”</span> 的结果
        </h1>
        <p className="text-on-surface-variant text-sm mt-2 font-medium">
          共为您检索到 {posts.length} 篇帖子，{communities.length} 个相关社区，{users.length} 名相关用户。
        </p>
      </div>

      {/* Tabs */}
      <div className="flex gap-4 p-1.5 bg-surface-container-high rounded-full w-fit mb-8 shadow-sm">
        <button
          onClick={() => setActiveTab('posts')}
          className={cn(
            "px-6 py-2 rounded-full font-bold text-sm transition-all",
            activeTab === 'posts'
              ? "bg-primary text-on-primary shadow-md hover:scale-[1.02]"
              : "text-on-surface-variant hover:text-on-surface"
          )}
        >
          帖子 ({posts.length})
        </button>
        <button
          onClick={() => setActiveTab('communities')}
          className={cn(
            "px-6 py-2 rounded-full font-bold text-sm transition-all",
            activeTab === 'communities'
              ? "bg-primary text-on-primary shadow-md hover:scale-[1.02]"
              : "text-on-surface-variant hover:text-on-surface"
          )}
        >
          社区 ({communities.length})
        </button>
        <button
          onClick={() => setActiveTab('users')}
          className={cn(
            "px-6 py-2 rounded-full font-bold text-sm transition-all",
            activeTab === 'users'
              ? "bg-primary text-on-primary shadow-md hover:scale-[1.02]"
              : "text-on-surface-variant hover:text-on-surface"
          )}
        >
          用户 ({users.length})
        </button>
      </div>

      {/* Content Area */}
      {loading ? (
        <div className="flex justify-center items-center py-20">
          <div className="w-12 h-12 border-4 border-primary/20 border-t-primary rounded-full animate-spin" />
        </div>
      ) : error ? (
        <div className="card-luminous text-center text-red-600 font-bold py-12">{error}</div>
      ) : (
        <div className="min-h-[400px]">
          <AnimatePresence mode="wait">
            {activeTab === 'posts' ? (
              <motion.div
                key="posts-list"
                initial={{ opacity: 0, y: 15 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -15 }}
                transition={{ duration: 0.25 }}
                className="flex flex-col gap-6"
              >
                {posts.length > 0 ? (
                  posts.map(post => <PostCard key={post.id} post={post} />)
                ) : (
                  <div className="py-20 text-center bg-surface-container-low rounded-[40px] border border-dashed border-outline-variant">
                    <Search size={64} className="mx-auto mb-4 text-on-surface-variant/20 rotate-12" />
                    <h2 className="text-xl font-bold text-on-surface mb-2">未找到相关帖子</h2>
                    <p className="text-on-surface-variant text-sm mt-2">
                      换个搜索词试试，或者在对应的社区发布你的看法吧。
                    </p>
                  </div>
                )}
              </motion.div>
            ) : activeTab === 'communities' ? (
              <motion.div
                key="communities-list"
                initial={{ opacity: 0, y: 15 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -15 }}
                transition={{ duration: 0.25 }}
                className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6"
              >
                {communities.length > 0 ? (
                  communities.map((community, i) => {
                    const slug = community.name.replace(/^c\//, '');
                    return (
                      <motion.div
                        key={community.id}
                        initial={{ opacity: 0, y: 15 }}
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
                            <div className="absolute -top-10 left-5 w-16 h-16 rounded-2xl bg-surface p-1 shadow-lg border border-outline-variant/30">
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
                            </div>
                          </div>
                        </Link>
                      </motion.div>
                    );
                  })
                ) : (
                  <div className="col-span-full py-20 text-center bg-surface-container-low rounded-[40px] border border-dashed border-outline-variant w-full">
                    <LayoutGrid size={64} className="mx-auto mb-4 text-on-surface-variant/20 rotate-12" />
                    <h2 className="text-xl font-bold text-on-surface mb-2">未找到相关社区</h2>
                    <p className="text-on-surface-variant text-sm mt-2">
                      换个搜索词试试，或者在主页中探索我们为您推荐的优质社区。
                    </p>
                  </div>
                )}
              </motion.div>
            ) : (
              <motion.div
                key="users-list"
                initial={{ opacity: 0, y: 15 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -15 }}
                transition={{ duration: 0.25 }}
                className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6"
              >
                {users.length > 0 ? (
                  users.map((item, i) => {
                    const isSelf = user && user.username === item.username;
                    return (
                      <motion.div
                        key={item.id}
                        initial={{ opacity: 0, y: 15 }}
                        animate={{ opacity: 1, y: 0 }}
                        transition={{ delay: i * 0.05 }}
                      >
                        <Link to={`/user/${item.username}`} className="block card-luminous !p-0 overflow-hidden hover:shadow-xl hover:-translate-y-1 transition-all group">
                          {/* Banner background */}
                          <div className="h-20 bg-gradient-to-r from-primary/20 via-secondary/15 to-tertiary/20 relative overflow-hidden">
                            {item.banner && <img src={item.banner} alt="Banner" className="w-full h-full object-cover" />}
                            <div className="absolute inset-0 bg-black/10" />
                          </div>
                          <div className="p-5 relative">
                            {/* Avatar */}
                            <div className="absolute -top-10 left-5 w-16 h-16 rounded-full bg-surface p-1 shadow-lg border border-outline-variant/30">
                              {item.avatar ? (
                                <img src={item.avatar} alt="Avatar" className="w-full h-full rounded-full object-cover animate-fade-in" />
                              ) : (
                                <div className="w-full h-full rounded-full bg-primary/10 text-primary flex items-center justify-center font-bold text-xl">
                                  {item.username.substring(0, 2).toUpperCase()}
                                </div>
                              )}
                            </div>
                            
                            <div className="mt-8">
                              <div className="flex items-start justify-between gap-4">
                                <div className="min-w-0">
                                  <h3 className="font-bold text-lg text-on-surface truncate group-hover:text-primary transition-colors">{item.displayName}</h3>
                                  <p className="text-sm font-medium text-on-surface-variant/70 truncate mb-2">@{item.username}</p>
                                </div>
                                {!isSelf && (
                                  <button
                                    onClick={(e) => toggleFollow(e, item.username, !!item.isFollowing)}
                                    className={cn(
                                      "px-4 py-1.5 rounded-full text-xs font-bold transition-all shrink-0 shadow-sm",
                                      item.isFollowing ? "bg-surface-variant text-on-surface-variant hover:bg-error hover:text-error-container" : "bg-primary text-on-primary hover:brightness-110"
                                    )}
                                  >
                                    {item.isFollowing ? '已关注' : '关注'}
                                  </button>
                                )}
                              </div>
                              
                              <p className="text-sm text-on-surface-variant line-clamp-2 mb-4 h-10 italic leading-relaxed">
                                {item.bio || '这个人很神秘，什么都没有写。'}
                              </p>
                              
                              {/* User stats summary */}
                              <div className="flex items-center justify-between border-t border-outline-variant/30 pt-3 text-xs text-on-surface-variant font-bold">
                                <div><span className="text-on-surface font-black mr-1">{item.stats.posts}</span> 帖子</div>
                                <div><span className="text-on-surface font-black mr-1">{item.stats.followers}</span> 粉丝</div>
                                <div><span className="text-on-surface font-black mr-1">{item.stats.following}</span> 关注</div>
                              </div>
                            </div>
                          </div>
                        </Link>
                      </motion.div>
                    );
                  })
                ) : (
                  <div className="col-span-full py-20 text-center bg-surface-container-low rounded-[40px] border border-dashed border-outline-variant w-full">
                    <Users size={64} className="mx-auto mb-4 text-on-surface-variant/20 rotate-12" />
                    <h2 className="text-xl font-bold text-on-surface mb-2">未找到相关用户</h2>
                    <p className="text-on-surface-variant text-sm mt-2">
                      换个搜索词试试，或者检查一下用户拼写是否正确吧。
                    </p>
                  </div>
                )}
              </motion.div>
            )}
          </AnimatePresence>
        </div>
      )}
    </div>
  );
}
