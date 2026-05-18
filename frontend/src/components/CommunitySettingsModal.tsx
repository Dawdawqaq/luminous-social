import { useState } from 'react';
import { motion, AnimatePresence } from 'motion/react';
import { X, Check, Settings, Camera, Plus, Trash2, GripVertical } from 'lucide-react';
import { cn } from '../lib/utils';
import { api } from '../lib/api';
import { Community } from '../types';

interface CommunitySettingsModalProps {
  community: Community;
  isOpen: boolean;
  onClose: () => void;
  onUpdate: (community: Community) => void;
}

export function CommunitySettingsModal({ community, isOpen, onClose, onUpdate }: CommunitySettingsModalProps) {
  const [formData, setFormData] = useState({
    name: community.displayName || community.name,
    description: community.description || '',
    iconUrl: community.icon || '',
    bannerUrl: community.banner || '',
    rules: community.rules || []
  });
  const [error, setError] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);

  if (!isOpen) return null;

  const handleRuleChange = (index: number, value: string) => {
    const newRules = [...formData.rules];
    newRules[index] = value;
    setFormData({ ...formData, rules: newRules });
  };

  const addRule = () => {
    setFormData({ ...formData, rules: [...formData.rules, ''] });
  };

  const removeRule = (index: number) => {
    setFormData({ ...formData, rules: formData.rules.filter((_, i) => i !== index) });
  };

  const handleImageUpload = async (e: React.ChangeEvent<HTMLInputElement>, type: 'icon' | 'banner') => {
    const file = e.target.files?.[0];
    if (!file) return;
    try {
      const res = await api.uploadImage(file);
      setFormData(prev => ({ ...prev, [type === 'icon' ? 'iconUrl' : 'bannerUrl']: res.url }));
    } catch (err) {
      setError('图片上传失败');
    }
  };

  const handleSubmit = async () => {
    setIsSubmitting(true);
    setError('');
    try {
      // Filter out empty rules
      const finalRules = formData.rules.filter(r => r.trim() !== '');
      const updated = await api.updateCommunity(community.name, {
        ...formData,
        rules: finalRules
      });
      onUpdate(updated);
      onClose();
    } catch (err) {
      setError(err instanceof Error ? err.message : '保存失败，请重试');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="fixed inset-0 z-[200] flex items-center justify-center p-4">
      <div className="absolute inset-0 bg-black/40 backdrop-blur-sm" onClick={onClose} />
      
      <motion.div 
        initial={{ opacity: 0, scale: 0.95, y: 20 }}
        animate={{ opacity: 1, scale: 1, y: 0 }}
        exit={{ opacity: 0, scale: 0.95, y: 20 }}
        className="relative w-full max-w-[600px] bg-surface-container-lowest rounded-[2rem] shadow-2xl overflow-hidden flex flex-col max-h-[90vh]"
      >
        <div className="flex items-center justify-between p-6 border-b border-outline-variant/30 shrink-0">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-full bg-secondary-container text-on-secondary-container flex items-center justify-center">
              <Settings size={20} />
            </div>
            <div>
              <h2 className="text-xl font-bold text-on-surface leading-tight">社区管理中心</h2>
              <p className="text-xs text-on-surface-variant">c/{community.name}</p>
            </div>
          </div>
          <button onClick={onClose} className="p-2 text-on-surface-variant hover:bg-surface-variant rounded-full transition-colors">
            <X size={20} />
          </button>
        </div>

        <div className="flex-1 overflow-y-auto p-6 relative">
          <div className="flex flex-col gap-8">
            {/* Visuals */}
            <div className="space-y-4">
              <h3 className="font-bold text-sm text-primary uppercase tracking-wider">视觉装潢</h3>
              <div className="relative w-full h-32 rounded-2xl bg-surface-container-high border border-dashed border-outline-variant overflow-hidden group">
                {formData.bannerUrl ? (
                  <img src={formData.bannerUrl} alt="Banner" className="w-full h-full object-cover" />
                ) : (
                  <div className="absolute inset-0 flex flex-col items-center justify-center text-on-surface-variant">
                    <Camera size={24} className="mb-2 opacity-50" />
                    <span className="text-xs font-bold">上传背景大图</span>
                  </div>
                )}
                <div className="absolute inset-0 bg-black/30 opacity-0 group-hover:opacity-100 transition-opacity flex items-center justify-center">
                  <label className="cursor-pointer px-4 py-2 bg-white text-black text-xs font-bold rounded-full shadow-lg">
                    更换背景图
                    <input type="file" className="hidden" accept="image/*" onChange={(e) => handleImageUpload(e, 'banner')} />
                  </label>
                </div>
              </div>

              <div className="flex items-center gap-4 -mt-8 ml-6 relative z-10">
                <div className="w-20 h-20 rounded-2xl bg-surface p-1 shadow-md">
                  <div className="w-full h-full bg-surface-container-high rounded-xl overflow-hidden relative group">
                    {formData.iconUrl ? (
                      <img src={formData.iconUrl} alt="Icon" className="w-full h-full object-cover" />
                    ) : (
                      <div className="w-full h-full flex items-center justify-center text-on-surface-variant">
                        <Camera size={24} className="opacity-50" />
                      </div>
                    )}
                    <div className="absolute inset-0 bg-black/40 opacity-0 group-hover:opacity-100 transition-opacity flex items-center justify-center">
                      <label className="cursor-pointer">
                        <Camera size={20} className="text-white" />
                        <input type="file" className="hidden" accept="image/*" onChange={(e) => handleImageUpload(e, 'icon')} />
                      </label>
                    </div>
                  </div>
                </div>
                <div className="mt-8 text-xs text-on-surface-variant font-medium">建议尺寸 256x256</div>
              </div>
            </div>

            {/* Basic Info */}
            <div className="space-y-4">
              <h3 className="font-bold text-sm text-primary uppercase tracking-wider">基础信息</h3>
              <div>
                <label className="block text-sm font-bold text-on-surface mb-2">显示名称</label>
                <input 
                  type="text" 
                  value={formData.name}
                  onChange={(e) => setFormData({...formData, name: e.target.value})}
                  className="w-full bg-surface-container-low border border-outline-variant rounded-xl px-4 py-3 outline-none focus:border-primary transition-colors font-medium"
                />
              </div>

              <div>
                <label className="block text-sm font-bold text-on-surface mb-2">社区简介</label>
                <textarea 
                  value={formData.description}
                  onChange={(e) => setFormData({...formData, description: e.target.value})}
                  className="w-full bg-surface-container-low border border-outline-variant rounded-xl px-4 py-3 outline-none focus:border-primary transition-colors font-medium min-h-[100px] resize-none"
                />
              </div>
            </div>

            {/* Rules */}
            <div className="space-y-4">
              <div className="flex items-center justify-between mb-2">
                <h3 className="font-bold text-sm text-primary uppercase tracking-wider">吧规设置</h3>
                <button onClick={addRule} className="text-xs font-bold text-primary flex items-center gap-1 hover:underline">
                  <Plus size={14} /> 新增条目
                </button>
              </div>
              <div className="flex flex-col gap-3">
                {formData.rules.map((rule, idx) => (
                  <div key={idx} className="flex items-start gap-2">
                    <div className="mt-2 text-on-surface-variant/50 cursor-move">
                      <GripVertical size={18} />
                    </div>
                    <span className="mt-2 text-sm font-bold text-on-surface-variant">{idx + 1}.</span>
                    <input 
                      type="text" 
                      value={rule}
                      onChange={(e) => handleRuleChange(idx, e.target.value)}
                      placeholder="输入规则内容..."
                      className="flex-1 bg-surface-container-low border border-outline-variant rounded-xl px-3 py-2 outline-none focus:border-primary transition-colors text-sm"
                    />
                    <button onClick={() => removeRule(idx)} className="mt-2 text-on-surface-variant hover:text-error transition-colors p-1">
                      <Trash2 size={16} />
                    </button>
                  </div>
                ))}
                {formData.rules.length === 0 && (
                  <p className="text-sm text-on-surface-variant italic text-center py-4">暂无吧规，点击右上角新增</p>
                )}
              </div>
            </div>

          </div>

          {error && (
            <motion.p initial={{ opacity: 0 }} animate={{ opacity: 1 }} className="text-sm font-bold text-error mt-6 text-center">
              {error}
            </motion.p>
          )}
        </div>

        <div className="p-6 border-t border-outline-variant/30 flex justify-end gap-3 bg-surface/50 shrink-0">
          <button 
            onClick={onClose}
            className="px-6 py-2.5 rounded-full font-bold text-on-surface-variant hover:bg-surface-variant transition-colors"
          >
            取消
          </button>
          <button 
            onClick={handleSubmit}
            disabled={isSubmitting}
            className="px-8 py-2.5 rounded-full bg-primary text-on-primary font-bold hover:shadow-lg transition-all flex items-center gap-2 active:scale-95 disabled:opacity-70"
          >
            {isSubmitting ? '保存中...' : '保存修改'} <Check size={18} />
          </button>
        </div>
      </motion.div>
    </div>
  );
}
