import React, { useState, useRef, useEffect, useCallback } from "react";
import { ShieldCheck, ArrowRight, Check, RotateCw } from "lucide-react";
import { captchaApi, CaptchaChallenge } from "../../api/client";
import { cn } from "../../utils";

interface SliderCaptchaProps {
  purpose: "login" | "register";
  onSuccess: (proof: string) => void;
  onReset?: () => void;
  className?: string;
}

export const SliderCaptcha: React.FC<SliderCaptchaProps> = ({
  purpose,
  onSuccess,
  onReset,
  className,
}) => {
  const [challenge, setChallenge] = useState<CaptchaChallenge | null>(null);
  const [position, setPosition] = useState(0);
  const [verified, setVerified] = useState(false);
  const [checking, setChecking] = useState(false);
  const [message, setMessage] = useState("正在加载验证");
  const track = useRef<HTMLDivElement>(null);
  const gesture = useRef<{
    start: number;
    initial: number;
    time: number;
  } | null>(null);
  const positionRef = useRef(0);
  const generation = useRef(0);
  const resetCallback = useRef(onReset);
  resetCallback.current = onReset;
  const load = useCallback(async () => {
    const current = ++generation.current;
    resetCallback.current?.();
    setChallenge(null);
    setVerified(false);
    setPosition(0);
    positionRef.current = 0;
    setMessage("正在加载验证");
    try {
      const next = await captchaApi.challenge(purpose);
      if (current !== generation.current) return;
      setChallenge(next);
      setMessage("拖动拼图到缺口");
    } catch (error) {
      if (current === generation.current)
        setMessage((error as Error).message || "验证加载失败，请刷新");
    }
  }, [purpose]);
  useEffect(() => {
    void load();
    return () => {
      generation.current++;
    };
  }, [load]);
  useEffect(() => {
    if (!challenge || verified) return;
    const timer = window.setTimeout(() => {
      gesture.current = null;
      void load();
    }, challenge.expiresIn * 1000);
    return () => window.clearTimeout(timer);
  }, [challenge, verified, load]);

  const begin = (event: React.PointerEvent<HTMLButtonElement>) => {
    if (!challenge || verified || checking) return;
    event.currentTarget.setPointerCapture(event.pointerId);
    gesture.current = {
      start: event.clientX,
      initial: positionRef.current,
      time: performance.now(),
    };
  };
  const move = (event: React.PointerEvent<HTMLButtonElement>) => {
    if (!gesture.current || !challenge || !track.current) return;
    const travel = track.current.clientWidth - 44;
    const next = Math.max(
      0,
      Math.min(
        gesture.current.initial +
          ((event.clientX - gesture.current.start) *
            (challenge.width - challenge.pieceSize)) /
            travel,
        challenge.width - challenge.pieceSize,
      ),
    );
    positionRef.current = next;
    setPosition(next);
  };
  const finish = async () => {
    if (!gesture.current || !challenge || checking) return;
    const current = generation.current;
    const duration = Math.round(performance.now() - gesture.current.time);
    gesture.current = null;
    setChecking(true);
    setMessage("正在验证");
    try {
      const result = await captchaApi.verify({
        purpose,
        challengeId: challenge.challengeId,
        offset: Math.round(positionRef.current),
        durationMillis: duration,
      });
      if (current !== generation.current) return;
      setVerified(true);
      setMessage("安全验证通过");
      onSuccess(result.proof);
    } catch (error) {
      setMessage((error as Error).message || "验证失败，请刷新重试");
      setChallenge(null);
    } finally {
      setChecking(false);
    }
  };

  return (
    <div className={cn("w-full select-none space-y-2", className)}>
      <div className="flex justify-between items-center text-[11px] font-sans text-ink-subtle dark:text-night-subtle">
        <span className="flex items-center gap-1">
          <ShieldCheck className="w-3.5 h-3.5" />
          {message}
        </span>
        <button
          type="button"
          onClick={() => void load()}
          disabled={checking}
          aria-label="刷新安全验证"
        >
          <RotateCw className="w-3.5 h-3.5" />
        </button>
      </div>
      {challenge && !verified && (
        <div
          className="relative w-full overflow-hidden rounded-md"
          style={{ aspectRatio: `${challenge.width}/${challenge.height}` }}
        >
          <img
            src={challenge.image}
            alt="拼图验证背景"
            className="w-full"
            draggable={false}
          />
          <img
            src={challenge.piece}
            alt="待对齐拼图"
            draggable={false}
            className="absolute shadow-md"
            style={{
              width: `${(challenge.pieceSize / challenge.width) * 100}%`,
              left: `${(position / challenge.width) * 100}%`,
              top: `${(challenge.pieceY / challenge.height) * 100}%`,
            }}
          />
        </div>
      )}
      <div
        ref={track}
        className="relative h-11 bg-paper-200/80 dark:bg-night-surface border border-paper-300 dark:border-night-border rounded-md overflow-hidden"
      >
        <div className="absolute inset-0 flex items-center justify-center text-xs text-ink-muted dark:text-night-muted">
          {verified
            ? "验证已完成"
            : checking
              ? "正在验证"
              : "按住滑块，对齐上方拼图"}
        </div>
        <button
          type="button"
          onPointerDown={begin}
          onPointerMove={move}
          onPointerUp={() => void finish()}
          onPointerCancel={() => {
            gesture.current = null;
          }}
          disabled={!challenge || checking || verified}
          aria-label="拖动安全验证滑块"
          className="absolute top-1 bottom-1 w-10 bg-paper-50 dark:bg-night-canvas border border-paper-300 dark:border-night-border rounded-md flex items-center justify-center shadow-tactile touch-none cursor-grab"
          style={{
            left: `calc(${challenge ? (position / (challenge.width - challenge.pieceSize)) * 100 : 0}% - ${challenge ? (position / (challenge.width - challenge.pieceSize)) * 44 : 0}px)`,
          }}
        >
          {verified ? (
            <Check className="w-4 h-4 text-forest" />
          ) : (
            <ArrowRight className="w-4 h-4" />
          )}
        </button>
      </div>
    </div>
  );
};
