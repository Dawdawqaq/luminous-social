import React, { useState } from 'react';
import { Modal } from '../ui/Modal';
import { Input } from '../ui/Input';
import { Textarea } from '../ui/Textarea';
import { Button } from '../ui/Button';
import { authApi } from '../../api/client';
import { useAuth } from '../../context/AuthContext';
import { useToast } from '../../context/ToastContext';

interface EditProfileModalProps {
  isOpen: boolean;
  onClose: () => void;
}

export const EditProfileModal: React.FC<EditProfileModalProps> = ({ isOpen, onClose }) => {
  const { user, updateUser } = useAuth();
  const { showToast } = useToast();
  const [displayName, setDisplayName] = useState(user?.displayName || '');
  const [bio, setBio] = useState(user?.bio || '');
  const [avatar, setAvatar] = useState(user?.avatar || '');
  const [banner, setBanner] = useState(user?.banner || '');
  const [isLoading, setIsLoading] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      setIsLoading(true);
      const updated = await authApi.updateProfile({
        displayName: displayName.trim(),
        bio: bio.trim(),
        avatar: avatar.trim() || undefined,
        banner: banner.trim() || undefined,
      });

      updateUser(updated);
      showToast('个人资料已更新', 'success');
      onClose();
    } catch (err: any) {
      showToast(err.message || '更新资料失败', 'error');
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title="修饰个人手记 · Edit Profile"
      subtitle="更新您的显示称谓、思想引语与头像"
      maxWidth="md"
    >
      <form onSubmit={handleSubmit} className="space-y-4">
        <Input
          label="显示称谓"
          value={displayName}
          onChange={(e) => setDisplayName(e.target.value)}
          required
        />

        <Textarea
          label="个人简介 / 思想箴言 (Bio)"
          rows={3}
          value={bio}
          onChange={(e) => setBio(e.target.value)}
          placeholder="撰写一段沉静的自我介绍..."
        />

        <Input
          label="头像 URL (Avatar)"
          placeholder="https://... 或 /uploads/..."
          value={avatar}
          onChange={(e) => setAvatar(e.target.value)}
        />

        <Input
          label="背景画幅 URL (Banner)"
          placeholder="https://... 或 /uploads/..."
          value={banner}
          onChange={(e) => setBanner(e.target.value)}
        />

        <div className="flex justify-end gap-3 pt-4 hairline-t">
          <Button type="button" variant="outline" size="sm" onClick={onClose}>
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
