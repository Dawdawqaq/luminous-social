import { createContext, ReactNode, useContext, useEffect, useMemo, useState } from 'react';
import { api, setAuthToken } from '../lib/api';
import { User } from '../types';

interface AuthContextValue {
  user: User | null;
  loading: boolean;
  login: (username: string, password: string) => Promise<void>;
  register: (username: string, displayName: string, password: string) => Promise<void>;
  logout: () => void;
  updateProfile: (payload: { displayName?: string; bio?: string; avatar?: string; banner?: string }) => Promise<void>;
  refreshUser: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    api.me()
      .then(setUser)
      .catch(() => setAuthToken(null))
      .finally(() => setLoading(false));
  }, []);

  const value = useMemo<AuthContextValue>(() => ({
    user,
    loading,
    async login(username, password) {
      const response = await api.login({ username, password });
      setAuthToken(response.token);
      setUser(response.user);
    },
    async register(username, displayName, password) {
      const response = await api.register({ username, displayName, password });
      setAuthToken(response.token);
      setUser(response.user);
    },
    async updateProfile(payload) {
      const updatedUser = await api.updateProfile(payload);
      setUser(updatedUser);
    },
    async refreshUser() {
      if (localStorage.getItem('luminous_token')) {
        const u = await api.me();
        setUser(u);
      }
    },
    logout() {
      setAuthToken(null);
      setUser(null);
    }
  }), [user, loading]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const value = useContext(AuthContext);
  if (!value) {
    throw new Error('useAuth must be used inside AuthProvider');
  }
  return value;
}
