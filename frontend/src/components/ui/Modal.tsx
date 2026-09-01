import React, { useEffect } from 'react';
import { X } from 'lucide-react';
import { cn } from '../../utils';

export interface ModalProps {
  isOpen: boolean;
  onClose: () => void;
  title?: string;
  subtitle?: string;
  children: React.ReactNode;
  maxWidth?: 'sm' | 'md' | 'lg' | 'xl' | '2xl';
}

export const Modal: React.FC<ModalProps> = ({
  isOpen,
  onClose,
  title,
  subtitle,
  children,
  maxWidth = 'md',
}) => {
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    if (isOpen) {
      document.body.style.overflow = 'hidden';
      window.addEventListener('keydown', handleKeyDown);
    }
    return () => {
      document.body.style.overflow = '';
      window.removeEventListener('keydown', handleKeyDown);
    };
  }, [isOpen, onClose]);

  if (!isOpen) return null;

  const maxWidths = {
    sm: 'max-w-sm',
    md: 'max-w-md',
    lg: 'max-w-lg',
    xl: 'max-w-2xl',
    '2xl': 'max-w-4xl',
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 overflow-y-auto">
      {/* Quiet Backdrop */}
      <div
        className="fixed inset-0 bg-ink-dark/40 dark:bg-black/60 backdrop-blur-[2px] transition-opacity animate-in fade-in"
        onClick={onClose}
      />

      {/* Modal Card */}
      <div
        className={cn(
          'relative w-full bg-paper-50 dark:bg-night-surface border border-paper-300 dark:border-night-border border-t-2 border-t-terracotta dark:border-t-terracotta-dark rounded-[2px] shadow-lifted z-10 my-8 overflow-hidden animate-in zoom-in-95 duration-150',
          maxWidths[maxWidth]
        )}
      >
        {/* Subtle Decorative Background Arches */}
        <div className="absolute top-0 right-0 w-48 h-48 pointer-events-none overflow-hidden opacity-[0.07] dark:opacity-[0.12] -mr-8 -mt-8" aria-hidden="true">
          <svg viewBox="0 0 200 200" fill="none" className="w-full h-full text-terracotta dark:text-terracotta-dark">
            <circle cx="160" cy="40" r="50" stroke="currentColor" strokeWidth="1" strokeDasharray="3 3" />
            <circle cx="160" cy="40" r="90" stroke="currentColor" strokeWidth="1" />
            <circle cx="160" cy="40" r="130" stroke="currentColor" strokeWidth="1" strokeDasharray="4 6" />
            <line x1="160" y1="40" x2="30" y2="40" stroke="currentColor" strokeWidth="1" strokeDasharray="2 4" />
            <line x1="160" y1="40" x2="60" y2="140" stroke="currentColor" strokeWidth="1" />
          </svg>
        </div>

        <div className="relative flex items-start justify-between p-5 hairline-b">
          <div>
            {title && (
              <h3 className="font-serif text-xl font-medium text-ink-primary dark:text-night-ink tracking-tight">
                {title}
              </h3>
            )}
            {subtitle && (
              <p className="text-xs text-ink-muted dark:text-night-muted font-sans mt-0.5">
                {subtitle}
              </p>
            )}
          </div>
          <button
            onClick={onClose}
            className="text-ink-subtle hover:text-ink-primary dark:text-night-subtle dark:hover:text-night-ink p-1 rounded transition-colors -mr-1 -mt-1 cursor-pointer"
          >
            <X className="w-4 h-4" />
          </button>
        </div>
        <div className="relative p-6">{children}</div>
      </div>
    </div>
  );
};
