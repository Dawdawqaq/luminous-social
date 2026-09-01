import React, { useState, useEffect } from 'react';
import { Modal } from '../ui/Modal';
import { Input } from '../ui/Input';
import { Textarea } from '../ui/Textarea';
import { Button } from '../ui/Button';
import { Community } from '../../types';
import { communityApi, postApi, uploadApi } from '../../api/client';
import { useToast } from '../../context/ToastContext';
import { MarkdownRenderer } from '../ui/MarkdownRenderer';
import { Image, Eye, Edit3 } from 'lucide-react';

interface CreatePostModalProps {
  isOpen: boolean;
  onClose: () => void;
  onPostCreated: () => void;
  defaultCommunity?: string;
}

export const CreatePostModal: React.FC<CreatePostModalProps> = ({
  isOpen,
  onClose,
  onPostCreated,
  defaultCommunity,
}) => {
  const { showToast } = useToast();
  const [communities, setCommunities] = useState<Community[]>([]);
  const [selectedCommunity, setSelectedCommunity] = useState<string>(defaultCommunity || '');
  const [title, setTitle] = useState('');
  const [content, setContent] = useState('');
  const [image, setImage] = useState('');
  const [postType, setPostType] = useState<'ESSAY' | 'DISCUSSION' | 'FIELD_NOTE'>('DISCUSSION');
  const [isPreview, setIsPreview] = useState(false);
  const [isUploading, setIsUploading] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);

  useEffect(() => {
    if (isOpen) {
      communityApi.list().then((res) => {
        const commList = res || [];
        setCommunities(commList);
        if (!selectedCommunity && commList.length > 0) {
          setSelectedCommunity(defaultCommunity || commList[0].name);
        }
      }).catch(() => {
        setCommunities([]);
      });
    }
  }, [isOpen, defaultCommunity, selectedCommunity]);

  const handleImageUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    try {
      setIsUploading(true);
      const res = await uploadApi.uploadImage(file);
      setImage(res.url);
      showToast('图片上传成功', 'success');
    } catch (err: any) {
      showToast(err.message || '图片上传失败', 'error');
    } finally {
      setIsUploading(false);
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!title.trim()) {
      showToast('请输入帖子标题', 'error');
      return;
    }
    if (!selectedCommunity) {
      showToast('请选择发布社区', 'error');
      return;
    }

    try {
      setIsSubmitting(true);
      await postApi.create({
        community: selectedCommunity,
        title: title.trim(),
        content: content.trim(),
        image: image || undefined,
        type: postType,
        isPublic: true,
      });

      showToast('帖子发布成功', 'success');
      setTitle('');
      setContent('');
      setImage('');
      onPostCreated();
      onClose();
    } catch (err: any) {
      showToast(err.message || '发布失败', 'error');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title="发布新帖子 · Create Post"
      subtitle="在选定的社区中分享见解、发起讨论或记录长文"
      maxWidth="xl"
    >
      <form onSubmit={handleSubmit} className="space-y-4">
        
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
          <div>
            <label className="block text-xs font-sans font-medium text-ink-muted dark:text-night-muted uppercase mb-1.5">
              帖子类型
            </label>
            <div className="grid grid-cols-3 gap-1.5">
              {(['DISCUSSION', 'ESSAY', 'FIELD_NOTE'] as const).map((type) => (
                <button
                  type="button"
                  key={type}
                  onClick={() => setPostType(type)}
                  className={`px-2 py-1.5 text-xs font-sans rounded-[2px] transition-colors cursor-pointer text-center ${
                    postType === type
                      ? 'bg-ink-primary text-paper-50 dark:bg-night-ink dark:text-night-canvas font-medium'
                      : 'bg-paper-200 dark:bg-night-surface text-ink-muted hover:text-ink-primary dark:text-night-muted'
                  }`}
                >
                  {type === 'DISCUSSION' ? '讨论交流' : type === 'ESSAY' ? '深度长文' : '实战手记'}
                </button>
              ))}
            </div>
          </div>

          <div>
            <label className="block text-xs font-sans font-medium text-ink-muted dark:text-night-muted uppercase mb-1.5">
              选择社区
            </label>
            <select
              value={selectedCommunity}
              onChange={(e) => setSelectedCommunity(e.target.value)}
              className="w-full px-3 py-1.5 text-sm font-sans bg-paper-50 dark:bg-night-surface border border-paper-300 dark:border-night-border rounded-[2px] text-ink-primary dark:text-night-ink focus:outline-none focus:border-ink-primary"
            >
              {communities.map((c) => (
                <option key={c.id || c.name} value={c.name}>
                  c/{c.displayName ? `${c.name} (${c.displayName})` : c.name}
                </option>
              ))}
            </select>
          </div>
        </div>

        <Input
          label="帖子标题"
          placeholder="清晰概括你的观点或问题..."
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          required
        />

        <div className="flex justify-between items-center text-xs font-sans text-ink-muted dark:text-night-muted pt-1">
          <span className="uppercase font-medium">正文内容 (支持 Markdown)</span>
          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={() => setIsPreview(false)}
              className={`flex items-center gap-1 px-2 py-0.5 rounded ${!isPreview ? 'text-ink-primary dark:text-night-ink font-semibold' : ''}`}
            >
              <Edit3 className="w-3 h-3" /> 编辑
            </button>
            <button
              type="button"
              onClick={() => setIsPreview(true)}
              className={`flex items-center gap-1 px-2 py-0.5 rounded ${isPreview ? 'text-ink-primary dark:text-night-ink font-semibold' : ''}`}
            >
              <Eye className="w-3 h-3" /> 预览
            </button>
          </div>
        </div>

        {isPreview ? (
          <div className="p-4 bg-paper-50 dark:bg-night-surface border border-paper-300 dark:border-night-border rounded-[2px] min-h-[160px] max-h-[300px] overflow-y-auto">
            {content ? (
              <MarkdownRenderer content={content} />
            ) : (
              <p className="text-xs text-ink-subtle italic">正文为空，暂无预览内容。</p>
            )}
          </div>
        ) : (
          <Textarea
            rows={7}
            placeholder="展开你的论述与背景信息... 支持 # 标题, > 引用, - 列表, `代码`"
            value={content}
            onChange={(e) => setContent(e.target.value)}
          />
        )}

        <div className="flex items-center justify-between pt-1">
          <label className="inline-flex items-center gap-1.5 text-xs text-ink-muted hover:text-ink-primary dark:text-night-muted dark:hover:text-night-ink cursor-pointer">
            <Image className="w-4 h-4 text-terracotta" />
            <span>{image ? '更换插图' : '上传插图 / 封面 (可选)'}</span>
            <input
              type="file"
              accept="image/*"
              className="hidden"
              onChange={handleImageUpload}
              disabled={isUploading}
            />
          </label>

          {isUploading && (
            <span className="text-xs text-terracotta">图片上传中...</span>
          )}

          {image && !isUploading && (
            <span className="text-xs text-[#5B8266]">已附加 1 张图片</span>
          )}
        </div>

        <div className="flex justify-end gap-3 pt-4 hairline-t">
          <Button type="button" variant="outline" size="sm" onClick={onClose}>
            取消
          </Button>
          <Button type="submit" size="sm" isLoading={isSubmitting}>
            立即发布
          </Button>
        </div>

      </form>
    </Modal>
  );
};
