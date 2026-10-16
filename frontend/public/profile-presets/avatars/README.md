# 第一版头像发布资源

九款定稿：珍珠耳环、大卫雕塑、怪盗基德、呐喊与星月夜、教父、法老、龙猫、小王子、自由女神。

设计原稿与完整生成提示词见 [定稿清单](../../../../../assets/characters/avatar-cos-v1/approved-v1.md)，注册及编辑目录以 `ProfilePresetCatalog` 返回为准。头像不使用评论表情包。

使用 `E:/python/python.exe scripts/prepare-profile-avatars.py` 从定稿 PNG 导出 320 × 320 WebP，只缩放与编码；界面负责圆形裁切。每张 WebP 旁的 JSON 记录原稿及提示词来源。`astronaut.webp` 是此前保留的参考资源，不属于当前九款预设，不在选择器展示。

用户于 2026-10-15 授权接入，注册可选择预设，个人主页点击本人的头像可更换或上传；编辑资料中的头像入口仍可用。
