---
name: Luminous frontend
description: 白色画布、墨线分镜与蓝黄强调色的通用兴趣社区
colors:
  blue: "#2563EB"
  blue-hover: "#1D4ED8"
  blue-light: "#EFF6FF"
  yellow: "#FDE047"
  yellow-light: "#FEF9C3"
  canvas: "#F3F4F6"
  surface: "#FFFFFF"
  ink: "#1A1A1A"
  muted: "#526071"
  ink-subtle: "#667085"
  rule: "#D1D5DB"
  badge-surface: "#EEF2F7"
  error: "#DC2626"
  error-dark: "#F87171"
  night-canvas: "#141820"
  night-surface: "#1F2633"
  night-surface-hover: "#2B3545"
  night-ink: "#F8FAFC"
  night-muted: "#B9C3D2"
  night-rule: "#3D4657"
  night-outline: "#778296"
  night-blue: "#93B4FF"
typography:
  brand:
    fontFamily: '"Luminous Display", sans-serif'
    fontSize: "48px"
    fontWeight: 800
    lineHeight: 1
    letterSpacing: "-0.035em"
    fontVariation: '"wdth" 80'
  display:
    fontFamily: '"Luminous Chinese Display", "Microsoft YaHei", sans-serif'
    fontSize: "clamp(40px, 4.1vw, 64px)"
    fontWeight: 900
    lineHeight: 1.2
    letterSpacing: "-0.02em"
  headline:
    fontFamily: '"Noto Sans SC", "Microsoft YaHei", "PingFang SC", system-ui, sans-serif'
    fontSize: "28px"
    fontWeight: 900
  title:
    fontFamily: '"Noto Sans SC", "Microsoft YaHei", "PingFang SC", system-ui, sans-serif'
    fontSize: "18px"
    fontWeight: 800
    lineHeight: 1.5
  body:
    fontFamily: '"Noto Sans SC", "Microsoft YaHei", "PingFang SC", system-ui, sans-serif'
    fontSize: "15px"
    fontWeight: 400
    lineHeight: 1.6
  excerpt:
    fontFamily: '"Noto Sans SC", "Microsoft YaHei", "PingFang SC", system-ui, sans-serif'
    fontSize: "14px"
    fontWeight: 400
    lineHeight: 1.75
  label:
    fontFamily: '"Noto Sans SC", "Microsoft YaHei", "PingFang SC", system-ui, sans-serif'
    fontSize: "15px"
    fontWeight: 700
    lineHeight: 1.6
  meta:
    fontFamily: '"Noto Sans SC", "Microsoft YaHei", "PingFang SC", system-ui, sans-serif'
    fontSize: "12px"
    fontWeight: 400
  badge:
    fontFamily: '"Noto Sans SC", "Microsoft YaHei", "PingFang SC", system-ui, sans-serif'
    fontSize: "10px"
    fontWeight: 500
    letterSpacing: "0.05em"
rounded:
  sm: "4px"
  inset: "5px"
  md: "6px"
  speech: "13px"
spacing:
  sm: "8px"
  compact: "12px"
  md: "16px"
  inset: "18px"
  section: "20px"
  grid: "22px"
  lg: "24px"
  display: "28px"
