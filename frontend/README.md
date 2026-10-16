# Luminous 前端

漫画波普风新版已接管 `frontend`。本目录是唯一前端源码和 Docker 构建入口，原版已按用户授权移除。个人主页、帖子与评论已接入后端真实成长等级，接口没有成长数据时隐藏徽章。开发使用参考规则，生产奖励默认关闭；见 [成长实现与验收](../doc/20_用户成长等级系统实现与验收.md)。

## Docker 开发环境

在仓库根目录运行。使用 `.env.dev`，只启动 `luminous-dev`，前端端口 3000，后端端口 8080。

1. 定位 Docker CLI。兼容 Docker Desktop 用户目录安装，不修改 PATH。

```powershell
. ./scripts/dev-docker.ps1
$devDockerCli = Resolve-LuminousDocker
```

2. 构建后端 JAR，然后构建镜像并启动独立开发环境。后端 Dockerfile 使用构建好的 JAR；必须先执行 Maven。

```powershell
mvn -f backend/pom.xml package -DskipTests
& $devDockerCli compose --env-file .env.dev -p luminous-dev up -d mysql redis minio namesrv broker
& ./scripts/init-dev-mq.ps1
& $devDockerCli compose --env-file .env.dev -p luminous-dev up -d --build backend frontend
```

打开 `http://localhost:3000`。Nginx 将 `/api/` 和 `/uploads/` 转发到开发后端；真实上传图片通过 `/api/files/{key}?token=...` 读取，MinIO 桶保持私有。Nginx 请求体上限为 12 MB，后端图片上限为 10 MB。

首次启动需要准备 `.env.dev`；无此文件时可以复制 `.env.dev.example`，不要覆盖已存在的配置。请保留模板中的开发容器项目名与隔离端口。

若拉取基础镜像出现 Docker Hub `EOF`，先重试并检查 Docker Desktop 的网络设置。本次联调在已下载基础镜像后使用本地构建完成；没有重置或删除数据库卷。

## 本地热更新与静态构建

开发后端保持运行。若 Docker 前端占用 3000，先停该开发前端容器，再启动 Vite：

```powershell
& $devDockerCli compose --env-file .env.dev -p luminous-dev stop frontend
npm --prefix frontend ci --cache .local/npm-cache-v2
npm --prefix frontend run dev
```

打开 `http://127.0.0.1:3000`。Vite 将 `/api` 和 `/uploads` 转发到本机 `http://localhost:8080`。本地 API 未启动时页面显示加载失败和重试入口。

```powershell
npm --prefix frontend run build
npm --prefix frontend run preview
```

`preview` 用于检查静态构建，真实接口联调请使用 Vite 开发代理或 Docker Nginx。切回 Docker 时先结束 Vite，再执行：

```powershell
& $devDockerCli compose --env-file .env.dev -p luminous-dev up -d frontend
```

## 功能与设计

注册分为填写账号资料、选择猫猫头像两个步骤；九款第一版头像可在本人主页点击头像后切换，支持键盘选择，也保留上传入口。目录来自 `/api/users/profile-presets`，所选头像随账号保存。完整设计原稿及提示词见 `assets/characters/avatar-cos-v1/approved-v1.md`，发布资源与来源见 `public/profile-presets/avatars/README.md`。

内容优先首页、社区发现与管理、加入和签到、发帖与图片上传、评论与点赞、收藏、个人资料与关注、全文搜索、登录注册和正式验证码、通知已读及管理入口均接现有接口。支持手机布局、键盘操作、弹窗焦点管理、浅深色主题和减少动态效果。

首页“推荐”与“热榜”使用独立排序：推荐考虑近七天内容的新鲜度、有效互动和已加入社区，并混入低互动新帖；热榜可切换“日榜 / 周榜”，分别展示近 24 小时 / 7 天发布且有有效外部互动的帖子。日周窗口为滚动时间，不按自然日或自然周划分；数量不足不补入窗口外内容。“最新”和“关注”继续沿用原有接口。

首页每次加载十条。推荐与热榜使用服务端稳定快照和游标，“加载更多”追加当前阅读顺序；候选分数变化和新帖不会重排已打开的快照，每页仍过滤已失效内容。快照十分钟后过期，页面保留已显示内容并提供“刷新列表”，不自动拼接新顺序。加载、请求失败、追加失败和空榜分别提示；空榜可以进入“看看最新”。规则、接口及验证边界见 [推荐与日周热榜](../doc/24_推荐与日周热榜.md)，局部方向记录见 [.impeccable/surfaces/home-rankings.md](.impeccable/surfaces/home-rankings.md)。

