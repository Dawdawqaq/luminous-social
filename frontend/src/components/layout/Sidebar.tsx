import React from "react";
import { Link, useLocation } from "react-router-dom";
import {
  Home,
  Compass,
  Hash,
  Bookmark,
  UserRound,
  Plus,
  ArrowRight,
  MessageCircle,
} from "lucide-react";
import { Avatar } from "../ui/Avatar";
import { useAuth } from "../../context/AuthContext";
import { useCommunities } from "../../context/CommunityContext";
interface SidebarProps {
  onOpenAuth: (mode?: "login" | "register") => void;
  onOpenCreatePost: () => void;
}
export const Sidebar: React.FC<SidebarProps> = ({
  onOpenAuth,
  onOpenCreatePost,
}) => {
  const { user, isAuthenticated } = useAuth();
  const { communities, isLoading, error, refresh } = useCommunities();
  const location = useLocation();
  const isFeed =
    location.pathname === "/" &&
    new URLSearchParams(location.search).get("filter") === "feed";
  const items = [
    {
      label: "首页",
      to: "/",
      icon: Home,
      active: location.pathname === "/" && !isFeed,
    },
    {
      label: "发现社区",
      to: "/salons",
      icon: Compass,
      active:
        location.pathname === "/salons" || location.pathname.startsWith("/s/"),
    },
    { label: "关注动态", to: "/?filter=feed", icon: Hash, active: isFeed },
    { label: "Lumi AI", to: "/lumi", icon: MessageCircle, active: location.pathname === "/lumi" },
    {
      label: "我的收藏",
      to: "/archive",
      icon: Bookmark,
      active: location.pathname === "/archive",
      auth: true,
    },
    {
      label: "个人主页",
      to: user ? "/u/" + encodeURIComponent(user.username) : "/",
      icon: UserRound,
      active:
        !!user &&
        location.pathname === "/u/" + encodeURIComponent(user.username),
      auth: true,
    },
  ];
  const joined = communities.filter((c) => c.joined);
  return (
    <>
      <aside className="desktop-sidebar panel" aria-label="主导航与我的社区">
        <nav className="sidebar-nav" aria-label="主导航">
          {items.map((item) =>
            item.auth && !isAuthenticated ? (
              <button
                key={item.label}
                className="nav-item"
                onClick={() => onOpenAuth("login")}
              >
                <item.icon size={24} />
                {item.label}
              </button>
            ) : (
              <Link
                key={item.label}
                to={item.to}
                className={"nav-item" + (item.active ? " active" : "")}
                aria-current={item.active ? "page" : undefined}
              >
                <item.icon size={24} />
                {item.label}
              </Link>
            ),
          )}
        </nav>
        <section className="sidebar-communities">
          <h2 className="sidebar-title">我的社区</h2>
          {!isAuthenticated ? (
            <p className="px-2 text-xs text-ink-muted dark:text-night-muted">
              登录后，在这里找到你加入的社区。
            </p>
          ) : isLoading ? (
            <p className="px-2 text-xs text-ink-muted dark:text-night-muted">
              正在加载…
            </p>
          ) : error ? (
            <button className="px-2 text-xs text-terracotta" onClick={refresh}>
              社区加载失败，重试
            </button>
          ) : joined.length ? (
            joined.slice(0, 5).map((c) => (
              <Link
                key={c.id || c.name}
                className="community-shortcut"
                to={"/s/" + encodeURIComponent(c.name.replace(/^c\//, ""))}
              >
                <Avatar src={c.icon} name={c.displayName || c.name} size="sm" />
                <span className="truncate">{c.displayName || c.name}</span>
              </Link>
            ))
          ) : (
            <Link className="community-shortcut" to="/salons">
              找一个喜欢的社区
              <ArrowRight size={16} />
            </Link>
          )}
        </section>
        <div className="sidebar-cat">
          <p>
            和有趣的人
            <br />
            一起分享生活吧！
          </p>
          <img
            src="/illustrations/cats/luminous-cat-sidebar-v2.png"
            alt=""
            aria-hidden="true"
            width="1186"
            height="1326"
            loading="lazy"
            decoding="async"
            draggable={false}
          />
        </div>
        {!isAuthenticated && (
          <button
            className="ink-button yellow small w-full"
            onClick={() => onOpenAuth("register")}
          >
            加入 Luminous
          </button>
        )}
        <div className="sidebar-footer">Luminous · 让热爱发光</div>
      </aside>
      <nav className="mobile-nav" aria-label="手机主导航">
        <Link
          to="/"
          className={location.pathname === "/" && !isFeed ? "active" : ""}
        >
          <Home size={21} />
          首页
        </Link>
        <Link
          to="/salons"
          className={location.pathname.startsWith("/s") ? "active" : ""}
        >
          <Compass size={21} />
          社区
        </Link>
        <button
          aria-label="发布帖子"
          onClick={onOpenCreatePost}
          className="mobile-create"
        >
          <Plus size={26} />
        </button>
        <Link to="/lumi" className={location.pathname === "/lumi" ? "active" : ""} aria-label="Lumi AI"><MessageCircle size={21} />Lumi AI</Link>
        <Link
          to="/archive"
          className={location.pathname === "/archive" ? "active" : ""}
        >
          <Bookmark size={21} />
          收藏
        </Link>
        {isAuthenticated && user ? (
          <Link
            to={"/u/" + encodeURIComponent(user.username)}
            className={location.pathname.startsWith("/u/") ? "active" : ""}
          >
            <UserRound size={21} />
            我的
          </Link>
        ) : (
          <button onClick={() => onOpenAuth("login")}>
            <UserRound size={21} />
            登录
          </button>
        )}
      </nav>
    </>
  );
};