components:
  button-primary:
    backgroundColor: "{colors.blue}"
    textColor: "{colors.surface}"
    typography: "{typography.label}"
    rounded: "{rounded.md}"
    padding: "9px 17px"
  button-secondary:
    backgroundColor: "{colors.yellow}"
    textColor: "{colors.ink}"
    typography: "{typography.label}"
    rounded: "{rounded.md}"
    padding: "9px 17px"
  button-outline:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.ink}"
    typography: "{typography.label}"
    rounded: "{rounded.md}"
    padding: "9px 17px"
  button-ghost:
    rounded: "{rounded.md}"
    padding: "8px 12px"
  panel:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.ink}"
    rounded: "{rounded.md}"
  input:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.ink}"
    rounded: "{rounded.md}"
    padding: "10px 14px"
  search-input:
    backgroundColor: "{colors.surface}"
    rounded: "{rounded.md}"
    padding: "9px 42px"
    height: "46px"
  nav-active:
    backgroundColor: "{colors.blue}"
    textColor: "{colors.surface}"
    rounded: "{rounded.md}"
    padding: "11px 13px"
  badge-default:
    backgroundColor: "{colors.badge-surface}"
    textColor: "{colors.muted}"
    typography: "{typography.badge}"
    rounded: "{rounded.md}"
    padding: "2px 8px"
  salon-card:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.ink}"
    rounded: "{rounded.md}"
    padding: "22px"
  post-row:
    padding: "12px 0"
  content-state:
    textColor: "{colors.muted}"
    padding: "40px 22px"
---

# Design System: Luminous frontend

## Overview

**Creative North Star: "日常社区的漫画分镜"**

白猫角色的正式造型基准为 [luminous-cat-master.png](../assets/characters/luminous-cat-master.png)，用户于 2026-10-11 明确确认。后续动作保持原型的头型、五官结构、蓝黄耳朵点缀、粉色脸颊与细手臂、小脚比例，具体规范见 [角色规范](../assets/characters/README.md)。新动作先供用户检查图片与位置，确认后再嵌入；此前脚部肥大的七张动作图仅作为历史草稿保留。

首页欢迎区与侧栏使用同一只白猫吉祥物：左耳小蓝色块、右耳小黄色块、爪心蓝黄双色星星。欢迎区按用户手绘草图表现框内探头：猫头位于框内下方，一只爪子搭住下边框，另一只带双色星星的爪子伸出右边框。猫身在框后、边线在中间、爪子在框前，通过同一透明图片的局部裁切保持连续轮廓；预留右侧及底部空间，保留标题与按钮的清晰阅读空间。猫咪主体以白色和黑色轮廓为主，后续等级标识围绕爪心星星变化。

用清爽的画布、明确的墨线与少量蓝黄强调色，把浏览、搜索、发布和讨论组织成容易理解的社区界面。粗体品牌与欢迎标题承担漫画的表达力，帖子标题、摘要和操作保持稳定、清楚的阅读节奏。

分镜主要由面板外框和内部细线建立。局部网点、黄色斜切色块和 Luminous 娘提供亲切感，硬阴影让主要操作呈现可按压的实体感。表达应服务于内容与参与入口，保持不同兴趣主题都能使用的通用性。

本规范只描述独立新版 `frontend`，不宣称原 `frontend` 已改变。规范来自当前实现；产品与首页方向分别见 [PRODUCT.md](../PRODUCT.md) 与 [home-direction.md](../.impeccable/home-direction.md)。运行与验收范围见 [README.md](README.md) 与 [验收说明](../doc/17_前端重制与验收说明.md)；视觉规范不构成真实后端联调或效果图精确复现通过的证据。

**Key Characteristics:**

- 浅色默认画布、墨线分镜和微圆角。
- 蓝色承担主要操作与选中态，黄色承担欢迎和参与提示。
- 自托管品牌字体与欢迎语专用中文字形，正文使用中文无衬线字体栈。
- 无模糊硬阴影、实体按压和可见键盘焦点。
- 清楚区分加载、请求失败与成功后的空内容。

## Colors

蓝黄双强调色建立漫画感，正文和容器由中性色承担；frontmatter 是颜色原值的规范来源。

### Primary

- **行动蓝**（`colors.blue`）：主要按钮、导航选中态、社区链接和局部网点。
- **深行动蓝**（`colors.blue-hover`）：Tailwind 已定义的兼容变体；基础实体按钮 hover 改变位移和阴影，不自动换成此色。
- **浅蓝底**（`colors.blue-light`）：蓝色标签的淡色背景，实际标签还叠加透明度。
- **夜间浅蓝**（`colors.night-blue`）：深色主题中的链接、焦点和部分文字强调。

