import React, { useEffect, useRef, useState } from "react";
import { ImagePlus, X, AtSign } from "lucide-react";
import { Comment } from "../../types";
import { ApiError, postApi } from "../../api/client";
import { useCommentCooldown } from "../../hooks/useCommentCooldown";
import { useToast } from "../../context/ToastContext";
import { COMMENT_IMAGE_TYPES, useCommentImage } from "../../hooks/useCommentImage";
import { EmoteEditor, EmoteEditorHandle } from "../emotes/EmoteEditor";
import { Button } from "../ui/Button";
import { CatEmotePicker } from "../emotes/CatEmotePicker";

interface Props {
  postId: string | number;
  target?: Comment;
  onAdded: (comment: Comment) => void;
  onCancel?: () => void;
  onBusyChange?: (busy: boolean) => void;
}

export const CommentComposer: React.FC<Props> = ({ postId, target, onAdded, onCancel, onBusyChange }) => {
  const { showToast } = useToast();
  const cooldown = useCommentCooldown();
  const [content, setContent] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [replyError, setReplyError] = useState("");
  const image = useCommentImage(submitting);
  const imageInput = useRef<HTMLInputElement>(null);
  const textarea = useRef<EmoteEditorHandle>(null);
  const submitLock = useRef(false);
  const isReply = !!target;
  const maxLength = isReply ? 150 : 300;
  const label = !target ? "发表看法" : target.parentId
    ? `回复 @${target.author.displayName || target.author.username}` : "回复这条评论";

  useEffect(() => {
    if (target) textarea.current?.focus();
    setReplyError("");
  }, [target?.id]);

  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    if (submitLock.current || cooldown.remaining > 0 || (!content.trim() && (isReply || !image.file))) return;
    if (content.length > maxLength) {
      setReplyError(`${isReply ? "楼中楼回复" : "评论"}最多 ${maxLength} 个字符。`);
      return;
    }
    submitLock.current = true;
    setSubmitting(true);
    onBusyChange?.(true);
    setReplyError("");
    try {
      setUploading(!isReply && !!image.file);
      const imageUrl = isReply ? undefined : await image.upload();
      setUploading(false);
      const created = await postApi.createComment(postId, content.trim(), imageUrl, target?.id);
      cooldown.impose(5);
      setContent("");
      image.clear();
      onAdded(created);
      showToast(isReply ? "回复已发布" : "评论已发布", "success");
    } catch (err) {
      const rateLimited = err instanceof ApiError && err.code === "COMMENT_RATE_LIMITED";
      if (rateLimited) cooldown.impose(err.retryAfterSeconds || 5);
      const message = rateLimited ? "发送有点快，请稍后再试。" : err instanceof Error ? err.message : "发布失败，请稍后重试。";
      setReplyError(rateLimited ? "" : message);
      showToast(message, "error");
    } finally {
      submitLock.current = false;
      setSubmitting(false);
      setUploading(false);
      onBusyChange?.(false);
    }
  };

  const onPaste = (event: React.ClipboardEvent<HTMLDivElement>) => {
    if (!isReply) return image.onPaste(event);
    if (Array.from(event.clipboardData.items).some(item => item.kind === "file" && item.type.startsWith("image/"))) {
      event.preventDefault();
      setReplyError("楼中楼不支持上传图片，请使用下方猫猫表情。");
    }
  };

  return (
    <form onSubmit={submit} className="space-y-3" aria-label={isReply ? "楼内回复" : "发布评论"}>
      <EmoteEditor
        ref={textarea}
        label={label}
        placeholder={isReply ? "说说你的想法…" : "聊聊你的想法…"}
        value={content}
        onChange={next => { setContent(next); setReplyError(""); }}
        onError={setReplyError}
        size={isReply ? "reply" : "normal"}
        rows={isReply ? 2 : 3}
        maxLength={maxLength}
        disabled={submitting}
        onPaste={onPaste}
        hint={`${content.length}/${maxLength} 字符 · ${isReply ? "楼内可用猫猫表情，不支持上传图片。" : "可粘贴一张图片，最大 10 MB。"}`}
        error={replyError || image.error}
      />
      {!isReply && image.preview && (
        <div className="relative w-fit rounded-md border border-ink-rule p-2 dark:border-night-border">
          <img src={image.preview} alt="待发布的评论图片" className="max-h-40 max-w-full sm:max-w-64 rounded object-contain" />
          <button type="button" aria-label="移除评论图片" disabled={submitting} onClick={image.clear}
            className="absolute right-1 top-1 flex h-8 w-8 items-center justify-center rounded-full border border-ink-primary bg-paper-50 text-ink-primary disabled:opacity-50 dark:bg-night-surface dark:text-night-ink dark:border-night-border">
            <X size={16} aria-hidden="true" />
          </button>
        </div>
      )}
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex flex-wrap items-center gap-3">
        <CatEmotePicker onSelect={emote => textarea.current?.insertEmote(emote)} disabled={submitting} />
        <button type="button" className="post-action" disabled={submitting} onClick={() => textarea.current?.insertText("@Lumi ")}>
          <AtSign size={18} aria-hidden="true" />问 Lumi
        </button>
        {!isReply && <>
          <input ref={imageInput} type="file" accept={COMMENT_IMAGE_TYPES} className="hidden" aria-label="选择评论图片" disabled={submitting}
            onChange={event => { image.select(Array.from(event.target.files || [])); event.target.value = ""; }} />
          <button type="button" className="post-action" disabled={submitting || !!image.file} onClick={() => imageInput.current?.click()}>
            <ImagePlus size={18} aria-hidden="true" />添加图片
          </button>
        </>}
        {isReply && <Button type="button" variant="ghost" size="sm" disabled={submitting} onClick={onCancel}>取消回复</Button>}
        </div>
        <Button type="submit" size="sm" disabled={cooldown.remaining > 0 || content.length > maxLength || (!content.trim() && (isReply || !image.file))} isLoading={submitting}>
          {uploading ? "上传图片中…" : cooldown.remaining > 0 ? `${cooldown.remaining} 秒后可发布` : isReply ? "发表回复" : "发表评论"}
        </Button>
      </div>
      {/(^|[^\w@])@lumi(?!\w)/i.test(content) && <p className="text-xs text-ink-muted dark:text-night-muted">发布后 Lumi 会围绕这篇帖子简短回答；仅明确 @ 才调用。联网搜索暂未开放。</p>}
    </form>
  );
};
