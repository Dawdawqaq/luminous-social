import React, { useState } from 'react';
import { Modal } from '../ui/Modal';
import { Input } from '../ui/Input';
import { Textarea } from '../ui/Textarea';
import { Button } from '../ui/Button';
import { communityApi } from '../../api/client';
import { useToast } from '../../context/ToastContext';

interface CreateSalonModalProps {
  isOpen: boolean;
  onClose: () => void;
  onCreated: (slug: string) => void;
}

export const CreateSalonModal: React.FC<CreateSalonModalProps> = ({
  isOpen,
  onClose,
  onCreated,
}) => {
  const { showToast } = useToast();
  const [slug, setSlug] = useState('');
  const [name, setName] = useState('');
  const [category, setCategory] = useState('Philosophy');
  const [description, setDescription] = useState('');
  const [isLoading, setIsLoading] = useState(false);

  const categories = ['Philosophy', 'Craft', 'Aesthetics', 'Living', 'Systems', 'Literature'];

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!slug.trim() || !name.trim()) {
      showToast('请填写沙龙英文标识与中文名称', 'error');
      return;
    }

    try {
      setIsLoading(true);
      await communityApi.create({
        slug: slug.trim().toLowerCase(),
        name: name.trim(),
        category,
        description: description.trim(),
      });

      showToast('沙龙房间已开辟', 'success');
      onCreated(slug.trim().toLowerCase());
      onClose();
    } catch (err: any) {
      showToast(err.message || '创建沙龙失败', 'error');
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title="开辟沙龙房间 · Open a Salon"
      subtitle="为特定领域的深度思想与研讨建立专属空间"
      maxWidth="md"
    >
      <form onSubmit={handleSubmit} className="space-y-4">
        <Input
          label="沙龙英文标识 (Slug)"
          placeholder="输入唯一英文标识 (如 aesthetics, slow-code)"
          value={slug}
          onChange={(e) => setSlug(e.target.value.replace(/[^a-zA-Z0-9_-]/g, ''))}
          hint="唯一标识符，将用于 URL 地址 /s/{slug}"
          required
        />

        <Input
          label="沙龙显示名称"
          placeholder="输入社区对外呈现的完整名称"
          value={name}
          onChange={(e) => setName(e.target.value)}
          required
        />

        <div>
          <label className="block text-xs font-sans font-medium text-ink-muted dark:text-night-muted uppercase mb-1.5">
            所属领域分类
          </label>
          <select
            value={category}
            onChange={(e) => setCategory(e.target.value)}
            className="w-full px-3 py-2 text-sm font-sans bg-paper-50 dark:bg-night-surface border border-paper-300 dark:border-night-border rounded-[2px] text-ink-primary dark:text-night-ink focus:outline-none"
          >
            {categories.map((cat) => (
              <option key={cat} value={cat}>
                {cat}
              </option>
            ))}
          </select>
        </div>

        <Textarea
          label="沙龙主旨与宣言"
          placeholder="阐述本沙龙的主旨、讨论范畴与期待的讨论氛围..."
          rows={3}
          value={description}
          onChange={(e) => setDescription(e.target.value)}
        />

        <div className="flex justify-end gap-3 pt-4 hairline-t">
          <Button type="button" variant="outline" size="sm" onClick={onClose}>
            取消
          </Button>
          <Button type="submit" size="sm" isLoading={isLoading}>
            确认开辟沙龙
          </Button>
        </div>
      </form>
    </Modal>
  );
};
