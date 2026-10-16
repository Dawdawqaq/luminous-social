import React, { createContext, useContext, useEffect, useRef, useState } from "react";
import { ApiError, authApi, userApi } from "../api/client";
import { User } from "../types";
import { useAuth } from "./AuthContext";
import { useToast } from "./ToastContext";

interface FollowChange { target: User; actorStats?: User["stats"] }
interface FollowState {
  revision: number;
  change: FollowChange | null;
  pendingUserId: string | null;
  busy: boolean;
  remainingSeconds: number;
  setFollowing: (target: Pick<User,"id" | "username">, following: boolean) => Promise<void>;
}
const Context = createContext<FollowState | null>(null);

/** 所有关注入口共用请求锁、冷却和变更通知，成功后按服务端事实同步资料。 */
export const FollowProvider: React.FC<{children: React.ReactNode}> = ({children}) => {
  const {user,token,updateUser} = useAuth();
  const {showToast} = useToast();
  const identity = useRef(user?.id); identity.current=user?.id;
  const currentToken = useRef(token); currentToken.current=token;
  const pending = useRef<symbol | null>(null);
  const deadline = useRef(0);
  const [cooldown,setCooldown] = useState(0);
  const [remainingSeconds,setRemainingSeconds] = useState(0);
  const [pendingUserId,setPendingUserId] = useState<string | null>(null);
  const [revision,setRevision] = useState(0);
  const [change,setChange] = useState<FollowChange | null>(null);
  useEffect(()=> {
    pending.current=null; deadline.current=0; setCooldown(0); setRemainingSeconds(0); setPendingUserId(null); setChange(null);
    setRevision(value=>value+1);
  },[user?.id]);
  useEffect(()=> {
    const seconds=()=>Math.max(0,Math.ceil((cooldown-Date.now())/1000));
    setRemainingSeconds(seconds());
    if(cooldown<=Date.now()) return;
    const timer=window.setInterval(()=> { const left=seconds(); setRemainingSeconds(left); if(!left) window.clearInterval(timer); },100);
    return ()=>window.clearInterval(timer);
  },[cooldown]);
  const wait = (seconds: number) => {
    deadline.current=Math.max(deadline.current,Date.now()+seconds*1000); setCooldown(deadline.current);
  };
  const setFollowing: FollowState["setFollowing"] = async (target, following) => {
    if(!user || !token) throw new Error("请先登录");
    if(pending.current || deadline.current>Date.now()) return;
    const actor=user.id,session=token,operation=Symbol(); pending.current=operation; setPendingUserId(target.id);
    try {
      const result=following ? await userApi.follow(target.username) : await userApi.unfollow(target.username);
      if(identity.current!==actor || currentToken.current!==session) return;
      wait(1); setChange({target:result}); setRevision(value=>value+1);
      // 同步失败不撤回已经成功的关注，也不因为统计刷新失败退出登录。
      const [own,peer]=await Promise.allSettled([authApi.me(),userApi.getProfile(target.username)]);
      if(identity.current!==actor || currentToken.current!==session) return;
      const actorStats=own.status==="fulfilled" && own.value.id===actor ? own.value.stats : undefined;
      if(own.status==="fulfilled" && own.value.id===actor) updateUser(own.value);
      setChange({target:peer.status==="fulfilled" ? peer.value : result,actorStats});
      if(own.status==="rejected" || peer.status==="rejected") showToast("关注状态已更新，人数稍后刷新","info");
    } catch(error) {
      if(identity.current===actor && currentToken.current===session && error instanceof ApiError && error.status===429)
        wait(error.retryAfterSeconds || 1);
      throw error;
    } finally {
      if(pending.current===operation) { pending.current=null; setPendingUserId(null); }
    }
  };
  return <Context.Provider value={{revision,change,pendingUserId,busy:pendingUserId!==null,remainingSeconds,setFollowing}}>{children}</Context.Provider>;
};

export function useFollowActions() {
  const value=useContext(Context);
  if(!value) throw new Error("关注操作必须在 FollowProvider 内使用");
  return value;
}
