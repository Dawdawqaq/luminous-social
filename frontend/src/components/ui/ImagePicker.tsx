import React, { useEffect, useId, useRef, useState } from "react";
import { Camera, Check, ImagePlus, UserRound } from "lucide-react";
import { cn } from "../../utils";
import type { ProfileImagePreset } from "../../profile/profilePresets";
import { AvatarPresetGrid } from "./AvatarPresetGrid";

interface ImagePickerProps {
  label: string;
  value?: string;
  file: File | null;
  onChange: (file: File) => void;
  variant?: "avatar" | "banner";
  disabled?: boolean;
  presets?: readonly ProfileImagePreset[];
  onSelectPreset?: (preset: ProfileImagePreset) => void;
}

const IMAGE_TYPES = ["image/jpeg", "image/png", "image/gif", "image/webp"];
const MAX_IMAGE_SIZE = 10 * 1024 * 1024;

export const ImagePicker: React.FC<ImagePickerProps> = ({
  label,
  value,
  file,
  onChange,
  variant = "avatar",
  disabled,
  presets = [],
  onSelectPreset,
}) => {
  const inputRef = useRef<HTMLInputElement>(null);
  const hintId = useId();
  const [preview, setPreview] = useState("");
  const [error, setError] = useState("");
  const [imageFailed, setImageFailed] = useState(false);
  const source = preview || value;

  useEffect(() => {
    if (!file) {
      setPreview("");
      return;
    }
    const url = URL.createObjectURL(file);
    setPreview(url);
    return () => URL.revokeObjectURL(url);
  }, [file]);

  useEffect(() => setImageFailed(false), [source]);

  const selectFile = (event: React.ChangeEvent<HTMLInputElement>) => {
    const selected = event.target.files?.[0];
    event.target.value = "";
    if (!selected) return;
    if (!IMAGE_TYPES.includes(selected.type)) {
      setError("请选择 JPG、PNG、GIF 或 WebP 图片。");
      return;
    }
    if (selected.size > MAX_IMAGE_SIZE) {
      setError("图片不能超过 10 MB，请选择更小的图片。");
      return;
    }
    setError("");
    onChange(selected);
  };

  const isBanner = variant === "banner";
  const selectedPreset = !file ? presets.find(preset => preset.url === value) : undefined;
  return (
    <div className="space-y-2">
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <p className="font-bold">{label}</p>
        {presets.length > 0 && (isBanner || !selectedPreset) && <p className="text-xs text-ink-muted dark:text-night-muted">
          {selectedPreset ? selectedPreset.name : source ? "自定义图片" : "选择喜欢的预设"}
        </p>}
      </div>
      <button
        type="button"
        aria-label={`选择${label}`}
        aria-describedby={hintId}
        disabled={disabled}
        onClick={() => inputRef.current?.click()}
        className={cn(
          "group w-full overflow-hidden rounded-md border-[1.5px] border-ink-primary bg-paper-50 text-left transition-colors hover:bg-paper-100 disabled:cursor-wait disabled:opacity-60 dark:border-night-border dark:bg-night-surface dark:hover:bg-night-surfaceHover",
          !isBanner && "flex items-center gap-4 p-3",
        )}
      >
        <span
          className={cn(
            "flex shrink-0 items-center justify-center overflow-hidden bg-ochre-light text-ink-primary dark:bg-night-surfaceHover dark:text-night-ink",
            isBanner ? presets.length ? "h-20 w-full" : "h-28 w-full" : "h-16 w-16 rounded-full border border-ink-primary",
          )}
        >
          {source && !imageFailed ? (
            <img
              src={source}
              alt={`${label}预览`}
              className="h-full w-full object-cover"
              onError={() => setImageFailed(true)}
            />
          ) : isBanner ? (
            <ImagePlus size={30} aria-hidden="true" />
          ) : (
            <UserRound size={28} aria-hidden="true" />
          )}
        </span>
        <span className={cn("block min-w-0", isBanner && "px-3 py-2.5")}>
          <span className="flex items-center gap-2 font-semibold text-terracotta dark:text-terracotta-dark">
            <Camera size={17} aria-hidden="true" />
            {presets.length ? `上传${label}` : source ? `更换${label}` : `选择${label}`}
          </span>
          <span className="mt-1 block truncate text-xs text-ink-muted dark:text-night-muted">
            {file ? file.name : isBanner ? "横向图片展示效果更好" : "选择一张喜欢的图片"}
          </span>
        </span>
      </button>
      {!isBanner && presets.length > 0 && onSelectPreset && <AvatarPresetGrid
        presets={presets} value={!file ? value : undefined} disabled={disabled}
        onSelect={(preset) => { setError(""); onSelectPreset(preset); }} />}
      {isBanner && presets.length > 0 && onSelectPreset && <div role="group" aria-label={`${label}预设`}
        className={cn("grid gap-2", isBanner ? "grid-cols-2 sm:grid-cols-3" : "grid-cols-6")}>
        {presets.map(preset => {
          const selected = selectedPreset?.id === preset.id;
          return <button key={preset.id} type="button" disabled={disabled} aria-pressed={selected}
            aria-label={`使用${preset.name}${label}`} title={preset.name} onClick={() => {setError("");onSelectPreset(preset);}}
            className={cn("relative min-w-0 rounded-md border-2 p-1 text-center transition-colors disabled:opacity-60 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-terracotta focus-visible:ring-offset-2 dark:focus-visible:ring-offset-night-surface",
              selected ? "border-terracotta bg-blue-50 dark:border-night-blue dark:bg-night-surfaceHover" : "border-transparent hover:border-ink-faint dark:hover:border-night-border")}>
            <img src={preset.url} alt="" loading="lazy" className={cn("w-full", isBanner ? "aspect-[4/1] rounded-sm object-cover" : "aspect-square rounded-full bg-blue-50 object-cover")}/>
            {isBanner && <span className="mt-1 block truncate text-xs">{preset.name}</span>}
            {selected && <span className="absolute -right-0.5 -top-0.5 rounded-full bg-terracotta p-0.5 text-white">
              <Check size={12} strokeWidth={3} aria-hidden="true"/>
            </span>}
          </button>;
        })}
      </div>}
      <input
        ref={inputRef}
        type="file"
        accept={IMAGE_TYPES.join(",")}
        aria-label={`${label}图片文件`}
        className="hidden"
        disabled={disabled}
        onChange={selectFile}
      />
      <p id={hintId} className="text-xs text-ink-muted dark:text-night-muted">
        JPG、PNG、GIF、WebP，最大 10 MB
      </p>
      {error && <p role="alert" className="text-sm text-red-600 dark:text-red-400">{error}</p>}
    </div>
  );
};
