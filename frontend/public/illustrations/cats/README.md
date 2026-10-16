# Luminous 猫猫正式页面素材

用户于 2026-10-11 确认正式原型并授权嵌入本组九张透明 PNG。

唯一角色基准：仓库中的 `assets/characters/luminous-cat-master.png`。素材由内置 image_gen 工具生成；此目录保存原文件副本，未改动原型。

| 文件 | 位置 |
| --- | --- |
| luminous-cat-sidebar-v2.png | 左边栏欢迎卡片，举星星打招呼 |
| luminous-cat-welcome-v2.png | 首页欢迎区右下角，探头、搭住下边框、举星星伸出右侧 |
| luminous-cat-explore-v2.png | 发现社区页头右侧，与创建按钮留开距离 |
| luminous-cat-community-v2.png | 社区详情的社区约定卡片内，托腮 |
| luminous-cat-reading-v2.png | 帖子正文末尾右侧、互动按钮之前，看书 |
| luminous-cat-collection-v2.png | 我的收藏页头右侧，抱星星 |
| luminous-cat-profile-v2.png | 个人资料卡底部右侧、统计旁边，整理耳朵 |
| luminous-cat-admin-v2.png | 管理后台页头右侧，小尺寸拿记录板 |
| luminous-cat-lost-v2.png | 404 错误提示上方居中，歪头 |
| luminous-cat-tearful.png | 删除帖子和评论的确认弹窗，委屈含泪 |

七个页面动作的完整提示词在 `assets/characters/page-cats-v2/` 的同名 `.prompt.txt`；首页和侧栏的提示词在 `assets/characters/home-sidebar-v2/`。
删除确认弹窗插画使用内置 `image_gen` 编辑正式原型，原图与提示词保存在 `assets/characters/delete-confirm/`，页面文件直接复制，透明通道保留。

页面动作由 `PageMascot.tsx` 集中引用，装饰不拦截点击、不重复播报，预留图片比例并按需加载。首页沿用三个图层与页面边框叠放，图片内没有画框。

手机端收小装饰；社区约定卡片随正文排到下方。星星只作为插画道具，本次未开发等级或经验功能。
