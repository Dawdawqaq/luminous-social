import React, { useState, useEffect, useCallback } from 'react';
import { useSearchParams, Link } from 'react-router-dom';
import { Post, Community } from '../types';
import { postApi, communityApi, feedApi } from '../api/client';
import { PostCard } from '../components/post/PostCard';
import { FeaturedHero } from '../components/post/FeaturedHero';
import { Button } from '../components/ui/Button';
import { Avatar } from '../components/ui/Avatar';
import { useAuth } from '../context/AuthContext';
import { Flame, Clock, Sparkles, Plus, ChevronRight, PenTool, Image, Loader2, Users } from 'lucide-react';

interface HomePageProps {
  onRequireAuth: () => void;
  onOpenCreateSalon: () => void;
  onOpenCreatePost: () => void;
}

interface CachedFeed {
  posts: Post[];
  salons: Community[];
  featuredPost: Post | null;
  page: number;
  hasNext: boolean;
  filter: string;
  community: string;
}

let feedCache: CachedFeed | null = null;

export const HomePage: React.FC<HomePageProps> = ({
  onRequireAuth,
  onOpenCreateSalon,
  onOpenCreatePost,
}) => {
  const { user, isAuthenticated } = useAuth();
  const [searchParams, setSearchParams] = useSearchParams();
  const filter = searchParams.get('filter') || 'hot';
  const selectedCommunity = searchParams.get('community') || '';

  const hasMatchingCache =
    feedCache &&
    feedCache.filter === filter &&
    feedCache.community === selectedCommunity;

  const [posts, setPosts] = useState<Post[]>(() => hasMatchingCache ? feedCache!.posts : []);
  const [salons, setSalons] = useState<Community[]>(() => hasMatchingCache ? feedCache!.salons : []);
  const [featuredPost, setFeaturedPost] = useState<Post | null>(() => hasMatchingCache ? feedCache!.featuredPost : null);
  const [isLoading, setIsLoading] = useState(() => !hasMatchingCache);
  const [page, setPage] = useState(() => hasMatchingCache ? feedCache!.page : 1);
  const [hasNext, setHasNext] = useState(() => hasMatchingCache ? feedCache!.hasNext : false);
  const [isLoadingMore, setIsLoadingMore] = useState(false);

  const [feedScore, setFeedScore] = useState<number | undefined>(undefined);
  const [feedOffset, setFeedOffset] = useState(0);

  const fetchInitialData = useCallback(async () => {
    try {
      if (!feedCache || feedCache.filter !== filter || feedCache.community !== selectedCommunity) {
        setIsLoading(true);
      }

      if (filter === 'feed') {
        if (!isAuthenticated) {
          setPosts([]);
          setIsLoading(false);
          return;
        }
        const [feedRes, salonsRes] = await Promise.allSettled([
          feedApi.getFeed({ size: 10 }),
          communityApi.list(),
        ]);

        let loadedPosts: Post[] = [];
        let loadedSalons: Community[] = [];
        let nextHasNext = false;

        if (feedRes.status === 'fulfilled') {
          loadedPosts = feedRes.value.items || [];
          nextHasNext = feedRes.value.hasMore;
          setFeedScore(feedRes.value.minScore);
          setFeedOffset(feedRes.value.offset);
        }

        if (salonsRes.status === 'fulfilled') {
          loadedSalons = salonsRes.value || [];
        }

        setPosts(loadedPosts);
        setSalons(loadedSalons);
        setFeaturedPost(null);
        setHasNext(nextHasNext);
        setPage(1);
        return;
      }

      const [postsRes, salonsRes] = await Promise.allSettled([
        postApi.list({ filter, community: selectedCommunity || undefined, page: 1, size: 10 }),
        communityApi.list(),
      ]);

      let loadedPosts: Post[] = [];
      let loadedSalons: Community[] = [];
      let nextHasNext = false;
      let loadedFeatured: Post | null = null;

      if (postsRes.status === 'fulfilled') {
        loadedPosts = postsRes.value.items || [];
        nextHasNext = postsRes.value.hasNext;
        if (loadedPosts.length > 0 && !selectedCommunity && filter === 'hot') {
          loadedFeatured = loadedPosts[0];
        }
      }

      if (salonsRes.status === 'fulfilled') {
        loadedSalons = salonsRes.value || [];
      }

      setPosts(loadedPosts);
      setSalons(loadedSalons);
      setFeaturedPost(loadedFeatured);
      setHasNext(nextHasNext);
      setPage(1);

      feedCache = {
        posts: loadedPosts,
        salons: loadedSalons,
        featuredPost: loadedFeatured,
        page: 1,
        hasNext: nextHasNext,
        filter,
        community: selectedCommunity,
      };
    } catch {
      if (!feedCache) {
        setPosts([]);
        setSalons([]);
        setFeaturedPost(null);
      }
    } finally {
      setIsLoading(false);
    }
  }, [filter, selectedCommunity, isAuthenticated]);

  useEffect(() => {
    fetchInitialData();
  }, [fetchInitialData]);

  const handleLoadMore = async () => {
    if (isLoadingMore || !hasNext) return;
    try {
      setIsLoadingMore(true);
      if (filter === 'feed') {
        const res = await feedApi.getFeed({
          lastScore: feedScore,
          offset: feedOffset,
          size: 10,
        });
        if (res.items && res.items.length > 0) {
          setPosts(prev => [...prev, ...res.items]);
          setHasNext(res.hasMore);
          setFeedScore(res.minScore);
          setFeedOffset(res.offset);
        }
        return;
      }

      const nextPage = page + 1;
      const res = await postApi.list({
        filter,
        community: selectedCommunity || undefined,
        page: nextPage,
        size: 10,
      });

      if (res.items && res.items.length > 0) {
        const merged = [...posts, ...res.items];
        setPosts(merged);
        setHasNext(res.hasNext);
        setPage(nextPage);
        if (feedCache) {
          feedCache.posts = merged;
          feedCache.page = nextPage;
          feedCache.hasNext = res.hasNext;
        }
      }
    } catch {
    } finally {
      setIsLoadingMore(false);
    }
  };

  const handleFilterChange = (newFilter: string) => {
    if (newFilter === 'feed' && !isAuthenticated) {
      onRequireAuth();
      return;
    }
    const params = new URLSearchParams(searchParams);
    params.set('filter', newFilter);
    setSearchParams(params);
  };

  const filters = [
    { key: 'hot', label: '热门讨论', icon: Flame },
    { key: 'new', label: '最新发布', icon: Clock },
    { key: 'feed', label: '关注动态', icon: Users },
    { key: 'top', label: '深度精华', icon: Sparkles },
  ];

  return (
    <div className="space-y-6 md:space-y-8">
      {featuredPost && <FeaturedHero post={featuredPost} />}

      {salons.length > 0 && (
        <div className="flex items-center gap-2 overflow-x-auto pb-1 scrollbar-none text-xs font-sans">
          <span className="text-[11px] uppercase tracking-wider text-ink-subtle dark:text-night-subtle shrink-0">
            活跃社区:
          </span>
          <Link
            to="/"
            className={`px-3 py-1.5 rounded-[2px] transition-colors shrink-0 ${
              !selectedCommunity
                ? 'bg-ink-primary text-paper-50 dark:bg-night-ink dark:text-night-canvas font-medium'
                : 'bg-paper-200/70 dark:bg-night-surface text-ink-muted hover:text-ink-primary dark:text-night-muted'
            }`}
          >
            全部讨论
          </Link>
          {salons.map((s) => (
            <Link
              key={s.id || s.name}
              to={`/?community=${s.name}`}
              className={`px-3 py-1.5 rounded-[2px] border border-paper-300 dark:border-night-border transition-colors shrink-0 ${
                selectedCommunity.toLowerCase() === s.name.toLowerCase()
                  ? 'bg-ink-primary text-paper-50 dark:bg-night-ink dark:text-night-canvas font-medium'
                  : 'bg-paper-50 dark:bg-night-surface hover:bg-paper-200/60 dark:hover:bg-night-surfaceHover text-ink-muted dark:text-night-muted'
              }`}
            >
              c/{s.name}
            </Link>
          ))}
          <Link
            to="/salons"
            className="px-2.5 py-1.5 text-terracotta dark:text-terracotta-dark hover:underline shrink-0 flex items-center gap-1 font-medium"
          >
            <span>更多社区</span>
            <ChevronRight className="w-3.5 h-3.5" />
          </Link>
        </div>
      )}

      <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 lg:gap-12 items-start">
        <section className="lg:col-span-8 space-y-4">
          <div
            onClick={() => {
              if (!isAuthenticated) onRequireAuth();
              else onOpenCreatePost();
            }}
            className="p-3.5 sm:p-4 bg-paper-50 dark:bg-night-surface border border-paper-300 dark:border-night-border rounded-[2px] shadow-tactile flex items-center gap-3 cursor-pointer hover:border-ink-primary/40 dark:hover:border-night-ink/40 transition-colors"
          >
            <Avatar src={user?.avatar} name={user?.displayName || user?.username || 'G'} size="sm" />
            <div className="flex-1 text-xs text-ink-subtle dark:text-night-subtle font-sans">
              有什么新的思考、问题或发现？点击发帖...
            </div>
            <div className="flex items-center gap-3 text-xs font-sans text-ink-muted dark:text-night-muted">
              <span className="hidden sm:inline-flex items-center gap-1 hover:text-ink-primary">
                <PenTool className="w-3.5 h-3.5 text-terracotta" /> 发长文
              </span>
              <span className="hidden sm:inline-flex items-center gap-1 hover:text-ink-primary">
                <Image className="w-3.5 h-3.5 text-terracotta" /> 带图讨论
              </span>
            </div>
          </div>

          <div className="flex items-center justify-between py-2 hairline-b">
            <div className="flex items-center gap-4">
              {filters.map((f) => {
                const Icon = f.icon;
                const isActive = filter === f.key;
                return (
                  <button
                    key={f.key}
                    onClick={() => handleFilterChange(f.key)}
                    className={`flex items-center gap-1.5 text-xs font-sans py-1 transition-colors cursor-pointer ${
                      isActive
                        ? 'text-ink-primary dark:text-night-ink font-semibold border-b border-terracotta'
                        : 'text-ink-muted hover:text-ink-primary dark:text-night-muted dark:hover:text-night-ink'
                    }`}
                  >
                    <Icon className="w-3.5 h-3.5" />
                    <span>{f.label}</span>
                  </button>
                );
              })}
            </div>

            <span className="text-[11px] text-ink-subtle dark:text-night-subtle font-sans">
              共 {posts.length} 条讨论
            </span>
          </div>

          {filter !== 'feed' && <p className="mb-3 text-xs text-ink-subtle dark:text-night-subtle">
            当前筛选最多展示 500 篇，可按社区或搜索缩小范围。
          </p>}
          {isLoading ? (
            <div className="py-24 text-center space-y-3">
              <Loader2 className="w-6 h-6 animate-spin mx-auto text-ink-subtle" />
              <p className="font-serif italic text-sm text-ink-muted dark:text-night-muted">
                正在载入社区讨论...
              </p>
            </div>
          ) : posts.length === 0 ? (
            <div className="py-20 text-center space-y-4 bg-paper-50/50 dark:bg-night-surface/20 border border-paper-300/60 dark:border-night-border/60 rounded-[2px]">
              <p className="font-serif italic text-base text-ink-muted dark:text-night-muted">
                当前筛选下暂无帖子。
              </p>
              <Button size="sm" onClick={onOpenCreatePost}>
                发布第一条帖子
              </Button>
            </div>
          ) : (
            <div className="divide-y divide-paper-300 dark:divide-night-border">
              {posts.map((post) => (
                <PostCard
                  key={post.id}
                  post={post}
                  onRequireAuth={onRequireAuth}
                  onVoteChange={(updated) => {
                    setPosts((prev) =>
                      prev.map((p) => (p.id === updated.id ? updated : p))
                    );
                  }}
                />
              ))}
            </div>
          )}

          {hasNext && !isLoading && (
            <div className="pt-6 text-center">
              <Button
                variant="outline"
                size="sm"
                onClick={handleLoadMore}
                isLoading={isLoadingMore}
              >
                加载更多帖子
              </Button>
            </div>
          )}
        </section>

        <aside className="lg:col-span-4 space-y-6 hidden lg:block">
          <div className="p-5 bg-paper-50 dark:bg-night-surface border border-paper-300 dark:border-night-border rounded-[2px] shadow-tactile space-y-3">
            <h3 className="font-serif text-base font-medium text-ink-primary dark:text-night-ink">
              关于 Luminous 社区
            </h3>
            <p className="font-serif italic text-xs text-ink-muted dark:text-night-muted leading-relaxed">
              这里是一处安静交流思想、作品与观点的现代数字社区。提倡认真推敲的表达，拒绝无效的灌水与多巴胺操纵。
            </p>
            <div className="pt-2 hairline-t flex items-center justify-between text-xs font-sans">
              <button
                onClick={onOpenCreateSalon}
                className="text-terracotta dark:text-terracotta-dark hover:underline font-medium cursor-pointer"
              >
                + 申请创建新社区
              </button>
            </div>
          </div>

          {salons.length > 0 && (
            <div className="space-y-3">
              <div className="flex items-center justify-between text-xs font-sans text-ink-muted dark:text-night-muted pb-1 hairline-b">
                <span className="font-semibold uppercase tracking-wider text-[11px]">
                  活跃社区
                </span>
                <Link to="/salons" className="hover:text-ink-primary dark:hover:text-night-ink">
                  查看全部 ↗
                </Link>
              </div>

              <div className="space-y-2">
                {salons.slice(0, 5).map((s) => (
                  <Link
                    key={s.id || s.name}
                    to={`/s/${s.name}`}
                    className="p-3 bg-paper-50 dark:bg-night-surface border border-paper-300 dark:border-night-border rounded-[2px] hover:border-ink-primary/40 dark:hover:border-night-ink/40 transition-colors flex items-center justify-between group"
                  >
                    <div className="flex items-center gap-2.5 truncate">
                      <Avatar src={s.icon} name={s.displayName || s.name} size="sm" />
                      <div className="truncate">
                        <p className="font-serif text-xs font-medium text-ink-primary dark:text-night-ink group-hover:text-terracotta transition-colors">
                          c/{s.name}
                        </p>
                        <p className="text-[11px] text-ink-subtle dark:text-night-subtle truncate">
                          {s.membersCount} 位成员
                        </p>
                      </div>
                    </div>
                    <ChevronRight className="w-3.5 h-3.5 text-ink-subtle shrink-0" />
                  </Link>
                ))}
              </div>
            </div>
          )}

          <div className="p-4 bg-paper-200/50 dark:bg-night-canvas/50 border border-paper-300/60 dark:border-night-border/60 rounded-[2px] text-xs font-sans text-ink-muted dark:text-night-muted space-y-2">
            <h4 className="font-semibold text-ink-primary dark:text-night-ink uppercase text-[11px]">
              社区交流准则
            </h4>
            <ul className="space-y-1 text-[11px] text-ink-subtle dark:text-night-subtle">
              <li>1. 善意包容，基于事实与论点交流</li>
              <li>2. 鼓励长篇手记与成体系的见解</li>
              <li>3. 尊重每一位创作者的知识产权</li>
            </ul>
          </div>
        </aside>
      </div>
    </div>
  );
};
