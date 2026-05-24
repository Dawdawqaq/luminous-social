import { Home, TrendingUp, Users, Star, Settings, Plus, Search, Menu, Stars, LogOut, ChevronLeft, ChevronRight, Sparkles, AlertCircle, CheckCircle2, ShieldCheck } from 'lucide-react';
import { NavLink, useNavigate } from 'react-router-dom';
import { cn } from '../lib/utils';
import { useAuth } from '../auth/AuthContext';
import { useState, useEffect } from 'react';
import { motion } from 'motion/react';

interface SidebarProps {
  isOpen?: boolean;
  onClose?: () => void;
  onToggle?: () => void;
  onPostClick?: () => void;
  onCreateCommunityClick?: () => void;
}

export function Sidebar({ isOpen, onClose, onToggle, onPostClick, onCreateCommunityClick }: SidebarProps) {
  const { user } = useAuth();
  
  const navItems = [
    { name: '首页', icon: Home, path: '/' },
    { name: '热门', icon: TrendingUp, path: '/trending' },
    { name: '新帖', icon: Stars, path: '/new' },
    { name: '社区', icon: Users, path: '/communities' },
    { name: '收藏', icon: Star, path: '/bookmarks' },
    { name: '个人资料', icon: Settings, path: '/settings' },
  ];

  if (user && user.role === 'ADMIN') {
    navItems.push({ name: '全局控制台', icon: ShieldCheck, path: '/admin' });
  }

  return (
    <>
      {/* Mobile Backdrop */}
      {isOpen && (
        <div 
          className="fixed inset-0 z-[90] lg:hidden"
          onClick={onClose}
        />
      )}

      <aside className={cn(
        "fixed top-0 h-screen w-[280px] bg-surface border-r border-outline-variant transition-transform duration-300 z-[100] shadow-2xl lg:shadow-none",
        isOpen ? "translate-x-0" : "-translate-x-full"
      )}>
        <div className="flex flex-col gap-4 h-full overflow-y-auto pt-20 px-4 pb-8 no-scrollbar">
          <div className="mb-8 px-2 flex items-center justify-between mt-4">
            <NavLink to="/" className="inline-block" onClick={() => onClose?.()}>
              <span className="font-display font-bold text-3xl bg-clip-text text-transparent bg-gradient-to-r from-primary via-[#EA4335] to-tertiary select-none">
                Luminous
              </span>
            </NavLink>
          </div>

          <nav className="flex flex-col gap-1 px-2">
            {navItems.map((item) => (
              <NavLink
                key={item.path}
                to={item.path}
                onClick={() => onClose?.()}
                className={({ isActive }) =>
                  cn(
                    "nav-item",
                    isActive ? "nav-item-active" : "nav-item-inactive"
                  )
                }
              >
                {({ isActive }) => (
                  <>
                    <item.icon size={24} strokeWidth={isActive ? 2.5 : 2} />
                    <span className="text-sm font-semibold tracking-wide">
                      {item.name}
                    </span>
                  </>
                )}
              </NavLink>
            ))}
          </nav>

          <div className="mt-auto px-4 flex flex-col gap-3">
            <button 
              onClick={() => {
                onCreateCommunityClick?.();
                onClose?.();
              }}
              className="w-full bg-surface-container-high text-on-surface-variant py-3.5 rounded-xl font-bold flex items-center justify-center gap-2 hover:bg-surface-variant transition-all active:scale-95 text-sm border border-outline-variant/30"
            >
              <Sparkles size={18} />
              创建社区
            </button>
            <button 
              onClick={() => {
                onPostClick?.();
                onClose?.();
              }}
              className="w-full bg-primary text-on-primary py-4 rounded-xl font-bold flex items-center justify-center gap-2 hover:shadow-lg transition-all active:scale-95"
            >
              <Plus size={20} />
              发布帖子
            </button>
          </div>
        </div>

        {/* Toggle Drawer Handle */}
        <button
          onClick={onToggle}
          className={cn(
            "hidden lg:flex absolute top-1/2 -translate-y-1/2 items-center justify-center cursor-pointer hover:text-primary text-on-surface-variant z-50 transition-all",
            isOpen 
              ? "w-8 h-8 -right-4 rounded-full bg-surface border border-outline-variant shadow-sm hover:scale-110" 
              : "w-8 h-16 -right-8 rounded-r-xl bg-surface border border-l-0 border-outline-variant shadow-[4px_0_8px_rgba(0,0,0,0.05)] hover:bg-surface-variant/30"
          )}
          title={isOpen ? "收起侧边栏" : "展开侧边栏"}
        >
          {isOpen ? <ChevronLeft size={20} /> : <ChevronRight size={20} />}
        </button>
      </aside>
    </>
  );
}