### Secondary

- **参与黄**（`colors.yellow`）：次要参与按钮、品牌星形、欢迎标题下划线和斜切背景。
- **浅参与黄**（`colors.yellow-light`）：已定义的淡色辅助色，不替代主要黄色。
- **错误红 / 夜间错误红**（`colors.error` / `colors.error-dark`）：表单校验边框和提示，不能取代普通动作蓝。

### Neutral

- **浅灰画布、白色纸面**（`colors.canvas` / `colors.surface`）：页面背景与内容面板。
- **正文墨色、说明灰、占位灰**（`colors.ink` / `colors.muted` / `colors.ink-subtle`）：正文、摘要与次级提示。
- **分隔灰、标签浅灰**（`colors.rule` / `colors.badge-surface`）：内部细线与中性标签。
- **夜间画布、夜间纸面、夜间悬停面**：对应 `night-canvas`、`night-surface`、`night-surface-hover`。
- **夜间文字、说明、细线、外框**：对应 `night-ink`、`night-muted`、`night-rule`、`night-outline`。

**The Action Color Rule.** 蓝色用于可以操作或已选中的对象，黄色用于欢迎和参与提示；正文依靠墨色和层级保持可读。

实际兼容命名：Tailwind `terracotta` 已映射为行动蓝，`ochre` 已映射为参与黄。这些名称不表示新版仍使用陶土配色。默认主题为浅色；深色通过 `.dark` 切换变量。黄色、主要按钮的蓝色及其黑色边框在当前实现中仍使用固定色值，不能把全部组件描述为自动采用夜间浅蓝。

## Typography

**Display Font:** 品牌使用自托管 Bricolage Grotesque 800（CSS 别名 `Luminous Display`）；欢迎语使用自托管 Noto Sans SC 900（别名 `Luminous Chinese Display`）。

**Body Font:** `Noto Sans SC, Microsoft YaHei, PingFang SC, system-ui, sans-serif`。正文的 Noto Sans SC 依赖系统已有字体，没有加载完整自托管中文字体。

**Label/Mono Font:** 标签延用正文无衬线；已有代码内容的等宽栈为 `Consolas, monospace`。

**Character:** 品牌通过斜体、收紧字距与字宽变化形成有力度的识别；欢迎语采用重字、轻微旋转与倾斜。正文和帖子标题保持水平排版，清楚阅读优先。

### Hierarchy

- **Brand**：使用 `typography.brand`；样式含 italic 与 `wdth 80`，在较窄桌面、平板和手机分别收至 42px、29px、26px。
- **Display**：使用 `typography.display`；仅欢迎标题采用旋转 -4deg、skewX -5deg 与黄色下划线。1280px 及以下改为 46px，手机改为 29px、行高 1.25。
- **Headline**：使用 `typography.headline`；普通页面标题，手机为 24px。
- **Title**：使用 `typography.title`；帖子标题，手机为 16px。侧栏标题为 17px、800。
- **Body**：使用 `typography.body`；常规内容。Markdown 正文行高为 1.85，不能强制压成首页摘要行高。
- **Excerpt**：使用 `typography.excerpt`；帖子摘要限制两行，手机字号为 13px。
- **Label / Meta / Badge**：使用相应角色；按钮强调操作，元信息支持快速扫描，标签为紧凑的 10px。Badge 的 uppercase 只作用于拉丁文字，不要求中文大写。

**The Display Scope Rule.** 中文展示字体只包含“找到同好，聊点喜欢的。”字形；新增欢迎文案前应更新子集，不能假定它覆盖任意中文正文。Tailwind 的 `serif` 别名实际也采用正文无衬线栈。

## Layout

