# Luminous 猫猫表情第二版

本目录保留用户确认的第二版六张设计原图和提示词。2026-10-13 用户确认后，六款素材已转换为一套轻量 WebP 并接入网站，原始 PNG 保持不变；实现与验证见 `doc/12_猫猫表情接入与验收.md`。评论模块完成后，未采用的第一版设计目录与可再生成的预览截图已按用户要求清理。

使用内置 `image_gen` 编辑第一版。猫猫以正式原型和第一版「开心」的头部比例为基准，四角星参考 `frontend/public/brand/luminous-star-soft.png`。

本轮调整：

- 大笑：去掉两只手，恢复完整头部下轮廓，保留笑眼、张嘴与粉色舌头。
- 喜欢：原来的爱心道具替换为品牌四角星，黄色为主、右角蓝色并柔和过渡，保留两只小爪子。
- 委屈：以开心猫猫为编辑底图，保留脸型、耳朵、胡须和腮红；使用下垂线条眼、猫猫嘴和一滴小蓝泪珠。
- 疑惑：保留歪头和托腮，调整头部构图比例，使视觉大小贴近开心。
- 开心、点赞：保留第一版素材，作为比例与角色连续性的参照。

## 素材与提示词

| 表情 | 图片 | 生成或编辑提示词 |
| --- | --- | --- |
| 开心 | [happy.png](happy.png) | [happy.prompt.txt](happy.prompt.txt) |
| 点赞 | [like.png](like.png) | [like.prompt.txt](like.prompt.txt) |
| 大笑 | [laugh.png](laugh.png) | [laugh.prompt.txt](laugh.prompt.txt) |
| 疑惑 | [confused.png](confused.png) | [confused.prompt.txt](confused.prompt.txt) |
| 委屈 | [sad.png](sad.png) | [sad.prompt.txt](sad.prompt.txt) |
| 喜欢 | [love.png](love.png) | [love.prompt.txt](love.prompt.txt) |

## 预览

[可切换深浅背景的独立页面](preview.html)。浅色、深色总览与手机截图可运行 `node assets/characters/emotes-preview-v2-20261013/capture.mjs` 重新生成。

保留 32、40、48px 对比与正文、评论、楼中楼排版示意。最终尺寸仍可在用户确认设计后调整。

`capture.mjs` 仅从本目录读取图片、检查透明通道并生成预览，图片不经过脚本裁剪或重绘。`validation.json` 中记录脸部中段白色毛面跨度，辅助比较视觉大小；该测量不是整个角色的外框尺寸。
