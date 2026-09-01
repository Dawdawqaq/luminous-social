import React, { useState, useRef, useEffect } from 'react';
import { ShieldCheck, ArrowRight, Check } from 'lucide-react';
import { cn } from '../../utils';

interface SliderCaptchaProps {
  onSuccess: () => void;
  className?: string;
}

export const SliderCaptcha: React.FC<SliderCaptchaProps> = ({ onSuccess, className }) => {
  const [sliderPosition, setSliderPosition] = useState(0);
  const [isDragging, setIsDragging] = useState(false);
  const [isVerified, setIsVerified] = useState(false);
  const trackRef = useRef<HTMLDivElement>(null);
  const startXRef = useRef(0);

  const handleMouseDown = (e: React.MouseEvent | React.TouchEvent) => {
    if (isVerified) return;
    setIsDragging(true);
    const clientX = 'touches' in e ? e.touches[0].clientX : e.clientX;
    startXRef.current = clientX - sliderPosition;
  };

  useEffect(() => {
    const handleMouseMove = (e: MouseEvent | TouchEvent) => {
      if (!isDragging || isVerified || !trackRef.current) return;
      const clientX = 'touches' in e ? e.touches[0].clientX : e.clientX;
      const trackWidth = trackRef.current.clientWidth - 44; // button width 44
      const newPos = Math.max(0, Math.min(clientX - startXRef.current, trackWidth));
      setSliderPosition(newPos);

      // Verify when dragged over 92%
      if (newPos >= trackWidth * 0.92) {
        setIsVerified(true);
        setIsDragging(false);
        setSliderPosition(trackWidth);
        onSuccess();
      }
    };

    const handleMouseUp = () => {
      if (!isDragging) return;
      setIsDragging(false);
      if (!isVerified) {
        setSliderPosition(0);
      }
    };

    if (isDragging) {
      window.addEventListener('mousemove', handleMouseMove);
      window.addEventListener('mouseup', handleMouseUp);
      window.addEventListener('touchmove', handleMouseMove);
      window.addEventListener('touchend', handleMouseUp);
    }

    return () => {
      window.removeEventListener('mousemove', handleMouseMove);
      window.removeEventListener('mouseup', handleMouseUp);
      window.removeEventListener('touchmove', handleMouseMove);
      window.removeEventListener('touchend', handleMouseUp);
    };
  }, [isDragging, isVerified, onSuccess, sliderPosition]);

  return (
    <div className={cn('w-full select-none space-y-1.5', className)}>
      <div className="flex justify-between items-center text-[11px] font-sans text-ink-subtle dark:text-night-subtle">
        <span className="uppercase tracking-wider">沙龙安全验证</span>
        {isVerified ? (
          <span className="text-[#5B8266] flex items-center gap-1 font-medium">
            <ShieldCheck className="w-3.5 h-3.5" /> 人机校验已通过
          </span>
        ) : (
          <span>向右滑动完成验证</span>
        )}
      </div>

      <div
        ref={trackRef}
        className={cn(
          'relative h-11 bg-paper-200/80 dark:bg-night-surface border border-paper-300 dark:border-night-border rounded-[2px] overflow-hidden flex items-center'
        )}
      >
        {/* Fill Highlight */}
        <div
          className={cn(
            'absolute left-0 top-0 bottom-0 transition-colors',
            isVerified ? 'bg-[#5B8266]/15' : 'bg-terracotta/10'
          )}
          style={{ width: `${sliderPosition + 44}px` }}
        />

        {/* Center Prompt Text */}
        <div className="absolute inset-0 flex items-center justify-center pointer-events-none text-xs font-sans text-ink-muted dark:text-night-muted tracking-wide">
          {isVerified ? '身份确认 · 欢迎进入沙龙' : '按住滑块，拖动至最右端'}
        </div>

        {/* Drag Handle */}
        <div
          onMouseDown={handleMouseDown}
          onTouchStart={handleMouseDown}
          className={cn(
            'absolute top-1 bottom-1 w-10 bg-paper-50 dark:bg-night-canvas border border-paper-300 dark:border-night-border rounded-[2px] flex items-center justify-center shadow-tactile cursor-grab active:cursor-grabbing transition-transform duration-75',
            isVerified && 'bg-[#5B8266] border-[#5B8266] text-white cursor-default'
          )}
          style={{ transform: `translateX(${sliderPosition}px)` }}
        >
          {isVerified ? (
            <Check className="w-4 h-4 text-white" />
          ) : (
            <ArrowRight className="w-4 h-4 text-ink-primary dark:text-night-ink" />
          )}
        </div>
      </div>
    </div>
  );
};
