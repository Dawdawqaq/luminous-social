import React, {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useRef,
  useState,
} from "react";
import { communityApi } from "../api/client";
import { Community } from "../types";
import { useAuth } from "./AuthContext";

interface CommunityState {
  communities: Community[];
  isLoading: boolean;
  error: string;
  refresh: () => void;
}
const CommunityContext = createContext<CommunityState | null>(null);
export const CommunityProvider: React.FC<{ children: React.ReactNode }> = ({
  children,
}) => {
  const { user } = useAuth();
  const [communities, setCommunities] = useState<Community[]>([]);
  const [isLoading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const generation = useRef(0);
  const refresh = useCallback(() => {
    const requestId = ++generation.current;
    setLoading(true);
    setError("");
    Promise.all([communityApi.browse({ size: 12 }), communityApi.browse({ scope: "joined", size: 12 })])
      .then(([all, joined]) => {
        const unique = new Map([...joined.items, ...all.items].map(item => [item.id, item]));
        if (requestId === generation.current) setCommunities([...unique.values()]);
      })
      .catch((err) => {
        if (requestId === generation.current)
          setError(err instanceof Error ? err.message : "社区加载失败");
      })
      .finally(() => {
        if (requestId === generation.current) setLoading(false);
      });
  }, []);
  useEffect(() => {
    setCommunities([]);
    refresh();
    window.addEventListener("luminous:communities-changed", refresh);
    return () => {
      ++generation.current;
      window.removeEventListener("luminous:communities-changed", refresh);
    };
  }, [refresh, user?.id]);
  return (
    <CommunityContext.Provider
      value={{ communities, isLoading, error, refresh }}
    >
      {children}
    </CommunityContext.Provider>
  );
};
export const useCommunities = () => {
  const state = useContext(CommunityContext);
  if (!state) throw new Error("社区上下文未初始化");
  return state;
};
