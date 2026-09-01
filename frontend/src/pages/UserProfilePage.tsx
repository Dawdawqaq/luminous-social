import React, { useState, useEffect, useCallback } from 'react';
import { useParams, Link } from 'react-router-dom';
import { User, Post } from '../types';
import { userApi, postApi } from '../api/client';
import { Avatar } from '../components/ui/Avatar';
import { Button } from '../components/ui/Button';
import { PostCard } from '../components/post/PostCard';
import { EditProfileModal } from '../components/auth/EditProfileModal';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';
import { Calendar, UserPlus, UserCheck, Settings, Loader2 } from 'lucide-react';

interface UserProfilePageProps {
  onRequireAuth: () => void;
}

export const UserProfilePage: React.FC<UserProfilePageProps> = ({ onRequireAuth }) => {
  const { username } = useParams<{ username: string }>();
  const { user: currentUser, isAuthenticated } = useAuth();
  const { showToast } = useToast();

  const [profile, setProfile] = useState<User | null>(null);
  const [posts, setPosts] = useState<Post[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isFollowing, setIsFollowing] = useState(false);
  const [isFollowLoading, setIsFollowLoading] = useState(false);
  const [isEditModalOpen, setIsEditModalOpen] = useState(false);

  const isOwnProfile = currentUser?.username === username;

  const fetchProfile = useCallback(async () => {
    if (!username) return;
    try {
      setIsLoading(true);
      const [userRes, postsRes] = await Promise.allSettled([
        userApi.getProfile(username),
        postApi.list({ author: username, filter: 'new', size: 20 }),
      ]);

      if (userRes.status === 'fulfilled' && userRes.value) {
        setProfile(userRes.value);
        setIsFollowing(!!userRes.value.isFollowing);
      }
      if (postsRes.status === 'fulfilled' && postsRes.value.items) {
        setPosts(postsRes.value.items);
      }
    } catch {
    } finally {
      setIsLoading(false);
    }
  }, [username]);

  useEffect(() => {
    fetchProfile();
  }, [fetchProfile]);

  const handleFollowToggle = async () => {
    if (!isAuthenticated) {
      onRequireAuth();
      return;
    }
    if (!username || isFollowLoading) return;

    try {
      setIsFollowLoading(true);
      if (isFollowing) {
        await userApi.unfollow(username).catch(() => {});
        setIsFollowing(false);
        showToast(`已取消关注 @${username}`, 'info');
      } else {
        await userApi.follow(username).catch(() => {});
        setIsFollowing(true);
        showToast(`已关注 @${username}`, 'success');
      }
    } catch (err: any) {
      showToast(err.message || '操作失败', 'error');
    } finally {
      setIsFollowLoading(false);
    }
  };

  if (isLoading) {
    return (
      <div className="py-32 text-center space-y-3">
        <Loader2 className="w-8 h-8 animate-spin mx-auto text-ink-subtle" />
        <p className="font-serif italic text-sm text-ink-muted dark:text-night-muted">
          正在载入用户手记与主页...
        </p>
      </div>
    );
  }

  if (!profile) {
    return (
      <div className="py-24 text-center space-y-4">
        <h2 className="font-serif text-2xl font-medium text-ink-primary dark:text-night-ink">
          未找到该用户
        </h2>
        <Link to="/">
          <Button variant="outline" size="sm">
            返回社区首页
          </Button>
        </Link>
      </div>
    );
  }

  return (
    <div className="space-y-8 animate-in fade-in duration-150">
      
      {/* Profile Header Card */}
      <div className="bg-paper-50 dark:bg-night-surface border border-paper-300 dark:border-night-border rounded-[2px] shadow-tactile p-6 sm:p-8 space-y-6">
        
        <div className="flex flex-col sm:flex-row justify-between items-start gap-4">
          <div className="flex items-start gap-4">
            <Avatar
              src={profile.avatar}
              name={profile.displayName || profile.username}
              size="xl"
            />
            <div className="space-y-1">
              <h1 className="font-serif text-2xl sm:text-3xl font-medium text-ink-primary dark:text-night-ink">
                {profile.displayName || profile.username}
              </h1>
              <p className="text-xs font-sans text-ink-subtle dark:text-night-subtle">
                @{profile.username}
              </p>
            </div>
          </div>

          <div className="flex items-center gap-3">
            {isOwnProfile ? (
              <Button
                variant="outline"
                size="sm"
                onClick={() => setIsEditModalOpen(true)}
              >
                <Settings className="w-3.5 h-3.5" />
                <span>编辑资料</span>
              </Button>
            ) : (
              <Button
                variant={isFollowing ? 'secondary' : 'primary'}
                size="sm"
                onClick={handleFollowToggle}
                isLoading={isFollowLoading}
              >
                {isFollowing ? (
                  <>
                    <UserCheck className="w-3.5 h-3.5" />
                    <span>已关注 (Following)</span>
                  </>
                ) : (
                  <>
                    <UserPlus className="w-3.5 h-3.5" />
                    <span>关注创作者 (Follow)</span>
                  </>
                )}
              </Button>
            )}
          </div>
        </div>

        {/* Bio */}
        {profile.bio && (
          <blockquote className="font-serif italic text-sm sm:text-base text-ink-muted dark:text-night-muted leading-relaxed border-l-2 border-terracotta/40 pl-4 py-0.5 max-w-2xl">
            “{profile.bio}”
          </blockquote>
        )}

        {/* Stats */}
        <div className="flex flex-wrap items-center gap-6 pt-4 hairline-t text-xs font-sans text-ink-muted dark:text-night-muted">
          <span>
            <strong className="text-ink-primary dark:text-night-ink font-medium">
              {profile.stats?.posts || posts.length}
            </strong>{' '}
            篇帖子
          </span>
          <span>
            <strong className="text-ink-primary dark:text-night-ink font-medium">
              {profile.stats?.followers || 0}
            </strong>{' '}
            关注者
          </span>
          <span>
            <strong className="text-ink-primary dark:text-night-ink font-medium">
              {profile.stats?.following || 0}
            </strong>{' '}
            正在关注
          </span>
          {profile.joinDate && (
            <span className="flex items-center gap-1 text-ink-subtle">
              <Calendar className="w-3.5 h-3.5" />
              <span>加入于 {profile.joinDate}</span>
            </span>
          )}
        </div>
      </div>

      {/* Published Posts */}
      <div className="space-y-4">
        <div className="flex items-center gap-6 pb-2 hairline-b text-xs font-sans">
          <span className="font-semibold text-ink-primary dark:text-night-ink border-b-2 border-terracotta pb-2">
            TA 发表的帖子 ({posts.length})
          </span>
        </div>

        {posts.length === 0 ? (
          <div className="py-16 text-center space-y-2 bg-paper-50 dark:bg-night-surface border border-paper-300 dark:border-night-border rounded-[2px]">
            <p className="font-serif italic text-sm text-ink-muted dark:text-night-muted">
              该用户尚未公开发表帖子。
            </p>
          </div>
        ) : (
          <div className="divide-y divide-paper-300 dark:divide-night-border">
            {posts.map((post) => (
              <PostCard
                key={post.id}
                post={post}
                onRequireAuth={onRequireAuth}
                onVoteChange={(updated) =>
                  setPosts((prev) =>
                    prev.map((p) => (p.id === updated.id ? updated : p))
                  )
                }
              />
            ))}
          </div>
        )}
      </div>

      {isEditModalOpen && (
        <EditProfileModal
          isOpen={isEditModalOpen}
          onClose={() => {
            setIsEditModalOpen(false);
            fetchProfile();
          }}
        />
      )}

    </div>
  );
};