interface TopNavProps {
  onMenuClick?: () => void;
}

export function TopNav({ onMenuClick }: TopNavProps) {
  const { user, login, register, logout } = useAuth();
  const navigate = useNavigate();
  const [searchQuery, setSearchQuery] = useState('');
  const [mode, setMode] = useState<'login' | 'register'>('login');
  const [showAuth, setShowAuth] = useState(false);
  const [authMessage, setAuthMessage] = useState('');
  const [username, setUsername] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  // 方案一新增：体验校验与安全防守状态
  const [usernameTip, setUsernameTip] = useState('');
  const [isUsernameValid, setIsUsernameValid] = useState(true);
  const [isCheckingUsername, setIsCheckingUsername] = useState(false);
  const [passwordStrength, setPasswordStrength] = useState<'weak' | 'medium' | 'strong' | ''>('');
  const [isSliderVerified, setIsSliderVerified] = useState(false);
  const [sliderVal, setSliderVal] = useState(0);

  // 当模式改变或弹窗关闭时，重置所有校验状态与值，确保交互纯净
  useEffect(() => {
    setError('');
    setUsername('');
    setDisplayName('');
    setPassword('');
    setUsernameTip('');
    setIsUsernameValid(true);
    setIsCheckingUsername(false);
    setPasswordStrength('');
    setIsSliderVerified(false);
    setSliderVal(0);
  }, [mode, showAuth]);

  useEffect(() => {
    const handleAuthRequired = (e: Event) => {
      const customEvent = e as CustomEvent<{ message?: string }>;
      const msg = customEvent.detail?.message || '您需要登录后才能继续操作';
      setAuthMessage(msg);
      setMode('login');
      setShowAuth(true);
    };
    window.addEventListener('auth:required', handleAuthRequired as EventListener);
    return () => window.removeEventListener('auth:required', handleAuthRequired as EventListener);
  }, []);

  const handleUsernameChange = (val: string) => {
    setUsername(val);
    if (mode === 'register') {
      setIsUsernameValid(true);
      setUsernameTip('');
    }
  };

  // 失去焦点时实时查重校验：利用已放行的 GET /api/users/{username} 优雅探测是否被占用
  const handleUsernameBlur = async () => {
    if (!username.trim() || mode !== 'register') return;
    
    const reg = /^[a-zA-Z0-9_]{3,32}$/;
    if (!reg.test(username)) {
      setIsUsernameValid(false);
      setUsernameTip('用户ID只能由字母、数字和下划线组成，长度为 3-32 位');
      return;
    }
    
    setIsCheckingUsername(true);
    setUsernameTip('正在检测 ID 是否可用...');
    try {
      const { api } = await import('../lib/api');
      await api.userProfile(username.trim());
      // 成功获取到资料，代表 ID 已经被占用！
      setIsUsernameValid(false);
      setUsernameTip('该用户 ID 已被他人捷足先登啦');
    } catch (err: any) {
      if (err.status === 404 || err.message?.includes('NOT_FOUND') || err.message?.includes('不存在')) {
        setIsUsernameValid(true);
        setUsernameTip('恭喜！该用户 ID 可以使用 ✓');
      } else {
        setIsUsernameValid(false);
        setUsernameTip('检测用户名失败，请重试');
      }
    } finally {
      setIsCheckingUsername(false);
    }
  };

  const handlePasswordChange = (val: string) => {
    setPassword(val);
    if (mode !== 'register') return;
    if (!val) {
      setPasswordStrength('');
      return;
    }
    if (val.length < 6) {
      setPasswordStrength('weak');
      return;
    }
    const hasSpecial = /[!@#$%^&*(),.?":{}|<>_]/;
    if (hasSpecial.test(val)) {
      setPasswordStrength('strong');
    } else {
      setPasswordStrength('medium');
    }
  };

  // 联合安全硬守卫
  const isFormValid = mode === 'login' 
    ? (username && password && isSliderVerified)
    : (username && displayName && password && isUsernameValid && !isCheckingUsername && password.length >= 6 && isSliderVerified);

  async function submitAuth() {
    if (!isSliderVerified) {
      setError('请拖动滑块完成人机验证');
      return;
    }
    if (mode === 'register') {
      if (!isUsernameValid) {
        setError('请修正不合规的用户ID');
        return;
      }
      if (password.length < 6) {
        setError('密码长度不能少于 6 位');
        return;
      }
    }
    setError('');
    setSubmitting(true);
    try {
      if (mode === 'login') {
        await login(username, password);
      } else {
        await register(username, displayName, password);
      }
      setShowAuth(false);
      setAuthMessage('');
    } catch (ex) {
      setError(ex instanceof Error ? ex.message : '认证失败');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <>
      <header className="glass-header h-16 w-full flex items-center px-4 fixed top-0 z-[60]">
        <div className="max-w-[1440px] w-full flex items-center justify-between mx-auto">
          <div className="flex items-center gap-4">
            <button 
              onClick={onMenuClick}
              className="lg:hidden p-2 -ml-2 text-on-surface-variant hover:bg-surface-variant/30 rounded-full transition-colors"
            >
              <Menu size={24} />
            </button>
            <div className="flex items-center">
              <NavLink to="/" className="inline-block">
                <span className="font-display font-bold text-2xl bg-clip-text text-transparent bg-gradient-to-r from-primary via-[#EA4335] to-tertiary select-none">
                  Luminous
                </span>
              </NavLink>
            </div>
          </div>

          <div className="hidden md:flex bg-surface-container-highest/50 rounded-full px-4 py-2 items-center gap-2 focus-within:ring-2 focus-within:ring-primary transition-all mx-auto w-full max-w-[600px]">
            <Search size={20} className="text-on-surface-variant" />
            <input
              type="text"
              placeholder="搜索社区..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === 'Enter' && searchQuery.trim()) {
                  navigate(`/search?q=${encodeURIComponent(searchQuery.trim())}`);
                }
              }}
              className="bg-transparent border-none outline-none focus:outline-none focus:ring-0 text-sm w-full font-medium placeholder:text-on-surface-variant/70"
              style={{ border: 'none', outline: 'none', boxShadow: 'none' }}
            />
          </div>

          <div className="flex items-center gap-4">
            <button className="md:hidden p-2 text-on-surface-variant">
              <Search size={24} />
            </button>
            {user ? (
              <div className="flex items-center gap-2">
                <NavLink to="/settings" className="w-8 h-8 rounded-full bg-primary-container text-on-primary-container flex items-center justify-center font-bold text-xs cursor-pointer overflow-hidden hover:opacity-80 transition-opacity">
                  {user.avatar ? <img src={user.avatar} alt={user.username} className="w-full h-full object-cover" /> : user.username.slice(0, 2).toUpperCase()}
                </NavLink>
                <button onClick={logout} className="p-2 text-on-surface-variant hover:bg-surface-variant/30 rounded-full">
                  <LogOut size={18} />
                </button>
              </div>
            ) : (
              <button
                onClick={() => {
                  setAuthMessage('');
                  setShowAuth(true);
                }}
                className="px-4 py-2 bg-primary text-on-primary rounded-full text-sm font-bold hover:shadow-md transition-all"
              >
                登录
              </button>
            )}
          </div>
        </div>
      </header>

      {showAuth && (
        <div className="fixed inset-0 z-[220] flex items-center justify-center p-4 bg-black/40 backdrop-blur-sm">
          <div className="w-full max-w-[420px] rounded-3xl bg-surface-container-lowest border border-outline-variant shadow-2xl p-6 relative overflow-hidden">
            <div className="flex bg-surface-container-low p-1 rounded-2xl mb-6">
              <button onClick={() => setMode('login')} className={cn("flex-1 py-2 rounded-xl text-sm font-bold transition-all", mode === 'login' ? "bg-primary text-on-primary shadow-sm scale-[1.02]" : "text-on-surface-variant")}>登录</button>
              <button onClick={() => setMode('register')} className={cn("flex-1 py-2 rounded-xl text-sm font-bold transition-all", mode === 'register' ? "bg-primary text-on-primary shadow-sm scale-[1.02]" : "text-on-surface-variant")}>注册</button>
            </div>
            
            {authMessage && (
              <motion.div 
                initial={{ opacity: 0, y: -10 }}
                animate={{ opacity: 1, y: 0 }}
                className="mb-6 p-4 rounded-2xl bg-gradient-to-r from-primary/10 via-[#EA4335]/5 to-tertiary/10 border border-primary/20 flex items-start gap-3"
              >
                <div className="p-1.5 rounded-xl bg-primary/20 text-primary shrink-0 mt-0.5">
                  <Sparkles size={16} />
                </div>
                <div>
                  <p className="text-xs font-bold text-primary mb-0.5">温馨提示</p>
                  <p className="text-sm font-semibold text-on-surface-variant leading-normal">{authMessage}</p>
                </div>
              </motion.div>
            )}
            
            <div className="flex flex-col gap-4">
              {/* 用户 ID 输入与检测 */}
              <div className="flex flex-col gap-1.5 relative">
                <input 
                  value={username} 
                  onChange={(e) => handleUsernameChange(e.target.value)} 
                  onBlur={handleUsernameBlur}
                  placeholder={mode === 'register' ? "唯一用户 ID (只能由字母、数字、下划线组成)" : "用户 ID"} 
                  className={cn(
                    "bg-surface-container-low border rounded-2xl px-4 py-3 outline-none transition-all w-full text-sm font-semibold focus:ring-1 focus:ring-primary/20",
                    mode === 'register' && !isUsernameValid ? "border-error focus:border-error text-error" : "border-outline-variant focus:border-primary"
                  )}
                />
                
                {/* 注册用户名合规及查重提示气泡 */}
                {mode === 'register' && usernameTip && (
                  <motion.div 
                    initial={{ opacity: 0, y: -5 }}
                    animate={{ opacity: 1, y: 0 }}
                    className={cn(
                      "text-xs px-3.5 py-2 rounded-xl flex items-center gap-1.5 font-bold border backdrop-blur-md shadow-sm transition-all",
                      isUsernameValid 
                        ? "bg-primary/5 text-primary border-primary/20" 
                        : "bg-error/5 text-error border-error/20"
                    )}
                  >
                    {isUsernameValid ? <CheckCircle2 size={12} className="shrink-0" /> : <AlertCircle size={12} className="shrink-0" />}
                    <span>{usernameTip}</span>
                  </motion.div>
                )}
              </div>

              {/* 昵称 */}
              {mode === 'register' && (
                <input 
                  value={displayName} 
                  onChange={(e) => setDisplayName(e.target.value)} 
                  placeholder="显示名称 (例如: 飞力创作)" 
                  className="bg-surface-container-low border border-outline-variant rounded-2xl px-4 py-3 outline-none focus:border-primary text-sm font-semibold w-full" 
                />
              )}

              {/* 密码输入与强度 */}
              <div className="flex flex-col gap-1.5">
                <input 
                  type="password" 
                  value={password} 
                  onChange={(e) => handlePasswordChange(e.target.value)} 
                  placeholder="账户密码 (不少于 6 位)" 
                  className="bg-surface-container-low border border-outline-variant rounded-2xl px-4 py-3 outline-none focus:border-primary text-sm font-semibold w-full" 
                />
                
                {/* 密码强度指示条 */}
                {mode === 'register' && password && (
                  <div className="flex flex-col gap-1 px-1">
                    <div className="flex justify-between items-center text-[10px] font-black text-on-surface-variant/70">
                      <span>密码强度: {passwordStrength === 'weak' ? '弱 (请输入至少 6 位)' : passwordStrength === 'medium' ? '中 (含特殊符号更安全)' : '安全强度极高 ✓'}</span>
                    </div>
                    <div className="h-1 bg-surface-container-high rounded-full overflow-hidden flex gap-0.5">
                      <div className={cn("h-full rounded-full transition-all duration-300", 
                        passwordStrength === 'weak' ? "w-1/3 bg-error" : passwordStrength === 'medium' ? "w-2/3 bg-yellow-500" : "w-full bg-primary"
                      )} />
                    </div>
                  </div>
                )}
              </div>

              {/* 拖动拼图人机校验滑块 */}
              <div className="flex flex-col gap-2 mt-2">
                <div className={cn(
                  "relative h-11 rounded-2xl border flex items-center justify-center overflow-hidden transition-all bg-surface-container-low shadow-inner",
                  isSliderVerified ? "border-primary bg-primary/5" : "border-outline-variant"
                )}>
                  <span className={cn(
                    "text-xs font-black select-none pointer-events-none transition-opacity",
                    isSliderVerified ? "text-primary opacity-100 animate-pulse" : "text-on-surface-variant/50"
                  )}>
                    {isSliderVerified ? "人机验证已通过 ✓" : "右滑滑块完成人机验证"}
                  </span>
                  
                  <div 
                    className="absolute left-0 top-0 bottom-0 bg-gradient-to-r from-primary/10 via-[#EA4335]/10 to-tertiary/10 transition-all pointer-events-none"
                    style={{ width: `${sliderVal}%` }}
                  />

                  <input
                    type="range"
                    min="0"
                    max="100"
                    value={sliderVal}
                    disabled={isSliderVerified}
                    onChange={(e) => {
                      const val = parseInt(e.target.value);
                      setSliderVal(val);
                      if (val >= 90) {
                        setIsSliderVerified(true);
                        setSliderVal(100);
                      }
                    }}
                    onMouseUp={() => {
                      if (sliderVal < 90) {
                        setSliderVal(0);
                        setIsSliderVerified(false);
                      }
                    }}
                    onTouchEnd={() => {
                      if (sliderVal < 90) {
                        setSliderVal(0);
                        setIsSliderVerified(false);
                      }
                    }}
                    className={cn(
                      "absolute inset-0 w-full h-full opacity-10 cursor-pointer accent-primary",
                      isSliderVerified && "pointer-events-none opacity-0"
                    )}
                  />
                  
                  {!isSliderVerified && (
                    <div 
                      className="absolute left-1 top-1 bottom-1 w-9 bg-primary text-on-primary rounded-xl flex items-center justify-center shadow-md cursor-grab transition-all pointer-events-none"
                      style={{ transform: `translateX(${sliderVal * 3.3}px)` }}
                    >
                      <ShieldCheck size={16} className="animate-pulse" />
                    </div>
                  )}
                </div>
              </div>

              {error && (
                <motion.p 
                  initial={{ opacity: 0, scale: 0.95 }}
                  animate={{ opacity: 1, scale: 1 }}
                  className="text-xs font-bold text-error bg-error/5 border border-error/20 p-2.5 rounded-xl flex items-center gap-1.5"
                >
                  <AlertCircle size={14} className="shrink-0" />
                  <span>{error}</span>
                </motion.p>
              )}

              <div className="flex justify-end gap-3 pt-2">
                <button onClick={() => setShowAuth(false)} className="px-5 py-2.5 rounded-full font-bold text-on-surface-variant hover:bg-surface-variant/40 text-xs">取消</button>
                <button 
                  onClick={submitAuth} 
                  disabled={submitting || !isFormValid} 
                  className={cn(
                    "px-6 py-2.5 rounded-full font-bold text-xs shadow-md transition-all flex items-center gap-1.5",
                    isFormValid 
                      ? "bg-primary text-on-primary hover:shadow-lg hover:scale-[1.02]" 
                      : "bg-surface-variant text-on-surface-variant/40 pointer-events-none"
                  )}
                >
                  {submitting ? '处理中...' : mode === 'login' ? '登录' : '立即注册'}
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
    </>
  );
}
