import { Pencil, Trash2 } from "lucide-react";
import { LumiConversation, LumiConversations } from "../../api/client";
import { Button } from "../ui/Button";

export function ConversationList({ data, selected, loading, error, onSelect, onRename, onDelete, onReload, onMore }: {
  data?: LumiConversations; selected?: string; loading: boolean; error: string;
  onSelect: (id: string) => void; onRename: (row: LumiConversation) => void; onDelete: (row: LumiConversation) => void;
  onReload: () => void; onMore: () => void;
}) {
  return <div className="lumi-conversations-content">
    {error && <div className="lumi-conversations-error" role="alert"><p>{error}</p><Button size="sm" variant="ghost" onClick={onReload}>重新加载</Button></div>}
    {!data && loading && <p role="status" className="lumi-conversations-hint">正在加载对话…</p>}
    {data && !data.items.length && <p className="lumi-conversations-hint">还没有对话，开始聊聊吧。</p>}
    <ul aria-label="历史对话" className="lumi-conversations-list">
      {data?.items.map(row => <li key={row.id} data-conversation-id={row.id} className={row.id === selected ? "selected" : ""}>
        <button type="button" className="lumi-conversation-open" aria-current={row.id === selected ? "true" : undefined} title={row.title} onClick={() => onSelect(row.id)}>
          <span className="lumi-conversation-title">{row.title}</span><span className="lumi-conversation-meta">{row.active || data.activeSessionId === row.id ? "正在回答…" : new Intl.DateTimeFormat("zh-CN", { month: "numeric", day: "numeric" }).format(new Date(row.activityAt))}</span>
        </button>
        <div className="lumi-conversation-actions"><button type="button" aria-label={`重命名对话：${row.title}`} onClick={() => onRename(row)}><Pencil size={15} aria-hidden="true" /></button><button type="button" disabled={row.active || data.activeSessionId === row.id} title={row.active ? "请先停止回答再删除" : "删除对话"} aria-label={`删除对话：${row.title}`} onClick={() => onDelete(row)}><Trash2 size={15} aria-hidden="true" /></button></div>
      </li>)}
    </ul>
    {data?.hasMore && <Button size="sm" variant="ghost" isLoading={loading} onClick={onMore}>加载更多对话</Button>}
  </div>;
}
