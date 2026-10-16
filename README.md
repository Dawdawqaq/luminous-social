# Luminous

Luminous 是 Java 21 / Spring Boot 3.5.0 与 React 18 / TypeScript / Vite 6 构建的社区应用，提供图文帖子、社区、投票、评论、收藏、关注动态、签到和通知中心。数据库使用 MySQL 8.4，Redis 7.4 用于可重建投影、缓存及限流，RocketMQ 5.3.1 用于事件投递。Flyway 自动执行仓库中的数据库迁移。

## 当前实现与边界

| 能力 | 实际实现 | 边界 |
| --- | --- | --- |
| 投票 | 数据库事务先保存投票意图、递增业务版本及可靠事件；提交后更新 Redis、发送 MQ。消费记录与投票记录、总分更新共用独立事务 | 投票展示读取已接收意图；落库投影最终一致。每次接收锁定帖子行，并以锁定读取获取最新水位和用户状态，避免外层事务旧快照复用版本或覆盖总分 |
| 投递与去重 | MQ 与本地降级使用同一事件 ID 和数据库消费入口；未完成事件由调度器重投 | 至少一次投递，消费记录保留期间防止重复业务效果；不宣称端到端 Exactly-once |
| 投票聚合 | 默认消费者逐条处理；候选 `processBatch` 每批最多 64 条，合并同帖总分更新 | 已提供隔离持续对照压测，尚未接入默认 MQ 消费；没有宣称消除热点行锁或生产吞吐提升 |
| 恢复与对账 | Redis 状态从数据库已接收版本恢复；对账考虑接收、应用水位，并检查总分、投票状态及缺失事件 | 正常消费积压不能被旧总分覆盖；数据库不可用时不能接受新的可靠投票 |
| 帖子缓存 | 共享 Redis 短缓存，有效期不超过 5 秒；提交后删除。同帖请求合并，回源并发每节点最多 8 个 | 已移除帖子 Caffeine 本地副本。删除失败存在最长约 5 秒旧值窗口，不宣称强一致缓存；过载返回 503 |
| 列表组装 | 作者、社区、收藏和投票状态批量 SQL 查询；所有帖子 UV 使用一个 Redis Pipeline | PFCOUNT 按键分别执行，不能把多个帖子的 HLL 合并统计。Redis 失败时 UV 展示 0，不回退逐帖查询 |
| 关注动态 | 数据库统一拉取当前关注作者最近 30 天的公开帖子，按创建时间、ID 倒序合并去重 | 覆盖普通作者与大 V。新关注包含窗口内历史，取消关注立即过滤；翻页期间关注变化可能改变结果 |
| 动态收件箱 | 提交后推送可重建 Redis 投影，每人最多 1000 条、7 天过期；超过 10000 粉丝不推送投影 | 实际读取依据数据库，不能描述成已经启用 Redis 推拉混合读取 |
| UV | 登录用户 ID 或一年期匿名 Cookie 作为稳定身份，Redis HyperLogLog 近似计数 | 是近似身份统计；清除 Cookie、不同设备及 Redis 数据丢失会影响统计，不是精准人数 |
| 评论 | 平铺评论、评论点赞与删除，`@` 文本回复，详情展示精选评论 | 当前没有父子评论树，不是多级评论 |
| 签到 | 数据库唯一键、用户锁保证每天首次成功一次，保存历史并跨月计算连续天数；上海时区 | 45 天 Redis Bitmap 是投影，历史查询依据数据库；旧 Bitmap 导入需显式开启升级开关 |
| 通知 | 可靠事件、事务落库、失败记录查询及管理员重放；前端操作后、窗口聚焦时刷新，前台每 15 秒轮询 | 未实现 WebSocket/SSE 实时推送。后台页面停止轮询，恢复可见时刷新；失败保留上次确认计数 |
| 安全 | JWT、BCrypt；滑块验证码凭证绑定会话及用途，首次认证后进一步绑定唯一操作及请求参数；Lua 原子限流 | 未使用凭证有效期 120 秒；首次认证后保留至幂等窗口结束，数据库失败仅允许原请求重试，重试不延长有效期。登录/注册必须携带验证码凭证与幂等 key。只信任配置的代理 IP；限流 Redis 故障返回 503 |
| 封禁与管理员 | JWT 请求查询数据库封禁状态；初始管理员由显式启动配置和固定行锁初始化 | Redis 故障不会放行封禁用户，数据库故障返回 503。公开注册始终创建普通用户 |
| 请求幂等 | 客户端合并重复点击，超时重试复用 key；同一资源的反向操作共用分组，切换后不复用切换前的成功 key；服务端事务保存成功响应并重放 | 默认保留 24 小时；相同 key 不同参数返回 409。客户端指纹及安全随机 key 兼容普通 HTTP 页面。过期后是新操作；结果表可能含令牌，应控制数据库访问 |
| 文件 | 本地或私有 MinIO；浏览器通过应用文件 API 读取，内部 S3 地址与公开应用地址分离 | 上传者、管理员或持有文件能力链接者可读取。数据库失败补偿删除，进程中断由持久化清理记录恢复；旧 MinIO URL 不自动改写 |
| 多节点 ID | 生产两节点使用不同 worker ID；校验 ID 范围及节点占用 | 启动配置错误会失败；依赖节点租约与时钟约束，不宣称任意环境无条件唯一 |
| 搜索 | MySQL ngram 全文检索与必要回退查询 | 当前没有 Elasticsearch，不给出未测量的毫秒级承诺 |