发帖先选择社区，未加入时可在编辑框中加入并保留内容。选择器支持搜索、“已加入 / 全部社区”和继续加载，搜索回车不会提交外层发帖表单，中文组合输入保留。发帖不再选择内容类型；标题必填且最多15字符，正文最多1000字符，正文和图片至少一项，长度使用前后端一致的 UTF-16 口径。图片最多一张，可选择、粘贴、替换或移除，支持 JPG、PNG、GIF、WebP，最大10 MB；正文沿用猫猫表情与预览。

草稿通过 IndexedDB 按账号和发帖入口社区隔离，保存文字、未上传文件与已上传地址，只在当前浏览器设备恢复。关闭提供保留、继续编辑或放弃；保存失败会明确提示。上传成功但发布失败时重试复用地址，成功后清除当前草稿并进入帖子，可以返回来源社区。成功发布最小间隔30秒、滚动10分钟最多10条；按钮显示冷却倒计时，最终准入由后端校验，失败和未加入不会消耗成功额度，删除帖子不返还额度。

发现社区由服务端搜索关键词、分类及已加入范围，每次12条继续加载；详情展示已有社区封面，最新帖子按编号每次10条续页，近七天热门复用榜单快照，过期提供刷新。创建保存分类，管理继续提供图标、封面及社区约定编辑；名称、简介、图片地址和约定条数由后端校验。完整行为及接口边界见 [发帖与社区流程优化](../doc/25_发帖与社区流程优化.md)，局部方向与复审范围见 [.impeccable/surfaces/posting-community.md](.impeccable/surfaces/posting-community.md)。

顶栏搜索使用一次 `/api/search/suggest` 请求，按帖子、社区、用户分组，每类最多四条；提示内部滚动，底部提供更多分类与全部结果入口。输入暂停240毫秒后请求，改词、关闭提示或开始中文组合输入时取消旧请求；方向键选择结果，普通回车打开选中结果或完整结果页，输入法确认回车不触发导航。完整结果页使用 `/api/search`，按分类每次20条继续加载，失败可重试，五分钟快照过期后保留已显示内容并提供刷新。命中词通过文本节点黄色高亮，不插入查询生成的 HTML。

输入 `@用户名` 仅查用户，`c/社区短名` 仅查社区，`id:正整数` 明确查用户编号，普通数字仍可作为用户名查询。名称或标题精确匹配优先于开头匹配、标题全文及正文命中；单字、`C++` 等短词仅做名称或标题精确、开头匹配，可补充关键词缩小范围。当前采用有界词法候选、20秒实例本地缓存和最多200条的 Redis 快照，每次回表重新检查可见性，不承诺语义搜索或全库无界排序。原三个业务搜索接口及社区发现筛选保持兼容入口，没有整体改用本轮实现。具体查询边界、目标验证与最终性能数值见 [搜索准确度与性能优化](../doc/26_搜索准确度与性能优化.md)，局部方向见 [.impeccable/surfaces/search.md](.impeccable/surfaces/search.md)。

评论及楼内回复工具栏提供“问 Lumi”，只插入 `@Lumi`，发布后才创建异步任务；直接回复 Lumi 而未明确 @ 不触发。已登录且未封禁用户可围绕当前公开正常帖子提问，Lumi 在原楼内以固定 BOT 身份和 AI 标识回复，最多150个 UTF-16 字符，不显示成长等级，个人主页不提供关注入口。支持最多三篇其他公开帖的有限只读站内搜索，来源显示为“相关帖子”；联网搜索暂不可用，明确要求联网时保留普通评论、显示拒绝原因且不调用模型。

本人调用评论下显示排队、查看中、已回复或失败原因；可查看并定位楼内回答，允许重试时显示“重试 Lumi”，状态读取失败另有重试入口。仅服务可用且有进行中任务时每三秒更新状态，页面隐藏暂停，回到页面刷新，切换帖子或账号取消旧请求。输入、状态与来源链接沿用评论排版，支持手机及深浅色主题。

当前开发配置由后端读取被 Git 忽略的 `.env.lumi.local`，密钥不进入前端；无密钥默认不执行付费请求，已有云端及生产模拟环境未自动启用。开发默认平台并行2个、每天100次尝试、应用费用预算0.10美元；应用账本估算不等同于供应商最终账单，失败或删除测试数据也不能抹去已发生费用。配置、用户额度、接口、清理方法及实际验证范围见 [Lumi 帖子问答实现与验证](../doc/28_Lumi帖子问答实现与验证.md)，局部方向及界面收尾见 [.impeccable/surfaces/lumi.md](.impeccable/surfaces/lumi.md)。这些开发证据不代表全仓回归、生产容量或后端正式审查通过。