内容在固定最大宽度（1600px）内居中。桌面顶栏为 sticky，内区最小高度（68px），横向内边距（24px）。左侧栏宽（216px），中央区域自适应，首页右栏宽（302px）；两处主要列间距均为（22px）。左侧栏粘性定位在距视口顶部（82px）处。主页中间面板纵向间距（12px）。

首页 A 表达为左侧导航与已加入社区、中间欢迎区与发布栏及帖子流、右侧参与统计与社区发现。此组合是当前首页的布局，不要求所有页面复制三栏。欢迎面板桌面高度（310px），内边距（27px 28px）；人物位于右侧，网点只放在面板边缘及侧栏猫咪附近。

响应式采用实际 CSS 边界：

| 条件 | 实际变化 |
| --- | --- |
| 宽度 > 1280px | 顶栏品牌与标语并列；桌面主列采用上述宽度。 |
| 宽度 ≤ 1280px | 左栏（190px）、右栏（260px）、列间距（18px）；页面横向内边距（20px）；社区卡片变两列。 |
| 宽度 ≤ 1100px | 首页右栏移到帖子流下方形成两列，隐藏最后的社区公约面板；左栏继续保留。 |
| 宽度 ≤ 767px | 左栏隐藏、主内容单列；顶栏搜索另起一行、顶部发帖按钮隐藏；五项固定底部导航；社区卡片单列。 |

手机画布左右留白（12px），底部内容预留（90px）；底栏考虑 `env(safe-area-inset-bottom)`。欢迎区高度（216px），内边距（22px 18px），人物缩为（190px）高。手机首页右栏隐藏个人统计、保留社区发现并恢复公约。768px 仍处在保留左导航的平板布局，不能把它标记为手机版。

内部留白采用 frontmatter 的实际间距步骤。长标题使用 `overflow-wrap: anywhere`；宽度变化时先让文字与列宽适配。帖子缩略图默认（166px × 85px），较窄桌面为（130px × 90px），手机为（88px × 82px）。

## Elevation & Depth

层次由清楚边框、纸面与浅灰画布、无模糊硬阴影共同建立。阴影表示组件的实体边缘；内部帖子行使用细线，避免每条内容都变成悬浮卡片。

### Shadow Vocabulary

- **纸面实体**（`4px 4px 0 #1a1a1a`）：主要面板与实体按钮默认态。
- **按压一半**（`2px 2px 0 #1a1a1a`）：实体按钮 hover，伴随 translate（2px, 2px）；手机发布按钮也采用此阴影。
- **完全按下**（`none`）：实体按钮 active，伴随 translate（4px, 4px）。
- **社区卡片提起**（`6px 6px 0 #1a1a1a`）：社区卡片 hover，伴随 translate（-2px, -2px）。
- **夜间纸面**（`4px 4px 0 #000`）：深色主题面板变量；固定色按钮和社区卡片 hover 仍沿用墨色硬阴影。
- **对话气泡**（`3px 3px 0 #1a1a1a`）：侧栏猫咪文案的局部表现。

**The Solid Edge Rule.** 漫画实体层次使用零模糊阴影；交互通过位移与阴影距离变化表达，不为正文容器添加漫射光晕。

按钮过渡为（120ms），transform 与 shadow 使用 ease-out；社区卡片过渡为（120ms）。入场为（160ms ease-out），骨架脉冲为（1.2s ease-in-out）。`prefers-reduced-motion: reduce` 关闭动画、过渡和滚动动态；当前规则仍保留 hover/active 的即时位移，不应写成完全取消位移。

## Shapes

主要面板、按钮、输入和选中导航使用微圆角（6px）；缩略图、发布提示框及代码块采用内嵌圆角（5px）；骨架与搜索结果采用小圆角（4px）。大面积纸面外框为（2px）墨线，容器内部行分隔为（1px）灰线。普通表单 Input 边框为（1.5px），搜索输入为（2px），这是实现中的明确差异。

