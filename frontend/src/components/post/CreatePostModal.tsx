import React, { useEffect, useRef, useState } from "react";
import { ImagePlus, Eye, X } from "lucide-react";
import { Modal } from "../ui/Modal";
import { Input } from "../ui/Input";
import { Button } from "../ui/Button";
import { EmoteEditor, EmoteEditorHandle } from "../emotes/EmoteEditor";
import { CatEmotePicker } from "../emotes/CatEmotePicker";
import { MarkdownRenderer } from "../ui/MarkdownRenderer";
import { CommunitySelector } from "../community/CommunitySelector";
import { useAuth } from "../../context/AuthContext";
import { useToast } from "../../context/ToastContext";
import { ApiError, communityApi, postApi, uploadApi } from "../../api/client";
import { Community, Post } from "../../types";
import { PostDraft, readPostDraft, savePostDraft } from "../../posting/postDrafts";

interface Props { isOpen: boolean; onClose: () => void; onPostCreated: (post: Post) => void; defaultCommunity?: string }
const blank = (community = ""): PostDraft => ({ community, title: "", content: "", image: "", file: null, updatedAt: Date.now() });
const hasContent = (draft: PostDraft) => !!(draft.title.trim() || draft.content.trim() || draft.file || draft.image);
const imageTypes = ["image/jpeg", "image/png", "image/gif", "image/webp"];

