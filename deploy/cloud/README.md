# 小内存云服务器部署准备

目标服务器：Ubuntu 22.04、x86_64、2 个逻辑 CPU、1.6GiB 内存、2GiB Swap、公网 IP `8.137.79.129`。本目录提供独立 `luminous-cloud` 编排，不能与仓库的开发或双节点生产模拟配置合并使用。

当前域名为 `myluminous.xyz`，尚未备案，网站与云容器已停止。以下启动及重载命令用于后续获准恢复服务时；添加 DNS 或准备配置不需要启动服务。

在阿里云云解析 DNS 中为 `myluminous.xyz` 添加两条默认线路 A 记录：主机记录 `@` 和 `www`，记录值均为 `8.137.79.129`，TTL 可用默认值。PowerShell 执行 `Resolve-DnsName myluminous.xyz -Type A` 和 `Resolve-DnsName www.myluminous.xyz -Type A`，核对结果为服务器 IP。若无结果，核对实名认证、域名状态、记录值及 DNS 缓存。

Nginx 模板以 `https://myluminous.xyz` 为主入口，HTTP 和 HTTPS 的 `www` 入口跳转主域名。后端 `APP_PUBLIC_BASE_URL`、CORS 和文件公共地址统一使用主域名。已有环境文件不会被 `init-env.sh` 覆盖，更新时只修改其 `APP_PUBLIC_BASE_URL`，保留全部密钥。配置文件修改后，后端需在恢复服务时通过 Compose 重建容器才能使用新环境值。

正式开放前需完成备案，并将证书替换为同时覆盖 `myluminous.xyz` 与 `www.myluminous.xyz` 的受信任证书；当前自签名证书仅供配置语法校验，不能视为域名 HTTPS 已就绪。

## 拓扑与容量边界

宿主机 Nginx 提供 HTTPS、前端静态文件和 `/api/` 转发；Docker 运行一个 Java 后端、MySQL、Redis、RocketMQ NameServer 和 Broker。上传文件使用应用文件 API 和独立持久卷，不直接通过 Nginx 暴露上传目录。数据库、Redis、MQ 不发布宿主机端口，后端只发布 `127.0.0.1:18081`。

| 服务 | JVM 最大堆 | 容器内存上限 | 内存与 Swap 合计上限 |
| --- | --- | --- | --- |
| 后端 | 256MiB | 512MiB | 768MiB |
| NameServer | 64MiB | 160MiB | 256MiB |
| Broker | 256MiB | 448MiB | 768MiB |
| MySQL | 不适用，缓冲池 128MiB | 448MiB | 768MiB |
| Redis | 不适用，数据上限 48MiB | 96MiB | 96MiB |

这些上限没有预留全部物理内存；总上限也包含操作系统无法使用的应用额度，不能相加后当作整机容量承诺。`mem_reservation` 也不是预先分配的内存。此配置用于低负载试运行，需要在实际服务器验证进程内存、Swap 换入换出、重启、请求延迟和 MQ 积压。Swap 无法消除容器自身的 OOM 风险。

Redis 使用 `noeviction`，避免验证码、幂等凭证和锁被静默驱逐；达到数据上限时写入会失败，应监测并调整容量。MySQL 关闭 Performance Schema 并缩小连接池，牺牲部分性能诊断和并发能力。MQ 单机、异步刷盘保留进程故障恢复所需的存储，但不提供节点高可用或断电零丢失保证。Broker 日志文件尺寸只能在新建存储时采用，已有 MQ 数据不能随意切换此参数。

## 1. 在开发机器生成部署包

在 PowerShell 仓库根目录执行。脚本重新构建 JAR 和前端，不在云服务器上安装 Maven、Node.js，也不将 `.env.dev`、`.env.prod` 或其他真实密钥打包。

```powershell
powershell -ExecutionPolicy Bypass -File scripts/package-cloud.ps1
```

部署包在 `.local/cloud/luminous-cloud-时间戳.tar.gz`，脚本输出完整路径和 SHA256；`manifest.json` 记录源提交、是否存在未提交修改和 JAR 校验值。打包跳过后端测试，不能将打包成功描述成业务回归通过。默认允许 Maven 下载构建依赖；依赖已经缓存且无网络时，可给脚本增加 `-Offline` 参数。

## 2. 连接服务器并检查前置条件

本机已验证的连接命令为 `ssh luminous-cloud`。以下命令在云服务器 root 终端执行。

```bash
uname -m
free -h
swapon --show
df -h /
docker info
docker compose version
docker ps --format '{{.Names}} {{.Ports}}'
nginx -t
ss -lntp
ip route
docker network ls
```

需要正常的 Docker Engine、Compose V2、已启用的 2GiB Swap 和可读的 `/etc/nginx/ssl/server.crt`、`server.key`。安全组允许 TCP 80、443，以及自己的 SSH 来源；无需开放 18081、3306、6379、9876、10911。确认 `172.29.79.0/24` 不与已有路由或 Docker 网络重叠。若冲突，统一修改 `compose.yml` 中的子网、网关、Broker 固定地址和可信代理地址，以及 `broker.conf` 中的 `brokerIP1`。

