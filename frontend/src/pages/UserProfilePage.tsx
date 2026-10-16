import React, { useEffect, useState } from "react";
import { Link, useParams, useSearchParams } from "react-router-dom";
import { Camera, Settings, UserPlus, UserCheck } from "lucide-react";
import { User, Post } from "../types";
import { userApi, postApi } from "../api/client";
import { useAuth } from "../context/AuthContext";
import { useFollowActions } from "../context/FollowContext";
import { useToast } from "../context/ToastContext";
import { useRemoteData } from "../hooks/useRemoteData";
import { Avatar } from "../components/ui/Avatar";
import { LevelBadge } from "../components/ui/LevelBadge";
import { LevelGuideModal } from "../components/ui/LevelGuideModal";
import { Button } from "../components/ui/Button";
import { ContentState } from "../components/common/ContentState";
import { PageMascot } from "../components/common/PageMascot";
import { PostCard } from "../components/post/PostCard";
import { EditProfileModal } from "../components/auth/EditProfileModal";
import { AvatarPickerModal } from "../components/auth/AvatarPickerModal";
import { FollowUserList } from "../components/auth/FollowUserList";
import { PageBackButton } from "../components/common/PageBackButton";
export const UserProfilePage: React.FC<{ onRequireAuth: () => void }> = ({
  onRequireAuth,
}) => {
  const { username = "" } = useParams();
  const [searchParams] = useSearchParams();
  const requestedTab = searchParams.get("tab");
  const activeTab = requestedTab === "followers" || requestedTab === "following" ? requestedTab : "posts";
  const { user, isAuthenticated, isLoading: authLoading } = useAuth();
  const follows = useFollowActions();
  const { showToast } = useToast();
  const profile = useRemoteData<User | null>(
    () => userApi.getProfile(username),
    [username, user?.id],
    null,
    !authLoading,
  );
  const posts = useRemoteData<Post[]>(
    async () =>
      (await postApi.list({ author: username, filter: "new", size: 20 })).items,
    [username, user?.id],
    [],
    !authLoading,
  );
  const [editing, setEditing] = useState(false);
  const [editingAvatar, setEditingAvatar] = useState(false);
  const [viewingLevels, setViewingLevels] = useState(false);
  useEffect(() => { setViewingLevels(false); setEditingAvatar(false); setEditing(false); }, [username, user?.id]);
  useEffect(() => {
    if (user) profile.setData(current => current?.id === user.id ? { ...current, ...user } : current);
  }, [user, profile.setData]);
  useEffect(()=> {
    const change=follows.change;
    if(!change) return;
    profile.setData(current=> {
      if(!current) return current;
      if(current.id===change.target.id) return change.target;
      return current.id===user?.id && change.actorStats ? {...current,stats:change.actorStats} : current;
    });
  },[follows.change,user?.id,profile.setData]);
  const follow = async () => {
    if (!isAuthenticated) {
      onRequireAuth();
      return;
    }
    if (!profile.data) return;
    try {
      await follows.setFollowing(profile.data,!profile.data.isFollowing);
    } catch (err) {
      showToast(err instanceof Error ? err.message : "关注操作失败", "error");
    }
  };
  if (profile.loading || authLoading)
    return (
      <div className="max-w-5xl mx-auto space-y-6">
        <PageBackButton />
        <div className="panel">
        <ContentState title="正在加载个人主页" loading />
        </div>
      </div>
    );
  if (profile.error || !profile.data)
    return (
      <div className="max-w-5xl mx-auto space-y-6">
        <PageBackButton />
        <div className="panel">
        <ContentState
          title="个人主页暂时无法打开"
          description={profile.error || "没有找到该用户。"}
          onRetry={profile.reload}
        />
        </div>
      </div>
    );
  const p = profile.data;
  const own = user?.id === p.id;
  return (
    <div className="max-w-5xl mx-auto space-y-6">
      <PageBackButton />
      <header className="panel overflow-hidden">
        {p.banner && (
          <img
            className="w-full h-32 sm:h-44 object-cover"
            src={p.banner}
            alt="个人主页封面"
          />
        )}
        <div className="p-6 sm:p-8">
          <div className="flex justify-between gap-4 items-start">
            <div className="flex gap-4 min-w-0">
              {own ? <button type="button" aria-label="更换头像" onClick={() => setEditingAvatar(true)}
                className={`group relative flex shrink-0 self-start rounded-full ${p.banner ? "-mt-12 sm:-mt-14" : ""}`}>
                <Avatar src={p.avatar} name={p.displayName || p.username} size="xl"
                  className={p.banner ? "ring-4 ring-white dark:ring-night-surface !w-20 !h-20 sm:!w-24 sm:!h-24" : undefined} />
                <span className="absolute -bottom-1 -right-1 flex h-7 w-7 items-center justify-center rounded-full border border-ink-primary bg-white text-ink-primary group-hover:bg-ochre-light dark:border-night-border dark:bg-night-surface dark:text-night-ink">
                  <Camera size={15} aria-hidden="true" />
                </span>
              </button> : <Avatar
                src={p.avatar}
                name={p.displayName || p.username}
                size="xl"
                className={p.banner ? "-mt-12 sm:-mt-14 ring-4 ring-white dark:ring-night-surface !w-20 !h-20 sm:!w-24 sm:!h-24" : undefined}
              />}
              <div className="min-w-0">
                <h1 className="text-2xl sm:text-3xl font-extrabold break-words">
                  {p.displayName || p.username}
                  {p.accountType === "BOT" && <span className="ml-2 text-sm font-semibold text-terracotta dark:text-terracotta-dark">AI</span>}
                </h1>
                {p.growth && (
                  <div className="flex flex-wrap items-center gap-2 mt-1 text-sm text-ink-muted dark:text-night-muted">
                    <LevelBadge level={p.growth.level} scope="personal" onClick={() => setViewingLevels(true)} />
                    <span>{p.growth.experience.toLocaleString()} 经验</span>
                    <span>{p.growth.maxLevel ? "已满级" : `距下一级 ${p.growth.experienceToNextLevel.toLocaleString()} 经验`}</span>
                  </div>
                )}
                <p className="text-sm text-ink-muted dark:text-night-muted break-all">
                  @{p.username}
                </p>
              </div>
            </div>
            {own ? (
              <Button
                aria-label="编辑资料"
                size="sm"
                variant="outline"
                onClick={() => setEditing(true)}
              >
                <Settings size={17} />
                <span className="hidden sm:inline">编辑资料</span>
              </Button>
            ) : p.accountType !== "BOT" ? (
              <Button
                size="sm"
                variant={p.isFollowing ? "outline" : "primary"}
                isLoading={follows.pendingUserId===p.id}
                disabled={follows.busy || follows.remainingSeconds>0}
                title={p.isFollowing ? "点击取消关注" : "关注这个用户"}
                onClick={follow}
              >
                {p.isFollowing ? (
                  <UserCheck size={17} />
                ) : (
                  <UserPlus size={17} />
                )}
                <span>{follows.remainingSeconds>0 ? `${follows.remainingSeconds} 秒后` : p.isFollowing ? "已关注" : "关注"}</span>
              </Button>
            ) : null}
          </div>
          {p.bio && (
            <p className="mt-5 whitespace-pre-wrap text-sm sm:text-base text-ink-muted dark:text-night-muted">
              {p.bio}
            </p>
          )}
          <div className="profile-summary mt-6 pt-5 hairline-t">
            <div className="profile-statistics text-sm text-ink-muted dark:text-night-muted">
              <Link replace to="?tab=posts" className="profile-statistic-link" aria-label={`查看帖子列表，${p.stats?.posts ?? posts.data.length} 篇`}>
                <strong className="text-ink-primary dark:text-night-ink">
                  {p.stats?.posts ?? posts.data.length}
                </strong>{" "}
                篇帖子
              </Link>
              <Link replace to="?tab=followers" className="profile-statistic-link" aria-label={`查看粉丝列表，${p.stats?.followers ?? 0} 人`}>
                <strong className="text-ink-primary dark:text-night-ink">
                  {p.stats?.followers ?? 0}
                </strong>{" "}
                位关注者
              </Link>
              <Link replace to="?tab=following" className="profile-statistic-link" aria-label={`查看关注列表，${p.stats?.following ?? 0} 人`}>
                <strong className="text-ink-primary dark:text-night-ink">
                  {p.stats?.following ?? 0}
                </strong>{" "}
                正在关注
              </Link>
            </div>
            <PageMascot pose="profile" className="profile-mascot" />
          </div>
        </div>
      </header>
      <section className="panel overflow-hidden">
        <nav className="feed-tabs" aria-label="个人主页内容">
          {([
            ["posts", own ? "我的帖子" : "TA 的帖子"],
            ["followers", "粉丝列表"],
            ["following", "关注列表"],
          ] as const).map(([tab, label]) => (
            <Link key={tab} replace to={`?tab=${tab}`} className="feed-tab" aria-current={activeTab === tab ? "page" : undefined}>
              {label}
            </Link>
          ))}
        </nav>
        <div className="px-5">
          {activeTab !== "posts" ? (
            <FollowUserList key={`${username}:${activeTab}`} username={username} kind={activeTab} own={own} onRequireAuth={onRequireAuth} />
          ) : posts.loading ? (
            <ContentState title="正在加载帖子" loading />
          ) : posts.error ? (
            <ContentState
              title="帖子加载失败"
              description={posts.error}
              onRetry={posts.reload}
            />
          ) : posts.data.length ? (
            posts.data.map((post) => (
              <PostCard
                key={post.id}
                post={post}
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
              title="还没有公开帖子"
              description="之后分享的内容会出现在这里。"
              showIcon={false}
            />
          )}
        </div>
      </section>
      {p.growth && viewingLevels && <LevelGuideModal isOpen onClose={() => setViewingLevels(false)}
        progress={p.growth} displayName={p.displayName || p.username} own={own} />}
      {own && editingAvatar && <AvatarPickerModal key={user.id} onClose={() => setEditingAvatar(false)}
        onSaved={(updated) => profile.setData(updated)} />}
      {editing && (
        <EditProfileModal
          isOpen={editing}
          onClose={() => {
            setEditing(false);
            profile.reload();
          }}
        />
      )}
    </div>
  );
};
