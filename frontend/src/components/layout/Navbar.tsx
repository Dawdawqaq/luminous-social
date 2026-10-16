import React, { useEffect, useRef, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import {
  Bell,
  Moon,
  Sun,
  Pencil,
  ChevronDown,
  UserRound,
  LogOut,
  Shield,
  Bookmark,
} from "lucide-react";
import { HeaderSearch } from "../search/HeaderSearch";
import { Avatar } from "../ui/Avatar";
import { useAuth } from "../../context/AuthContext";
import { useTheme } from "../../context/ThemeContext";
import { useNotifications } from "../../context/NotificationContext";
interface NavbarProps {
  onOpenAuth: (mode?: "login" | "register") => void;
  onOpenCreatePost: () => void;
  onOpenNotifications: () => void;
}
export const Navbar: React.FC<NavbarProps> = ({
  onOpenAuth,
  onOpenCreatePost,
  onOpenNotifications,
}) => {
  const { user, isAuthenticated, isAdmin, logout } = useAuth();
  const { theme, toggleTheme } = useTheme();
  const { unreadCount } = useNotifications();
  const navigate = useNavigate();
  const [menu, setMenu] = useState(false);
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (!menu) return;
    const close = (e: MouseEvent) => {
      if (!ref.current?.contains(e.target as Node)) setMenu(false);
    };
    const key = (e: KeyboardEvent) => {
      if (e.key === "Escape") setMenu(false);
    };
    document.addEventListener("mousedown", close);
    document.addEventListener("keydown", key);
    return () => {
      document.removeEventListener("mousedown", close);
      document.removeEventListener("keydown", key);
    };
  }, [menu]);
  return (
    <header className="site-header">
      <div className="header-inner">
        <Link to="/" className="brand" aria-label="Luminous 首页">
          <img
            className="brand-mark"
            src="/brand/luminous-star-soft.png"
            alt=""
            aria-hidden="true"
            width="37"
            height="37"
          />
          <div>
            <span className="brand-word">Luminous</span>
            <span className="brand-tagline">让热爱发光</span>
          </div>
        </Link>
        <div className="header-search">
          <HeaderSearch />
        </div>
        <div className="header-actions">
          <button
            className="icon-button"
            aria-label={theme === "dark" ? "切换浅色模式" : "切换深色模式"}
            onClick={toggleTheme}
          >
            {theme === "dark" ? <Sun size={21} /> : <Moon size={21} />}
          </button>
          {isAuthenticated && (
            <button
              className="icon-button relative"
              aria-label={
                "通知" + (unreadCount ? "，" + unreadCount + "条未读" : "")
              }
              onClick={onOpenNotifications}
            >
              <Bell size={23} />
              {unreadCount > 0 && (
                <span className="absolute top-0 right-0 rounded-full bg-terracotta text-white px-1 text-[10px]">
                  {unreadCount > 99 ? "99+" : unreadCount}
                </span>
              )}
            </button>
          )}
          {isAuthenticated && user ? (
            <div className="relative" ref={ref}>
              <button
                className="flex items-center gap-2 px-1 py-1"
                aria-label="打开用户菜单"
                aria-expanded={menu}
                onClick={() => setMenu(!menu)}
              >
                <Avatar
                  src={user.avatar}
                  name={user.displayName || user.username}
                  size="md"
                />
                <span className="user-name max-w-20 truncate font-bold text-sm">
                  {user.displayName || user.username}
                </span>
                <ChevronDown size={15} />
              </button>
              {menu && (
                <div className="panel user-menu">
                  <Link
                    to={"/u/" + encodeURIComponent(user.username)}
                    onClick={() => setMenu(false)}
                  >
                    <UserRound size={17} />
                    个人主页
                  </Link>
                  <Link to="/archive" onClick={() => setMenu(false)}>
                    <Bookmark size={17} />
                    我的收藏
                  </Link>
                  {isAdmin && (
                    <Link to="/admin" onClick={() => setMenu(false)}>
                      <Shield size={17} />
                      管理后台
                    </Link>
                  )}
                  <button
                    onClick={() => {
                      setMenu(false);
                      logout();
                      navigate("/");
                    }}
                  >
                    <LogOut size={17} />
                    退出登录
                  </button>
                </div>
              )}
            </div>
          ) : (
            <button
              className="ink-button small"
              onClick={() => onOpenAuth("login")}
            >
              登录
            </button>
          )}
          <button
            className="ink-button primary header-publish"
            onClick={onOpenCreatePost}
          >
            <Pencil size={19} />
            发帖
          </button>
        </div>
      </div>
    </header>
  );
};
