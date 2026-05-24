import { useEffect, useState } from 'react';
import { api } from '../lib/api';
import { useAuth } from '../auth/AuthContext';
import { Shield, ShieldBan, UserCheck, AlertTriangle } from 'lucide-react';
import { cn } from '../lib/utils';
import { motion } from 'motion/react';
import { Link, useNavigate } from 'react-router-dom';

export function AdminView() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const [users, setUsers] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (user && user.role !== 'ADMIN') {
      navigate('/');
      return;
    }
    loadUsers();
  }, [user, navigate]);

  const loadUsers = async () => {
    try {
      const data = await api.adminListUsers();
      setUsers(data);
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  const toggleBan = async (targetUser: any) => {
    try {
      if (targetUser.isBanned) {
        await api.adminUnbanUser(targetUser.username);
      } else {
        await api.adminBanUser(targetUser.username);
      }
      loadUsers();
    } catch (err) {
      alert(err instanceof Error ? err.message : '操作失败');
    }
  };

  if (loading) {
    return (
      <div className="flex justify-center items-center py-20">
        <div className="w-12 h-12 border-4 border-primary/20 border-t-primary rounded-full animate-spin" />
      </div>
    );
  }

  return (
    <div className="max-w-[1000px] mx-auto pb-20">
      <div className="relative w-full rounded-3xl overflow-hidden bg-gradient-to-r from-red-600/10 via-orange-500/5 to-red-900/10 border border-red-500/20 p-8 mb-8 backdrop-blur-md">
        <div className="flex items-center gap-3 mb-2">
          <Shield className="text-red-500" size={24} />
          <span className="text-sm font-bold text-red-500 tracking-wider uppercase">全局管理控制台</span>
        </div>
        <h1 className="text-3xl font-display font-black text-on-surface">系统管理中心</h1>
        <p className="text-on-surface-variant text-sm mt-2 font-medium">管理员专用界面。您可以监控全站用户状态并执行封禁操作。</p>
      </div>

      <div className="card-luminous overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm whitespace-nowrap">
            <thead className="bg-surface-variant/30 text-on-surface-variant">
              <tr>
                <th className="px-6 py-4 font-bold">用户</th>
                <th className="px-6 py-4 font-bold">角色</th>
                <th className="px-6 py-4 font-bold">注册时间</th>
                <th className="px-6 py-4 font-bold">状态</th>
                <th className="px-6 py-4 font-bold text-right">操作</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-outline-variant/30">
              {users.map((u) => (
                <tr key={u.id} className="hover:bg-surface-variant/20 transition-colors">
                  <td className="px-6 py-4">
                    <div className="flex items-center gap-3">
                      {u.avatar ? (
                        <img src={u.avatar} className="w-10 h-10 rounded-full object-cover" />
                      ) : (
                        <div className="w-10 h-10 rounded-full bg-primary/10 text-primary flex items-center justify-center font-bold">
                          {u.username.substring(0, 2).toUpperCase()}
                        </div>
                      )}
                      <div>
                        <Link to={`/user/${u.username}`} className="font-bold text-on-surface hover:text-primary transition-colors block">{u.displayName}</Link>
                        <span className="text-xs text-on-surface-variant">@{u.username}</span>
                      </div>
                    </div>
                  </td>
                  <td className="px-6 py-4">
                    <span className={cn("px-2 py-1 rounded-full text-xs font-bold", u.role === 'ADMIN' ? "bg-red-500/10 text-red-500" : "bg-surface-variant text-on-surface-variant")}>
                      {u.role === 'ADMIN' ? '超级管理员' : '普通用户'}
                    </span>
                  </td>
                  <td className="px-6 py-4 text-on-surface-variant">{u.joinDate}</td>
                  <td className="px-6 py-4">
                    {u.isBanned ? (
                      <span className="flex items-center gap-1.5 text-error font-bold text-xs"><AlertTriangle size={14} /> 已封禁</span>
                    ) : (
                      <span className="flex items-center gap-1.5 text-green-500 font-bold text-xs"><UserCheck size={14} /> 正常</span>
                    )}
                  </td>
                  <td className="px-6 py-4 text-right">
                    {u.role !== 'ADMIN' && (
                      <button
                        onClick={() => toggleBan(u)}
                        className={cn(
                          "px-4 py-2 rounded-xl text-xs font-bold transition-all shadow-sm flex items-center gap-2 ml-auto",
                          u.isBanned ? "bg-surface-variant text-on-surface hover:bg-surface-container-highest" : "bg-error/10 text-error hover:bg-error hover:text-error-container"
                        )}
                      >
                        {u.isBanned ? <><UserCheck size={14} /> 解除封禁</> : <><ShieldBan size={14} /> 封禁账号</>}
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