## 3. 上传并解压构建产物

下面 PowerShell 命令中的部署包路径和 SSH Host 使用本次实际值。只上传刚生成的归档，不上传仓库中的本地密钥文件。

```powershell
scp -i '本地私钥完整路径' '部署包完整路径.tar.gz' root@8.137.79.129:/root/luminous-cloud.tar.gz
```

服务器端执行；首次部署目录为空。升级时先完成第 7 步备份，再更新产物，保留原环境文件和卷。

```bash
sha256sum /root/luminous-cloud.tar.gz
install -d -m 750 /opt/luminous
tar -xzf /root/luminous-cloud.tar.gz -C /opt/luminous
cd /opt/luminous
bash init-env.sh
docker compose -p luminous-cloud --env-file .env.cloud.local -f compose.yml config --quiet
```

校验归档 SHA256 与开发机器的输出一致。密钥在服务器生成，环境文件权限为 `600`；初始化脚本不会覆盖已有密钥。需要初始管理员时，用编辑器填写 `.env.cloud.local` 的 `BOOTSTRAP_ADMIN_USERNAME/PASSWORD`；首次管理员创建成功后清空这两项，再重建后端。不要在日志、截图或聊天中展示环境文件内容。

## 4. 构建运行时镜像并分阶段启动

后端镜像从 Java 21 JRE 基础镜像构建，仅包含一份新 JAR。首次需要服务器能拉取 Docker Hub 镜像；若超时，使用可访问的镜像源或从开发机器导出可信镜像，不通过旧后端镜像叠加 JAR 绕过。

```bash
cd /opt/luminous
docker compose -p luminous-cloud --env-file .env.cloud.local -f compose.yml pull mysql redis namesrv broker
docker compose -p luminous-cloud --env-file .env.cloud.local -f compose.yml build backend

# 官方 RocketMQ 镜像使用 UID 3000，初始化持久卷的顶层目录属主。
docker compose -p luminous-cloud --env-file .env.cloud.local -f compose.yml run --rm --no-deps --user 0 --entrypoint sh broker -c 'mkdir -p /home/rocketmq/store /home/rocketmq/logs && chown 3000:3000 /home/rocketmq/store /home/rocketmq/logs'

docker compose -p luminous-cloud --env-file .env.cloud.local -f compose.yml up -d --wait --wait-timeout 300 mysql redis namesrv
docker compose -p luminous-cloud --env-file .env.cloud.local -f compose.yml up -d --wait --wait-timeout 300 broker
docker compose -p luminous-cloud --env-file .env.cloud.local -f compose.yml up -d backend
docker compose -p luminous-cloud --env-file .env.cloud.local -f compose.yml logs --tail=100 backend broker
```

TCP 健康检查仅确认 MQ 监听端口，仍需确认 Broker 的 `boot success`、后端生产者与两个消费者启动成功，以及 Flyway 迁移成功。启动失败时检查上述日志和 `docker compose ... ps`，不要通过删卷解决。

后端启动后，以只读请求确认 API 正常；响应应包含 `success:true`，HTTP 200 本身不足以证明业务结果成功。

```bash
curl --fail --silent --show-error http://127.0.0.1:18081/api/posts
```

## 5. 安装前端和 Nginx 配置

以下步骤适用于当前仅启用 `/etc/nginx/sites-enabled/default` 的服务器。先运行 `nginx -T` 核对实际配置；若存在其他站点，先评估默认监听冲突，不直接覆盖其他站点。现有 `/var/www/game` 的服务入口将由 Luminous 替代。

```bash
cd /opt/luminous
backup_dir="/opt/luminous/backups/nginx-$(date +%Y%m%d%H%M%S)"
install -d -m 700 "$backup_dir"
nginx -T > "$backup_dir/nginx.full.txt" 2>&1
cp -a /etc/nginx/sites-enabled/default "$backup_dir/default.enabled"
cp -a /etc/nginx/sites-available/default "$backup_dir/default.available"

install -d -m 755 /var/www/luminous /var/www/luminous-acme
cp -a frontend/. /var/www/luminous/
chown -R www-data:www-data /var/www/luminous
find /var/www/luminous -type d -exec chmod 755 {} +
find /var/www/luminous -type f -exec chmod 644 {} +
install -m 644 nginx-log.conf /etc/nginx/conf.d/luminous-log.conf
install -m 644 nginx.conf /etc/nginx/sites-available/luminous
ln -sfn /etc/nginx/sites-available/luminous /etc/nginx/sites-enabled/luminous
rm -f /etc/nginx/sites-enabled/default

if ! nginx -t; then
  rm -f /etc/nginx/sites-enabled/luminous
  cp -a "$backup_dir/default.enabled" /etc/nginx/sites-enabled/default
  echo "配置校验失败，已恢复原站点入口；请修正配置后重试。"
  exit 1
fi
systemctl reload nginx
```