头像与通知计数采用圆形或胶囊形，保留身份与状态识别。猫咪对话气泡使用较大局部圆角（13px）、斜置与尖角，不能把这一装饰形状推广到所有容器。黄色斜切背景与少量网点构成漫画局部几何，正文阅读区保持平直。

## Components

### Buttons

直接、可按压的实体操作。

- **Shape:** 微圆角、墨色（2px）边框，常规最小高度（42px）；普通内边距由 `button-primary` 等配方定义。
- **Primary:** 蓝底白字；`primary` 与兼容变体 `terracotta` 等价。
- **Secondary / Outline:** `secondary` 为黄底墨字；`outline` 为纸面底墨字。
- **Ghost / Subtle:** 两者为无实体边框与硬阴影的紧凑操作，font-weight（600），hover 使用浅灰纸面或夜间悬停面；不继承实体按钮的按压。
- **Hover / Focus / Active:** 遵循硬阴影状态；通用键盘焦点为（3px）蓝色 outline、offset（3px）。禁用实体按钮 opacity（0.55）且无阴影；加载使用 `aria-busy` 并禁用提交。
- **Sizing:** small 最小高度（36px）、padding（6px 12px）、字号（13px）；large 采用（12px 24px）与（16px）字号。移动欢迎区还使用自身的紧凑尺寸覆盖。

### Chips

紧凑信息标签；这里的 Badge 是通用状态组件，不表示已开发成长徽章。

- **Style:** 默认浅灰底、灰线、说明色文字；（10px）、字距（0.05em）、padding（2px 8px）、圆角（6px）。
- **Variants:** 蓝色强调、仅文字 subtle、细线 outline；蓝色变体使用淡蓝背景与透明蓝边框。
- **State:** 当前 Badge 为非交互 span，不虚构点击、hover 选择或筛选能力。

等级标识另用 `LevelBadge`，保留经用户确认的黄蓝四角星与墨色外边，六款造型分别用于 1—4、5—9、10—12、13—15、16—17、18 级；双星款仅用于 18 级满级。
普通标识高 28px，图片 24px；较大标识高 36px，图片 32px。等级标识仅由星星与等级文字组成，透明背景、无矩形外框、无额外左右内边距；文字采用现有主题变量，不增加阴影或交互。
`LV` 与等级数字组成同一个文字组，不添加组内间隔；较小的 `LV` 按文字基线与数字对齐，视觉位置略低。星星与文字组保持 4px 间隔，全部等级展示位置复用该规则。
个人与各社区内等级分别传入；缺失或非法等级不显示。当前真实接口尚无等级数据，组件已准备，业务页面等待成长接口接入。

### Cards / Containers

分镜外框组织内容，内部细线组织列表。

- **Panel:** 纸面底、（2px）外框、微圆角与纸面实体阴影。
- **Post feed:** 多条内容共用一个面板，默认行 padding（12px 0），手机为（18px 0）；两行摘要与紧凑操作栏。标题、链接、投票与收藏选中态使用行动蓝；实际内容及统计由 API 数据提供。
- **Salon card:** padding（22px）、最小高度（218px）；标题（18px / 800），简介两行；hover 使用提起阴影，已加入状态为蓝色说明。
- **Aside panel:** padding（18px）；个人参与统计只展示帖子数、关注者和已加入社区数，不展示等级、经验或成长任务。

### Inputs / Fields

删除帖子与评论使用 `DeleteConfirmModal`，不调用浏览器原生确认弹窗。复用现有墨线面板、焦点管理和硬阴影按钮；小尺寸含泪猫猫以正式原型为参考，素材来源见 `assets/characters/delete-confirm/README.md`。
初始焦点放在取消按钮；删除请求期间禁用关闭、取消和重复提交，失败在弹窗内显示接口提示并支持重试，成功后才关闭。危险操作使用红色确认按钮，正文明确说明删除后无法恢复，不以角色表情替代操作后果。

用清楚边框和明确提示建立可编辑性。

