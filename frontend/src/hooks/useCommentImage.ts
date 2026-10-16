import { useEffect, useRef, useState } from "react";
import type { ClipboardEvent } from "react";
import { uploadApi } from "../api/client";

export const COMMENT_IMAGE_TYPES = "image/jpeg,image/png,image/gif,image/webp";
const MAX_SIZE = 10 * 1024 * 1024;

export function useCommentImage(disabled: boolean) {
  const [file, setFile] = useState<File | null>(null);
  const [preview, setPreview] = useState("");
  const [error, setError] = useState("");
  const fileRef = useRef<File | null>(null);
  const uploadedUrl = useRef<string>();

  useEffect(() => {
    if (!file) { setPreview(""); return; }
    const url = URL.createObjectURL(file);
    setPreview(url);
    return () => URL.revokeObjectURL(url);
  }, [file]);

  const select = (files: File[]) => {
    if (disabled || !files.length) return;
    if (fileRef.current || files.length > 1) {
      setError("每条评论最多一张图片，请先移除已有图片再添加。");
      return;
    }
    const selected = files[0];
    if (!COMMENT_IMAGE_TYPES.split(",").includes(selected.type)) {
      setError("请选择 JPG、PNG、GIF 或 WebP 图片。");
      return;
    }
    if (!selected.size || selected.size > MAX_SIZE) {
      setError("图片不能为空或超过 10 MB，请选择更小的图片。");
      return;
    }
    fileRef.current = selected;
    uploadedUrl.current = undefined;
    setFile(selected);
    setError("");
  };

  const onPaste = (event: ClipboardEvent<HTMLElement>) => {
    const images = Array.from(event.clipboardData.items)
      .filter(item => item.kind === "file" && item.type.startsWith("image/"))
      .map(item => item.getAsFile()).filter((item): item is File => !!item);
    // 普通文字沿用浏览器粘贴行为；图片不会作为文件名插入正文。
    if (!images.length) return;
    event.preventDefault();
    select(images);
  };

  const clear = () => {
    fileRef.current = null;
    uploadedUrl.current = undefined;
    setFile(null);
    setError("");
  };

  const upload = async () => {
    if (!fileRef.current) return undefined;
    // 发布评论失败后复用已上传的图片，避免重试产生重复文件。
    if (!uploadedUrl.current) uploadedUrl.current = (await uploadApi.uploadImage(fileRef.current)).url;
    return uploadedUrl.current;
  };

  return { file, preview, error, select, onPaste, clear, upload };
}
