import { useState } from 'react';
import { motion, AnimatePresence } from 'motion/react';
import { X, ArrowRight, ArrowLeft, Check, Sparkles } from 'lucide-react';
import { cn } from '../lib/utils';
import { api } from '../lib/api';
import { useNavigate } from 'react-router-dom';

interface CreateCommunityModalProps {
  isOpen: boolean;
  onClose: () => void;
}

export function CreateCommunityModal({ isOpen, onClose }: CreateCommunityModalProps) {
  const navigate = useNavigate();
  const [step, setStep] = useState(1);
  const [formData, setFormData] = useState({
    slug: '',
    name: '',
    description: '',
    category: 'phone'
  });
  const [error, setError] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);

  if (!isOpen) return null;

  const nextStep = () => {
    if (step === 1 && (!formData.slug || !formData.name)) {
      setError('请填写必填项');
      return;
    }
    if (step === 1 && !/^[a-zA-Z0-9_-]+$/.test(formData.slug)) {
      setError('社区 ID 只能包含字母、数字、下划线和连字符');
      return;
    }
    setError('');
    setStep(prev => prev + 1);
  };

  const prevStep = () => {
    setError('');
    setStep(prev => prev - 1);
  };

  const handleSubmit = async () => {
    if (!formData.description) {
      setError('请填写社区简介');
      return;
    }
    setIsSubmitting(true);
    setError('');
    try {
      await api.createCommunity(formData);
      onClose();
      // Reset state
      setStep(1);
      setFormData({ slug: '', name: '', description: '', category: 'phone' });
      // Navigate to new community
      navigate(`/community/${formData.slug}`);
    } catch (err) {
      setError(err instanceof Error ? err.message : '创建失败，请重试');
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
        className="relative w-full max-w-[500px] bg-surface-container-lowest rounded-[2rem] shadow-2xl overflow-hidden flex flex-col max-h-[90vh]"
      >
        <div className="flex items-center justify-between p-6 border-b border-outline-variant/30">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-full bg-primary-container text-on-primary-container flex items-center justify-center">
              <Sparkles size={20} />
            </div>
            <div>
              <h2 className="text-xl font-bold text-on-surface leading-tight">创建我的社区</h2>
              <p className="text-xs text-on-surface-variant">建设属于你的兴趣宇宙</p>
            </div>
          </div>
          <button onClick={onClose} className="p-2 text-on-surface-variant hover:bg-surface-variant rounded-full transition-colors">
            <X size={20} />
          </button>
        </div>

        <div className="flex-1 overflow-y-auto p-6 relative">
          {/* Progress Bar */}
          <div className="flex gap-2 mb-8">
            <div className={cn("h-1.5 flex-1 rounded-full transition-colors", step >= 1 ? "bg-primary" : "bg-surface-variant")} />
            <div className={cn("h-1.5 flex-1 rounded-full transition-colors", step >= 2 ? "bg-primary" : "bg-surface-variant")} />
          </div>

          <AnimatePresence mode="wait">
            {step === 1 && (
              <motion.div
                key="step1"
                initial={{ opacity: 0, x: -20 }}
                animate={{ opacity: 1, x: 0 }}
                exit={{ opacity: 0, x: 20 }}
                className="flex flex-col gap-5"
              >
                <div>
                  <label className="block text-sm font-bold text-on-surface mb-2">社区唯一标识 (ID)</label>
                  <div className="relative flex items-center">
                    <span className="absolute left-4 text-on-surface-variant font-medium select-none">c/</span>
                    <input 
                      type="text" 
                      value={formData.slug}
                      onChange={(e) => setFormData({...formData, slug: e.target.value.toLowerCase()})}
                      placeholder="例如: tech_hub" 
                      className="w-full bg-surface-container-low border border-outline-variant rounded-2xl pl-9 pr-4 py-3 outline-none focus:border-primary transition-colors font-medium"
                    />
                  </div>
                  <p className="text-xs text-on-surface-variant mt-2 ml-2">作为访问链接使用，创建后不可修改。</p>
                </div>

                <div>
                  <label className="block text-sm font-bold text-on-surface mb-2">显示名称</label>
                  <input 
                    type="text" 
                    value={formData.name}
                    onChange={(e) => setFormData({...formData, name: e.target.value})}
                    placeholder="例如: 极客科技交流区" 
                    className="w-full bg-surface-container-low border border-outline-variant rounded-2xl px-4 py-3 outline-none focus:border-primary transition-colors font-medium"
                  />
                </div>
              </motion.div>
            )}

            {step === 2 && (
              <motion.div
                key="step2"
                initial={{ opacity: 0, x: -20 }}
                animate={{ opacity: 1, x: 0 }}
                exit={{ opacity: 0, x: 20 }}
                className="flex flex-col gap-5"
              >
                <div>
                  <label className="block text-sm font-bold text-on-surface mb-2">社区分区</label>
                  <div className="flex gap-3">
                    {['brain', 'phone', 'game', 'daily'].map((cat) => (
                      <button
                        key={cat}
                        onClick={() => setFormData({...formData, category: cat})}
                        className={cn(
                          "px-4 py-2 rounded-xl text-sm font-bold transition-all border",
                          formData.category === cat 
                            ? "bg-primary-container text-on-primary-container border-transparent" 
                            : "bg-surface-container-low text-on-surface-variant border-outline-variant hover:bg-surface-variant"
                        )}
                      >
                        {cat === 'brain' ? '科技' : cat === 'phone' ? '数码' : cat === 'game' ? '游戏' : '日常'}
                      </button>
                    ))}
                  </div>
                </div>

                <div>
                  <label className="block text-sm font-bold text-on-surface mb-2">社区简介</label>
                  <textarea 
                    value={formData.description}
                    onChange={(e) => setFormData({...formData, description: e.target.value})}
                    placeholder="一句话介绍你的社区，吸引志同道合的朋友..." 
                    className="w-full bg-surface-container-low border border-outline-variant rounded-2xl px-4 py-3 outline-none focus:border-primary transition-colors font-medium min-h-[120px] resize-none"
                  />
                </div>
              </motion.div>
            )}
          </AnimatePresence>

          {error && (
            <motion.p initial={{ opacity: 0 }} animate={{ opacity: 1 }} className="text-sm font-bold text-error mt-4">
              {error}
            </motion.p>
          )}
        </div>

        <div className="p-6 border-t border-outline-variant/30 flex justify-between bg-surface/50">
          {step > 1 ? (
            <button 
              onClick={prevStep}
              className="px-6 py-2.5 rounded-full font-bold text-on-surface-variant hover:bg-surface-variant transition-colors flex items-center gap-2"
            >
              <ArrowLeft size={18} /> 上一步
            </button>
          ) : (
            <div />
          )}

          {step === 1 ? (
            <button 
              onClick={nextStep}
              className="px-8 py-2.5 rounded-full bg-primary text-on-primary font-bold hover:shadow-lg transition-all flex items-center gap-2 active:scale-95"
            >
              下一步 <ArrowRight size={18} />
            </button>
          ) : (
            <button 
              onClick={handleSubmit}
              disabled={isSubmitting}
              className="px-8 py-2.5 rounded-full bg-primary text-on-primary font-bold hover:shadow-lg transition-all flex items-center gap-2 active:scale-95 disabled:opacity-70 disabled:active:scale-100"
            >
              {isSubmitting ? '创建中...' : '完成创建'} <Check size={18} />
            </button>
          )}
        </div>
      </motion.div>
    </div>
  );
}
