import { ReactNode } from 'react';
import { Sidebar, TopNav } from './Navigation';
import { motion, AnimatePresence } from 'motion/react';
import { useState, useEffect } from 'react';
import { cn } from '../lib/utils';
import { CreatePostModal } from './CreatePostModal';
import { CreateCommunityModal } from './CreateCommunityModal';
import { useAuth } from '../auth/AuthContext';

interface LayoutProps {
  children: ReactNode;
}

export function Layout({ children }: LayoutProps) {
  const { user } = useAuth();
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [isCreateCommunityModalOpen, setIsCreateCommunityModalOpen] = useState(false);
  const [isSidebarOpen, setIsSidebarOpen] = useState(() => {
    const saved = localStorage.getItem('luminous_sidebar_open');
    if (saved !== null) {
      return saved === 'true';
    }
    return window.innerWidth >= 1024;
  });

  useEffect(() => {
    localStorage.setItem('luminous_sidebar_open', String(isSidebarOpen));
  }, [isSidebarOpen]);

  return (
    <div className="min-h-screen bg-surface">
      <TopNav onMenuClick={() => setIsSidebarOpen(!isSidebarOpen)} />
      <div className="max-w-[1440px] mx-auto flex">
        <Sidebar 
          aria-label="Main Navigation" 
          isOpen={isSidebarOpen} 
          onClose={() => setIsSidebarOpen(false)} 
          onToggle={() => setIsSidebarOpen(!isSidebarOpen)}
          onPostClick={() => {
            if (!user) {
              window.dispatchEvent(new CustomEvent('auth:required', { detail: { message: '您需要先登录，才能发布新帖子' } }));
              return;
            }
            setIsModalOpen(true);
          }}
          onCreateCommunityClick={() => {
            if (!user) {
              window.dispatchEvent(new CustomEvent('auth:required', { detail: { message: '您需要先登录，才能创建属于您自己的社区' } }));
              return;
            }
            setIsCreateCommunityModalOpen(true);
          }}
        />
        <main className={cn(
          "flex-1 px-6 py-8 min-h-screen pt-24 transition-all duration-300",
          isSidebarOpen ? "lg:ml-[280px]" : "ml-0"
        )}>
          <AnimatePresence mode="wait">
            {children}
          </AnimatePresence>
        </main>
      </div>

      <CreatePostModal 
        isOpen={isModalOpen} 
        onClose={() => setIsModalOpen(false)} 
      />
      <CreateCommunityModal 
        isOpen={isCreateCommunityModalOpen} 
        onClose={() => setIsCreateCommunityModalOpen(false)} 
      />
    </div>
  );
}

