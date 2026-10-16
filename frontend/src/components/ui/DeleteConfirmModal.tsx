import React, { useEffect, useId, useRef, useState } from "react";
import { Modal } from "./Modal";
import { Button } from "./Button";
import "./DeleteConfirmModal.css";

interface DeleteConfirmModalProps {
  isOpen: boolean;
  onClose: () => void;
  onConfirm: () => Promise<void>;
  kind: "post" | "comment" | "notification" | "conversation";
  preview?: string;
  preserveReplies?: boolean;
}

export const DeleteConfirmModal: React.FC<DeleteConfirmModalProps> = ({
  isOpen,
  onClose,
  onConfirm,
  kind,
  preview,
  preserveReplies = false,
}) => {
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState("");
  const pending = useRef(false);
  const cancelRef = useRef<HTMLButtonElement>(null);
  const descriptionId = useId();
  const noun = kind === "post" ? "帖子" : kind === "notification" ? "通知" : kind === "conversation" ? "对话" : "评论";

  useEffect(() => {
    if (isOpen) setError("");
  }, [isOpen]);

  const confirm = async () => {
    // 在同一事件周期内拦住重复点击，请求完成前不能关闭或重复提交。
    if (pending.current) return;
    pending.current = true;
    setSubmitting(true);
    setError("");
    try {
      await onConfirm();
      onClose();
    } catch (err) {
      setError(err instanceof Error ? err.message : "删除失败，请稍后重试。");
    } finally {
      pending.current = false;
      setSubmitting(false);
    }
  };

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title={`删除${noun}`}
      maxWidth="md"
      dismissible={!submitting}
      initialFocusRef={cancelRef}
      role="alertdialog"
      describedBy={descriptionId}
    >
      <div className="delete-confirm-copy">
        <img
          className="delete-confirm-cat"
          src="/illustrations/cats/luminous-cat-tearful.png"
          alt=""
          width={1157}
          height={1359}
          decoding="async"
          draggable={false}
        />
        <div className="min-w-0">
          <p id={descriptionId} className="delete-confirm-description">
            {kind === "conversation" ? "这段对话的聊天内容、标题和本地草稿将被清除。" : kind === "notification" ? "这条通知将从列表中移除，原帖子和评论不受影响。" : kind === "post" ? "帖子及其评论将不再展示。" : preserveReplies
              ? "原评论将显示为已删除，楼内其他人的回复会保留。" : "这条评论将不再展示。"}
            删除后无法恢复，请确认是否继续。
          </p>
          {preview && <p className="delete-confirm-preview">{preview}</p>}
        </div>
      </div>
      {error && <p className="delete-confirm-error" role="alert">{error}</p>}
      <div className="delete-confirm-actions">
        <Button ref={cancelRef} variant="outline" onClick={onClose} disabled={submitting}>
          取消
        </Button>
        <Button variant="outline" className="delete-confirm-submit" onClick={confirm} isLoading={submitting}>
          {submitting ? "正在删除…" : "确认删除"}
        </Button>
      </div>
    </Modal>
  );
};
