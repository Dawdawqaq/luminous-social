import React from "react";
import { Navbar } from "./Navbar";
import { Sidebar } from "./Sidebar";
interface AppLayoutProps {
  children: React.ReactNode;
  onOpenAuth: (mode?: "login" | "register") => void;
  onOpenCreatePost: () => void;
  onOpenNotifications: () => void;
}
export const AppLayout: React.FC<AppLayoutProps> = ({
  children,
  ...actions
}) => (
  <div className="min-h-screen">
    <a
      href="#main-content"
      className="sr-only focus:not-sr-only focus:fixed focus:top-2 focus:left-2 focus:z-[100] focus:bg-white focus:p-3 focus:text-black"
    >
      跳转到主要内容
    </a>
    <Navbar {...actions} />
    <div className="site-frame">
      <Sidebar
        onOpenAuth={actions.onOpenAuth}
        onOpenCreatePost={actions.onOpenCreatePost}
      />
      <main id="main-content" className="page-main">
        {children}
      </main>
    </div>
  </div>
);