- **Style:** 普通 Input 用（1.5px）墨线、纸面底、（14px）字体、padding（10px 14px）；搜索用（46px）固定高度和左右图标空间。
- **Focus:** 保留通用蓝色外轮廓；光标使用主题行动蓝。
- **Error:** 普通 Input 边框与提示使用错误红，设置 `aria-invalid` 并连接帮助文本。请求失败提示不能冒充“暂无内容”。
- **Disabled:** 只记录组件实际传入的 disabled 语义，不虚构未实现的专用禁用视觉。

### Navigation

桌面导航以图标、清楚中文标签和整行选中背景共同定位。

- 普通导航文字（16px / 600）、行最小高度（50px），padding（11px 13px），选中态为蓝底白字，未选中 hover 为画布底。
- 顶栏 sticky，包含品牌、搜索与账户操作；搜索结果和用户菜单使用面板层次，结果支持 hover 与选中背景。
- Feed tab 用行动蓝文字及（3px）底线标记选中；保证 `aria-selected` 语义，不只依赖颜色。
- 手机底栏为五列、图标与（10px）标签，中央发布入口为实体蓝色按钮；底部安全区保持可点击内容完整。
- 保留“跳转到主要内容”键盘入口、可访问名称和焦点反馈。

### Avatar Preset Selection

头像预设沿用既有 Modal、Button、Avatar 与 ImagePicker，注册、本人主页更换弹窗及编辑资料共用 `AvatarPresetGrid`。九款定稿以三列纯图片网格排列，圆形图片不显示名称；选中项使用既有行动蓝边框、浅色底和对号，深色主题采用现有对应颜色。原生 radio 通过 `aria-label` 保留读屏名称与方向键选择，焦点轮廓独立于选中状态。

本人主页相机提示贴近头像右下角，点击头像进入预设或上传，再保存确认。手机保持三列，模态框可纵向滚动。本轮没有增加全局 token；具体流程、素材版本和验收证据见 `frontend/.impeccable/surfaces/avatar-presets.md`。

### Welcome Panel and Content States

欢迎区以专用重字、黄色斜切色块和 Luminous 猫猫作为签名组合；图像是品牌插画，不伪装成用户内容。猫猫统一以 `assets/characters/luminous-cat-master.png` 为造型基准，保留白色大头、笑眼、左蓝右黄耳内小块、细手臂和小爪子。九张正式动作的来源和页面位置见 `public/illustrations/cats/README.md`，完整提示词保存在 `assets/characters/page-cats-v2` 和 `assets/characters/home-sidebar-v2`。首页保留探头出框效果，其余页面用小尺寸装饰；不盖住内容和按钮，手机端相应收小。字体来源及许可在 `public/fonts`。

ContentState 共用图标、标题、说明和操作布局，padding（40px 22px）：加载为 status，失败为 alert 并提供“重新加载”，成功空内容用讨论图标与具体参与入口。不要在网络失败时用空态鼓励发帖，也不要把模拟接口截屏当作真实社区活跃度。

## Do's and Don'ts

### Do:

- **Do** 用墨线外框、内部细线与零模糊硬阴影保持漫画分镜的层次。
- **Do** 保持正文水平清晰，把重字体与斜置限制在品牌和欢迎表达。
- **Do** 区分加载、失败和成功空态，并让失败操作可以重试。
- **Do** 新增界面时使用新版 token 和已实现组件，核对深色主题及手机布局。
- **Do** 维护插画、字体的来源与许可，并在更换欢迎语时同步更新中文展示字体子集。

### Don't:

- **Don't** 因兼容名称 terracotta、ochre 或 serif，重新引入陶土色、赭石色或正文衬线字体。
- **Don't** 在正文阅读区铺满网点、旋转内容或添加模糊阴影。
- **Don't** 展示尚未开发的等级、经验、成长任务或伪造参与统计。
- **Don't** 把本规范应用结果宣称为原 frontend 已重制，或宣称真实后端联调和效果图精确复现已通过。
