# 2026-10-07 云服务器首次部署记录

## 最新运行状态：已关闭服务

2026-10-07 19:44（Asia/Shanghai），根据用户要求停止网站及本项目全部云容器，为后续网站用途和备案准备保留环境。本次 SSH 已恢复，关闭操作已执行并验证：

- Nginx 为 `inactive`，开机自启为 `disabled`。
- `luminous-cloud` 的 backend、MySQL、Redis、NameServer、Broker 五个容器均为 `Exited`。
- 服务器不再监听 80、443 和 18081，公网 HTTP/HTTPS 连接均被拒绝；SSH 22 仍监听。
- MySQL、Redis、Broker store、Broker logs、uploads 五个持久卷仍存在，镜像和环境文件未删除。
- MySQL、Redis 退出码为 0，后端为 143，两个 RocketMQ 容器为 137。停止完成不等于已经验证再次启动和 MQ 存储恢复；恢复服务时需重新检查日志及 Broker 登记。

此前的部署验收和资源快照属于历史运行结果，不能视为当前仍在线。暂停期间不自动重启网站；以下启动命令仅供用户后续明确要求恢复服务时使用。

## 域名配置准备：myluminous.xyz

2026-10-07，在服务停止状态下配置主入口 `https://myluminous.xyz`，HTTP 及 HTTPS 的 `www.myluminous.xyz` 跳转主域名。已同步云端 Nginx 站点、部署模板、Compose 默认值和环境初始化脚本；现有 `.env.cloud.local` 仅替换 `APP_PUBLIC_BASE_URL`，全部密钥保留。

- Nginx 语法检查、环境初始化脚本语法检查及 Compose 配置检查通过。
- 修改后再次确认 Nginx 仍为 `inactive/disabled`，本云项目没有运行中的容器；没有启动或重载服务。
- 旧配置备份位于 `/opt/luminous/backups/domain-20261007-obNQQS`，目录权限为 `700`、环境文件为 `600`。本次临时上传目录已清除，未重新构建或上传应用包。
- 修改时主域名无 A 记录，`www` 尚不存在；用户需在阿里云 DNS 添加 `@` 和 `www` 两条 A 记录，均指向 `8.137.79.129`。
- 尚未备案，证书仍为原自签名证书；通过语法检查不代表域名解析、可信 HTTPS 或公网访问已完成。
- 后续恢复服务时需通过 Compose 重建后端容器加载新地址，并检查 MQ 存储恢复与 Broker 登记。公开开放前先完成备案及主域名、www 域名的受信任证书配置。

## 已部署状态

网站入口：`https://8.137.79.129`，HTTP 入口返回 301 跳转 HTTPS。服务器为 Ubuntu 22.04、x86_64、2 个逻辑 CPU、1.6GiB 内存、2GiB 已启用 Swap。Docker Engine 29.7.2、Compose 5.4.0、Nginx 1.18.0。

应用目录为 `/opt/luminous`，前端目录为 `/var/www/luminous`。独立 Compose 项目为 `luminous-cloud`；后端只发布 `127.0.0.1:18081`，数据库、Redis、MQ 均未发布宿主机端口。源提交为 `b2ddbad0b01cd5f69d479bdec46953c364e7eb09`，本次云部署配置仍包含未提交修改。

基础包 `luminous-cloud-20261007-113359.tar.gz` 校验值为 `1EA3EED4148F63A0A75028DB11A59BBB04ACA49FDD4DF23417E882E02D316BB9`。部署后发现 MQ 客户端需要可写的运行用户家目录，已将 Dockerfile 修正为创建 `/home/luminous`，重新构建并重建后端；该基础包的旧 Dockerfile 不能作为最终运行配置。后端 JAR 未改变，SHA256 为 `EE65574BF6374EBD6D378339E7683043BAF14EF4F967B5A5E91B276AEE83EB9E`。后续打包使用当前 `scripts/package-cloud.ps1`，其中已包含修正。

Nginx 原站点备份位于 `/opt/luminous/backups/nginx-20261007120820`；当前启用站点为 `/etc/nginx/sites-enabled/luminous`，日志格式定义为 `/etc/nginx/conf.d/luminous-log.conf`。原 `/var/www/game` 已不作为网站入口。

## 实际启动和只读验收

