import React, { useState, useEffect, useRef } from 'react';
import { X, CheckCheck } from 'lucide-react';
import { Notification } from '../../types';
import { notificationApi } from '../../api/client';
import { formatTimeAgo } from '../../utils';
import { useToast } from '../../context/ToastContext';
import { useAuth } from '../../context/AuthContext';
import { useNotifications } from '../../context/NotificationContext';
import { Avatar } from '../ui/Avatar';

interface NotificationDrawerProps {
  isOpen: boolean;
  onClose: () => void;
}

export const NotificationDrawer: React.FC<NotificationDrawerProps> = ({ isOpen, onClose }) => {
  const { showToast } = useToast();
  const { user, isAuthenticated } = useAuth();
  const { unreadCount, refresh } = useNotifications();
  const requests = useRef(0);
  const [notifications, setNotifications] = useState<Notification[]>([]);
  const [isLoading, setIsLoading] = useState(false);

  const fetchNotifications = async () => {
    const request = ++requests.current;
    try {
      setIsLoading(true);
      const res = await notificationApi.list({ size: 30 });
      if (request === requests.current) setNotifications(res.items || []);
    } catch {
    } finally {
      if (request === requests.current) setIsLoading(false);
    }
  };

  useEffect(() => {
    if (isOpen && isAuthenticated) { void fetchNotifications(); void refresh(); }
    else { requests.current++; setNotifications([]); }
    return () => { requests.current++; };
  }, [isOpen, isAuthenticated, user?.id]);
  useEffect(() => { if (isOpen && isAuthenticated) void fetchNotifications(); }, [unreadCount]);

  const handleMarkAllAsRead = async () => {
    try {
      await notificationApi.markAllAsRead();
      await fetchNotifications();
      showToast('全部通知已标记为已读', 'info');
    } catch (err: any) {
      showToast(err.message || '操作失败', 'error');
    }
  };

  const handleMarkAsRead = async (id: string) => {
    try {
      await notificationApi.markAsRead(id);
      setNotifications((prev) =>
        prev.map((n) => (n.id === id ? { ...n, isRead: true } : n))
      );
    } catch {}
  };

  if (!isOpen || !isAuthenticated) return null;

  return (
    <div className="fixed inset-0 z-50 overflow-hidden">
      <div className="fixed inset-0 bg-ink-dark/40 dark:bg-black/60 backdrop-blur-[2px]" onClick={onClose} />

      <div className="fixed inset-y-0 right-0 max-w-full flex pl-10">
        <div className="w-screen max-w-md bg-paper-50 dark:bg-night-surface border-l border-paper-300 dark:border-night-border shadow-lifted flex flex-col animate-in slide-in-from-right duration-200">
          <div className="p-4 sm:p-5 hairline-b flex items-center justify-between">
            <div>
              <h3 className="font-serif text-lg font-medium text-ink-primary dark:text-night-ink">
                沙龙回响通知
              </h3>
              <p className="text-xs font-sans text-ink-subtle dark:text-night-subtle">
                来自他人的共鸣、批注与关注
              </p>
            </div>
            <div className="flex items-center gap-2">
              <button
                onClick={handleMarkAllAsRead}
                className="p-1.5 text-xs text-ink-muted hover:text-ink-primary dark:text-night-muted dark:hover:text-night-ink transition-colors cursor-pointer"
                title="全部标记为已读"
              >
                <CheckCheck className="w-4 h-4" />
              </button>
              <button
                onClick={onClose}
                className="p-1.5 text-ink-subtle hover:text-ink-primary dark:hover:text-night-ink transition-colors cursor-pointer"
              >
                <X className="w-4 h-4" />
              </button>
            </div>
          </div>

          <div className="flex-1 overflow-y-auto p-4 space-y-3">
            {notifications.length === 0 ? (
              <div className="py-16 text-center text-xs font-serif italic text-ink-subtle dark:text-night-subtle">
                暂无新的回响通知。在此静待思想的激荡。
              </div>
            ) : (
              notifications.map((n) => (
                <div
                  key={n.id}
                  onClick={() => handleMarkAsRead(n.id)}
                  className={`p-3.5 rounded-[2px] border transition-colors flex items-start gap-3 ${
                    n.isRead
                      ? 'bg-paper-100/50 dark:bg-night-canvas/50 border-paper-300/60 dark:border-night-border/60 opacity-80'
                      : 'bg-paper-50 dark:bg-night-surface border-terracotta/30 dark:border-terracotta-dark/30 shadow-tactile'
                  }`}
                >
                  <Avatar src={n.actor.avatar} name={n.actor.displayName || n.actor.username} size="sm" />
                  <div className="flex-1 text-xs space-y-1">
                    <p className="text-ink-primary dark:text-night-ink font-sans">
                      <span className="font-medium">{n.actor.displayName || n.actor.username}</span>{' '}
                      <span className="text-ink-muted dark:text-night-muted">
                        {n.type === 'VOTE' ? '对您的文章产生了共鸣 (Echo)' : n.type === 'COMMENT' ? '在您的篇章下留下了回响' : '关注了您的思想空间'}
                      </span>
                    </p>
                    {n.contentPreview && (
                      <p className="font-serif italic text-ink-muted dark:text-night-muted line-clamp-2">
                        “{n.contentPreview}”
                      </p>
                    )}
                    <span className="text-[10px] text-ink-subtle dark:text-night-subtle block">
                      {formatTimeAgo(n.createdAt)}
                    </span>
                  </div>
                  {!n.isRead && (
                    <span className="w-1.5 h-1.5 rounded-full bg-terracotta mt-1.5 shrink-0" />
                  )}
                </div>
              ))
            )}
          </div>
        </div>
      </div>
    </div>
  );
};