模板使用已有自签名证书，访问 `https://myluminous.xyz` 会提示证书不受信任或名称不匹配；公开上线前应更换为覆盖主域名与 www 的受信任证书并配置自动续期。前端深层路由回退 `index.html`，不存在的静态资源返回 404，API 路径不会被改写成前端页面。入口代理覆盖客户端提交的 `X-Forwarded-For`，后端只信任 Docker 桥接网关。访问日志省略查询字符串和 Referer，避免文件能力令牌被记录。

## 6. 检查运行与手动验收

```bash
curl --fail --silent --show-error -k --resolve myluminous.xyz:443:127.0.0.1 https://myluminous.xyz/ -o /dev/null
curl --fail --silent --show-error -k --resolve myluminous.xyz:443:127.0.0.1 https://myluminous.xyz/api/posts
docker compose -p luminous-cloud --env-file .env.cloud.local -f compose.yml ps
docker stats --no-stream
free -h
vmstat 1 10
docker compose -p luminous-cloud --env-file .env.cloud.local -f compose.yml exec -T redis sh -c 'REDISCLI_AUTH="$REDIS_PASSWORD" redis-cli INFO memory'
```

`-k` 仅用于服务器内部诊断自签名证书，不能把这些请求描述为可信 HTTPS 验证。Swap 有占用并不代表问题；关注 `vmstat` 首次汇总样本之后是否持续换入换出、请求延迟和容器是否因 OOM 重启。确认公网浏览器可访问，再手动检查验证码、注册登录、发帖、投票切换、通知和文件上传读取。云环境不运行 E2E、压测或自动写入测试套件；自动化业务验收仍使用独立开发环境。

## 7. 停止、备份与更新

停止时只停本云项目，保留所有持久卷：

```bash
cd /opt/luminous
docker compose -p luminous-cloud --env-file .env.cloud.local -f compose.yml stop
```

运行中先备份 MySQL，再在一致的停止状态下备份卷。下面数据库备份避免将密码放到宿主机命令行；备份目录必须限制访问。

```bash
cd /opt/luminous
install -d -m 700 backups
umask 077
docker compose -p luminous-cloud --env-file .env.cloud.local -f compose.yml exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysqldump -u root --single-transaction --routines --events --triggers --databases luminous' > "backups/mysql-$(date +%Y%m%d%H%M%S).sql"
```

上传卷、Redis AOF、Broker store、环境文件也需独立备份并验证恢复。数据库迁移可能不可逆，更新失败不能保证仅切回旧 JAR 就能恢复；应保存旧构建产物和迁移前数据库备份。禁止使用 `down -v` 或 Docker 全局清理作为更新步骤。

## 8. 更新产物的磁盘保留规则

每次更新前后都检查磁盘，清理仅在新版本启动、公开只读接口检查和手动验收通过之后进行。更新失败时保留旧版本和诊断材料，不能提前删除回滚产物。

| 对象 | 保留规则 |
| --- | --- |
| 云端后端镜像 | 当前版本及一个最近验证可用的回滚版本；其余历史版本按明确标签清理 |
| 云端旧 JAR、前端备份 | 当前版本及一个对应的回滚版本；版本编号与镜像对应 |
| 云端上传压缩包、临时解压目录 | 校验、部署和验收完成后删除，避免与正式产物重复占用空间 |
| 本地 `.local/cloud` 部署包 | 保留最近两个有效归档；归档内容及校验通过后可删除对应打包目录 |
| 数据库、上传文件、MQ、Redis 持久卷 | 不属于旧包清理范围；数据备份另行制定保留规则，数据库迁移前备份需确认可恢复 |

服务器检查命令只读取统计，不删除数据：

```bash
df -h /
docker system df
du -sh /opt/luminous /var/www/luminous /var/lib/docker
docker image ls luminous-cloud-backend
```

根分区剩余空间低于 5GiB 时暂停发布，先定位占用并清理已确认过期的应用产物；即使高于此值，也需预留本次上传、镜像构建及必要数据备份的峰值空间。镜像共享基础层，不能将各镜像显示的大小直接相加；删除旧 JAR 也不会释放镜像中的 JAR 层。

清理前列出候选路径、镜像标签以及正在运行容器的镜像 ID，保护当前与回滚版本。Docker 镜像仅按已经确认过期的 `luminous-cloud-backend` 标签逐个执行 `docker image rm`，不使用强制删除或全局 `prune`。若移除标签后旧镜像仍有其他引用，需核对引用后再处理，不能将标签删除成功当作实际空间已释放。清理后再次执行上述磁盘检查。

禁止使用 `docker system prune -a --volumes`、`docker volume prune`、`down -v` 或直接删除 Docker 数据目录来处理旧包。当前打包脚本不会自动清理历史归档，发布后的清理需显式执行，不应描述为已实现自动回收。

## 当前验证边界

本地可验证 Compose 展开、脚本语法、后端打包、前端构建和归档内容。每次部署还需要核对服务器认证、镜像拉取、Nginx 站点、证书、网络子网和实际内存。2026-10-07 的首次云部署已完成启动和只读页面检查，具体结果及限制见 [DEPLOYMENT.md](DEPLOYMENT.md)；这些检查不代替负载和故障恢复验收。
