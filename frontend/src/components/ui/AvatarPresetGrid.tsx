import React, { useId } from "react";
import { Check } from "lucide-react";
import type { ProfileImagePreset } from "../../profile/profilePresets";
import { cn } from "../../utils";

interface AvatarPresetGridProps {
  presets: readonly ProfileImagePreset[];
  value?: string;
  onSelect: (preset: ProfileImagePreset) => void;
  disabled?: boolean;
}

export const AvatarPresetGrid: React.FC<AvatarPresetGridProps> = ({ presets, value, onSelect, disabled }) => {
  const groupName = useId();
  return (
    <fieldset disabled={disabled} className="min-w-0">
      <legend className="sr-only">头像预设</legend>
      <div className="grid grid-cols-3 gap-2 sm:gap-3">
        {presets.map((preset) => {
          const selected = value === preset.url;
          return (
            <label key={preset.id} className={cn(
              "relative flex min-w-0 cursor-pointer items-center justify-center rounded-md border-2 px-2 py-2.5 has-[:focus-visible]:outline has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-offset-2 has-[:focus-visible]:outline-terracotta",
              selected ? "border-terracotta bg-blue-50 dark:border-terracotta-dark dark:bg-night-surfaceHover" : "border-transparent hover:bg-paper-100 dark:hover:bg-night-surfaceHover",
              disabled && "cursor-wait opacity-60",
            )}>
              <input className="sr-only" type="radio" name={groupName} value={preset.id}
                checked={selected} onChange={() => onSelect(preset)} aria-label={preset.name} />
              <img src={preset.url} alt="" width={80} height={80}
                className="aspect-square w-full max-w-20 rounded-full object-cover" />
              {selected && <span className="absolute right-1 top-1 rounded-full bg-terracotta p-0.5 text-white dark:bg-terracotta-dark dark:text-night-canvas">
                <Check size={13} strokeWidth={3} aria-hidden="true" />
              </span>}
            </label>
          );
        })}
      </div>
    </fieldset>
  );
};
