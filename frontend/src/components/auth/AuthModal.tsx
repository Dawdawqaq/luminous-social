import React, { useState, useEffect, useRef } from 'react';
import { Modal } from '../ui/Modal';
import { Input } from '../ui/Input';
import { Button } from '../ui/Button';
import { SliderCaptcha } from '../ui/SliderCaptcha';
import { PasswordStrength } from '../ui/PasswordStrength';
import { authApi, userApi } from '../../api/client';
import { useAuth } from '../../context/AuthContext';
import { useToast } from '../../context/ToastContext';
import { CheckCircle2 } from 'lucide-react';

interface AuthModalProps {
  isOpen: boolean;
  onClose: () => void;
  defaultMode?: 'login' | 'register';
}

export const AuthModal: React.FC<AuthModalProps> = ({
  isOpen,
  onClose,
  defaultMode = 'login',
}) => {
  const { login } = useAuth();
  const { showToast } = useToast();
  const [mode, setMode] = useState<'login' | 'register'>(defaultMode);

  const [username, setUsername] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [password, setPassword] = useState('');

  const [captchaProof, setCaptchaProof] = useState('');
  const [captchaGeneration, setCaptchaGeneration] = useState(0);
  const submitting = useRef(false);
  const [usernameStatus, setUsernameStatus] = useState<'idle' | 'checking' | 'available' | 'taken'>('idle');
  const [usernameError, setUsernameError] = useState('');
  const [isLoading, setIsLoading] = useState(false);

  const handleModeSwitch = (newMode: 'login' | 'register') => {
    setMode(newMode);
    setUsername('');
    setDisplayName('');
    setPassword('');
    setCaptchaProof('');
    setCaptchaGeneration(value => value + 1);
    setUsernameStatus('idle');
    setUsernameError('');
  };

  useEffect(() => {
    setMode(defaultMode);
    setUsername('');
    setDisplayName('');
    setPassword('');
    setCaptchaProof('');
    setCaptchaGeneration(value => value + 1);
    setUsernameStatus('idle');
    setUsernameError('');
  }, [defaultMode, isOpen]);

  const handleUsernameBlur = async () => {
    if (mode !== 'register' || !username.trim()) return;

    if (!/^[a-zA-Z0-9_]{3,32}$/.test(username)) {
      setUsernameError('用户 ID 只能包含 3-32 位字母、数字和下划线');
      setUsernameStatus('idle');
      return;
    }

    try {
      setUsernameStatus('checking');
      const res = await userApi.getProfile(username.trim());
      if (res && res.username) {
        setUsernameStatus('taken');
        setUsernameError('该用户 ID 已被注册');
      } else {
        setUsernameStatus('available');
        setUsernameError('');
      }
    } catch {
      setUsernameStatus('available');
      setUsernameError('');
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (submitting.current) return;
    if (mode === 'login' && (!username.trim() || !password)) {
      showToast('请完整填写用户 ID 与密码', 'error');
      return;
    }

    if (mode === 'register' && (!username.trim() || !displayName.trim() || !password)) {
      showToast('请完整填写用户 ID、昵称与密码', 'error');
      return;
    }

    if (!captchaProof) {
      showToast('请先完成下方的滑块安全验证', 'warning');
      return;
    }

    try {
      submitting.current = true;
      setIsLoading(true);
      if (mode === 'login') {
        const res = await authApi.login({
          username: username.trim(),
          password,
        }, captchaProof);
        login(res.token, res.user);
        showToast(`登录成功，欢迎回来 ${res.user.displayName || res.user.username}`, 'success');
      } else {
        const res = await authApi.register({
          username: username.trim(),
          displayName: displayName.trim(),
          password,
        }, captchaProof);
        login(res.token, res.user);
        showToast('账号注册成功，已自动登录', 'success');
      }
      onClose();
    } catch (err: any) {
      showToast(err.message || '登录失败，请检查账号密码', 'error');
      if (err.status && err.status >= 400 && err.status < 500) {
        setCaptchaProof(''); setCaptchaGeneration(value => value + 1);
      }
    } finally {
      submitting.current = false;
      setIsLoading(false);
    }
  };

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title={mode === 'login' ? '用户登录 · Log In' : '注册新账号 · Register'}
      subtitle={
        mode === 'login'
          ? '登录您的 Luminous 社区账号以参与发帖和讨论'
          : '创建一个新账号，开启您的社区探索之旅'
      }
      maxWidth="sm"
    >
      <form onSubmit={handleSubmit} className="space-y-4" autoComplete={mode === 'register' ? 'off' : 'on'}>
        <div>
          <Input
            label="用户 ID"
            placeholder={mode === 'register' ? '设置用户 ID (字母/数字/下划线)' : '输入用户 ID'}
            value={username}
            autoComplete={mode === 'register' ? 'off' : 'username'}
            onChange={(e) => {
              setUsername(e.target.value);
              setUsernameStatus('idle');
              setUsernameError('');
            }}
            onBlur={handleUsernameBlur}
            error={usernameError}
            required
          />
          {mode === 'register' && usernameStatus === 'available' && !usernameError && (
            <p className="text-[11px] text-[#5B8266] flex items-center gap-1 mt-1 font-sans">
              <CheckCircle2 className="w-3 h-3" /> 该用户 ID 可用
            </p>
          )}
        </div>

        {mode === 'register' && (
          <Input
            label="昵称"
            placeholder="输入公开显示的昵称"
            value={displayName}
            autoComplete="off"
            onChange={(e) => setDisplayName(e.target.value)}
            required
          />
        )}

        <div>
          <Input
            label="密码"
            type="password"
            placeholder={mode === 'register' ? '设置密码 (至少 6 位)' : '输入密码'}
            value={password}
            autoComplete={mode === 'register' ? 'new-password' : 'current-password'}
            onChange={(e) => setPassword(e.target.value)}
            required
          />
          {mode === 'register' && <PasswordStrength password={password} />}
        </div>

        <SliderCaptcha key={`${mode}-${captchaGeneration}`} purpose={mode} onReset={() => setCaptchaProof('')}
          onSuccess={proof => setCaptchaProof(proof)} />

        <Button
          type="submit"
          className="w-full mt-2"
          isLoading={isLoading}
          disabled={!captchaProof}
        >
          {mode === 'login' ? '立即登录' : '完成注册'}
        </Button>

        <div className="text-center pt-2 hairline-t text-xs font-sans text-ink-muted dark:text-night-muted">
          {mode === 'login' ? (
            <p>
              还没有账号？{' '}
              <button
                type="button"
                onClick={() => handleModeSwitch('register')}
                className="text-terracotta dark:text-terracotta-dark hover:underline font-medium cursor-pointer"
              >
                立即注册 ↗
              </button>
            </p>
          ) : (
            <p>
              已有账号？{' '}
              <button
                type="button"
                onClick={() => handleModeSwitch('login')}
                className="text-terracotta dark:text-terracotta-dark hover:underline font-medium cursor-pointer"
              >
                返回登录 ↗
              </button>
            </p>
          )}
        </div>
      </form>
    </Modal>
  );
};