公开帖子对所有人可见；非公开帖子仅作者和管理员可读取或操作。详情、评论、投票和动态均执行对应检查，无权限读取返回 404，匿名写操作返回 401。

## 分页约定

- 普通分页要求 `page >= 1`、`1 <= size <= 100`，非法数值、非数字及溢出返回 400。
- 帖子列表只浏览当前筛选及排序的前 **500 条**，`total` 表示这个浏览窗口内的数量。末页可能不足 `size`，`hasNext=false`；起始位置超出窗口返回 `POST_WINDOW_LIMIT`，可按社区、作者或搜索缩小范围。详情仍可通过帖子 ID 访问。
- 收藏、通知和失败消息查询的起始 offset 最多 10000，越界返回 `PAGE_DEPTH_LIMIT`。这不是全库无限深分页。
- `GET /api/feed` 使用 `lastScore` 与同毫秒组的 `offset`，新一页仍处于相同 score 时累计旧 offset；单页 1～100，同组 offset 最多 10000。超过范围需调整时间游标。排序加入 ID 保证静态数据同时间戳不重不漏；内容或关注变化期间不提供快照分页。

## 接口约定

统一返回 `{ "success": true, "data": ..., "error": null, "timestamp": ... }`；错误返回 `success=false` 和 `error.code/message`。身份认证使用 `Authorization: Bearer <token>`，浏览器保留验证码会话 Cookie。

| 方法与路径 | 参数 | 返回 | 常见异常 |
| --- | --- | --- | --- |
| `GET /api/posts` | `filter`、`community`、`search`、`author`、`page`、`size` | `items/page/size/total/hasNext` | 400 非法分页或超出浏览窗口 |
| `GET /api/feed` | `lastScore`、`offset`、`size`，需要登录 | `items/minScore/offset/hasMore` | 400 无效游标、401 未登录 |
| `GET /api/notifications/unread-count` | 需要登录 | `{ unreadCount }` | 401 未登录 |
| `PUT /api/notifications/{id}/read` | 通知 ID，需要登录 | 成功响应 | 403 无权访问、404 不存在 |
| `PUT /api/notifications/read-all` | 需要登录 | `{ markedCount }` | 401 未登录 |
| `POST /api/posts/{id}/vote` | JSON `{ value: -1/0/1 }` | 当前帖子 DTO | 400 无效状态、401、404、503 依赖不可用 |

验证码、登录注册、幂等结果重放和文件读取接口详见后端 Swagger；可靠投票与故障验收见 `doc/10_可靠事件与投票恢复修复说明.md`，认证存储见 `doc/12_认证幂等与文件存储修复验收说明.md`。`doc/` 是本地研发资料，默认被 Git 忽略，发布使用本 README。

## 独立 Docker 开发环境

真实云服务器的单节点部署准备见 [deploy/cloud/README.md](deploy/cloud/README.md)。该配置面向小内存服务器，保留 RocketMQ，由宿主机 Nginx 托管前端和 HTTPS，使用独立 `luminous-cloud` 项目及持久卷；不能直接复用下面的开发配置或双节点生产模拟配置。

2026-10-07 已完成首次云部署，启动和只读页面检查结果、资源快照及管理命令见 [deploy/cloud/DEPLOYMENT.md](deploy/cloud/DEPLOYMENT.md)。

需要 Docker Engine 和 Compose **2.24.4+**。基础编排启动独立 MySQL、Redis、MinIO、MQ、后端和前端；模板默认关闭应用 MQ 投递以便本机调试，本地消费仍走同一数据库事务入口。以下命令在仓库根目录运行，不操作其他 Compose 项目。

