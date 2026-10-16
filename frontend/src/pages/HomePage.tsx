import React, { useCallback, useEffect, useRef, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import {
  Compass,
  Pencil,
  Image,
  Users,
  Heart,
  ShieldCheck,
  MessageCircle,
  ArrowRight,
  RefreshCw,
} from "lucide-react";
import { Post } from "../types";
import { ApiError, feedApi, postApi } from "../api/client";
import { useAuth } from "../context/AuthContext";
import { useFollowActions } from "../context/FollowContext";
import { useCommunities } from "../context/CommunityContext";
import { Avatar } from "../components/ui/Avatar";
import { PostCard } from "../components/post/PostCard";
import { ContentState } from "../components/common/ContentState";
interface HomePageProps {
  onRequireAuth: () => void;
  onOpenCreateSalon: () => void;
  onOpenCreatePost: () => void;
}
interface FeedState {
  posts: Post[];
  page: number;
  hasNext: boolean;
  score?: number;
  offset: number;
  cursor?: string;
}
const emptyFeed: FeedState = { posts: [], page: 1, hasNext: false, offset: 0 };
const welcomeCatImage = "/illustrations/cats/luminous-cat-welcome-v2.png";
export const HomePage: React.FC<HomePageProps> = ({
  onRequireAuth,
  onOpenCreateSalon,
  onOpenCreatePost,
}) => {
  const { user, isAuthenticated, isLoading: authLoading } = useAuth();
  const { revision: followRevision } = useFollowActions();
  const {
    communities,
    isLoading: communitiesLoading,
    error: communitiesError,
    refresh: refreshCommunities,
  } = useCommunities();
  const [params, setParams] = useSearchParams();
  const rawFilter = params.get("filter") || "hot";
  const filter = ["hot", "new", "feed", "top"].includes(rawFilter)
    ? rawFilter
    : "hot";
  const community = params.get("community") || "";
  const period = filter === "top" && params.get("period") === "week" ? "week" : "day";
  const [feed, setFeed] = useState<FeedState>(emptyFeed);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState("");
  const [moreError, setMoreError] = useState("");
  const [rankingExpired, setRankingExpired] = useState(false);
  const [revision, setRevision] = useState(0);
  const generation = useRef(0);
  const morePending = useRef(false);
  useEffect(() => {
    const requestId = ++generation.current;
    setFeed(emptyFeed);
    setError("");
    setMoreError("");
    setRankingExpired(false);
    setLoadingMore(false);
    morePending.current = false;
    if (authLoading) {
      setLoading(true);
      return;
    }
    if (filter === "feed" && !isAuthenticated) {
      setLoading(false);
      return;
    }
    setLoading(true);
    const fetch = async () => {
      try {
        let next: FeedState;
        if (filter === "feed") {
          const res = await feedApi.getFeed({ size: 10 });
          next = {
            posts: res.items || [],
            page: 1,
            hasNext: res.hasMore && res.items.length > 0,
            score: res.minScore,
            offset: res.offset,
          };
        } else if (filter === "new") {
          const res = await postApi.list({
            filter,
            community: community || undefined,
            page: 1,
            size: 10,
          });
          next = {
            posts: res.items || [],
            page: 1,
            hasNext: res.hasNext && res.items.length > 0,
            offset: 0,
          };
        } else {
          const res = await postApi.ranking({ type: filter === "top" ? "hot" : "recommend", period,
            community: community || undefined, size: 10 });
          next = { posts: res.items, page: 1, hasNext: res.hasMore, cursor: res.nextCursor || undefined, offset: 0 };
        }
        if (requestId === generation.current) setFeed(next);
      } catch (err) {
        if (requestId === generation.current)
          setError(err instanceof Error ? err.message : "帖子加载失败");
      } finally {
        if (requestId === generation.current) setLoading(false);
      }
    };
    void fetch();
    return () => {
      ++generation.current;
    };
  }, [filter, period, community, user?.id, isAuthenticated, authLoading, revision, filter === "feed" ? followRevision : 0]);
  const loadMore = useCallback(async () => {
    if (morePending.current || !feed.hasNext) return;
    const requestId = generation.current;
    morePending.current = true;
    setLoadingMore(true);
    setMoreError("");
    try {
      const response =
        filter === "feed"
          ? await feedApi.getFeed({
              size: 10,
              lastScore: feed.score,
              offset: feed.offset,
            })
          : filter === "hot" || filter === "top"
          ? await postApi.ranking({ type: filter === "top" ? "hot" : "recommend", period,
              community: community || undefined, cursor: feed.cursor, size: 10 })
          : await postApi.list({
              filter,
              community: community || undefined,
              page: feed.page + 1,
              size: 10,
            });
      if (requestId !== generation.current) return;
      const items = response.items || [];
      const hasNext =
        "hasMore" in response ? response.hasMore : response.hasNext;
      setFeed((previous) => {
        const seen = new Set(previous.posts.map((p) => p.id));
        const fresh = items.filter((p) => !seen.has(p.id));
        return {
          posts: [...previous.posts, ...fresh],
          page: previous.page + 1,
          hasNext: hasNext && items.length > 0,
          score: "minScore" in response ? response.minScore : previous.score,
          offset: "offset" in response ? response.offset : 0,
          cursor: "nextCursor" in response ? response.nextCursor || undefined : undefined,
        };
      });
    } catch (err) {
      if (requestId === generation.current) {
        setMoreError(err instanceof Error ? err.message : "更多帖子加载失败");
        if (err instanceof ApiError && err.code === "RANKING_SNAPSHOT_EXPIRED") setRankingExpired(true);
      }
    } finally {
      if (requestId === generation.current) {
        morePending.current = false;
        setLoadingMore(false);
      }
    }
  }, [feed, filter, period, community]);
  const joined = communities.filter((c) => c.joined);
  const suggestions = communities.filter((c) => !c.joined).slice(0, 3);
  const changeFilter = (value: string) => {
    const next = new URLSearchParams(params);
    next.set("filter", value);
    setParams(next);
  };
  return (
    <div className="home-grid">
      <div className="home-main">
        <section className="panel welcome-panel" aria-label="欢迎来到 Luminous">
          <div className="welcome-backdrop" aria-hidden="true" />
          <div className="welcome-copy">
            <h1>
              找到同好，
              <br />
              聊点<span>喜欢的。</span>
            </h1>
            <p>分享你的日常、问题和新发现。</p>
            <div className="welcome-actions">
              <button className="ink-button primary" onClick={onOpenCreatePost}>
                <Pencil size={18} />
                分享新发现
              </button>
              <Link to="/salons" className="ink-button">
                <Compass size={18} />
                发现社区
              </Link>
            </div>
          </div>
          <div className="welcome-note" aria-hidden="true">
            在这里
            <br />
            遇见同样热爱的你！
          </div>
          <img
            className="welcome-mascot welcome-cat-body"
            src={welcomeCatImage}
            alt="白猫从框内探头，一只爪子搭住下边框，另一只带双色星星的爪子伸出右边框"
            width="1774"
            height="887"
            fetchPriority="high"
          />
          <span className="welcome-frame-edge" aria-hidden="true" />
          <img
            className="welcome-mascot welcome-cat-paw"
            src={welcomeCatImage}
            alt=""
            aria-hidden="true"
            width="1774"
            height="887"
            fetchPriority="high"
          />
          <img
            className="welcome-mascot welcome-cat-reach"
            src={welcomeCatImage}
            alt=""
            aria-hidden="true"
            width="1774"
            height="887"
            fetchPriority="high"
          />
        </section>
        <div className="panel composer">
          <Avatar
            src={user?.avatar}
            name={user?.displayName || "L"}
            size="md"
          />
          <button className="composer-prompt" onClick={onOpenCreatePost}>
            今天有什么想分享？
          </button>
          <button
            className="composer-image icon-button"
            aria-label="发帖并添加图片"
            onClick={onOpenCreatePost}
          >
            <Image size={22} />
          </button>
          <button
            className="ink-button primary small"
            onClick={onOpenCreatePost}
          >
            发布
          </button>
        </div>
        <section className="panel feed-panel" aria-label="帖子列表">
          <div role="tablist" aria-label="帖子排序" className="feed-tabs">
            {[
              { key: "hot", label: "推荐" },
              { key: "new", label: "最新" },
              { key: "feed", label: "关注" },
              { key: "top", label: "热榜" },
            ].map((tab) => (
              <button
                key={tab.key}
                role="tab"
                aria-selected={filter === tab.key}
                className="feed-tab"
                onClick={() => changeFilter(tab.key)}
              >
                {tab.label}
              </button>
            ))}
          </div>
          {(filter === "hot" || filter === "top") && <div className="flex flex-wrap items-center justify-between gap-3 px-5 py-3 hairline-b">
            {filter === "top" ? <div className="flex flex-wrap items-center gap-3">
              <div role="group" aria-label="热榜时间范围" className="flex gap-1">
                {([['day', '日榜'], ['week', '周榜']] as const).map(([value, label]) => <button
                  key={value} type="button" aria-pressed={period === value}
                  className={`min-h-9 rounded-md px-3 text-sm font-semibold ${period === value ? "bg-terracotta text-white" : "hover:bg-paper-100 dark:hover:bg-night-surfaceHover"}`}
                  onClick={() => { const next = new URLSearchParams(params); next.set('period', value); setParams(next); }}>{label}</button>)}
              </div>
              <p className="text-xs text-ink-muted dark:text-night-muted">{period === "day" ? "近 24 小时发布" : "近 7 天发布"}</p>
            </div> : <p className="text-sm text-ink-muted dark:text-night-muted">近期好内容，也给新帖子一点机会。</p>}
            <button type="button" aria-label={filter === "top" ? "刷新热榜" : "刷新推荐"}
              disabled={loading || loadingMore} onClick={() => setRevision(value => value + 1)}
              className="inline-flex min-h-9 items-center gap-1.5 rounded-md px-2 text-sm text-terracotta hover:bg-paper-100 disabled:opacity-50 dark:text-terracotta-dark dark:hover:bg-night-surfaceHover">
              <RefreshCw size={15} aria-hidden="true" />刷新
            </button>
          </div>}
          {community && (
            <div className="px-5 py-3 text-sm hairline-b flex justify-between gap-3">
              <span className="min-w-0 break-words">
                正在浏览：
                {communities.find((c) => c.name === community)?.displayName ||
                  community}
              </span>
              <button
                className="shrink-0 self-start whitespace-nowrap text-terracotta dark:text-terracotta-dark"
                onClick={() => {
                  const next = new URLSearchParams(params);
                  next.delete("community");
                  setParams(next);
                }}
              >
                查看全部
              </button>
            </div>
          )}
          <div className="feed-body" aria-busy={loading}>
            {loading ? (
              <div role="status" aria-label="正在加载帖子">
                {[0, 1, 2].map((i) => (
                  <div key={i} className="loading-lines">
                    <div className="loading-line w-1/3" />
                    <div className="loading-line w-4/5" />
                    <div className="loading-line w-3/5" />
                  </div>
                ))}
              </div>
            ) : error ? (
              <ContentState
                title="帖子暂时加载失败"
                description={error}
                onRetry={() => setRevision((r) => r + 1)}
              />
            ) : filter === "feed" && !isAuthenticated ? (
              <ContentState
                title="登录后查看关注动态"
                description="关注喜欢的人，他们的新帖子会出现在这里。"
              >
                <button
                  className="ink-button primary small"
                  onClick={onRequireAuth}
                >
                  登录
                </button>
              </ContentState>
            ) : feed.posts.length ? (
              feed.posts.map((post) => (
                <PostCard
                  key={post.id}
                  post={post}
                  onRequireAuth={onRequireAuth}
                  onVoteChange={(updated) =>
                    setFeed((previous) => ({
                      ...previous,
                      posts: previous.posts.map((p) =>
                        p.id === updated.id ? updated : p,
                      ),
                    }))
                  }
                />
              ))
            ) : (
              <ContentState
                title={
                  filter === "feed" ? "关注动态还没有新内容" : filter === "top" ? "暂时没有上榜帖子" : "这里还没有帖子"
                }
                description={
                  filter === "feed"
                    ? "去看看社区，找到想关注的人。"
                    : filter === "top" ? `最近${period === "day" ? "24 小时" : "7 天"}发布的帖子有更多互动后，会出现在这里。`
                    : "分享一个问题或新发现，开启第一段讨论。"
                }
              >
                <button
                  className="ink-button primary small"
                  onClick={
                    filter === "feed"
                      ? () => setParams({ filter: "hot" })
                      : filter === "top" ? () => changeFilter("new")
                      : onOpenCreatePost
                  }
                >
                  {filter === "feed" ? "看看推荐" : filter === "top" ? "看看最新" : "发布第一篇帖子"}
                </button>
              </ContentState>
            )}
            {moreError && (
              <p role="alert" className="py-3 text-sm text-red-600">
                {moreError}
              </p>
            )}
            {rankingExpired && <div className="py-3 text-center"><button className="ink-button small" onClick={() => setRevision(value => value + 1)}>刷新列表</button></div>}
            {feed.hasNext && !loading && !error && !rankingExpired && (
              <div className="py-5 text-center">
                <button
                  className="ink-button small"
                  onClick={loadMore}
                  disabled={loadingMore}
                >
                  {loadingMore
                    ? "正在加载…"
                    : moreError
                      ? "重试加载"
                      : "加载更多"}
                </button>
              </div>
            )}
          </div>
        </section>
      </div>
      <aside className="home-aside" aria-label="社区与个人信息">
        <section className="panel aside-panel">
          <h2 className="aside-heading">
            <span>
              <Users size={23} />
              {isAuthenticated ? "我的社区生活" : "一起聊点喜欢的"}
            </span>
          </h2>
          {isAuthenticated && user ? (
            <>
              <Link
                to={"/u/" + encodeURIComponent(user.username)}
                className="account-name"
              >
                <Avatar
                  src={user.avatar}
                  name={user.displayName || user.username}
                  size="lg"
                />
                {user.displayName || user.username}
              </Link>
              <div className="account-stats">
                <div>
                  <strong>{user.stats?.posts ?? 0}</strong>
                  <span>帖子</span>
                </div>
                <div>
                  <strong>{user.stats?.followers ?? 0}</strong>
                  <span>关注者</span>
                </div>
                <div>
                  <strong>{joined.length}</strong>
                  <span>已加入</span>
                </div>
              </div>
              <Link
                className="ink-button yellow small w-full"
                to={
                  joined.length
                    ? "/s/" +
                      encodeURIComponent(joined[0].name.replace(/^c\//, ""))
                    : "/salons"
                }
              >
                {joined.length ? "去我的社区看看" : "找到喜欢的社区"}
                <ArrowRight size={16} />
              </Link>
            </>
          ) : (
            <>
              <p className="text-sm text-ink-muted dark:text-night-muted mb-5">
                分享你的发现，收藏喜欢的内容，遇见聊得来的朋友。
              </p>
              <button
                className="ink-button yellow small w-full"
                onClick={onRequireAuth}
              >
                登录参与社区
              </button>
            </>
          )}
        </section>
        <section className="panel aside-panel">
          <h2 className="aside-heading">
            <span>
              <Compass size={23} />
              发现更多社区
            </span>
            <Link to="/salons">全部</Link>
          </h2>
          {communitiesLoading ? (
            <p className="text-sm text-ink-muted">正在加载…</p>
          ) : communitiesError ? (
            <button
              className="text-sm text-terracotta"
              onClick={refreshCommunities}
            >
              社区加载失败，重试
            </button>
          ) : suggestions.length ? (
            suggestions.map((c) => (
              <div className="aside-community" key={c.id || c.name}>
                <Avatar src={c.icon} name={c.displayName || c.name} size="md" />
                <Link
                  className="aside-community-text"
                  to={"/s/" + encodeURIComponent(c.name.replace(/^c\//, ""))}
                >
                  <strong>{c.displayName || c.name}</strong>
                  <p>{c.description}</p>
                </Link>
                <Link
                  to={"/s/" + encodeURIComponent(c.name.replace(/^c\//, ""))}
                >
                  看看
                </Link>
              </div>
            ))
          ) : (
            <>
              <p className="text-sm text-ink-muted dark:text-night-muted mb-4">
                {communities.length
                  ? "你已加入这里的所有社区。"
                  : "还没有社区，创建一个感兴趣的主题吧。"}
              </p>
              <button
                className="ink-button small w-full"
                onClick={onOpenCreateSalon}
              >
                创建社区
              </button>
            </>
          )}
        </section>
        <section className="panel aside-panel">
          <h2 className="aside-heading">
            <span>
              <ShieldCheck size={23} />
              聊得开心，互相尊重
            </span>
          </h2>
          <ul className="guidelines">
            <li>
              <MessageCircle size={17} />
              友善交流，允许不同的观点。
            </li>
            <li>
              <Users size={17} />
              不发布攻击、歧视或引战内容。
            </li>
            <li>
              <Heart size={17} />
              分享有价值的内容，一起维护社区氛围。
            </li>
          </ul>
        </section>
      </aside>
    </div>
  );
};
