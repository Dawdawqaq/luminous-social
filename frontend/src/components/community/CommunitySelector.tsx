import React, { useState } from "react";
import { Community } from "../../types";
import { useCommunityBrowse } from "../../hooks/useCommunityBrowse";
import { Avatar } from "../ui/Avatar";
import { Button } from "../ui/Button";

export const CommunitySelector: React.FC<{ value: Community | null; onSelect: (value: Community) => void; disabled?: boolean; revision?: string }> = ({ value, onSelect, disabled, revision }) => {
  const [expanded, setExpanded] = useState(!value);
  const [search, setSearch] = useState("");
  const [scope, setScope] = useState("joined");
  const browse = useCommunityBrowse(search, scope, "", expanded, revision);
  return <div className="space-y-3">
    <div className="flex items-center justify-between gap-3">
      <span className="font-semibold text-sm">发布到哪个社区</span>
      <Button size="sm" variant="ghost" disabled={disabled} aria-expanded={expanded} onClick={() => setExpanded(!expanded)}>{expanded ? "收起选择" : "更换社区"}</Button>
    </div>
    {value && <div className="flex items-center gap-2 text-sm"><Avatar src={value.icon} name={value.displayName || value.name} size="sm" /><strong className="break-words min-w-0">{value.displayName || value.name}</strong><span className="shrink-0 text-ink-muted dark:text-night-muted">{value.joined ? "已加入" : "尚未加入"}</span></div>}
    {expanded && <div className="border border-paper-300 dark:border-night-border rounded-md p-3 space-y-3">
      <input type="search" className="search-input w-full" aria-label="搜索发布社区" placeholder="搜索社区" maxLength={100} value={search} onChange={e => setSearch(e.target.value)} onKeyDown={event => {
        // 组合输入的回车留给输入法；普通搜索回车不能隐式提交外层发帖表单。
        if (event.key === "Enter" && !event.nativeEvent.isComposing && event.nativeEvent.keyCode !== 229) event.preventDefault();
      }} disabled={disabled} />
      <div className="flex gap-2">{[["joined", "已加入"], ["all", "全部社区"]].map(([id, label]) => <Button key={id} size="sm" variant={scope === id ? "primary" : "ghost"} aria-pressed={scope === id} disabled={disabled} onClick={() => setScope(id)}>{label}</Button>)}</div>
      {browse.loading ? <p role="status" className="text-sm">正在加载社区…</p> : browse.error ? <div role="alert" className="text-sm">{browse.error}<Button size="sm" onClick={browse.refresh}>重试</Button></div> : !browse.items.length ? <p className="text-sm text-ink-muted dark:text-night-muted">{scope === "joined" && !search ? "还没有加入社区，可以在全部社区中选择。" : "没有找到匹配的社区。"}</p> : <div className="max-h-44 overflow-y-auto divide-y divide-paper-200 dark:divide-night-border">{browse.items.map(item => <button key={item.id} type="button" disabled={disabled} aria-pressed={value?.id === item.id} className="flex w-full items-center gap-2 px-1 py-2 text-left text-sm hover:text-terracotta" onClick={() => { onSelect(item); setExpanded(false); }}><Avatar src={item.icon} name={item.displayName || item.name} size="sm" /><span className="min-w-0 break-words flex-1">{item.displayName || item.name}</span>{item.joined && <span className="text-xs shrink-0">已加入</span>}</button>)}</div>}
      {browse.moreError && <p role="alert" className="text-sm">{browse.moreError}</p>}
      {browse.hasNext && <Button size="sm" variant="ghost" onClick={browse.loadMore} isLoading={browse.loadingMore} disabled={disabled}>更多社区</Button>}
    </div>}
  </div>;
};