1. 复制独立开发模板。用途是明确端口、私有存储凭据和浏览器公开地址，不覆盖已有 `.env`。

   ```powershell
   Copy-Item .env.dev.example .env.dev
   ```

   Linux/macOS 对应命令：

   ```bash
   cp .env.dev.example .env.dev
   ```

2. 先检查展开配置，再构建启动。用途是发现变量、YAML 或端口配置错误，并构建最新代码。

   ```bash
   docker compose --env-file .env.dev -f docker-compose.yml config --quiet
   docker compose --env-file .env.dev -f docker-compose.yml up -d --build
   docker compose --env-file .env.dev -f docker-compose.yml ps
   ```

   浏览器访问 `http://localhost:13000`，独立 API 为 `http://localhost:18080`，MinIO 控制台为 `http://localhost:19001`。文件公开地址使用前端入口，经 `/api/files/` 代理读取，不返回容器内 `minio:9000`。

3. 查看启动问题，必要时停止该独立项目。用途是定位 Flyway、数据库、存储等依赖错误；`down` 保留卷数据。

   ```bash
   docker compose --env-file .env.dev -f docker-compose.yml logs --tail=100 backend mysql minio
   docker compose --env-file .env.dev -f docker-compose.yml down
   ```

   端口占用时修改 `.env.dev` 对应端口，同时调整 `APP_PUBLIC_BASE_URL`、`MINIO_PUBLIC_ENDPOINT` 与 CORS。若修改已有卷的数据库或 MinIO 密码，环境变量不会自动修改原有账户，应使用原凭据或执行受控账号变更。不要通过删除共享卷解决问题。首次 MinIO 尚未就绪导致后端重启时，查看日志并等待存储服务可用。

## 本地源码开发

需要 JDK 21、Maven 3.9、Node.js 18+、npm 和仓库前端依赖，以及独立 MySQL 8.4 / Redis。下例连接上面的开发端口，在仓库根目录使用 PowerShell。源码后端使用本地文件存储、MQ 关闭，不依赖 MinIO：

```powershell
$env:SPRING_PROFILES_ACTIVE='dev'
$env:DB_HOST='127.0.0.1'
$env:DB_PORT='33067'
$env:DB_NAME='luminous_dev'
$env:DB_USER='luminous_dev_user'
$env:DB_PASSWORD='luminous_dev_password'
$env:REDIS_HOST='127.0.0.1'
$env:REDIS_PORT='63797'
$env:REDIS_USERNAME=''
$env:REDIS_PASSWORD=''
$env:SERVER_PORT='8080'
$env:STORAGE_TYPE='local'
$env:ROCKETMQ_ENABLED='false'
$env:APP_PUBLIC_BASE_URL='http://localhost:3000'
$env:CORS_ALLOWED_ORIGINS='http://localhost:3000'
Set-Location backend
mvn spring-boot:run
```

Linux/macOS 在仓库根目录：

```bash
cd backend
SPRING_PROFILES_ACTIVE=dev DB_HOST=127.0.0.1 DB_PORT=33067 DB_NAME=luminous_dev \
DB_USER=luminous_dev_user DB_PASSWORD=luminous_dev_password \
REDIS_HOST=127.0.0.1 REDIS_PORT=63797 REDIS_USERNAME= REDIS_PASSWORD= SERVER_PORT=8080 STORAGE_TYPE=local \
ROCKETMQ_ENABLED=false APP_PUBLIC_BASE_URL=http://localhost:3000 \
CORS_ALLOWED_ORIGINS=http://localhost:3000 mvn spring-boot:run
```

另一终端从仓库根目录启动前端：

```bash
cd frontend
npm ci
npm run dev
```

浏览器访问 `http://localhost:3000`，Vite 将 `/api` 转发到 `http://localhost:8080`。Flyway 执行全部已提交迁移，当前到 V12；不会仅执行旧文档中的 V1～V9。若数据库连接失败，先核对端口、用户权限和库名。不要把独立测试配置指向共享开发或生产库。

## 双节点生产模拟

生产配置是**基础 Compose 加覆盖文件**，覆盖文件不能单独启动。双节点统一 JWT 密钥、私有存储凭据与公开文件地址；datacenter ID 均为 1，worker ID 分别为 1、2。

1. 创建专用环境文件，并填写真实密码、至少 32 字节随机 JWT 密钥、应用公开地址与 CORS。公开注册不会产生管理员；如需初始化管理员，设置 `BOOTSTRAP_ADMIN_USERNAME/PASSWORD`，初始化成功后清除密码配置。

   ```powershell
   Copy-Item .env.prod.example .env.prod
   ```

   Linux/macOS 使用 `cp .env.prod.example .env.prod`。模板中的 `replace-with-...` 均为占位值，生产启动校验会拒绝不合格 JWT 密钥。

