import React, { useRef, useState } from "react";
import { Modal } from "../ui/Modal";
import { Input } from "../ui/Input";
import { Textarea } from "../ui/Textarea";
import { Button } from "../ui/Button";
import { ImagePicker } from "../ui/ImagePicker";
import { Community } from "../../types";
import { communityApi, uploadApi } from "../../api/client";
import { useToast } from "../../context/ToastContext";

interface EditSalonModalProps {
  isOpen: boolean;
  onClose: () => void;
  community: Community;
  onUpdated: (updated: Community) => void;
}

export const EditSalonModal: React.FC<EditSalonModalProps> = ({
  isOpen,
  onClose,
  community,
  onUpdated,
}) => {
  const { showToast } = useToast();
  const [name, setName] = useState(community.displayName || community.name);
  const [description, setDescription] = useState(community.description || "");
  const [iconUrl, setIconUrl] = useState(community.icon || "");
  const [bannerUrl, setBannerUrl] = useState(community.banner || "");
  const [iconFile, setIconFile] = useState<File | null>(null);
  const [bannerFile, setBannerFile] = useState<File | null>(null);
  const [rules, setRules] = useState<string>(
    (community.rules || []).join("\n"),
  );
  const [isLoading, setIsLoading] = useState(false);
  const pending = useRef(false);
  const handleClose = () => {
    if (!pending.current) onClose();
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (pending.current) return;
    pending.current = true;
    try {
      setIsLoading(true);
      let nextIcon = iconUrl;
      let nextBanner = bannerUrl;
      if (iconFile) {
        nextIcon = (await uploadApi.uploadImage(iconFile)).url;
        setIconUrl(nextIcon);
        setIconFile(null);
      }
      if (bannerFile) {
        nextBanner = (await uploadApi.uploadImage(bannerFile)).url;
        setBannerUrl(nextBanner);
        setBannerFile(null);
      }
      const ruleList = rules
        .split("\n")
        .map((r) => r.trim())
        .filter(Boolean);
      if (ruleList.length > 10 || ruleList.some(rule => rule.length > 200)) {
        throw new Error("社区约定最多10条，每条不超过200字符。");
      }

      const updated = await communityApi.update(community.name, {
        name: name.trim(),
        description: description.trim(),
        iconUrl: nextIcon || undefined,
        bannerUrl: nextBanner || undefined,
        rules: ruleList,
      });

      showToast("社区已更新", "success");
      onUpdated(updated);
      onClose();
    } catch (err) {
      showToast(err instanceof Error ? err.message : "更新社区失败", "error");
    } finally {
      pending.current = false;
      setIsLoading(false);
    }
  };

  return (
    <Modal
      isOpen={isOpen}
      onClose={handleClose}
      title="编辑社区"
      subtitle={`管理 c/${community.name} 的基本信息与讨论规约`}
      maxWidth="md"
      dismissible={!isLoading}
    >
      <form onSubmit={handleSubmit} className="space-y-4">
        <Input
          label="社区名称"
          maxLength={96}
          value={name}
          onChange={(e) => setName(e.target.value)}
          required
          disabled={isLoading}
        />

        <Textarea
          label="社区简介"
          maxLength={1024}
          rows={3}
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          disabled={isLoading}
        />

        <ImagePicker
          label="社区图标"
          value={iconUrl}
          file={iconFile}
          onChange={setIconFile}
          disabled={isLoading}
        />

        <ImagePicker
          label="社区封面"
          variant="banner"
          value={bannerUrl}
          file={bannerFile}
          onChange={setBannerFile}
          disabled={isLoading}
        />

        <Textarea
          label="社区规则（每行一条）"
          placeholder="1. 保持真诚与包容\n2. 拒绝无营养的灌水"
          rows={4}
          maxLength={2009}
          hint="最多10条，每条不超过200字符。"
          value={rules}
          onChange={(e) => setRules(e.target.value)}
          disabled={isLoading}
        />

        <div className="flex justify-end gap-3 pt-4 hairline-t">
          <Button type="button" variant="outline" size="sm" onClick={handleClose} disabled={isLoading}>
            取消
          </Button>
          <Button type="submit" size="sm" isLoading={isLoading}>
            保存配置
          </Button>
        </div>
      </form>
    </Modal>
  );
};
