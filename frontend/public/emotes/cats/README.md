# Luminous 内置猫猫表情

六款素材使用用户确认的第二版，来源：`assets/characters/emotes-preview-v2-20261013/`。

部署仅包含一套 192×192 透明 WebP，由 `python scripts/prepare-cat-emotes.py` 缩小和编码原图生成，不改变表情设计。六张合计 44266 字节。PC 与手机共用同一文件，通过 CSS 调整显示尺寸。

本地重新生成素材需要 Python 与 Pillow。

文件名与内置标记由 `frontend/src/emotes/catEmotes.ts` 管理，不接受用户提供的路径。原始 PNG 与生成提示词保留在设计稿目录。