2. 核对合并结果，再启动。外部入口是 Nginx 的 HTTP 80；后端和前端服务不单独暴露端口，数据库、Redis、MinIO 管理端口仅绑定宿主机回环地址，Broker 不公开端口。

   ```bash
   docker compose --env-file .env.prod -f docker-compose.yml -f docker-compose.prod.yml config --quiet
   docker compose --env-file .env.prod -f docker-compose.yml -f docker-compose.prod.yml up -d --build
   docker compose --env-file .env.prod -f docker-compose.yml -f docker-compose.prod.yml ps
   ```

3. 如需 HTTPS，准备真实证书及私钥，填写 `TLS_CERT_FILE/TLS_KEY_FILE`，将公开地址和 CORS 改为 HTTPS，再加入 TLS 覆盖。用途是实际启用 443 和 HTTP 跳转，TLS 覆盖同时开启安全 Cookie。

   ```bash
   docker compose --env-file .env.prod -f docker-compose.yml -f docker-compose.prod.yml -f docker-compose.tls.yml config --quiet
   docker compose --env-file .env.prod -f docker-compose.yml -f docker-compose.prod.yml -f docker-compose.tls.yml up -d --build
   ```

   配置报 `!override` 不能解析时升级 Compose；公开文件无法读取时核对应用公开域名、`/api/files/` 代理与能力 token，不能用容器内部地址替代。TLS 启动失败时检查证书路径、权限和证书匹配域名。跨节点 401 应核对统一 JWT 密钥及数据库状态；节点 ID 占用报错时修正 worker/datacenter 配置。

开发容器已在本机启动，验证了首页资源、帖子和社区列表、登录验证码接口及 MinIO 就绪状态；Flyway 已升级至 V12，真实 MySQL 8.4 的两项投票旧快照回归通过。生产集群、生产 TLS 和完整文件上传流程尚未验收；这些部署命令不代表生产环境已验证。代理只信任 Nginx 的配置 IP，调整网络时应同步更新 `PROXY_IP/PROXY_SUBNET`。

## 回归测试与持续压测

在仓库根目录运行以下 PowerShell 命令。默认使用隔离 H2 和脚本自己创建的随机端口 Redis；关闭时只停止本次 Redis 进程，不重启共享基础设施。Maven 使用离线缓存，首次缺少依赖需先在允许联网的环境准备依赖。

```powershell
./scripts/verify-issue-fixes.ps1 -RedisServerPath D:\Redis\redis-server.exe
./scripts/verify-client-idempotency.ps1
./scripts/verify-issue-fixes.ps1 -RunAggregationBenchmark -AggregationOnly -BenchmarkSeconds 15 -RedisServerPath D:\Redis\redis-server.exe
```

- 第一条覆盖全部单元测试及指定隔离集成测试，包括投票状态转换、事务回滚、消费中断、重投与乱序、Redis 恢复、带积压对账、多节点缓存、同毫秒动态分页、重复取消关注、并发及跨月签到、HTTP 分页、真实 HLL Pipeline、通知读写，以及认证数据库失败后的原请求重试和验证码绑定校验。
- 第二条验证客户端幂等、反向操作连续切换、HTTP 环境的 SHA-256 和安全随机 key、通知计数竞态与认证测试夹具，使用仓库已有 TypeScript 编译器及 Node 测试，不依赖在线服务。
- 第三条运行持续投票 A/B 对照：4 个生产线程、4 个消费线程、128 用户、单热点帖子、容量 4096 的进程内队列；每十次注入重复消息、批次交替逆序。两种路径各预热 10 秒，按逐条、32 条批次、32 条批次、逐条运行，示例每轮 15 秒（允许 10～120 秒），并排空验证业务版本和所有总分。

报告保存到 `backend/target/vote-aggregation-benchmark.json`，包含吞吐、每秒积压、总分 SQL 更新次数、排空时间、加锁耗时及一致性结果。加锁指标是 `SELECT FOR UPDATE` 获取耗时，包含 SQL 执行及等待；**不是 MySQL 引擎锁等待**。该实验不包含 HTTP、Redis 投影、Broker 网络或 MySQL 磁盘成本，不能作为生产 SLA。真实 MySQL/Broker 的长期吞吐、锁等待与积压仍需专用环境验证，默认 MQ 聚合保持关闭。

