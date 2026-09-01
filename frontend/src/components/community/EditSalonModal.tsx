import React, { useState } from 'react';
import { Modal } from '../ui/Modal';
import { Input } from '../ui/Input';
import { Textarea } from '../ui/Textarea';
import { Button } from '../ui/Button';
import { Community } from '../../types';
import { communityApi } from '../../api/client';
import { useToast } from '../../context/ToastContext';

interface EditSalonModalProps {
  isOpen: boolean;
  onClose: () => void;
  community: Community;
  onUpdated: (updated: Community) => void;
}

export const EditSalonModal: React.FC<EditSalonModalProps> = ({
  isOpen,
  onClose,
  community,
  onUpdated,
}) => {
  const { showToast } = useToast();
  const [name, setName] = useState(community.displayName || community.name);
  const [description, setDescription] = useState(community.description || '');
  const [iconUrl, setIconUrl] = useState(community.icon || '');
  const [bannerUrl, setBannerUrl] = useState(community.banner || '');
  const [rules, setRules] = useState<string>((community.rules || []).join('\n'));
  const [isLoading, setIsLoading] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      setIsLoading(true);
      const ruleList = rules
        .split('\n')
        .map((r) => r.trim())
        .filter(Boolean);

      const updated = await communityApi.update(community.name, {
        name: name.trim(),
        description: description.trim(),
        iconUrl: iconUrl.trim() || undefined,
        bannerUrl: bannerUrl.trim() || undefined,
        rules: ruleList,
      });

      showToast('沙龙配置已更新', 'success');
      onUpdated(updated);
      onClose();
    } catch (err: any) {
      showToast(err.message || '更新沙龙失败', 'error');
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title="配置沙龙房间 · Salon Settings"
      subtitle={`管理 c/${community.name} 的基本信息与讨论规约`}
      maxWidth="md"
    >
      <form onSubmit={handleSubmit} className="space-y-4">
        <Input
          label="沙龙显示名称"
          value={name}
          onChange={(e) => setName(e.target.value)}
          required
        />

        <Textarea
          label="沙龙宣言与主旨"
          rows={3}
          value={description}
          onChange={(e) => setDescription(e.target.value)}
        />

        <Input
          label="沙龙图标 URL (Icon)"
          placeholder="https://... 或 /uploads/..."
          value={iconUrl}
          onChange={(e) => setIconUrl(e.target.value)}
        />

        <Input
          label="沙龙背景画幅 URL (Banner)"
          placeholder="https://... 或 /uploads/..."
          value={bannerUrl}
          onChange={(e) => setBannerUrl(e.target.value)}
        />

        <Textarea
          label="沙龙守则 (每行一条规约)"
          placeholder="1. 保持真诚与包容\n2. 拒绝无营养的灌水"
          rows={4}
          value={rules}
          onChange={(e) => setRules(e.target.value)}
        />

        <div className="flex justify-end gap-3 pt-4 hairline-t">
          <Button type="button" variant="outline" size="sm" onClick={onClose}>
            取消
          </Button>
          <Button type="submit" size="sm" isLoading={isLoading}>
            保存配置
          </Button>
        </div>
      </form>
    </Modal>
  );
};
