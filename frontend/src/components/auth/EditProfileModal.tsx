import React, { useRef, useState } from "react";
import { Modal } from "../ui/Modal";
import { Input } from "../ui/Input";
import { Textarea } from "../ui/Textarea";
import { Button } from "../ui/Button";
import { ImagePicker } from "../ui/ImagePicker";
import { authApi, uploadApi, userApi } from "../../api/client";
import { useAuth } from "../../context/AuthContext";
import { useToast } from "../../context/ToastContext";
import { useRemoteData } from "../../hooks/useRemoteData";
import type { ProfilePresetCatalog } from "../../profile/profilePresets";

interface EditProfileModalProps {
  isOpen: boolean;
  onClose: () => void;
}

export const EditProfileModal: React.FC<EditProfileModalProps> = ({
  isOpen,
  onClose,
}) => {
  const { user, updateUser } = useAuth();
  const { showToast } = useToast();
  const presets = useRemoteData<ProfilePresetCatalog | null>(userApi.profilePresets, [], null, isOpen);
  const [displayName, setDisplayName] = useState(user?.displayName || "");
  const [bio, setBio] = useState(user?.bio || "");
  const [avatar, setAvatar] = useState(user?.avatar || "");
  const [banner, setBanner] = useState(user?.banner || "");
  const [avatarFile, setAvatarFile] = useState<File | null>(null);
  const [bannerFile, setBannerFile] = useState<File | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const pending = useRef(false);
  const handleClose = () => {
    if (!pending.current) onClose();
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (pending.current) return;
    pending.current = true;
    try {
      setIsLoading(true);
      let nextAvatar = avatar;
      let nextBanner = banner;
      if (avatarFile) {
        nextAvatar = (await uploadApi.uploadImage(avatarFile)).url;
        setAvatar(nextAvatar);
        setAvatarFile(null);
      }
      if (bannerFile) {
        nextBanner = (await uploadApi.uploadImage(bannerFile)).url;
        setBanner(nextBanner);
        setBannerFile(null);
      }
      const updated = await authApi.updateProfile({
        displayName: displayName.trim(),
        bio: bio.trim(),
        avatar: nextAvatar || undefined,
        banner: nextBanner || undefined,
      });

      updateUser(updated);
      showToast("个人资料已更新", "success");
      onClose();
    } catch (err) {
      showToast(err instanceof Error ? err.message : "更新资料失败", "error");
    } finally {
      pending.current = false;
      setIsLoading(false);
    }
  };

  return (
    <Modal
      isOpen={isOpen}
      onClose={handleClose}
      title="编辑个人资料"
      subtitle="介绍一下你，让同好更容易认识你。"
      maxWidth="lg"
    >
      <form onSubmit={handleSubmit} className="space-y-4">
        <Input
          label="昵称"
          minLength={2}
          maxLength={96}
          value={displayName}
          onChange={(e) => setDisplayName(e.target.value)}
          required
          disabled={isLoading}
        />

        <Textarea
          label="个人简介"
          maxLength={512}
          rows={3}
          value={bio}
          onChange={(e) => setBio(e.target.value)}
          placeholder="聊聊你的兴趣，或最近在做的事。"
          disabled={isLoading}
        />

        <ImagePicker
          label="头像"
          value={avatar}
          file={avatarFile}
          onChange={setAvatarFile}
          disabled={isLoading}
          presets={presets.data?.avatars}
          onSelectPreset={(preset) => {setAvatar(preset.url);setAvatarFile(null);}}
        />

        <ImagePicker
          label="主页背景"
          variant="banner"
          value={banner}
          file={bannerFile}
          onChange={setBannerFile}
          disabled={isLoading}
          presets={presets.data?.banners}
          onSelectPreset={(preset) => {setBanner(preset.url);setBannerFile(null);}}
        />

        {presets.loading && <p role="status" className="text-sm text-ink-muted dark:text-night-muted">正在加载预设…</p>}
        {presets.error && <div role="alert" className="flex flex-wrap items-center justify-between gap-2 text-sm">
          <p>预设暂时无法加载，仍可上传自己的图片。</p>
          <Button size="sm" variant="ghost" disabled={isLoading} onClick={presets.reload}>重新加载预设</Button>
        </div>}

        <div className="flex justify-end gap-3 pt-4 hairline-t">
          <Button type="button" variant="outline" size="sm" onClick={handleClose} disabled={isLoading}>
            取消
          </Button>
          <Button type="submit" size="sm" isLoading={isLoading}>
            保存修改
          </Button>
        </div>
      </form>
    </Modal>
  );
};
