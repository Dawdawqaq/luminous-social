import { useCallback, useEffect, useRef, useState } from "react";
import { notificationApi } from "../api/client";
import { Notification, NotificationSummary } from "../types";

interface InboxPage { items: Notification[]; cursor: string | null; hasNext: boolean; loading: boolean; loaded: boolean; error: string; scrollTop: number }
const empty = (): InboxPage => ({items:[],cursor:null,hasNext:false,loading:false,loaded:false,error:"",scrollTop:0});

/** 分类保留分页与滚动位置，账号或窗口变化时隔离迟到响应。 */
export function useNotificationInbox(identity: string, enabled: boolean, type: string) {
  const pages = useRef(new Map<string,InboxPage>());
  const epoch = useRef(0);
  const selectedType = useRef(type); selectedType.current=type;
  const [,render] = useState(0);
  const [summary,setSummary] = useState<NotificationSummary | null>(null);
  const [hasUpdates,setHasUpdates] = useState(false);
  const baseline = useRef<string | null>(null);
  const summarySequence = useRef(0);
  const refreshSummary = useCallback(async (acceptRevision = false) => {
    const run = epoch.current, sequence = ++summarySequence.current;
    const result = await notificationApi.summary();
    if(run !== epoch.current || sequence !== summarySequence.current) return;
    setSummary(result);
    if(baseline.current === null || acceptRevision) { baseline.current = result.revision; setHasUpdates(false); }
    else if(result.revision !== baseline.current) setHasUpdates(true);
  }, []);
  const load = useCallback(async (category: string, reset = false) => {
    let page = pages.current.get(category) || empty();
    if(page.loading) return;
    if(!reset && page.loaded && !page.hasNext) return;
    if(reset) page = empty();
    page = {...page,loading:true,error:""}; pages.current.set(category,page); render(n=>n+1);
    const run = epoch.current;
    try {
      const result = await notificationApi.inbox(category,reset ? null : page.cursor);
      if(run !== epoch.current) return;
      const current = pages.current.get(category) || page;
      const unique = new Map(current.items.map(item=>[item.id,item]));
      result.items.forEach(item=> { if(!unique.has(item.id)) unique.set(item.id,item); });
      pages.current.set(category,{...current,items:[...unique.values()],cursor:result.nextCursor,hasNext:result.hasNext,loaded:true,loading:false});
    } catch(error) {
      if(run === epoch.current) pages.current.set(category,{...(pages.current.get(category)||page),loading:false,error:error instanceof Error ? error.message : "通知加载失败，请重试"});
    } finally { if(run === epoch.current) render(n=>n+1); }
  }, []);
  useEffect(()=> {
    epoch.current++; pages.current.clear(); baseline.current=null; setSummary(null); setHasUpdates(false); render(n=>n+1);
    if(!enabled) return;
    void refreshSummary().catch(()=>{});
    const timer = window.setInterval(()=> { if(document.visibilityState === "visible") void refreshSummary().catch(()=>{}); },15000);
    return ()=> { epoch.current++; window.clearInterval(timer); };
  }, [identity,enabled,refreshSummary]);
  useEffect(()=> { if(enabled && !pages.current.get(type)?.loaded) void load(type); },[type,identity,enabled,load]);
  const patch = (update: (items: Notification[])=>Notification[]) => {
    pages.current.forEach((page,key)=>pages.current.set(key,{...page,items:update(page.items)})); render(n=>n+1);
  };
  return {page:pages.current.get(type)||empty(),summary,hasUpdates,patch,refreshSummary,
    loadMore:()=>load(type),
    reload:async()=> { epoch.current++; pages.current.clear(); await load(selectedType.current,true); await refreshSummary(true); },
    saveScroll:(top:number)=> { const page=pages.current.get(type); if(page) page.scrollTop=top; },
  };
}