桌面侧栏和手机底栏提供“Lumi AI”，进入 `/lumi` 后登录即可分别保存和继续多个私有文字会话。“新对话”直接创建独立记录并保留旧聊天，不再要求清空确认；默认最多50段未删除会话，可配置1～200。电脑面板内有会话列表，1100px及以下使用列表抽屉；按最近消息活动排序，首批20段并可继续加载，每段消息首批20轮、向上续载。标题取首问短标题，不调用模型；可重命名（最多40个 UTF-16 字符）及单独删除，删除复用委屈猫确认，活动目标须先停止并等待完成。V31迁移保留未擦除旧记录和任务事实。

A生成时可以切换B或新建，A继续写回A；B可以编辑草稿，全账号仍同时最多一个任务，后台提示可返回正在回答的会话。真实上游回答由服务端最多每250毫秒保存一次，前端通过 SSE 约每500毫秒读取有变化的累计快照并按版本合并；连接不可用时三秒历史轮询兜底，隐藏暂停，恢复重新读取。刷新或断线恢复同一任务，不自动重发模型。停止中继续占用并发，确认停止后保留部分内容；失败保留内容并在允许时原位置重试。

回答以聊天独立的 `react-markdown` / `remark-gfm` 支持标题、列表、引用、表格、安全链接与代码，提供回答和代码复制；不执行原始 HTML、不展示模型图片。表格与代码局部横向滚动，排版依赖仅随懒加载聊天路由加载。输入随内容增高，限高后内部滚动，支持 Ctrl/Command+Enter 和中文输入法保护。草稿及未确认操作标识通过当前标签页 sessionStorage 按账号与会话隔离；发送或删除只清目标，新建不清旧草稿，相同内容的不确定重试复用标识。存储不可用时退化为内存输入。当前页面内缓存最近十段已加载记录并恢复阅读位置，退出或刷新后按服务端分页读取；URL的 `?chat=id` 和按账号保存的上次选择用于定位，权限由后端判断。账号切换使旧缓存及回调作废，不承诺跨设备或跨标签页草稿同步。手机消息与输入16px，visualViewport调整面板，模拟键盘状态隐藏底栏并收紧页头和输入区。

每条输入最多1000个 UTF-16 字符，回答最多4000字符、模型输出最多768 Token；服务端按用户及会话隔离最近最多6轮有回答的完整问答对，总请求预算16000字节。STOPPED/FAILED部分回答明确标记未完成后可供继续，空回答和PENDING/RUNNING/STOPPING不进入历史。个人默认每天20次、间隔5秒，与评论助手共用 DeepSeek后端配置、平台并行2个、每天100次尝试和0.10美元应用估算预算；新建或删除不重置并发、冷却、每日额度或费用事实。私有正文不进入通用幂等响应缓存。当前无联网、站内工具、附件、角色设置、跨会话记忆、历史搜索、长对话摘要或重新生成成功回答，评论助手150字符规则保留。仅开发3000/8080启用，实现、接口与验证边界见 [Lumi 多会话管理与上下文隔离](../doc/31_Lumi多会话管理与上下文隔离.md)，[流式迭代](../doc/30_Lumi聊天流式交互迭代.md)及[首版闭环](../doc/29_Lumi私有聊天最小闭环.md)保留旧阶段历史；局部规范见 [.impeccable/surfaces/lumi-chat.md](.impeccable/surfaces/lumi-chat.md)。

本轮101项定向测试通过、0失败0错误0跳过，其中35项真实 MySQL 临时库验证，汇总为 `output/lumi-conversations-test-results.json`。真实批次 `1792072550562` 四次 DeepSeek调用及前四组检查通过，993输入/113输出Token、应用估算435微美元；后续截图辅助等待隐藏懒加载图片挂起中止，不能称整批真实 E2E 完成。最终批次 `1792074237412` 为零模型夹具，三组检查全部通过，faults为空、failure为null；覆盖20/20分页、草稿与阅读位置、迟到响应、账号隔离及两端列表抽屉、命名/删除确认、后台提示、深色和模拟键盘。两批数据均finally清理，只读核对标记用户和消息为0，V31成功；前端src/dist/public实际密钥精确扫描命中0。真实与夹具结果分见 [real-result](.impeccable/review/lumi-conversations/real-result-1792072550562.json) 和 [fixture-result](.impeccable/review/lumi-conversations/fixture-result.json)。

