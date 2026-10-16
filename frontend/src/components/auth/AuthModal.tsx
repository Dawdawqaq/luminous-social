import React, { useState, useEffect, useRef } from "react";
import { Modal } from "../ui/Modal";
import { Input } from "../ui/Input";
import { Button } from "../ui/Button";
import { SliderCaptcha } from "../ui/SliderCaptcha";
import { PasswordStrength } from "../ui/PasswordStrength";
import { ApiError, authApi, userApi } from "../../api/client";
import { useAuth } from "../../context/AuthContext";
import { useToast } from "../../context/ToastContext";
import { CheckCircle2 } from "lucide-react";
import { AvatarPresetGrid } from "../ui/AvatarPresetGrid";
import { useRemoteData } from "../../hooks/useRemoteData";
import type { ProfilePresetCatalog } from "../../profile/profilePresets";

interface AuthModalProps {
  isOpen: boolean;
  onClose: () => void;
  defaultMode?: "login" | "register";
}

export const AuthModal: React.FC<AuthModalProps> = ({
  isOpen,
  onClose,
  defaultMode = "login",
}) => {
  const { login } = useAuth();
  const { showToast } = useToast();
  const [mode, setMode] = useState<"login" | "register">(defaultMode);

  const [username, setUsername] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [password, setPassword] = useState("");

  const [captchaProof, setCaptchaProof] = useState("");
  const [captchaGeneration, setCaptchaGeneration] = useState(0);
  const submitting = useRef(false);
  const usernameGeneration = useRef(0);
  const [usernameStatus, setUsernameStatus] = useState<
    "idle" | "checking" | "available" | "taken"
  >("idle");
  const [usernameError, setUsernameError] = useState("");
  const [isLoading, setIsLoading] = useState(false);
  const [choosingAvatar, setChoosingAvatar] = useState(false);
  const [avatarPresetId, setAvatarPresetId] = useState("");
  const presets = useRemoteData<ProfilePresetCatalog | null>(userApi.profilePresets, [], null, isOpen && mode === "register");
  const formRef = useRef<HTMLFormElement>(null);
  const selectedAvatar = presets.data?.avatars.find(preset => preset.id === avatarPresetId);

  useEffect(() => {
    const avatars = presets.data?.avatars;
    if (avatars?.length && !avatars.some(preset => preset.id === avatarPresetId)) {
      setAvatarPresetId(avatars[Math.floor(Math.random() * avatars.length)].id);
    }
  }, [presets.data, avatarPresetId]);

  useEffect(() => {
    if (!choosingAvatar || presets.loading) return;
    const frame = requestAnimationFrame(() => formRef.current?.querySelector<HTMLInputElement>('input:checked, input[type="radio"]')?.focus());
    return () => cancelAnimationFrame(frame);
  }, [choosingAvatar, presets.loading]);

  const handleModeSwitch = (newMode: "login" | "register") => {
    if (submitting.current) return;
    usernameGeneration.current++;
    setMode(newMode);
    setUsername("");
    setDisplayName("");
    setPassword("");
    setCaptchaProof("");
    setCaptchaGeneration((value) => value + 1);
    setUsernameStatus("idle");
    setUsernameError("");
    setChoosingAvatar(false);
    setAvatarPresetId("");
  };

  useEffect(() => {
    usernameGeneration.current++;
    setMode(defaultMode);
    setUsername("");
    setDisplayName("");
    setPassword("");
    setCaptchaProof("");
    setCaptchaGeneration((value) => value + 1);
    setUsernameStatus("idle");
    setUsernameError("");
    setChoosingAvatar(false);
    setAvatarPresetId("");
  }, [defaultMode, isOpen]);

  const handleUsernameBlur = async () => {
    if (mode !== "register" || !username.trim()) return;
    const generation = ++usernameGeneration.current;

    if (!/^[a-zA-Z0-9_]{3,32}$/.test(username)) {
      setUsernameError("用户名只能包含 3-32 位字母、数字和下划线");
      setUsernameStatus("idle");
      return;
    }

    try {
      setUsernameStatus("checking");
      const res = await userApi.getProfile(username.trim());
      if (generation !== usernameGeneration.current) return;
      if (res && res.username) {
        setUsernameStatus("taken");
        setUsernameError("该用户 ID 已被注册");
      } else {
        setUsernameStatus("available");
        setUsernameError("");
      }
    } catch (error) {
      if (generation !== usernameGeneration.current) return;
      if (error instanceof ApiError && error.status === 404) {
        setUsernameStatus("available");
        setUsernameError("");
      } else {
        setUsernameStatus("idle");
        setUsernameError("暂时无法确认用户名是否可用，请稍后重试。");
      }
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (submitting.current) return;
    if (mode === "login" && (!username.trim() || !password)) {
      showToast("请完整填写用户 ID 与密码", "error");
      return;
    }

    if (
      mode === "register" &&
      (!username.trim() || !displayName.trim() || !password)
    ) {
      showToast("请完整填写用户 ID、昵称与密码", "error");
      return;
    }

    if (mode === "register" && !choosingAvatar) {
      if (usernameStatus === "taken") return;
      setChoosingAvatar(true);
      return;
    }

    if (!captchaProof) {
      showToast("请先完成下方的滑块安全验证", "warning");
      return;
    }

    try {
      submitting.current = true;
      setIsLoading(true);
      if (mode === "login") {
        const res = await authApi.login(
          {
            username: username.trim(),
            password,
          },
          captchaProof,
        );
        login(res.token, res.user);
        showToast(
          `登录成功，欢迎回来 ${res.user.displayName || res.user.username}`,
          "success",
        );
      } else {
        const res = await authApi.register(
          {
            username: username.trim(),
            displayName: displayName.trim(),
            password,
            avatarPresetId: selectedAvatar?.id,
          },
          captchaProof,
        );
        login(res.token, res.user);
        showToast("账号注册成功，已自动登录", "success");
      }
      onClose();
    } catch (err: any) {
      showToast(err.message || (mode === "register" ? "注册失败，请稍后重试" : "登录失败，请检查账号密码"), "error");
      if (err.status && err.status >= 400 && err.status < 500) {
        setCaptchaProof("");
        setCaptchaGeneration((value) => value + 1);
      }
    } finally {
      submitting.current = false;
      setIsLoading(false);
    }
  };

  return (
    <Modal
      isOpen={isOpen}
      onClose={() => { if (!submitting.current) onClose(); }}
      dismissible={!isLoading}
      title={mode === "login" ? "登录 Luminous" : choosingAvatar ? "选一只猫猫，作为你的头像" : "加入 Luminous"}
      subtitle={
        mode === "login" ? "登录后，继续分享和讨论。" : choosingAvatar ? "以后点击个人主页的头像，随时可以更换。" : "找到同好，聊点喜欢的。"
      }
      maxWidth={choosingAvatar ? "lg" : "sm"}
    >
      <form
        ref={formRef}
        onSubmit={handleSubmit}
        className="space-y-4"
        autoComplete={mode === "register" ? "off" : "on"}
      >
        {choosingAvatar ? <div className="space-y-3">
          {presets.loading && <p role="status" className="py-8 text-center text-sm text-ink-muted dark:text-night-muted">正在加载猫猫头像…</p>}
          {presets.data && <AvatarPresetGrid presets={presets.data.avatars} value={selectedAvatar?.url}
            disabled={isLoading} onSelect={(preset) => setAvatarPresetId(preset.id)} />}
          {presets.error && <div role="alert" className="space-y-2 text-sm">
            <p>头像预设暂时无法加载。可以重试，或先注册使用随机头像，之后再更换。</p>
            <Button variant="outline" size="sm" onClick={presets.reload} disabled={isLoading}>重新加载头像</Button>
          </div>}
        </div> : <>
        <div>
          <Input
            label="用户名"
            minLength={mode === "register" ? 3 : undefined}
            maxLength={32}
            pattern={mode === "register" ? "[a-zA-Z0-9_]+" : undefined}
            placeholder={
              mode === "register"
                ? "设置用户 ID (字母/数字/下划线)"
                : "输入用户 ID"
            }
            value={username}
            autoComplete={mode === "register" ? "off" : "username"}
            onChange={(e) => {
              usernameGeneration.current++;
              setUsername(e.target.value);
              setUsernameStatus("idle");
              setUsernameError("");
            }}
            onBlur={handleUsernameBlur}
            error={usernameError}
            required
            disabled={isLoading}
          />
          {mode === "register" &&
            usernameStatus === "available" &&
            !usernameError && (
              <p className="text-[11px] text-[#5B8266] flex items-center gap-1 mt-1 font-sans">
                <CheckCircle2 className="w-3 h-3" /> 该用户 ID 可用
              </p>
            )}
        </div>

        {mode === "register" && (
          <Input
            label="昵称"
            minLength={2}
            maxLength={96}
            placeholder="输入公开显示的昵称"
            value={displayName}
            autoComplete="off"
            onChange={(e) => setDisplayName(e.target.value)}
            required
            disabled={isLoading}
          />
        )}

        <div>
          <Input
            label="密码"
            minLength={mode === "register" ? 6 : undefined}
            maxLength={72}
            type="password"
            placeholder={
              mode === "register" ? "设置密码 (至少 6 位)" : "输入密码"
            }
            value={password}
            autoComplete={
              mode === "register" ? "new-password" : "current-password"
            }
            onChange={(e) => setPassword(e.target.value)}
            required
            disabled={isLoading}
          />
          {mode === "register" && <PasswordStrength password={password} />}
        </div>
        </>}

        {(mode === "login" || choosingAvatar) && <SliderCaptcha
          key={`${mode}-${captchaGeneration}`}
          purpose={mode}
          onReset={() => setCaptchaProof("")}
          onSuccess={(proof) => setCaptchaProof(proof)}
        />}

        {choosingAvatar && <Button className="w-full" variant="outline" disabled={isLoading} onClick={() => setChoosingAvatar(false)}>返回账号资料</Button>}
        <Button
          type="submit"
          className="w-full mt-2"
          isLoading={isLoading}
          disabled={mode === "login" || choosingAvatar ? !captchaProof || presets.loading : usernameStatus === "taken" || usernameStatus === "checking"}
        >
          {mode === "login" ? "立即登录" : choosingAvatar ? selectedAvatar ? "使用这个头像，完成注册" : "完成注册" : "下一步：选择头像"}
        </Button>

        <div className="text-center pt-2 hairline-t text-xs font-sans text-ink-muted dark:text-night-muted">
          {mode === "login" ? (
            <p>
              还没有账号？{" "}
              <button
                type="button"
                disabled={isLoading}
                onClick={() => handleModeSwitch("register")}
                className="text-terracotta dark:text-terracotta-dark hover:underline font-medium cursor-pointer"
              >
                立即注册 ↗
              </button>
            </p>
          ) : (
            <p>
              已有账号？{" "}
              <button
                type="button"
                disabled={isLoading}
                onClick={() => handleModeSwitch("login")}
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
