import React, { useState, useEffect } from 'react';
import { AdminUser } from '../types';
import { adminApi } from '../api/client';
import { Button } from '../components/ui/Button';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';
import { Shield, Ban, CheckCircle, Search, Loader2 } from 'lucide-react';
import { Link } from 'react-router-dom';

export const AdminPage: React.FC = () => {
  const { isAdmin } = useAuth();
  const { showToast } = useToast();
  const [users, setUsers] = useState<AdminUser[]>([]);
  const [search, setSearch] = useState('');
  const [isLoading, setIsLoading] = useState(true);

  const fetchUsers = async () => {
    try {
      setIsLoading(true);
      const res = await adminApi.listUsers();
      setUsers(res || []);
    } catch (err: any) {
      showToast(err.message || '获取用户列表失败', 'error');
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    if (isAdmin) fetchUsers();
  }, [isAdmin]);

  const handleBanToggle = async (user: AdminUser) => {
    try {
      if (user.isBanned) {
        await adminApi.unbanUser(user.username);
        showToast(`已解除对 @${user.username} 的封禁`, 'info');
      } else {
        await adminApi.banUser(user.username);
        showToast(`已对 @${user.username} 执行沙龙禁言与封禁`, 'warning');
      }
      fetchUsers();
    } catch (err: any) {
      showToast(err.message || '操作失败', 'error');
    }
  };

  if (!isAdmin) {
    return (
      <div className="py-24 text-center space-y-4">
        <h2 className="font-serif text-2xl font-medium text-ink-primary dark:text-night-ink">
          权限受限
        </h2>
        <p className="text-xs font-sans text-ink-muted dark:text-night-muted">
          只有系统管理员能够进入沙龙治理中心。
        </p>
      </div>
    );
  }

  const filteredUsers = users.filter(
    (u) =>
      u.username.toLowerCase().includes(search.toLowerCase()) ||
      u.displayName.toLowerCase().includes(search.toLowerCase())
  );

  return (
    <div className="space-y-8 animate-in fade-in duration-200">
      <div className="space-y-2 pb-6 hairline-b">
        <div className="flex items-center gap-2">
          <Shield className="w-5 h-5 text-terracotta" />
          <h1 className="font-serif text-3xl font-medium text-ink-primary dark:text-night-ink tracking-tight">
            沙龙治理中心 · Salon Administration
          </h1>
        </div>
        <p className="font-serif italic text-sm text-ink-muted dark:text-night-muted">
          维护沙龙研讨秩序，管理用户身份与发言权限。
        </p>
      </div>

      <div className="flex justify-between items-center">
        <div className="relative w-full max-w-sm">
          <Search className="w-3.5 h-3.5 absolute left-3 top-1/2 -translate-y-1/2 text-ink-subtle" />
          <input
            type="text"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="按用户名或称谓搜索..."
            className="w-full pl-9 pr-3 py-1.5 text-xs font-sans bg-paper-50 dark:bg-night-surface border border-paper-300 dark:border-night-border rounded-[2px] text-ink-primary dark:text-night-ink focus:outline-none"
          />
        </div>
        <span className="text-xs font-sans text-ink-subtle">共 {filteredUsers.length} 名入座用户</span>
      </div>

      {isLoading ? (
        <div className="py-20 text-center space-y-3">
          <Loader2 className="w-6 h-6 animate-spin mx-auto text-ink-subtle" />
          <p className="font-serif italic text-sm text-ink-muted">正在载入用户花名册...</p>
        </div>
      ) : (
        <div className="overflow-x-auto border border-paper-300 dark:border-night-border rounded-[2px] bg-paper-50 dark:bg-night-surface">
          <table className="w-full text-left text-xs font-sans">
            <thead className="hairline-b bg-paper-200/50 dark:bg-night-canvas text-ink-muted dark:text-night-muted uppercase tracking-wider">
              <tr>
                <th className="px-4 py-3">用户称谓与 ID</th>
                <th className="px-4 py-3">角色权限</th>
                <th className="px-4 py-3">账号状态</th>
                <th className="px-4 py-3">入座时间</th>
                <th className="px-4 py-3 text-right">治理操作</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-paper-300 dark:divide-night-border">
              {filteredUsers.map((u) => (
                <tr key={u.id || u.username} className="hover:bg-paper-100/50 dark:hover:bg-night-surfaceHover transition-colors">
                  <td className="px-4 py-3">
                    <Link to={`/u/${u.username}`} className="font-medium text-ink-primary dark:text-night-ink hover:underline">
                      {u.displayName || u.username}
                    </Link>
                    <span className="text-[11px] text-ink-subtle block">@{u.username}</span>
                  </td>
                  <td className="px-4 py-3">
                    <span className={`px-2 py-0.5 rounded-[2px] text-[10px] uppercase font-mono ${u.role === 'ADMIN' ? 'bg-terracotta text-white' : 'bg-paper-200 dark:bg-night-border'}`}>
                      {u.role}
                    </span>
                  </td>
                  <td className="px-4 py-3">
                    {u.isBanned ? (
                      <span className="text-terracotta font-medium flex items-center gap-1">
                        <Ban className="w-3 h-3" /> 已封禁
                      </span>
                    ) : (
                      <span className="text-[#5B8266] flex items-center gap-1">
                        <CheckCircle className="w-3 h-3" /> 正常
                      </span>
                    )}
                  </td>
                  <td className="px-4 py-3 text-ink-subtle">{u.createdAt || '-'}</td>
                  <td className="px-4 py-3 text-right">
                    {u.role !== 'ADMIN' && (
                      <Button
                        size="sm"
                        variant={u.isBanned ? 'secondary' : 'outline'}
                        onClick={() => handleBanToggle(u)}
                      >
                        {u.isBanned ? '解封' : '封禁禁言'}
                      </Button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
};