- MySQL、Redis、NameServer、Broker 健康检查通过，后端 API 返回 `success:true`。
- Flyway 最新三项版本为 10、11、12，均成功。
- MQ 管理查询确认 `LuminousCloudCluster` 下的 Broker 为 `luminous-cloud-broker`，地址 `172.29.79.20:10911`，版本 5.3.1，处于激活状态。
- 后端生产者、通知消费者、投票消费者启动成功。客户端日志目录和上传目录可写；修正后的后端启动日志未出现先前的目录创建错误。
- 本地公网请求和隔离浏览器检查：首页、应用 JS/CSS、favicon、帖子 API、社区 API、登录拼图验证码 API 均为 HTTP 200。
- 首页正常显示空列表，登录表单及拼图图片正常加载。浏览器控制台未发现 JavaScript 错误。
- 五个容器均未发生 OOM，当前容器重启次数为 0。

仅验证启动、公开只读接口、表单加载和 Broker 登记，未执行云端注册、发帖、投票写入测试或压测。这些结果不代表已经完成负载、故障恢复或消息投递全链路验收。

## 资源快照

2026-10-07 12:08 至 12:12（Asia/Shanghai），空数据低负载状态：

| 服务 | Docker 内存快照 |
| --- | --- |
| 后端 | 约 347MiB |
| Broker | 约 243MiB |
| NameServer | 约 93MiB |
| MySQL | 约 227MiB |
| Redis | 约 6MiB |

整机使用约 1.1～1.2GiB，可用约 250MiB；Swap 为 0。连续 `vmstat` 采样未出现换入换出。这是启动后的快照，后续实际使用、消息积压、数据增长和 AOF 重写可能提高占用。`docker image ls` 显示后端镜像约 681MB；此数值包含 Docker 存储统计中的镜像内容，共享基础镜像不可简单重复相加。

## 后续更新的磁盘约束

每次发布后只保留当前应用版本和一个经过验证的回滚版本；云端临时上传包与解压目录在验收通过后清理，本地保留最近两个有效部署归档。旧镜像、旧 JAR、前端备份分别核对，避免只删 JAR 而遗漏镜像层。数据卷和数据备份不纳入应用旧包清理，更新失败时不清理回滚版本。

2026-10-07 增补只读检查：根分区总计 40G、已用 8.6G、可用 29G；`/opt/luminous` 为 137M，`/var/www/luminous` 为 380K，`/var/lib/docker` 为 2.2G。`docker system df` 显示镜像统计约 2.723GB、持久卷约 228.1MB，构建缓存为 0；不同统计口径不能直接相加。后端仅有 `latest` 标签，尚未因多次发布积累回滚版本。本次仅检查占用，没有删除文件或镜像。

根分区可用空间低于 5GiB 时暂停发布并排查；每次仍需另行检查构建、上传与备份需要的临时空间。具体保留规则、检查命令和清理边界见 [README.md 第 8 节](README.md#8-更新产物的磁盘保留规则)。目前没有自动清理任务。

## 已知限制

- 当前复用自签名证书，证书 CN 为 `127.0.0.1`，浏览器会提示不受信任或名称不匹配。诊断请求和隔离浏览器使用了忽略证书错误配置，因此没有验证可信 HTTPS。公开长期运行前应更换受信任证书。
- 当前浏览器中 Google Fonts 请求失败，页面回退到系统字体；应用本身的资源和 API 正常。
- 服务器直接访问 Docker Hub 超时，通过其已有配置中的 `docker.m.daocloud.io` 获取官方镜像，再标记为标准镜像名；未改动 Docker 全局镜像配置。后端首次构建使用 `DOCKER_BUILDKIT=0 COMPOSE_BAKE=false` 读取已下载的基础镜像。未来更新仍需确认镜像仓库可达。
- 当前为全新的云数据库，没有导入本地开发数据，也没有配置初始管理员。

## 常用管理命令

本机 PowerShell 连接：

```powershell
ssh luminous-cloud
```

服务器检查状态、日志和内存：

```bash
cd /opt/luminous
docker compose -p luminous-cloud --env-file .env.cloud.local -f compose.yml ps
docker compose -p luminous-cloud --env-file .env.cloud.local -f compose.yml logs --tail=100 backend broker
docker stats --no-stream
free -h
vmstat 1 10
```

停止五个云项目容器并保留数据：

```bash
cd /opt/luminous
docker compose -p luminous-cloud --env-file .env.cloud.local -f compose.yml stop
```

后续经用户要求再次启动容器：

```bash
cd /opt/luminous
docker compose -p luminous-cloud --env-file .env.cloud.local -f compose.yml up -d
```

单独停止容器不会停止 Nginx；本次已经额外执行 `systemctl disable --now nginx`，网站访问也已关闭。恢复容器后 Nginx 不会自动恢复，需要先确认网站用途、备案及证书配置，再执行：

```bash
nginx -t
systemctl enable --now nginx
```

更新前备份数据库、上传卷和 MQ 存储；不得使用 `down -v` 或全局清理命令作为更新步骤。环境文件仅服务器 root 可读，不输出密钥。
