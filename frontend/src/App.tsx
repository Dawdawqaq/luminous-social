import React, { useState } from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { ThemeProvider } from './context/ThemeContext';
import { ToastProvider } from './context/ToastContext';
import { NotificationProvider } from './context/NotificationContext';
import { AuthProvider } from './context/AuthContext';
import { AppLayout } from './components/layout/AppLayout';

import { HomePage } from './pages/HomePage';
import { PostDetailPage } from './pages/PostDetailPage';
import { SalonsPage } from './pages/SalonsPage';
import { SalonDetailPage } from './pages/SalonDetailPage';
import { UserProfilePage } from './pages/UserProfilePage';
import { BookmarksPage } from './pages/BookmarksPage';
import { AdminPage } from './pages/AdminPage';
import { NotFoundPage } from './pages/NotFoundPage';

import { useAuth } from './context/AuthContext';
import { AuthModal } from './components/auth/AuthModal';
import { CreatePostModal } from './components/post/CreatePostModal';
import { CreateSalonModal } from './components/community/CreateSalonModal';
import { NotificationDrawer } from './components/notification/NotificationDrawer';
import { ScrollToTop } from './components/common/ScrollToTop';

export function AppContent() {
  const { isAuthenticated } = useAuth();
  const [isAuthOpen, setIsAuthOpen] = useState(false);
  const [authMode, setAuthMode] = useState<'login' | 'register'>('login');
  const [isCreatePostOpen, setIsCreatePostOpen] = useState(false);
  const [createPostCommunity, setCreatePostCommunity] = useState<string | undefined>(undefined);
  const [isCreateSalonOpen, setIsCreateSalonOpen] = useState(false);
  const [isNotificationsOpen, setIsNotificationsOpen] = useState(false);

  const openAuth = (mode: 'login' | 'register' = 'login') => {
    setAuthMode(mode);
    setIsAuthOpen(true);
  };

  const openCreatePost = (communityName?: string) => {
    if (!isAuthenticated) {
      openAuth('login');
      return;
    }
    setCreatePostCommunity(communityName);
    setIsCreatePostOpen(true);
  };

  const openCreateSalon = () => {
    if (!isAuthenticated) {
      openAuth('login');
      return;
    }
    setIsCreateSalonOpen(true);
  };

  return (
    <AppLayout
      onOpenAuth={() => openAuth('login')}
      onOpenCreatePost={() => openCreatePost()}
      onOpenNotifications={() => setIsNotificationsOpen(true)}
    >
      <Routes>
        <Route
          path="/"
          element={
            <HomePage
              onRequireAuth={() => openAuth('login')}
              onOpenCreateSalon={openCreateSalon}
              onOpenCreatePost={() => openCreatePost()}
            />
          }
        />
        <Route
          path="/post/:id"
          element={<PostDetailPage onRequireAuth={() => openAuth('login')} />}
        />
        <Route
          path="/salons"
          element={<SalonsPage onOpenCreateSalon={openCreateSalon} />}
        />
        <Route
          path="/s/:slug"
          element={
            <SalonDetailPage
              onRequireAuth={() => openAuth('login')}
              onOpenCreatePost={(slug) => openCreatePost(slug)}
            />
          }
        />
        <Route
          path="/s/c/:slug"
          element={
            <SalonDetailPage
              onRequireAuth={() => openAuth('login')}
              onOpenCreatePost={(slug) => openCreatePost(slug)}
            />
          }
        />
        <Route
          path="/c/:slug"
          element={
            <SalonDetailPage
              onRequireAuth={() => openAuth('login')}
              onOpenCreatePost={(slug) => openCreatePost(slug)}
            />
          }
        />
        <Route
          path="/u/:username"
          element={<UserProfilePage onRequireAuth={() => openAuth('login')} />}
        />
        <Route
          path="/archive"
          element={<BookmarksPage onRequireAuth={() => openAuth('login')} />}
        />
        <Route path="/admin" element={<AdminPage />} />
        <Route path="/404" element={<NotFoundPage />} />
        <Route path="*" element={<Navigate to="/404" replace />} />
      </Routes>

      {/* Global Modals & Drawers */}
      <AuthModal
        isOpen={isAuthOpen}
        onClose={() => setIsAuthOpen(false)}
        defaultMode={authMode}
      />

      <CreatePostModal
        isOpen={isCreatePostOpen}
        onClose={() => {
          setIsCreatePostOpen(false);
          setCreatePostCommunity(undefined);
        }}
        defaultCommunity={createPostCommunity}
        onPostCreated={() => {
          // Trigger refresh if on homepage
          if (window.location.pathname === '/') {
            window.location.reload();
          }
        }}
      />

      <CreateSalonModal
        isOpen={isCreateSalonOpen}
        onClose={() => setIsCreateSalonOpen(false)}
        onCreated={(newSlug) => {
          window.location.href = `/s/${newSlug}`;
        }}
      />

      <NotificationDrawer
        isOpen={isNotificationsOpen}
        onClose={() => setIsNotificationsOpen(false)}
      />
    </AppLayout>
  );
}

export default function App() {
  return (
    <ThemeProvider>
      <ToastProvider>
        <AuthProvider>
          <NotificationProvider>
          <BrowserRouter>
            <ScrollToTop />
            <AppContent />
          </BrowserRouter>
          </NotificationProvider>
        </AuthProvider>
      </ToastProvider>
    </ThemeProvider>
  );
}