export const CreatePostModal: React.FC<Props> = ({ isOpen, onClose, onPostCreated, defaultCommunity }) => {
  const { user } = useAuth();
  const { showToast } = useToast();
  const key = `${user?.id || "guest"}:${defaultCommunity || "general"}`;
  const [draft, setDraft] = useState<PostDraft>(blank());
  const [ready, setReady] = useState(false);
  const [selected, setSelected] = useState<Community | null>(null);
  const [communityError, setCommunityError] = useState("");
  const [saving, setSaving] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [joining, setJoining] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [preview, setPreview] = useState(false);
  const [imagePreview, setImagePreview] = useState("");
  const [closing, setClosing] = useState(false);
  const [deadline, setDeadline] = useState(0);
  const [now, setNow] = useState(Date.now());
  const editor = useRef<EmoteEditorHandle>(null);
  const fileInput = useRef<HTMLInputElement>(null);
  const pending = useRef(false);
  const generation = useRef(0);
  const draftRef = useRef(draft); draftRef.current = draft;
  const remaining = Math.max(0, Math.ceil((deadline - now) / 1000));
  const change = (patch: Partial<PostDraft>) => { setDraft(previous => ({ ...previous, ...patch, updatedAt: Date.now() })); setError(""); };
  useEffect(() => {
    const version = ++generation.current;
    setReady(false); setSelected(null); setError(""); setCommunityError(""); setClosing(false); setPreview(false);
    if (!isOpen || !user) return;
    readPostDraft(key).then(saved => {
      if (generation.current !== version) return;
      setDraft(saved || blank(defaultCommunity)); setSaving(saved && hasContent(saved) ? "已恢复此设备上的草稿" : ""); setReady(true);
    }).catch(() => { if (generation.current === version) { setDraft(blank(defaultCommunity)); setSaving("此浏览器无法保存草稿，请勿刷新页面"); setReady(true); } });
    let stored = 0;
    try { stored = Number(localStorage.getItem(`luminous:post-cooldown:${user.id}`)) || 0; } catch { /* 浏览器存储不可用时仍由后端校验。 */ }
    setDeadline(stored); setNow(Date.now());
    return () => { ++generation.current; };
  }, [isOpen, key, user?.id, defaultCommunity]);
  useEffect(() => {
    if (!isOpen || !ready) return;
    const version = generation.current;
    const timer = window.setTimeout(() => {
      savePostDraft(key, hasContent(draft) ? draft : undefined).then(() => { if (version === generation.current) setSaving(hasContent(draft) ? "草稿已保存在此设备" : ""); }).catch(() => { if (version === generation.current) setSaving("草稿保存失败，请勿刷新页面"); });
    }, 300);
    return () => window.clearTimeout(timer);
  }, [draft, key, ready, isOpen]);
  useEffect(() => {
    if (!isOpen || !ready || !draft.community) return;
    let active = true;
    setSelected(null); setCommunityError("");
    communityApi.getBySlug(draft.community).then(result => { if (active) setSelected(result); }).catch(err => { if (active) setCommunityError(err instanceof Error ? err.message : "社区加载失败"); });
    return () => { active = false; };
  }, [draft.community, ready, isOpen, user?.id]);
  useEffect(() => {
    if (!draft.file) { setImagePreview(draft.image); return; }
    const url = URL.createObjectURL(draft.file); setImagePreview(url);
    return () => URL.revokeObjectURL(url);
  }, [draft.file, draft.image]);
  useEffect(() => { if (!isOpen || deadline <= Date.now()) return; const timer = window.setInterval(() => setNow(Date.now()), 250); return () => window.clearInterval(timer); }, [deadline, isOpen]);
  useEffect(() => {
    if (!isOpen || !ready || !hasContent(draft)) return;
    const protect = (event: BeforeUnloadEvent) => { event.preventDefault(); };
    window.addEventListener("beforeunload", protect);
    return () => window.removeEventListener("beforeunload", protect);
  }, [isOpen, ready, draft]);
  const close = () => { if (pending.current || joining) return; if (hasContent(draft)) setClosing(true); else onClose(); };
  const finishClose = async (keep: boolean) => {
    try { await savePostDraft(key, keep ? draftRef.current : undefined); if (!keep) setDraft(blank(defaultCommunity)); setClosing(false); onClose(); }
    catch {
      if (!keep) { setDraft(blank(defaultCommunity)); setClosing(false); onClose(); showToast("已关闭编辑，本地存储不可用，旧草稿可能仍会保留", "error"); }
      else setError("草稿保存失败，请继续编辑或选择放弃草稿。");
    }
  };
  const selectImage = (files: File[]) => {
    if (pending.current || !files.length) return;
    if (files.length > 1) { setError("每篇帖子最多一张图片，请一次选择一张。"); return; }
    const file = files[0];
    if (!imageTypes.includes(file.type) || !file.size || file.size > 10 * 1024 * 1024) { setError("请选择不超过10 MB的 JPG、PNG、GIF 或 WebP 图片。"); return; }
    change({ file, image: "" });
  };
  const join = async () => {
    if (!selected || joining) return;
    setJoining(true);
    try { setSelected(await communityApi.join(selected.name)); window.dispatchEvent(new Event("luminous:communities-changed")); }
    catch (err) { setError(err instanceof Error ? err.message : "加入失败，请重试"); }
    finally { setJoining(false); }
  };
  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    if (pending.current || !selected?.joined || remaining || !draft.title.trim() || (!draft.content.trim() && !draft.file && !draft.image)) return;
    pending.current = true; setBusy(true); setError("");
    const version = generation.current;
    try {
      let image = draft.image;
      if (draft.file && !image) { setUploading(true); image = (await uploadApi.uploadImage(draft.file)).url; change({ image }); setUploading(false); }
      if (version !== generation.current) return;
      const post = await postApi.create({ community: selected.name, title: draft.title.trim(), content: draft.content.trim(), image: image || undefined });
      if (version !== generation.current) return;
      const next = Date.now() + 30000; setDeadline(next); setNow(Date.now());
      try { localStorage.setItem(`luminous:post-cooldown:${user!.id}`, String(next)); } catch { /* 后端额度仍然生效。 */ }
      setDraft(blank(defaultCommunity));
      try { await savePostDraft(key); } catch { /* 已发布内容不因本地清理失败重复提交。 */ }
      showToast("帖子已发布", "success"); onClose(); onPostCreated(post);
    } catch (err) {
      if (version !== generation.current) return;
      if (err instanceof ApiError && err.code === "POST_RATE_LIMITED") { setDeadline(Date.now() + (err.retryAfterSeconds || 30) * 1000); setNow(Date.now()); }
      if (err instanceof ApiError && err.code === "COMMUNITY_JOIN_REQUIRED") setSelected(previous => previous ? { ...previous, joined: false } : previous);
      setError(err instanceof Error ? err.message : "发布失败，草稿已保留，请重试");
    } finally { pending.current = false; setBusy(false); setUploading(false); }
  };
  return <Modal isOpen={isOpen} onClose={close} dismissible={!busy && !joining} title="分享新发现" subtitle="日常、问题、作品，都可以聊聊。" maxWidth="xl">
    {!ready ? <p role="status">正在恢复编辑内容…</p> : <form onSubmit={submit} className="space-y-5">
      {closing ? <div role="alert" className="space-y-3"><h3 className="font-bold">要保留这篇草稿吗？</h3><p className="text-sm text-ink-muted dark:text-night-muted">文字和图片可以保存在此设备，下次继续编辑。</p><div className="flex flex-wrap gap-3"><Button size="sm" onClick={() => finishClose(true)}>保留并关闭</Button><Button size="sm" variant="outline" onClick={() => setClosing(false)}>继续编辑</Button><Button size="sm" variant="ghost" onClick={() => finishClose(false)}>放弃草稿</Button></div></div> : <>
        <CommunitySelector key={key} value={selected} disabled={busy || joining} revision={user?.id} onSelect={item => { change({ community: item.name }); setSelected(item); }} />
        {communityError && <p role="alert" className="text-sm">{communityError}，请重新选择社区。</p>}
        {selected && !selected.joined && <div className="flex flex-wrap items-center gap-3 text-sm"><span>加入社区后即可发布，已填写内容会保留。</span><Button size="sm" variant="secondary" isLoading={joining} onClick={join}>加入社区</Button></div>}
        <Input label="标题" placeholder="用一句短标题概括这次分享" value={draft.title} onChange={e => change({ title: e.target.value })} maxLength={15} hint={`${draft.title.length}/15 字符`} required disabled={busy} />
        <div className="relative"><Button className="absolute -top-2 right-0 z-10" variant="ghost" size="sm" aria-pressed={preview} onClick={() => setPreview(!preview)} disabled={busy}><Eye size={16} />{preview ? "继续编辑" : "预览"}</Button>
        {preview ? <div className="min-h-40 pt-8"><p className="font-semibold text-sm mb-3">正文预览</p><MarkdownRenderer content={draft.content || "还没有正文，可以添加图片分享。"} /></div> : <EmoteEditor ref={editor} label="正文" maxLength={1000} rows={5} placeholder="分享你的发现，也可以只发一张图片…" value={draft.content} onChange={content => change({ content })} disabled={busy} onError={setError} onPaste={event => { const files = Array.from(event.clipboardData.files).filter(file => file.type.startsWith("image/")); if (files.length) { event.preventDefault(); selectImage(files); } }} hint={`${draft.content.length}/1000 字符 · 正文和图片至少有一项`} />}</div>
        {imagePreview && <div className="relative w-max max-w-full"><img src={imagePreview} alt="待发布图片" className="max-h-40 max-w-full rounded-md object-contain" /><Button size="sm" variant="outline" className="absolute right-1 top-1 bg-white dark:bg-night-surface" aria-label="移除帖子图片" disabled={busy} onClick={() => change({ file: null, image: "" })}><X size={16} /></Button></div>}
        <div className="sticky bottom-0 bg-white dark:bg-night-surface py-3 hairline-t space-y-3">
          <div className="flex flex-wrap items-center justify-between gap-3"><div className="flex items-center gap-3"><CatEmotePicker onSelect={emote => editor.current?.insertEmote(emote)} disabled={preview || busy} /><Button size="sm" variant="ghost" disabled={busy} onClick={() => fileInput.current?.click()}><ImagePlus size={19} />{imagePreview ? "替换图片" : "添加图片"}</Button><input ref={fileInput} type="file" className="hidden" accept={imageTypes.join(",")} aria-label="添加帖子图片" disabled={busy} onChange={event => { selectImage(Array.from(event.target.files || [])); event.target.value = ""; }} /></div><Button type="submit" size="sm" isLoading={busy} disabled={remaining > 0 || !selected?.joined || !draft.title.trim() || (!draft.content.trim() && !draft.file && !draft.image) || draft.title.length > 15 || draft.content.length > 1000}>{uploading ? "上传图片中…" : remaining ? `${remaining} 秒后可发布` : error && draft.file && !draft.image ? "重试发布" : "发布帖子"}</Button></div>
          <p role="status" className="text-xs text-ink-muted dark:text-night-muted">{saving || "最多一张图片，支持粘贴，最大10 MB。"}</p>
        </div>
      </>}
      {error && <p role="alert" className="text-sm text-red-600 dark:text-red-400">{error}</p>}
    </form>}
  </Modal>;
};
