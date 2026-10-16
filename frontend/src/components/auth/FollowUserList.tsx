import React, { useEffect, useRef, useState } from "react";
import { Link } from "react-router-dom";
import { FollowUser, PageResponse } from "../../types";
import { userApi } from "../../api/client";
import { useAuth } from "../../context/AuthContext";
import { useFollowActions } from "../../context/FollowContext";
import { useToast } from "../../context/ToastContext";
import { Avatar } from "../ui/Avatar";
import { Button } from "../ui/Button";
import { ContentState } from "../common/ContentState";

interface Props {
  username: string;
  kind: "followers" | "following";
  own: boolean;
  onRequireAuth: () => void;
}
const empty = (): PageResponse<FollowUser> => ({items:[],page:1,size:20,total:0,hasNext:false});

export const FollowUserList: React.FC<Props> = ({username,kind,own,onRequireAuth}) => {
  const {user,isAuthenticated} = useAuth();
  const follows = useFollowActions();
  const {showToast} = useToast();
  const [page,setPage] = useState(1);
  const [data,setData] = useState<PageResponse<FollowUser>>(empty);
  const [loading,setLoading] = useState(true);
  const [error,setError] = useState("");
  const [retry,setRetry] = useState(0);
  const previousKey = useRef("");
  const label = kind === "followers" ? "粉丝" : "关注";
  useEffect(()=> {
    let cancelled=false;
    const key=`${username}:${kind}:${page}:${user?.id || "anonymous"}`;
    if(previousKey.current!==key) {setData(empty());previousKey.current=key;}
    setLoading(true);setError("");
    const load=kind === "followers" ? userApi.listFollowers(username,page) : userApi.listFollowing(username,page);
    load.then(result=> {
      if(cancelled) return;
      // 末页取消最后一个关注后回到上一页，避免留在空页。
      if(own && kind === "following" && page>1 && !result.items.length) {setPage(value=>value-1);return;}
      setData(result);
    }).catch(exception=> {if(!cancelled)setError(exception instanceof Error ? exception.message : "加载失败，请重试");})
      .finally(()=> {if(!cancelled)setLoading(false);});
    return ()=> {cancelled=true;};
  },[username,kind,page,user?.id,follows.revision,retry,own]);
  useEffect(()=> {
    const change=follows.change;
    if(!change) return;
    setData(previous=> {
      const remove=own && kind === "following" && !change.target.isFollowing;
      const present=previous.items.some(person=>person.id===change.target.id);
      return {...previous,total:remove && present ? Math.max(0,previous.total-1) : previous.total,
        items:previous.items.filter(person=>!(remove && person.id===change.target.id))
          .map(person=>person.id===change.target.id ? {...person,isFollowing:!!change.target.isFollowing} : person)};
    });
  },[follows.change,own,kind]);
  const toggle = async (person: FollowUser) => {
    if(!isAuthenticated) {onRequireAuth();return;}
    try {await follows.setFollowing(person,!person.isFollowing);}
    catch(exception) {showToast(exception instanceof Error ? exception.message : "关注操作失败","error");}
  };
  return <div aria-busy={loading}>
    {loading && !data.items.length ? <ContentState title={`正在加载${label}列表`} loading/> : data.items.length ? (
      <ul className="divide-y divide-ink-faint dark:divide-night-borderSubtle">
        {data.items.map(person=> <li key={person.id} data-follow-user-id={person.id} className="flex items-center gap-3 py-4">
          <Link to={`/u/${encodeURIComponent(person.username)}`} className="flex flex-1 min-w-0 items-center gap-3 rounded-md px-2 hover:bg-paper-100 dark:hover:bg-night-surfaceHover"
            aria-label={`查看 ${person.displayName || person.username} 的个人主页`}>
            <Avatar src={person.avatar} name={person.displayName || person.username} size="lg"/>
            <div className="min-w-0 flex-1">
              <p className="font-bold break-words line-clamp-2" title={person.displayName || person.username}>{person.displayName || person.username}</p>
              <p className="text-sm text-ink-muted dark:text-night-muted truncate" title={`@${person.username}`}>@{person.username}</p>
              {person.bio && <p className="mt-1 text-sm text-ink-muted dark:text-night-muted line-clamp-2 break-words">{person.bio}</p>}
            </div>
          </Link>
          {person.id!==user?.id && <Button size="sm" variant={person.isFollowing ? "outline" : "primary"}
            className="shrink-0 min-w-20" title={person.isFollowing ? "点击取消关注" : own && kind === "followers" ? "回关这个用户" : "关注这个用户"}
            aria-label={`${person.isFollowing ? "取消关注" : own && kind === "followers" ? "回关" : "关注"} ${person.displayName || person.username}`}
            disabled={follows.busy || follows.remainingSeconds>0} isLoading={follows.pendingUserId===person.id} onClick={()=>void toggle(person)}>
            {follows.remainingSeconds>0 ? `${follows.remainingSeconds} 秒后` : person.isFollowing ? "已关注" : own && kind === "followers" ? "回关" : "关注"}
          </Button>}
        </li>)}
      </ul>
    ) : !error && <ContentState showIcon={false} title={page>1 ? "这一页暂无用户" : kind === "followers" ? "还没有粉丝" : "还没有关注任何人"}
      description={page>1 ? "关注关系可能已变化，可以返回上一页查看。" : kind === "followers" ? own ? "关注你的人会出现在这里。" : "关注 TA 的人会出现在这里。" : own ? "你关注的人会出现在这里。" : "TA 关注的人会出现在这里。"}/>}
    {error && <ContentState title={`${label}列表加载失败`} description={error} onRetry={()=>setRetry(value=>value+1)}/>}
    <div className="flex flex-wrap items-center justify-between gap-3 py-4 hairline-t">
      <p className="text-sm text-ink-muted dark:text-night-muted" role="status">第 {page} 页{!loading && !error && ` · 共 ${data.total} 人`}</p>
      <div className="flex gap-2">
        <Button size="sm" variant="outline" disabled={loading || follows.busy || page===1} onClick={()=>setPage(value=>value-1)}>上一页</Button>
        <Button size="sm" variant="outline" disabled={loading || follows.busy || !!error || !data.hasNext} onClick={()=>setPage(value=>value+1)}>下一页</Button>
      </div>
    </div>
  </div>;
};
