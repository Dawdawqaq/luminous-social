import React, { useEffect, useRef, useState } from "react";
import { authApi, uploadApi, userApi } from "../../api/client";
import { useAuth } from "../../context/AuthContext";
import { useToast } from "../../context/ToastContext";
import { useRemoteData } from "../../hooks/useRemoteData";
import type { ProfilePresetCatalog } from "../../profile/profilePresets";
import type { User } from "../../types";
import { Button } from "../ui/Button";
import { ImagePicker } from "../ui/ImagePicker";
import { Modal } from "../ui/Modal";

interface AvatarPickerModalProps {
  onClose: () => void;
  onSaved: (user: User) => void;
}

export const AvatarPickerModal: React.FC<AvatarPickerModalProps> = ({ onClose, onSaved }) => {
  const { user, updateUser } = useAuth();
  const { showToast } = useToast();
  const presets = useRemoteData<ProfilePresetCatalog | null>(userApi.profilePresets, [], null);
  const [avatar, setAvatar] = useState(user?.avatar || "");
  const [file, setFile] = useState<File | null>(null);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const pending = useRef(false);
  const active = useRef(true);
  const accountId = useRef(user?.id);
  accountId.current = user?.id;
  useEffect(() => { active.current = true; return () => { active.current = false; }; }, []);
  const changed = !!file || avatar !== (user?.avatar || "");

  const save = async (event: React.FormEvent) => {
    event.preventDefault();
    if (pending.current || !changed) return;
    pending.current = true;
    setSaving(true);
    setError("");
    const actorId = user?.id;
    try {
      let nextAvatar = avatar;
      if (file) {
        nextAvatar = (await uploadApi.uploadImage(file)).url;
        if (!active.current || accountId.current !== actorId) return;
        setAvatar(nextAvatar);
        setFile(null);
      }
      const updated = await authApi.updateProfile({ avatar: nextAvatar });
      if (!active.current || accountId.current !== actorId) return;
      updateUser(updated);
      onSaved(updated);
      showToast("头像已更新", "success");
      onClose();
    } catch (cause) {
      if (active.current && accountId.current === actorId) setError(cause instanceof Error ? cause.message : "头像保存失败，请重试。");
    } finally {
      pending.current = false;
      if (active.current) setSaving(false);
    }
  };

  return (
    <Modal isOpen onClose={() => { if (!pending.current) onClose(); }} dismissible={!saving}
      title="更换头像" subtitle="选一只喜欢的猫猫，也可以上传自己的图片。" maxWidth="lg">
      <form onSubmit={save} className="space-y-4">
        <ImagePicker label="头像" value={avatar} file={file} onChange={setFile} disabled={saving}
          presets={presets.data?.avatars}
          onSelectPreset={(preset) => { setAvatar(preset.url); setFile(null); setError(""); }} />
        {presets.loading && <p role="status" className="text-sm text-ink-muted dark:text-night-muted">正在加载头像预设…</p>}
        {presets.error && <div role="alert" className="space-y-2 text-sm">
          <p>头像预设暂时无法加载，仍可上传自己的图片。</p>
          <Button size="sm" variant="outline" disabled={saving} onClick={presets.reload}>重新加载预设</Button>
        </div>}
        {error && <p role="alert" className="text-sm text-red-600 dark:text-red-400">{error}</p>}
        <div className="flex justify-end gap-3 pt-4 hairline-t">
          <Button variant="outline" disabled={saving} onClick={onClose}>取消</Button>
          <Button type="submit" isLoading={saving} disabled={!changed}>保存头像</Button>
        </div>
      </form>
    </Modal>
  );
};
