import React, { useState, useEffect } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { Plus, Sun, Moon, Bell, LogIn, LogOut, User, Bookmark, Shield } from 'lucide-react';
import { useAuth } from '../../context/AuthContext';
import { useTheme } from '../../context/ThemeContext';
import { Avatar } from '../ui/Avatar';
import { notificationApi } from '../../api/client';
import { HeaderSearch } from '../search/HeaderSearch';

interface NavbarProps {
  onOpenSearch?: () => void;
  onOpenAuth: (mode?: 'login' | 'register') => void;
  onOpenCreatePost: () => void;
  onOpenNotifications: () => void;
}

export const Navbar: React.FC<NavbarProps> = ({
  onOpenAuth,
  onOpenCreatePost,
  onOpenNotifications,
}) => {
  const { user, isAuthenticated, logout, isAdmin } = useAuth();
  const { theme, toggleTheme } = useTheme();
  const location = useLocation();
  const navigate = useNavigate();
  const [unreadCount, setUnreadCount] = useState<number>(0);
  const [isMenuOpen, setIsMenuOpen] = useState(false);

  useEffect(() => {
    if (isAuthenticated) {
      notificationApi.getUnreadCount().then((res) => {
        setUnreadCount(res.unreadCount || 0);
      }).catch(() => {});
    } else {
      setUnreadCount(0);
    }
  }, [isAuthenticated, location.pathname]);

  const navLinks = [
    { label: '全部帖子', path: '/', exact: true },
    { label: '社区列表', path: '/salons' },
    { label: '我的收藏', path: '/archive', requiresAuth: true },
  ];

  return (
    <header className="sticky top-0 z-40 bg-paper-100/95 dark:bg-night-canvas/95 backdrop-blur-md hairline-b transition-colors">
      <div className="max-w-6xl mx-auto px-4 sm:px-6 h-16 flex items-center justify-between">
        
        {/* Left: Brand & Main Forum Nav */}
        <div className="flex items-center gap-6 sm:gap-8">
          <Link to="/" className="flex items-center gap-1.5 group">
            <span className="font-serif text-2xl font-bold tracking-tight text-ink-primary dark:text-night-ink group-hover:opacity-85 transition-opacity">
              LUMINOUS
            </span>
            <span className="w-1.5 h-1.5 rounded-full bg-terracotta -mt-1 group-hover:scale-125 transition-transform" />
          </Link>

          <nav className="flex items-center gap-4 sm:gap-6">
            {navLinks.map((link) => {
              if (link.requiresAuth && !isAuthenticated) return null;
              const isActive = link.exact
                ? location.pathname === link.path
                : location.pathname.startsWith(link.path);

              return (
                <Link
                  key={link.path}
                  to={link.path}
                  className={`text-xs sm:text-sm font-sans tracking-wide transition-colors py-1 ${
                    isActive
                      ? 'text-ink-primary dark:text-night-ink font-semibold border-b-2 border-terracotta'
                      : 'text-ink-muted hover:text-ink-primary dark:text-night-muted dark:hover:text-night-ink'
                  }`}
                >
                  {link.label}
                </Link>
              );
            })}
          </nav>
        </div>

        {/* Right: Search, Publish, Theme, User Profile */}
        <div className="flex items-center gap-2.5 sm:gap-3">
          
          {/* Seamless Inline Header Search */}
          <HeaderSearch />

          {/* Theme Toggle */}
          <button
            onClick={toggleTheme}
            className="p-1.5 text-ink-muted hover:text-ink-primary dark:text-night-muted dark:hover:text-night-ink transition-colors cursor-pointer"
            title={theme === 'dark' ? '切换为浅色日光纸张' : '切换为深色夜室模式'}
          >
            {theme === 'dark' ? <Sun className="w-4 h-4" /> : <Moon className="w-4 h-4" />}
          </button>

          {/* Notification Button */}
          {isAuthenticated && (
            <button
              onClick={onOpenNotifications}
              className="relative p-1.5 text-ink-muted hover:text-ink-primary dark:text-night-muted dark:hover:text-night-ink transition-colors cursor-pointer"
              title="消息通知"
            >
              <Bell className="w-4 h-4" />
              {unreadCount > 0 && (
                <span className="absolute top-1 right-1 w-2 h-2 rounded-full bg-terracotta" />
              )}
            </button>
          )}

          {/* Primary Action: New Post Button */}
          <button
            onClick={() => {
              if (!isAuthenticated) {
                onOpenAuth('login');
              } else {
                onOpenCreatePost();
              }
            }}
            className="inline-flex items-center gap-1.5 px-3.5 py-1.5 text-xs font-sans font-medium bg-ink-primary text-paper-50 dark:bg-night-ink dark:text-night-canvas hover:bg-ink-dark dark:hover:bg-white rounded-[2px] transition-colors cursor-pointer shadow-tactile"
          >
            <Plus className="w-3.5 h-3.5" />
            <span>发帖</span>
          </button>

          {/* User Menu / Login Button */}
          {isAuthenticated && user ? (
            <div className="relative">
              <button
                onClick={() => setIsMenuOpen(!isMenuOpen)}
                className="flex items-center gap-2 p-0.5 rounded hover:bg-paper-200/50 dark:hover:bg-night-surface transition-colors cursor-pointer"
              >
                <Avatar src={user.avatar} name={user.displayName || user.username} size="sm" />
              </button>

              {isMenuOpen && (
                <>
                  <div className="fixed inset-0 z-40" onClick={() => setIsMenuOpen(false)} />
                  <div className="absolute right-0 mt-2 w-52 bg-paper-50 dark:bg-night-surface border border-paper-300 dark:border-night-border rounded-[2px] shadow-lifted z-50 py-1 animate-in fade-in zoom-in-95">
                    <div className="px-4 py-2.5 hairline-b">
                      <p className="font-serif font-medium text-sm text-ink-primary dark:text-night-ink truncate">
                        {user.displayName || user.username}
                      </p>
                      <p className="font-sans text-[11px] text-ink-subtle dark:text-night-subtle truncate">
                        @{user.username}
                      </p>
                    </div>

                    <div className="py-1">
                      <Link
                        to={`/u/${user.username}`}
                        onClick={() => setIsMenuOpen(false)}
                        className="flex items-center gap-2.5 px-4 py-2 text-xs text-ink-muted hover:text-ink-primary hover:bg-paper-200/50 dark:text-night-muted dark:hover:text-night-ink dark:hover:bg-night-surfaceHover"
                      >
                        <User className="w-3.5 h-3.5" />
                        <span>个人主页</span>
                      </Link>

                      <Link
                        to="/archive"
                        onClick={() => setIsMenuOpen(false)}
                        className="flex items-center gap-2.5 px-4 py-2 text-xs text-ink-muted hover:text-ink-primary hover:bg-paper-200/50 dark:text-night-muted dark:hover:text-night-ink dark:hover:bg-night-surfaceHover"
                      >
                        <Bookmark className="w-3.5 h-3.5" />
                        <span>我的收藏</span>
                      </Link>

                      {isAdmin && (
                        <Link
                          to="/admin"
                          onClick={() => setIsMenuOpen(false)}
                          className="flex items-center gap-2.5 px-4 py-2 text-xs text-terracotta hover:bg-paper-200/50 dark:hover:bg-night-surfaceHover"
                        >
                          <Shield className="w-3.5 h-3.5" />
                          <span>社区管理后台</span>
                        </Link>
                      )}
                    </div>

                    <div className="hairline-t pt-1">
                      <button
                        onClick={() => {
                          setIsMenuOpen(false);
                          logout();
                          navigate('/');
                        }}
                        className="w-full flex items-center gap-2.5 px-4 py-2 text-xs text-ink-subtle hover:text-terracotta hover:bg-paper-200/50 dark:hover:bg-night-surfaceHover transition-colors cursor-pointer text-left"
                      >
                        <LogOut className="w-3.5 h-3.5" />
                        <span>退出登录</span>
                      </button>
                    </div>
                  </div>
                </>
              )}
            </div>
          ) : (
            <button
              onClick={() => onOpenAuth('login')}
              className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-sans font-medium text-ink-primary dark:text-night-ink border border-paper-300 dark:border-night-border hover:bg-paper-200/60 dark:hover:bg-night-surface rounded-[2px] transition-colors cursor-pointer"
            >
              <LogIn className="w-3.5 h-3.5" />
              <span>登录 / 注册</span>
            </button>
          )}

        </div>
      </div>
    </header>
  );
};