`VoteMySqlIsolationIntegrationTest` 专门验证 MySQL `REPEATABLE READ` 旧快照下，同用户切换投票及不同用户连续投票的版本、差值和总分。未设置 `LUMINOUS_MYSQL_REVIEW_PASSWORD` 时跳过；仅连接开发 MySQL 的固定端口 33067，自建随机命名的临时数据库并在结束后删除，测试账号需要建库和删库权限。先确保开发 MySQL 已就绪，再单独执行以下命令；连接失败时检查开发端口及账号权限，无需改成其他环境端口。

```powershell
$env:LUMINOUS_MYSQL_REVIEW_USER = 'root'
$reviewDatabasePassword = Read-Host '开发 MySQL 密码' -AsSecureString
try {
    $env:LUMINOUS_MYSQL_REVIEW_PASSWORD = [System.Net.NetworkCredential]::new('', $reviewDatabasePassword).Password
    mvn -o -f backend/pom.xml test '-Dtest=VoteMySqlIsolationIntegrationTest'
    if ($LASTEXITCODE -ne 0) { throw 'MySQL 投票回归失败，请检查测试报告。' }
} finally {
    Remove-Item Env:LUMINOUS_MYSQL_REVIEW_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:LUMINOUS_MYSQL_REVIEW_USER -ErrorAction SilentlyContinue
    $reviewDatabasePassword.Dispose()
}
```

前端构建检查：

```bash
cd frontend
npm run build
```

`doc/01`～`doc/08` 是历史设计或测量资料，部分描述已过时，以当前源码、本 README 和最新验收记录为准。历史吞吐数字不代表当前版本。

## 主要代码位置

| 路径 | 作用 |
| --- | --- |
| `backend/src/main/java/com/luminous/api/post` | 帖子、平铺评论、收藏与访问权限 |
| `backend/src/main/java/com/luminous/api/vote` | 投票意图、版本、事务消费与投影恢复 |
| `backend/src/main/java/com/luminous/api/mq` | 可靠事件、消费记录、投递与失败重放 |
| `backend/src/main/java/com/luminous/api/cache` | 共享短缓存、有界回源与 UV 批量读取 |
| `backend/src/main/java/com/luminous/api/feed` | 数据库关注流与有界收件箱投影 |
| `backend/src/main/java/com/luminous/api/security` | JWT、服务端验证码、可信代理、原子限流与幂等 |
| `backend/src/main/java/com/luminous/api/upload` | 上传事务和持久化补偿 |
| `frontend/src/context/NotificationContext.tsx` | 共享未读计数与可见页面轮询 |
| `frontend/src/notification/UnreadCounter.ts` | 合并查询、隔离用户和拒绝过期结果 |

## 新版前端

漫画波普风新版已接管 `frontend`，旧版源码已移除。Docker、部署打包和本地构建统一使用这个目录，开发前端端口为 3000，后端为 8080。

```powershell
npm --prefix frontend ci --cache .local/npm-cache-v2
npm --prefix frontend run dev
```

Docker 前端运行时，切换到本地热更新需要先停止开发前端容器以释放 3000；操作步骤见前端说明。

本地开发代理和 Docker Nginx 均接入现有后端。成长后端已实现双维度经验、等级计算、奖励账本、每日限额与违规扣回，页面使用真实等级数据；参考参数只用于开发验收，生产奖励默认关闭，普通浏览不奖励。实现与契约见 [成长系统说明](doc/20_用户成长等级系统实现与验收.md)。启动、资源来源与真实联调记录见 [前端说明](frontend/README.md) 与 `doc/18_新版前端接管与真实联调验收.md`。此前模拟接口验收记录保留在 `doc/17_前端重制与验收说明.md`。

## 数据库升级注意

正式版本使用 V23 补齐关注分页索引；旧本地 V13 草案不再作为发布迁移，已执行该草案的开发库需要先核对迁移历史。

Lumi 使用独立内部用户名 `lumi.bot`，展示名为 Lumi，不覆盖既有普通 Lumi/lumi 账号。本轮 V28 在首次提交前修订；已经执行旧 V28 草案的开发库，直接更新源码会被 Flyway 默认校验拒绝。升级前应先备份，核对全部已应用脚本、校验值及实际表结构，确认只有已知草案差异后再受控恢复迁移记录并执行后续迁移。V32 仅调整固定 BOT 账号的内部用户名。不要关闭校验或在应用启动时自动 repair；存在未知残留、其他校验差异或已经正式发布的基线时，须另行制定兼容升级方案。代码提交及构建通过不代表现有数据库已经升级。

本项目采用 [MIT License](LICENSE)。第三方字体保留各自许可，见 `frontend/public/fonts`。