独立 [完整 UI 复查](.impeccable/review/lumi-conversations/review.md) 为 ship、无 material_fixes；十张最终截图同目录，视口与PNG尺寸见doc/31。一次detector为0 primary、16 advisory。本轮前端构建主包390.45kB、聊天包183.64kB均为压缩前产物大小，不是性能实测；手机键盘仅作合成visualViewport验证，未作真实手机键盘或Safari验证。这些证据不代表全仓回归、生产容量、任意模型回答准确性或后端正式主审通过。旧阶段92项测试、27项真实MySQL验证及八张截图继续归档于doc/30，不作为本轮最终结果。

界面不内置演示帖子、互动数或成长等级。主题偏好保留 `luminous_v2_theme`，登录凭证沿用 `luminous_token`。旧来源 `3001` 的浏览器存储与 `3000` 隔离，切换后需要重新登录。

Luminous 娘和猫咪为本次生成素材，来源记录位于 `public/illustrations`。自托管 Bricolage Grotesque 与 Noto Sans SC 最小字库的来源、许可位于 `public/fonts`。实际设计规范见 [DESIGN.md](DESIGN.md) 和仓库 `.impeccable/design.json`。

初轮模拟接口记录保留在 `doc/17_前端重制与验收说明.md`；真实 Docker 联调与问题修复见 `doc/18_新版前端接管与真实联调验收.md`。

## 开发验收与清理

新搜索的独立验收在仓库根目录、已启动的 `luminous-dev` 上运行。包装脚本校验开发容器归属，固定样例和真实双端流程使用 `[E2E_TEST]` 标记，成功或失败均按批次清理；不要改为生产模拟地址。浏览器取消与503重试使用明确故障注入，其余正常流程读取真实开发服务。

```powershell
& ./scripts/verify-search.ps1
```

最终结果、性能测量及执行计划见 `output/playwright/search/` 与 [doc/26](../doc/26_搜索准确度与性能优化.md)。目标测试中的真实 MySQL 用例须实际运行，不能将跳过算作通过；这组验证不代表全仓测试或生产并发容量。

发帖与社区流程的独立验收同样在仓库根目录、已启动的 `luminous-dev` 上执行。脚本检查开发容器归属，使用正式验证码及 `[E2E_TEST]` 标记数据，成功或失败均按批次清理数据和上传对象；不要改用生产模拟环境。

```powershell
& ./scripts/verify-posting-community.ps1
```

结果与两端截图位于 `output/playwright/posting-community/`。桌面、手机覆盖文件草稿恢复、账号隔离、社区选择与加入、单图粘贴、表情、失败重试、发布跳转返回和冷却等流程；上传失败使用一次明确的503故障注入。目标测试、真实临时 MySQL 验证及榜单回归范围见 [doc/25](../doc/25_发帖与社区流程优化.md)，不表示全仓或生产容量验证通过。

推荐与日周热榜的独立验收在仓库根目录、已启动的 `luminous-dev` 上执行。包装脚本校验开发容器归属，覆盖真实排序与两端浏览流程，并在成功或失败后精确清理本批 `[E2E_TEST]` 数据及对应派生缓存；不清空 Redis。环境不符合时应先恢复独立开发服务，不改用生产模拟地址。

```powershell
& ./scripts/verify-post-rankings.ps1
```

本轮结果与截图位于 `output/playwright/post-rankings/`；截图滚动到帖子列表区，仅作为排序控件、列表及空态的局部证据。完整验证记录与边界见 [doc/24](../doc/24_推荐与日周热榜.md)。

```powershell
& ./scripts/verify-client-idempotency.ps1
& ./scripts/run-e2e.ps1 -TargetApi http://localhost:8080/api
```

真实浏览器验收复用工作区 Playwright 工具和已安装的 Chrome，覆盖 19 项页面流程；如工具未安装，先准备工作区工具目录：

```powershell
npm install --prefix .local/browser-tools playwright
& ./scripts/verify-frontend-live.ps1
```

浏览器报告和截图位于 `output/playwright/luminous-integration`。浏览器脚本直接调用真实服务，不拦截或模拟 API；它只在独立开发 Redis 中读取本次验证码答案，然后操作真实滑块并调用正式验证接口。

真实测试使用 `[E2E_TEST]` 标记。清理脚本验证 Docker Compose 归属，只允许 `luminous-dev`，按标记清理关联数据、上传对象和实体缓存。测试失败也会运行清理，清理失败不会报告成功。需要手动补清理时执行：

```powershell
& ./scripts/clean-test-data.ps1
```
