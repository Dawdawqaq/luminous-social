import React, {
  createContext,
  useContext,
  useEffect,
  useMemo,
  useSyncExternalStore,
} from "react";
import { useAuth } from "./AuthContext";
import { notificationApi } from "../api/client";
import { UnreadCounter } from "../notification/UnreadCounter";
import { NOTIFICATIONS_CHANGED } from "../notification/notificationEvents";

const NotificationContext = createContext({
  unreadCount: 0,
  refresh: async () => {},
});
export function NotificationProvider({
  children,
}: {
  children: React.ReactNode;
}) {
  const { user, isAuthenticated } = useAuth();
  const identity = isAuthenticated && user ? user.id : "";
  const counter = useMemo(
    () =>
      new UnreadCounter(async () => {
        const controller = new AbortController();
        const timeout = window.setTimeout(() => controller.abort(), 5000);
        try {
          return (await notificationApi.getUnreadCount(controller.signal))
            .unreadCount;
        } finally {
          window.clearTimeout(timeout);
        }
      }),
    [],
  );
  const count = useSyncExternalStore(counter.subscribe, counter.getSnapshot);
  useEffect(() => {
    counter.activate(identity);
    if (!identity) return;
    const refresh = () => {
      if (document.visibilityState !== "hidden") void counter.refresh();
    };
    refresh();
    const timer = window.setInterval(refresh, 15000);
    window.addEventListener("focus", refresh);
    window.addEventListener(NOTIFICATIONS_CHANGED, refresh);
    document.addEventListener("visibilitychange", refresh);
    return () => {
      window.clearInterval(timer);
      window.removeEventListener("focus", refresh);
      window.removeEventListener(NOTIFICATIONS_CHANGED, refresh);
      document.removeEventListener("visibilitychange", refresh);
      counter.activate("");
    };
  }, [counter, identity]);
  return (
    <NotificationContext.Provider
      value={{
        unreadCount: counter.getIdentity() === identity ? count : 0,
        refresh: counter.refresh,
      }}
    >
      {children}
    </NotificationContext.Provider>
  );
}
export const useNotifications = () => useContext(NotificationContext);
