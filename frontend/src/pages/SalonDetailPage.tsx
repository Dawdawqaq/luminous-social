import React, { useState, useEffect, useCallback } from 'react';
import { useParams, Link } from 'react-router-dom';
import { Community, Post, CheckInStats } from '../types';
import { communityApi, postApi, checkInApi } from '../api/client';
import { PostCard } from '../components/post/PostCard';
import { Button } from '../components/ui/Button';
import { Avatar } from '../components/ui/Avatar';
import { EditSalonModal } from '../components/community/EditSalonModal';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';
import { Users, Plus, Settings, ArrowLeft, Loader2, ShieldAlert, Calendar, Award, CheckCircle2 } from 'lucide-react';

interface SalonDetailPageProps {
  onRequireAuth: () => void;
  onOpenCreatePost: (defaultCommunity?: string) => void;
}

export const SalonDetailPage: React.FC<SalonDetailPageProps> = ({
  onRequireAuth,
  onOpenCreatePost,
}) => {
  const { slug } = useParams<{ slug: string }>();
  const { user, isAuthenticated } = useAuth();
  const { showToast } = useToast();

  const [community, setCommunity] = useState<Community | null>(null);
  const [posts, setPosts] = useState<Post[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isJoining, setIsJoining] = useState(false);
  const [isEditModalOpen, setIsEditModalOpen] = useState(false);
  const [checkInStats, setCheckInStats] = useState<CheckInStats | null>(null);
  const [isCheckingIn, setIsCheckingIn] = useState(false);

  const fetchCheckInStats = useCallback(async (communitySlug: string) => {
    if (!isAuthenticated) return;
    try {
      const stats = await checkInApi.getStats(communitySlug);
      setCheckInStats(stats);
    } catch {
      // Ignored if not joined or error
    }
  }, [isAuthenticated]);

  const fetchSalonData = useCallback(async () => {
    if (!slug) return;
    try {
      setIsLoading(true);
      const [salonRes, postsRes] = await Promise.allSettled([
        communityApi.getBySlug(slug),
        postApi.list({ community: slug, size: 20 }),
      ]);

      if (salonRes.status === 'fulfilled' && salonRes.value) {
        setCommunity(salonRes.value);
        if (salonRes.value.joined) {
          fetchCheckInStats(salonRes.value.name);
        }
      }
      if (postsRes.status === 'fulfilled' && postsRes.value.items) {
        setPosts(postsRes.value.items);
      }
    } catch {
    } finally {
      setIsLoading(false);
    }
  }, [slug, fetchCheckInStats]);

  const handleCheckIn = async () => {
    if (!isAuthenticated) {
      onRequireAuth();
      return;
    }
    if (!community) return;
    try {
      setIsCheckingIn(true);
      const res = await checkInApi.checkIn(community.name);
      if (res.success) {
        showToast(`签到成功！已连续签到 ${res.consecutiveDays} 天`, 'success');
        setCheckInStats({
          totalDays: res.totalDays,
          consecutiveDays: res.consecutiveDays,
          checkedToday: true,
          checkedDays: checkInStats ? [...checkInStats.checkedDays, new Date().getDate()] : [new Date().getDate()],
        });
      } else {
        showToast(res.message || '今日已完成签到', 'info');
      }
    } catch (err: any) {
      showToast(err.message || '签到失败', 'error');
    } finally {
      setIsCheckingIn(false);
    }
  };

  useEffect(() => {
    fetchSalonData();
  }, [fetchSalonData]);

  const handleJoinToggle = async () => {
    if (!isAuthenticated) {
      onRequireAuth();
      return;
    }
    if (!community || isJoining) return;

    try {
      setIsJoining(true);
      if (community.joined) {
        await communityApi.leave(community.name);
        setCommunity({
          ...community,
          joined: false,
          membersCount: Math.max(0, community.membersCount - 1),
        });
        showToast(`已退出 c/${community.name} 社区`, 'info');
      } else {
        await communityApi.join(community.name);
        setCommunity({
          ...community,
          joined: true,
          membersCount: community.membersCount + 1,
        });
        showToast(`已成功加入 c/${community.name} 社区`, 'success');
      }
    } catch (err: any) {
      showToast(err.message || '操作失败', 'error');
    } finally {
      setIsJoining(false);
    }
  };

  if (isLoading) {
    return (
      <div className="py-32 text-center space-y-3">
        <Loader2 className="w-8 h-8 animate-spin mx-auto text-ink-subtle" />
        <p className="font-serif italic text-sm text-ink-muted dark:text-night-muted">
          正在载入社区...
        </p>
      </div>
    );
  }

  if (!community) {
    return (
      <div className="py-24 text-center space-y-4">
        <h2 className="font-serif text-2xl font-medium text-ink-primary dark:text-night-ink">
          社区不存在
        </h2>
        <Link to="/salons">
          <Button variant="outline" size="sm">
            返回社区列表
          </Button>
        </Link>
      </div>
    );
  }

  const isOwner = user?.id && community.ownerId === user.id;

  return (
    <div className="space-y-8 animate-in fade-in duration-150">
      
      <Link
        to="/salons"
        className="inline-flex items-center gap-1.5 text-xs font-sans text-ink-muted hover:text-ink-primary dark:text-night-muted dark:hover:text-night-ink transition-colors"
      >
        <ArrowLeft className="w-3.5 h-3.5" />
        <span>返回全部社区</span>
      </Link>

      {/* Community Header Showcase */}
      <div className="relative overflow-hidden bg-paper-50 dark:bg-night-surface border border-paper-300 dark:border-night-border rounded-[2px] shadow-tactile p-6 sm:p-8 space-y-4">
        <div className="flex flex-col sm:flex-row justify-between sm:items-start gap-4">
          <div className="flex items-start gap-4">
            <Avatar
              src={community.icon}
              name={community.displayName || community.name}
              size="lg"
            />
            <div className="space-y-1">
              <h1 className="font-serif text-2xl sm:text-3xl font-medium text-ink-primary dark:text-night-ink tracking-tight">
                c/{community.name}
              </h1>
              {community.displayName && (
                <p className="text-xs font-sans text-ink-muted dark:text-night-muted">
                  {community.displayName}
                </p>
              )}
            </div>
          </div>

          <div className="flex items-center gap-3">
            {isOwner && (
              <Button
                variant="outline"
                size="sm"
                onClick={() => setIsEditModalOpen(true)}
              >
                <Settings className="w-3.5 h-3.5" />
                <span>配置社区</span>
              </Button>
            )}
            <Button
              variant={community.joined ? 'secondary' : 'primary'}
              size="sm"
              onClick={handleJoinToggle}
              isLoading={isJoining}
            >
              {community.joined ? '已加入 (Joined)' : '加入社区 (Join)'}
            </Button>
          </div>
        </div>

        <p className="font-serif italic text-sm sm:text-base text-ink-muted dark:text-night-muted max-w-2xl leading-relaxed">
          “{community.description || '探索思想与作品的专属社区空间。'}”
        </p>

        <div className="flex flex-wrap items-center gap-6 pt-3 hairline-t text-xs font-sans text-ink-subtle dark:text-night-subtle">
          <span className="flex items-center gap-1.5">
            <Users className="w-3.5 h-3.5 text-ink-muted" />
            <strong className="text-ink-primary dark:text-night-ink font-medium">
              {community.membersCount}
            </strong>{' '}
            位成员
          </span>
          <span>
            <strong className="text-ink-primary dark:text-night-ink font-medium">
              {posts.length}
            </strong>{' '}
            篇讨论与手稿
          </span>
        </div>
      </div>

      {/* Main Grid: Left Posts Stream + Right Community Rules */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 lg:gap-12 items-start">
        
        {/* Posts Stream */}
        <div className="lg:col-span-8 space-y-4">
          <div className="flex items-center justify-between pb-3 hairline-b">
            <h3 className="font-serif text-lg font-medium text-ink-primary dark:text-night-ink">
              社区帖子 ({posts.length})
            </h3>
            <Button
              size="sm"
              onClick={() => {
                if (!isAuthenticated) onRequireAuth();
                else onOpenCreatePost(community.name);
              }}
            >
              <Plus className="w-3.5 h-3.5" />
              <span>在此发帖</span>
            </Button>
          </div>

          {posts.length === 0 ? (
            <div className="py-20 text-center space-y-3 bg-paper-50/50 dark:bg-night-surface/20 border border-paper-300/60 dark:border-night-border/60 rounded-[2px]">
              <p className="font-serif italic text-sm text-ink-muted dark:text-night-muted">
                本社区暂无帖子。成为第一个发帖的人吧。
              </p>
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
        </div>

        {/* Right Rules & Checkin */}
        <aside className="lg:col-span-4 space-y-6">
          {/* 社区签到卡片 (Redis Bitmap) */}
          <div className="p-5 bg-paper-50 dark:bg-night-surface border border-paper-300 dark:border-night-border rounded-[2px] shadow-tactile space-y-4">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-1.5 text-xs font-sans font-semibold uppercase tracking-wider text-ink-primary dark:text-night-ink">
                <Calendar className="w-3.5 h-3.5 text-terracotta" />
                <span>社区每日签到</span>
              </div>
              {checkInStats?.checkedToday && (
                <span className="inline-flex items-center gap-1 text-[11px] font-sans text-forest dark:text-forest-light bg-forest/10 px-2 py-0.5 rounded-[2px]">
                  <CheckCircle2 className="w-3 h-3" />
                  已签到
                </span>
              )}
            </div>

            <div className="grid grid-cols-2 gap-3 text-center py-2 bg-paper-200/50 dark:bg-night-surfaceHover rounded-[2px]">
              <div>
                <div className="text-xl font-bold font-mono text-terracotta">
                  {checkInStats?.consecutiveDays || 0}
                </div>
                <div className="text-[11px] text-ink-subtle dark:text-night-subtle font-sans mt-0.5">
                  连续签到天数
                </div>
              </div>
              <div>
                <div className="text-xl font-bold font-mono text-ink-primary dark:text-night-ink">
                  {checkInStats?.totalDays || 0}
                </div>
                <div className="text-[11px] text-ink-subtle dark:text-night-subtle font-sans mt-0.5">
                  本月累计天数
                </div>
              </div>
            </div>

            {community.joined ? (
              <Button
                variant={checkInStats?.checkedToday ? 'secondary' : 'primary'}
                className="w-full justify-center text-xs"
                disabled={checkInStats?.checkedToday || isCheckingIn}
                onClick={handleCheckIn}
              >
                {isCheckingIn ? (
                  <Loader2 className="w-3.5 h-3.5 animate-spin mr-1.5" />
                ) : (
                  <Award className="w-3.5 h-3.5 mr-1.5 text-terracotta" />
                )}
                <span>{checkInStats?.checkedToday ? '今日已完成签到' : '点击签到打卡'}</span>
              </Button>
            ) : (
              <p className="text-[11px] text-center text-ink-muted dark:text-night-muted italic font-serif">
                加入该社区后即可参与每日签到
              </p>
            )}
          </div>

          {community.rules && community.rules.length > 0 && (
            <div className="p-5 bg-paper-50 dark:bg-night-surface border border-paper-300 dark:border-night-border rounded-[2px] shadow-tactile space-y-3">
              <div className="flex items-center gap-1.5 text-xs font-sans font-semibold uppercase tracking-wider text-ink-primary dark:text-night-ink">
                <ShieldAlert className="w-3.5 h-3.5 text-terracotta" />
                <span>社区守则</span>
              </div>
              <ul className="space-y-2 text-xs font-sans text-ink-muted dark:text-night-muted">
                {community.rules.map((rule, idx) => (
                  <li key={idx} className="flex items-start gap-2">
                    <span className="text-terracotta font-mono font-bold">
                      {idx + 1}.
                    </span>
                    <span className="leading-relaxed">{rule}</span>
                  </li>
                ))}
              </ul>
            </div>
          )}
        </aside>

      </div>

      {isEditModalOpen && (
        <EditSalonModal
          isOpen={isEditModalOpen}
          onClose={() => setIsEditModalOpen(false)}
          community={community}
          onUpdated={(updated) => setCommunity(updated)}
        />
      )}

    </div>
  );
};
