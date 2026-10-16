# 主页背景预设

六款背景为蓝色时刻、柠檬画纸、星星轨道、日常分镜、蓝色浪潮、一点星光，保持原有蓝黄配色、色块与网点构图。

2026-10-15 按用户要求，移除蓝色时刻中间的三条蓝短线；六张背景共十颗星星统一使用 `frontend/public/brand/luminous-star-soft.png`，保留正式图标的黑边、黄色主体、右角蓝色与柔和渐变。

品牌图片仅缩小为 128 × 128 PNG 并内嵌到 SVG，不重绘图标。内嵌使 SVG 作为图片显示时也能独立加载品牌星星，避免依赖外部图片被浏览器限制。其余短横线装饰、色块、轨道及图形位置保持原样。

重做内嵌资源可在仓库根目录执行 `E:/python/python.exe scripts/prepare-profile-banners.py`。脚本可重复执行，只更新已标记的品牌图像。

本轮前端构建通过，已更新 `luminous-dev`。六个开发服务 SVG 已逐一与本地文件核对一致，桌面及手机素材对照图见 `output/playwright/profile-banners/overview.png`、`mobile.png`。这是素材显示核验，没有重复执行注册或写入用户数据。

2026-10-15，独立 finish reviewer 查看全部六张背景及桌面、手机对照图，并解码比较内嵌品牌星星像素后，给出 `disposition: ship`、`material_fixes: 无`；结论仅覆盖本轮六款背景素材修正，未新增设计 token，无需修改 `DESIGN.md` 或 `.impeccable/design.json`。
