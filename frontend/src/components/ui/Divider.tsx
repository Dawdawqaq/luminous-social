import React from 'react';
import { cn } from '../../utils';

export interface DividerProps {
  className?: string;
  label?: string;
}

export const Divider: React.FC<DividerProps> = ({ className, label }) => {
  if (label) {
    return (
      <div className={cn('relative flex py-4 items-center', className)}>
        <div className="flex-grow hairline-t" />
        <span className="flex-shrink mx-3 text-[11px] font-sans uppercase tracking-widest text-ink-subtle dark:text-night-subtle">
          {label}
        </span>
        <div className="flex-grow hairline-t" />
      </div>
    );
  }
  return <div className={cn('w-full hairline-t my-4', className)} />;
};
