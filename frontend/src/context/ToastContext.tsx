import React, { createContext, useContext, useState, useCallback } from 'react';
import { CheckCircle2, AlertCircle, Info, X } from 'lucide-react';

export type ToastType = 'success' | 'error' | 'info' | 'warning';

export interface Toast {
  id: string;
  title?: string;
  message: string;
  type: ToastType;
}

interface ToastContextType {
  showToast: (message: string, type?: ToastType, title?: string) => void;
  success: (message: string, title?: string) => void;
  error: (message: string, title?: string) => void;
  info: (message: string, title?: string) => void;
}

const ToastContext = createContext<ToastContextType | undefined>(undefined);

export const ToastProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [toasts, setToasts] = useState<Toast[]>([]);

  const removeToast = useCallback((id: string) => {
    setToasts((prev) => prev.filter((t) => t.id !== id));
  }, []);

  const showToast = useCallback((message: string, type: ToastType = 'info', title?: string) => {
    const id = Math.random().toString(36).substring(2, 9);
    const newToast: Toast = { id, message, type, title };
    setToasts((prev) => [...prev, newToast]);

    setTimeout(() => {
      removeToast(id);
    }, 4200);
  }, [removeToast]);

  const success = useCallback((msg: string, title?: string) => showToast(msg, 'success', title), [showToast]);
  const error = useCallback((msg: string, title?: string) => showToast(msg, 'error', title), [showToast]);
  const info = useCallback((msg: string, title?: string) => showToast(msg, 'info', title), [showToast]);

  return (
    <ToastContext.Provider value={{ showToast, success, error, info }}>
      {children}
      {/* Toast Render Container */}
      <div className="fixed bottom-6 right-6 z-50 flex flex-col gap-2.5 max-w-md pointer-events-none">
        {toasts.map((toast) => {
          const isError = toast.type === 'error';
          const isSuccess = toast.type === 'success';

          return (
            <div
              key={toast.id}
              className={`pointer-events-auto flex items-start gap-3 px-4 py-3.5 bg-paper-50 dark:bg-night-surface border border-paper-300 dark:border-night-border rounded-[2px] shadow-lifted transition-all duration-200 animate-in fade-in slide-in-from-bottom-2 ${
                isError ? 'border-l-4 border-l-terracotta' : isSuccess ? 'border-l-4 border-l-[#5B8266]' : ''
              }`}
            >
              <div className="mt-0.5 shrink-0">
                {isSuccess && <CheckCircle2 className="w-4 h-4 text-[#5B8266]" />}
                {isError && <AlertCircle className="w-4 h-4 text-terracotta" />}
                {!isSuccess && !isError && <Info className="w-4 h-4 text-ink-muted dark:text-night-muted" />}
              </div>
              <div className="flex-1 text-xs">
                {toast.title && (
                  <h4 className="font-serif font-medium text-sm text-ink-primary dark:text-night-ink mb-0.5">
                    {toast.title}
                  </h4>
                )}
                <p className="text-ink-muted dark:text-night-muted leading-relaxed font-sans">{toast.message}</p>
              </div>
              <button
                onClick={() => removeToast(toast.id)}
                className="text-ink-subtle hover:text-ink-primary dark:text-night-subtle dark:hover:text-night-ink transition-colors p-0.5 -mr-1 -mt-0.5"
              >
                <X className="w-3.5 h-3.5" />
              </button>
            </div>
          );
        })}
      </div>
    </ToastContext.Provider>
  );
};

export const useToast = () => {
  const context = useContext(ToastContext);
  if (!context) {
    throw new Error('useToast must be used within a ToastProvider');
  }
  return context;
};
