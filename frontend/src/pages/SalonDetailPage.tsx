import React, { useEffect, useRef, useState } from "react";
import { Link, useParams } from "react-router-dom";
import {
  Users,
  Pencil,
  Settings,
  CalendarDays,
  Check,
  ShieldCheck,
} from "lucide-react";
import { Community, Post, CheckInStats } from "../types";
import { communityApi, postApi, checkInApi } from "../api/client";
import { useAuth } from "../context/AuthContext";
import { useToast } from "../context/ToastContext";
import { useRemoteData } from "../hooks/useRemoteData";
import { Avatar } from "../components/ui/Avatar";
import { Button } from "../components/ui/Button";
import { ContentState } from "../components/common/ContentState";
import { PageMascot } from "../components/common/PageMascot";
import { PostCard } from "../components/post/PostCard";
import { EditSalonModal } from "../components/community/EditSalonModal";
import { useCommunityPosts } from "../hooks/useCommunityPosts";
import { PageBackButton } from "../components/common/PageBackButton";
interface Props {
  onRequireAuth: () => void;
  onOpenCreatePost: (community?: string) => void;
}
export const SalonDetailPage: React.FC<Props> = ({
  onRequireAuth,
  onOpenCreatePost,
}) => {
  const { slug = "" } = useParams();
  const { user, isAuthenticated, isLoading: authLoading } = useAuth();
  const { showToast } = useToast();
  const community = useRemoteData<Community | null>(
    () => communityApi.getBySlug(slug),
    [slug, user?.id],
    null,
    !authLoading,
  );
  useEffect(() => {
    let active = true;
    const refreshMembership = () => {
      communityApi.getBySlug(slug).then(updated => { if (active) community.setData(updated); }).catch(() => {});
    };
    window.addEventListener("luminous:communities-changed", refreshMembership);
    return () => { active = false; window.removeEventListener("luminous:communities-changed", refreshMembership); };
  }, [slug, user?.id]);
  const [order, setOrder] = useState("new");
  const posts = useCommunityPosts(slug, order, !authLoading, user?.id);
  const stats = useRemoteData<CheckInStats | null>(
    () => checkInApi.getStats(slug),
    [slug, user?.id, community.data?.joined],
    null,
    isAuthenticated && !!community.data?.joined,
  );
  const [joining, setJoining] = useState(false);
  const [checking, setChecking] = useState(false);
  const [editing, setEditing] = useState(false);
  const busy = useRef({ join: false, check: false });
  const join = async () => {
    if (!isAuthenticated) {
      onRequireAuth();
      return;
    }
    if (!community.data || busy.current.join) return;
    busy.current.join = true;
    setJoining(true);
    try {
      const updated = community.data.joined
        ? await communityApi.leave(slug)
        : await communityApi.join(slug);
      community.setData(updated);
      window.dispatchEvent(new Event("luminous:communities-changed"));
      showToast(updated.joined ? "已加入社区" : "已退出社区", "success");
    } catch (err) {
      showToast(err instanceof Error ? err.message : "操作失败", "error");
    } finally {
      busy.current.join = false;
      setJoining(false);
    }
  };
  const checkIn = async () => {
    if (busy.current.check) return;
    busy.current.check = true;
    setChecking(true);
    try {
      const result = await checkInApi.checkIn(slug);
      stats.setData((previous) => ({
        totalDays: result.totalDays,
        consecutiveDays: result.consecutiveDays,
        checkedToday: true,
        checkedDays: previous?.checkedDays || [],
      }));
      showToast(result.message || "签到成功", "success");
    } catch (err) {
      showToast(err instanceof Error ? err.message : "签到失败", "error");
    } finally {
      busy.current.check = false;
      setChecking(false);
    }
  };
  if (community.loading || authLoading)
    return (
      <div className="space-y-6">
        <PageBackButton fallback="/salons" fallbackLabel="返回社区列表" />
        <div className="panel">
        <ContentState title="正在加载社区" loading />
        </div>
      </div>
    );
  if (community.error || !community.data)
    return (
      <div className="space-y-6">
        <PageBackButton fallback="/salons" fallbackLabel="返回社区列表" />
        <div className="panel">
        <ContentState
          title="社区暂时无法打开"
          description={community.error || "社区不存在或已关闭。"}
          onRetry={community.reload}
        />
        </div>
      </div>
    );
  const c = community.data;
  return (
    <div className="space-y-6">
      <PageBackButton fallback="/salons" fallbackLabel="返回社区列表" />
      <header className="panel overflow-hidden">
        {c.banner && <img src={c.banner} alt="社区封面" className="w-full h-32 sm:h-44 object-cover" />}
        <div className="p-6 sm:p-7">
        <div className="flex flex-col sm:flex-row gap-5 sm:justify-between sm:items-start">
          <div className="flex gap-4 min-w-0">
            <Avatar src={c.icon} name={c.displayName || c.name} size="xl" />
            <div className="min-w-0">
              <h1 className="text-2xl sm:text-3xl font-extrabold break-words">
                {c.displayName || c.name}
              </h1>
              <p className="text-sm text-ink-muted dark:text-night-muted mt-1 flex items-center gap-2">
                <Users size={16} />
                {c.membersCount} 位成员
              </p>
            </div>
          </div>
          <div className="flex gap-3 flex-wrap">
            <Button
              variant={c.joined ? "outline" : "primary"}
              size="sm"
              isLoading={joining}
              onClick={join}
            >
              {c.joined ? "已加入 · 退出" : "加入社区"}
            </Button>
            <Button size="sm" onClick={() => onOpenCreatePost(c.name)}>
              <Pencil size={16} />
              发帖
            </Button>
            {user?.id === c.ownerId && (
              <Button
                size="sm"
                variant="ghost"
                onClick={() => setEditing(true)}
              >
                <Settings size={17} />
                管理
              </Button>
            )}
          </div>
        </div>
        <p className="mt-5 text-sm sm:text-base text-ink-muted dark:text-night-muted leading-relaxed whitespace-pre-wrap">
          {c.description}
        </p>
      </div></header>
      <div className="grid grid-cols-1 xl:grid-cols-[minmax(0,1fr)_280px] gap-6 items-start">
        <section className="panel overflow-hidden">
          <div className="px-5 py-4 hairline-b flex flex-wrap gap-3 justify-between items-center">
            <h2 className="font-extrabold text-lg">社区讨论</h2>
            <div className="flex flex-wrap gap-2">{[["new", "最新"], ["hot", "近七天热门"]].map(([value, label]) => <Button key={value} variant={order === value ? "primary" : "ghost"} size="sm" aria-pressed={order === value} onClick={() => setOrder(value)}>{label}</Button>)}</div>
          </div>
          <div className="px-5">
            {posts.loading ? (
              <ContentState title="正在加载讨论" loading />
            ) : posts.error ? (
              <ContentState
                title="帖子加载失败"
                description={posts.error}
                onRetry={posts.reload}
              />
            ) : posts.data.length ? (
              posts.data.map((p) => (
                <PostCard
                  key={p.id}
                  post={p}
                  onRequireAuth={onRequireAuth}
                  onVoteChange={(updated) =>
                    posts.setData((previous) =>
                      previous.map((item) =>
                        item.id === updated.id ? updated : item,
                      ),
                    )
                  }
                />
              ))
            ) : (
              <ContentState
                title={order === "hot" ? "近七天还没有上榜帖子" : "还没有讨论"}
                description="分享一个想法，和社区里的朋友聊起来。"
              >
                <Button size="sm" onClick={() => onOpenCreatePost(c.name)}>
                  发布第一篇帖子
                </Button>
              </ContentState>
            )}
            {posts.moreError && <p role="alert" className="text-sm py-3">{posts.moreError}</p>}
            {posts.expired ? <div className="py-4 text-center"><Button size="sm" onClick={posts.reload}>刷新列表</Button></div> : posts.hasMore && <div className="py-4 text-center"><Button size="sm" variant="outline" isLoading={posts.loadingMore} onClick={posts.loadMore}>{posts.moreError ? "重试加载" : "加载更多帖子"}</Button></div>}
          </div>
        </section>
        <aside className="space-y-5">
          {c.joined && (
            <section className="panel aside-panel">
              <h2 className="aside-heading">
                <span>
                  <CalendarDays size={22} />
                  社区签到
                </span>
              </h2>
              {stats.loading ? (
                <p className="text-sm text-ink-muted dark:text-night-muted">
                  正在加载签到记录…
                </p>
              ) : stats.error ? (
                <ContentState
                  title="签到记录加载失败"
                  description={stats.error}
                  onRetry={stats.reload}
                />
              ) : (
                <>
                  <p className="text-sm text-ink-muted dark:text-night-muted mb-4">
                    累计签到 <strong>{stats.data?.totalDays ?? 0}</strong>{" "}
                    天，连续 <strong>{stats.data?.consecutiveDays ?? 0}</strong>{" "}
                    天。
                  </p>
                  <Button
                    variant="secondary"
                    className="w-full"
                    isLoading={checking}
                    disabled={stats.data?.checkedToday}
                    onClick={checkIn}
                  >
                    {stats.data?.checkedToday ? (
                      <>
                        <Check size={17} />
                        今日已签到
                      </>
                    ) : (
                      "签到，和大家打个招呼"
                    )}
                  </Button>
                </>
              )}
            </section>
          )}
          <section className="panel aside-panel">
            <h2 className="aside-heading">
              <span>
                <ShieldCheck size={22} />
                社区约定
              </span>
            </h2>
            <ol className="list-decimal pl-5 space-y-3 text-sm text-ink-muted dark:text-night-muted">
              {(c.rules?.length
                ? c.rules
                : [
                    "围绕社区主题交流，分享真实的想法。",
                    "尊重他人，不发布攻击或引战内容。",
                  ]
              ).map((rule, i) => (
                <li key={i}>{rule}</li>
              ))}
            </ol>
            <div className="community-mascot-slot">
              <PageMascot pose="community" />
            </div>
          </section>
        </aside>
      </div>
      {editing && (
        <EditSalonModal
          isOpen={editing}
          onClose={() => setEditing(false)}
          community={c}
          onUpdated={(updated) => {
            community.setData(updated);
            window.dispatchEvent(new Event("luminous:communities-changed"));
          }}
        />
      )}
    </div>
  );
};
