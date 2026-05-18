import { X, Globe, Lock, Shield, ChevronDown, Send, Brain, Smartphone, Image as ImageIcon, Trash2 } from 'lucide-react';
import { motion, AnimatePresence } from 'motion/react';
import { useState, useRef, ChangeEvent, useEffect } from 'react';
import { cn } from '../lib/utils';
import { api } from '../lib/api';
import { useAuth } from '../auth/AuthContext';
import { Community } from '../types';

interface CreatePostModalProps {
  isOpen: boolean;
  onClose: () => void;
}

export function CreatePostModal({ isOpen, onClose }: CreatePostModalProps) {
  const { user } = useAuth();
  const [community, setCommunity] = useState('');
  const [communities, setCommunities] = useState<Community[]>([]);
  const [showDropdown, setShowDropdown] = useState(false);
  const [title, setTitle] = useState('');
  const [content, setContent] = useState('');
  const [isPublic, setIsPublic] = useState(true);
  const [selectedImage, setSelectedImage] = useState<string | null>(null);
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');
  const fileInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (isOpen && user) {
      api.communities().then(res => {
        const joined = res.filter(c => c.joined);
        setCommunities(joined);
        if (joined.length > 0 && !joined.find(c => c.name.replace(/^c\//, '') === community)) {
          setCommunity(joined[0].name.replace(/^c\//, ''));
        }
      }).catch(console.error);
    }
  }, [isOpen, user]);

  const selectedCommunity = communities.find(c => c.name.replace(/^c\//, '') === community);

  const handleImageChange = (e: ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) {
      setSelectedFile(file);
      const reader = new FileReader();
      reader.onloadend = () => {
        setSelectedImage(reader.result as string);
      };
      reader.readAsDataURL(file);
    }
  };

  const removeImage = () => {
    setSelectedImage(null);
    setSelectedFile(null);
    if (fileInputRef.current) {
      fileInputRef.current.value = '';
    }
  };

  const submitPost = async () => {
    if (!user) {
      setError('请先登录后再发布帖子');
      return;
    }
    setError('');
    setSubmitting(true);
    try {
      if (!community) throw new Error('请选择要发布的社区');
      const image = selectedFile ? (await api.uploadImage(selectedFile)).url : null;
      await api.createPost({ community, title, content, image, isPublic });
      setTitle('');
      setContent('');
      removeImage();
      setShowDropdown(false);
      onClose();
      window.dispatchEvent(new CustomEvent('luminous:post-created'));
    } catch (ex) {
      setError(ex instanceof Error ? ex.message : '发布失败');
    } finally {
      setSubmitting(false);
    }
  };

  if (!isOpen) return null;

  return (
    <AnimatePresence>
      {isOpen && (
        <div className="fixed inset-0 z-[200] flex items-center justify-center p-4">
          {/* Backdrop with blur */}
          <motion.div 
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            onClick={onClose}
            className="absolute inset-0 bg-surface/60 backdrop-blur-xl"
          />

          {/* Modal Container */}
          <motion.div 
            initial={{ opacity: 0, scale: 0.95, y: 20 }}
            animate={{ opacity: 1, scale: 1, y: 0 }}
            exit={{ opacity: 0, scale: 0.95, y: 20 }}
            className="relative w-full max-w-[560px] bg-surface-container-high rounded-[2.5rem] border border-outline-variant shadow-2xl overflow-hidden flex flex-col shrink-0 mx-auto"
          >
            {/* Header */}
            <div className="px-8 py-6 border-b border-outline-variant flex items-center justify-between shrink-0">
              <h2 className="text-xl font-display font-bold text-on-surface whitespace-nowrap">创建新帖子</h2>
              <button 
                onClick={onClose}
                className="p-2 hover:bg-surface-variant rounded-full transition-colors text-on-surface-variant"
              >
                <X size={24} />
              </button>
            </div>

            {/* Form */}
            <div className="p-8 overflow-y-auto max-h-[70vh]">
              <div className="mb-6 flex flex-col gap-3">
                <label className="text-sm font-bold text-primary px-1 whitespace-nowrap">发布到社区</label>
                <div className="relative w-full">
                  <button 
                    onClick={() => setShowDropdown(!showDropdown)}
                    className="w-full flex items-center justify-between bg-surface-container-lowest border border-outline-variant rounded-2xl px-5 py-4 hover:border-primary transition-all group"
                  >
                    <div className="flex items-center gap-4">
                      <div className="w-10 h-10 rounded-xl bg-primary-container/20 flex items-center justify-center text-primary shrink-0 overflow-hidden">
                        {selectedCommunity?.icon ? <img src={selectedCommunity.icon} className="w-full h-full object-cover" /> : <Shield size={22} />}
                      </div>
                      <span className="font-bold text-on-surface text-lg whitespace-nowrap">
                        {selectedCommunity ? (selectedCommunity.displayName || selectedCommunity.name) : '请选择社区...'}
                      </span>
                    </div>
                    <ChevronDown size={20} className={cn("text-on-surface-variant transition-transform shrink-0", showDropdown && "rotate-180")} />
                  </button>

                  <AnimatePresence>
                    {showDropdown && (
                      <motion.div 
                        initial={{ opacity: 0, y: -10 }}
                        animate={{ opacity: 1, y: 0 }}
                        exit={{ opacity: 0, y: -10 }}
                        className="absolute top-full left-0 right-0 mt-2 bg-surface-container-high border border-outline-variant rounded-2xl shadow-xl overflow-hidden z-50 max-h-60 overflow-y-auto"
                      >
                        {communities.length === 0 ? (
                          <div className="p-4 text-center text-on-surface-variant font-medium text-sm">
                            您还没有关注任何社区，请先去大厅逛逛吧！
                          </div>
                        ) : (
                          communities.map(c => {
                            const slug = c.name.replace(/^c\//, '');
                            return (
                              <button
                                key={c.id}
                                onClick={() => { setCommunity(slug); setShowDropdown(false); }}
                                className="w-full flex items-center gap-3 px-5 py-3 hover:bg-surface-variant transition-colors text-left border-b border-outline-variant/30 last:border-0"
                              >
                                <div className="w-8 h-8 rounded-lg bg-primary-container flex items-center justify-center shrink-0 overflow-hidden">
                                  {c.icon ? <img src={c.icon} className="w-full h-full object-cover" /> : <Shield size={16} />}
                                </div>
                                <span className="font-bold text-on-surface">{c.displayName || c.name}</span>
                              </button>
                            )
                          })
                        )}
                      </motion.div>
                    )}
                  </AnimatePresence>
                </div>
              </div>

              {/* Title Input */}
              <div className="mb-6 flex flex-col gap-3">
                <label className="text-sm font-bold text-primary px-1 whitespace-nowrap">文章标题</label>
                <input 
                  type="text" 
                  value={title}
                  onChange={(e) => setTitle(e.target.value)}
                  placeholder="给你的发现起个响亮的名字吧..."
                  className="w-full bg-surface-container-lowest border border-outline-variant rounded-2xl px-5 py-4 text-on-surface focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary transition-all font-bold text-lg"
                />
              </div>

              {/* Content Input */}
              <div className="mb-6 flex flex-col gap-3">
                <label className="text-sm font-bold text-primary px-1 whitespace-nowrap">详细描述</label>
                <textarea 
                  value={content}
                  onChange={(e) => setContent(e.target.value)}
                  placeholder="分享精彩瞬间，支持 Markdown 语法..."
                  className="w-full min-h-[140px] bg-surface-container-lowest border border-outline-variant rounded-2xl p-5 text-on-surface focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary transition-all font-medium resize-none leading-relaxed text-base"
                />
              </div>

              {/* Image Upload/Preview */}
              <div className="mb-8">
                <input 
                  type="file" 
                  ref={fileInputRef} 
                  onChange={handleImageChange} 
                  accept="image/*" 
                  className="hidden" 
                />
                
                <AnimatePresence>
                  {selectedImage ? (
                    <motion.div 
                      initial={{ opacity: 0, scale: 0.9 }}
                      animate={{ opacity: 1, scale: 1 }}
                      exit={{ opacity: 0, scale: 0.9 }}
                      className="relative rounded-2xl overflow-hidden border border-outline-variant shadow-md aspect-video group"
                    >
                      <img src={selectedImage} alt="Selected" className="w-full h-full object-cover" />
                      <div className="absolute inset-0 bg-black/40 opacity-0 group-hover:opacity-100 transition-opacity flex items-center justify-center">
                        <button 
                          onClick={removeImage}
                          className="bg-error text-on-error p-3 rounded-full hover:scale-110 transition-transform shadow-lg"
                        >
                          <Trash2 size={24} />
                        </button>
                      </div>
                    </motion.div>
                  ) : (
                    <button 
                      onClick={() => fileInputRef.current?.click()}
                      className="w-full py-8 border-2 border-dashed border-outline-variant rounded-2xl flex flex-col items-center justify-center gap-2 hover:border-primary/50 hover:bg-primary-container/5 transition-all text-on-surface-variant group"
                    >
                      <div className="w-12 h-12 rounded-full bg-surface-container-lowest flex items-center justify-center group-hover:scale-110 transition-transform">
                        <ImageIcon size={24} className="text-primary" />
                      </div>
                      <span className="text-sm font-bold">导入图片</span>
                    </button>
                  )}
                </AnimatePresence>
              </div>

              {/* Privacy Footer */}
              <div className="flex items-center justify-between pt-4 border-t border-outline-variant mt-4">
                <div className="flex items-center gap-3">
                  <button 
                    onClick={() => setIsPublic(true)}
                    className={cn(
                      "flex items-center gap-2 px-4 py-2 rounded-xl transition-all whitespace-nowrap",
                      isPublic ? "bg-primary text-on-primary shadow-lg" : "bg-surface-variant/30 text-on-surface-variant"
                    )}
                  >
                    <Globe size={18} />
                    <span className="text-sm font-bold">公开</span>
                  </button>
                  <button 
                    onClick={() => setIsPublic(false)}
                    className={cn(
                      "flex items-center gap-2 px-4 py-2 rounded-xl transition-all whitespace-nowrap",
                      !isPublic ? "bg-primary text-on-primary shadow-lg" : "bg-surface-variant/30 text-on-surface-variant"
                    )}
                  >
                    <Lock size={18} />
                    <span className="text-sm font-bold">私密</span>
                  </button>
                </div>

                <button 
                  className={cn(
                    "flex items-center gap-3 px-6 py-3 rounded-2xl font-bold transition-all shadow-xl active:scale-95 group shrink-0",
                    title.trim() && content.trim() && community
                      ? "bg-primary text-on-primary hover:shadow-primary/20 scale-100" 
                      : "bg-surface-variant text-on-surface-variant opacity-50 scale-95"
                  )}
                  disabled={!title.trim() || !content.trim() || !community || submitting}
                  onClick={submitPost}
                >
                  <span className="whitespace-nowrap">{submitting ? '发布中...' : '立即发布'}</span>
                  <Send size={20} className="group-hover:translate-x-1 group-hover:-translate-y-1 transition-transform" />
                </button>
              </div>
              {error && <p className="mt-4 text-sm font-bold text-red-600">{error}</p>}
            </div>
          </motion.div>
        </div>
      )}
    </AnimatePresence>
  );
}
