import React, { useRef, useState } from "react";
import { Modal } from "../ui/Modal";
import { Input } from "../ui/Input";
import { Textarea } from "../ui/Textarea";
import { Button } from "../ui/Button";
import { communityApi } from "../../api/client";
import { useToast } from "../../context/ToastContext";

interface CreateSalonModalProps {
  isOpen: boolean;
  onClose: () => void;
  onCreated: (slug: string) => void;
}

export const CreateSalonModal: React.FC<CreateSalonModalProps> = ({
  isOpen,
  onClose,
  onCreated,
}) => {
  const { showToast } = useToast();
  const [slug, setSlug] = useState("");
  const [name, setName] = useState("");
  const [category, setCategory] = useState("Living");
  const [description, setDescription] = useState("");
  const [isLoading, setIsLoading] = useState(false);
  const pending = useRef(false);

  const categories = [
    "Philosophy",
    "Craft",
    "Aesthetics",
    "Living",
    "Systems",
    "Literature",
  ];
  const categoryLabels: Record<string, string> = {
    Philosophy: "交流思考",
    Craft: "手作创作",
    Aesthetics: "艺术审美",
    Living: "生活兴趣",
    Systems: "科技数码",
    Literature: "读书写作",
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (pending.current) return;
    if (!slug.trim() || !name.trim()) {
      showToast("请填写社区短名称和显示名称", "error");
      return;
    }

    pending.current = true;
    try {
      setIsLoading(true);
      await communityApi.create({
        slug: slug.trim().toLowerCase(),
        name: name.trim(),
        category,
        description: description.trim(),
      });

      showToast("社区已创建", "success");
      onCreated(slug.trim().toLowerCase());
      setSlug(""); setName(""); setDescription("");
      onClose();
    } catch (err: any) {
      showToast(err.message || "创建社区失败", "error");
    } finally {
      pending.current = false;
      setIsLoading(false);
    }
  };

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      dismissible={!isLoading}
      title="创建社区"
      subtitle="为喜欢的主题，找一群聊得来的朋友。"
      maxWidth="md"
    >
      <form onSubmit={handleSubmit} className="space-y-4"><fieldset disabled={isLoading} className="space-y-4">
        <Input
          label="社区短名称"
          maxLength={64}
          placeholder="例如 photography、reading"
          value={slug}
          onChange={(e) =>
            setSlug(e.target.value.replace(/[^a-zA-Z0-9_-]/g, ""))
          }
          hint="用于社区链接，可使用字母、数字、下划线和短横线。"
          required
        />

        <Input
          label="社区名称"
          maxLength={96}
          placeholder="输入社区对外呈现的完整名称"
          value={name}
          onChange={(e) => setName(e.target.value)}
          required
        />

        <div>
          <label
            htmlFor="community-category"
            className="block text-sm font-sans font-semibold mb-2"
          >
            所属领域分类
          </label>
          <select
            id="community-category"
            value={category}
            onChange={(e) => setCategory(e.target.value)}
            className="w-full px-3 py-2 text-sm font-sans bg-paper-50 dark:bg-night-surface border border-paper-300 dark:border-night-border rounded-md text-ink-primary dark:text-night-ink focus:outline-none"
          >
            {categories.map((cat) => (
              <option key={cat} value={cat}>
                {categoryLabels[cat]}
              </option>
            ))}
          </select>
        </div>

        <Textarea
          label="社区简介"
          maxLength={1024}
          placeholder="这里聊什么？用几句话介绍给大家。"
          rows={3}
          value={description}
          onChange={(e) => setDescription(e.target.value)}
        />

        <div className="flex justify-end gap-3 pt-4 hairline-t">
          <Button type="button" variant="outline" size="sm" onClick={onClose}>
            取消
          </Button>
          <Button type="submit" size="sm" isLoading={isLoading}>
            创建社区
          </Button>
        </div>
      </fieldset></form>
    </Modal>
  );
};
