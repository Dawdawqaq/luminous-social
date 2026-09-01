import React from 'react';
import { Navbar } from './Navbar';
import { Footer } from './Footer';
import { ArtisticBackground } from './ArtisticBackground';

interface AppLayoutProps {
  children: React.ReactNode;
  onOpenAuth: () => void;
  onOpenCreatePost: () => void;
  onOpenNotifications: () => void;
}

export const AppLayout: React.FC<AppLayoutProps> = ({
  children,
  onOpenAuth,
  onOpenCreatePost,
  onOpenNotifications,
}) => {
  return (
    <div className="relative min-h-screen flex flex-col bg-paper-100 dark:bg-night-canvas text-ink-primary dark:text-night-ink transition-colors duration-200 overflow-x-hidden">
      {/* Claude-inspired Organic Artistic & Celestial Background */}
      <ArtisticBackground />

      {/* Main Content Layer */}
      <div className="relative z-10 flex flex-col min-h-screen">
        <Navbar
          onOpenAuth={onOpenAuth}
          onOpenCreatePost={onOpenCreatePost}
          onOpenNotifications={onOpenNotifications}
        />
        <main className="flex-1 w-full max-w-6xl mx-auto px-4 sm:px-6 py-6 md:py-8">
          {children}
        </main>
        <Footer />
      </div>
    </div>
  );
};
