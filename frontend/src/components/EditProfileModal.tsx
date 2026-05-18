import { useState } from 'react';
import { X, Upload, Loader2, User as UserIcon, Image as ImageIcon } from 'lucide-react';
import { useAuth } from '../auth/AuthContext';
import { api } from '../lib/api';

interface EditProfileModalProps {
  onClose: () => void;
}

export function EditProfileModal({ onClose }: EditProfileModalProps) {
  const { user, updateProfile } = useAuth();
  const [displayName, setDisplayName] = useState(user?.displayName || '');
  const [bio, setBio] = useState(user?.bio || '');
  const [avatarUrl, setAvatarUrl] = useState(user?.avatar || '');
  const [bannerUrl, setBannerUrl] = useState(user?.banner || '');
  const [submitting, setSubmitting] = useState(false);
  const [uploadingAvatar, setUploadingAvatar] = useState(false);
  const [uploadingBanner, setUploadingBanner] = useState(false);
  const [error, setError] = useState('');

  if (!user) return null;

  const handleAvatarUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    setUploadingAvatar(true);
    try {
      const res = await api.uploadImage(file);
      setAvatarUrl(res.url);
    } catch (err: any) {
      setError(err.message || '上传头像失败');
    } finally {
      setUploadingAvatar(false);
    }
  };

  const handleBannerUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    setUploadingBanner(true);
    try {
      const res = await api.uploadImage(file);
      setBannerUrl(res.url);
    } catch (err: any) {
      setError(err.message || '上传背景失败');
    } finally {
      setUploadingBanner(false);
    }
  };

  const handleSubmit = async () => {
    setSubmitting(true);
    setError('');
    try {
      await updateProfile({
        displayName,
        bio,
        avatar: avatarUrl,
        banner: bannerUrl
      });
      onClose();
    } catch (err: any) {
      setError(err.message || '更新资料失败');
      setSubmitting(false);
    }
  };

  return (
    <div className="fixed inset-0 z-[200] flex items-center justify-center p-4 bg-black/40 backdrop-blur-sm">
      <div className="w-full max-w-[500px] bg-surface-container-lowest rounded-3xl overflow-hidden shadow-2xl flex flex-col max-h-[90vh]">
        <div className="flex items-center justify-between p-6 border-b border-outline-variant">
          <h2 className="text-xl font-bold text-on-surface">编辑个人资料</h2>
          <button onClick={onClose} className="p-2 text-on-surface-variant hover:bg-surface-variant/50 rounded-full">
            <X size={20} />
          </button>
        </div>
        
        <div className="flex-1 overflow-y-auto p-6 flex flex-col gap-6">
          {error && <div className="text-red-600 text-sm font-bold bg-red-50 p-3 rounded-xl">{error}</div>}
          
          <div className="flex flex-col gap-2">
            <label className="text-sm font-bold text-on-surface-variant flex items-center gap-2">
              <ImageIcon size={16} /> 背景封面
            </label>
            <div className="relative h-32 bg-surface-container-low rounded-2xl overflow-hidden group border border-outline-variant">
              {bannerUrl ? (
                <img src={bannerUrl} alt="Banner" className="w-full h-full object-cover" />
              ) : (
                <div className="w-full h-full flex items-center justify-center text-on-surface-variant/30">
                  <ImageIcon size={32} />
                </div>
              )}
              <label className="absolute inset-0 bg-black/40 flex items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity cursor-pointer">
                <input type="file" accept="image/*" className="hidden" onChange={handleBannerUpload} disabled={uploadingBanner} />
                {uploadingBanner ? <Loader2 className="animate-spin text-white" size={24} /> : <Upload className="text-white" size={24} />}
              </label>
            </div>
          </div>

          <div className="flex flex-col gap-2">
            <label className="text-sm font-bold text-on-surface-variant flex items-center gap-2">
              <UserIcon size={16} /> 个人头像
            </label>
            <div className="relative w-24 h-24 bg-primary-container rounded-[1.5rem] overflow-hidden group border border-outline-variant mx-auto">
              {avatarUrl ? (
                <img src={avatarUrl} alt="Avatar" className="w-full h-full object-cover" />
              ) : (
                <div className="w-full h-full flex items-center justify-center text-on-primary-container font-bold text-3xl">
                  {user.username.slice(0, 2).toUpperCase()}
                </div>
              )}
              <label className="absolute inset-0 bg-black/40 flex items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity cursor-pointer rounded-[1.5rem]">
                <input type="file" accept="image/*" className="hidden" onChange={handleAvatarUpload} disabled={uploadingAvatar} />
                {uploadingAvatar ? <Loader2 className="animate-spin text-white" size={24} /> : <Upload className="text-white" size={24} />}
              </label>
            </div>
          </div>

          <div className="flex flex-col gap-2">
            <label className="text-sm font-bold text-on-surface-variant">显示名称</label>
            <input 
              value={displayName} 
              onChange={e => setDisplayName(e.target.value)}
              className="bg-surface-container-low border border-outline-variant rounded-xl px-4 py-3 outline-none focus:border-primary w-full text-on-surface"
              placeholder="您的称呼"
            />
          </div>

          <div className="flex flex-col gap-2">
            <label className="text-sm font-bold text-on-surface-variant">个人简介</label>
            <textarea 
              value={bio} 
              onChange={e => setBio(e.target.value)}
              className="bg-surface-container-low border border-outline-variant rounded-xl px-4 py-3 outline-none focus:border-primary w-full resize-none h-24 text-on-surface"
              placeholder="介绍一下你自己..."
            />
          </div>
        </div>

        <div className="p-6 border-t border-outline-variant flex justify-end gap-3 bg-surface">
          <button onClick={onClose} className="px-5 py-2.5 rounded-full font-bold text-on-surface-variant hover:bg-surface-variant/50 transition-colors">取消</button>
          <button onClick={handleSubmit} disabled={submitting} className="px-6 py-2.5 rounded-full font-bold text-on-primary bg-primary hover:shadow-lg disabled:opacity-60 transition-all flex items-center gap-2">
            {submitting && <Loader2 size={16} className="animate-spin" />}
            保存资料
          </button>
        </div>
      </div>
    </div>
  );
}
