# Luminous — 现代人文社区探索大厅 (Premium Full-Stack Platform)

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.0-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![React](https://img.shields.io/badge/React-18-blue.svg)](https://reactjs.org/)
[![Vite](https://img.shields.io/badge/Vite-6-purple.svg)](https://vitejs.dev/)
[![MySQL](https://img.shields.io/badge/MySQL-8.4-blue.svg)](https://www.mysql.com/)
[![Redis](https://img.shields.io/badge/Redis-7.4-red.svg)](https://redis.io/)
[![RocketMQ](https://img.shields.io/badge/RocketMQ-5.3-red.svg)](https://rocketmq.apache.org/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

> **Luminous** 是一款高性能、现代化的人文社交发现与互动平台。用户可以在「沙龙 (Salon) / 社区」中创作图文手记、点赞投票、评论互动、收藏归档与每日签到，构建关注关系并接收实时互动通知；管理员可进行全站用户治理。
>
> 项目采用 **Java 21 / Spring Boot 3.5 后端** + **React 18 / Vite 6 / Tailwind CSS 前端**，遵循 **Anti-Overengineering** 原则保持扁平轻量设计，针对高频读写场景落地 **Redis 7.4 Lua 原子写缓冲 + RocketMQ 5.3 异步削峰落库** 与 **Caffeine L1 + Redis L2 多级防穿透读缓存**，支持 Flyway 数据库版本化迁移、双模式存储（本地/MinIO）与标准 Docker 一键容器化编排。

---

## ✨ 核心特性

### 1. ⚡ 高频互动写缓冲架构（Redis Lua + RocketMQ）
*   **原子写缓冲**：点赞 / 投票操作通过 Redis Lua 脚本在内存中执行原子计数与用户状态判定，毫秒级即时响应前端。
*   **异步削峰落库**：高并发点赞事件通过 RocketMQ 推送给下游消费者批量持久化至 MySQL，彻底消除热点帖子并发点赞引发的行锁争抢与死锁。
*   **降级与幂等保障**：消费端基于 Redis `SETNX`（24h TTL）实现严格消费去重；RocketMQ 离线或未启用时，生产者自动平滑降级为本地同步落库；后台定时对账调度器每 15 分钟校验并修复 Redis 与 MySQL 的数据一致性。

### 2. 🛡️ 多级读缓存与三级防护体系（Caffeine + Redis + Redisson）
*   **多级缓存架构**：`PostCacheService` 采用 Caffeine 本地缓存（L1，TTL 30s）+ Redis 分布式缓存（L2，TTL 30m + 随机抖动防雪崩）。
*   **击穿防护**：热点帖子缓存失效时通过 Redisson 分布式互斥锁（`RLock`）限制单线程回源数据库重建缓存，其他并发线程有界自旋等待读取重建成品。
*   **穿透防护**：数据库不存在的空值写入短暂空值缓存（`""`，TTL 2m），阻断恶意 ID 穿透。
*   **强一致性保障**：写操作执行延迟双删（500ms 异步清理）；系统启动时自动预热 Top 100 高赞帖子。

### 3. 📜 内容生态与关注流（Post / Community / Feed）
*   **文体类型支持**：支持选择 `ESSAY`（长篇手记）、`DISCUSSION`（议题讨论）、`FIELD_NOTE`（实地见闻）进行创作，内置原生安全 Markdown 排版与阅读时长预估。
*   **关注流 (Feed 流)**：推拉结合设计，用户发帖通过 Redis Pipeline 批量推入粉丝收件箱（ZSet），大 V 账号自动采用拉模式；前端基于 Redis SortedSet Score 进行游标滚动分页拉取，消除 N+1 查询。
*   **轻量全文检索**：基于 MySQL `ngram` 全文倒排索引对帖子标题、正文及创作者昵称进行毫秒级全文检索。
*   **独立访客统计 (UV)**：基于 Redis HyperLogLog（`PFADD` / `PFCOUNT`）以极小内存开销精准统计帖子独立访客量。
*   **评论与精选边注**：多级评论发表、点赞、删除、`@` 快捷回复；帖子详情页提取 Top 3 高赞评论作为古典边注（Marginalia）锚定展示。
*   **社区沙龙与 Bitmap 签到**：社区创建、加入/退出、信息与守则配置；基于 Redis Bitmap（`setBit` / `bitCount` / `bitField`）实现每日打卡与月度日历热力图统计。

### 4. 🔐 用户体系与安全治理
*   **身份认证**：JWT 无状态令牌签发与校验，密码采用 BCrypt 强哈希加密。
*   **安全防线**：原生滑动验证码（`SliderCaptcha`，零外部依赖）+ Redis ZSet 滑动时间窗口限流器（对登录/注册 IP 实施 429 拦截）。
*   **实时封禁**：管理员一键封禁违规用户，Redis 黑名单在拦截器毫秒级阻断已签发 JWT。
*   **接口幂等防重**：前端在写操作自动生成 `X-Idempotency-Key`，后端拦截器基于 Redis `SETNX` 拦截重复提交。
*   **文件安全与双模存储**：Apache Tika 文件头魔数校验阻断伪造后缀；统一抽象 `StorageProvider` 支持本地磁盘与 MinIO / S3 云端存储一键切换。
*   **雪花算法唯一 ID**：自研分布式 64 位 `SnowflakeIdGenerator`，具备时钟回拨容错自旋保护。

---

## 🛠️ 技术栈总览

| 层次 | 技术选型 | 版本 | 作用说明 |
| :--- | :--- | :--- | :--- |
| **后端语言** | Java | 21 (LTS) | 虚拟线程就绪、强类型 Record 数据载体 |
| **基础框架** | Spring Boot | 3.5.0 | 核心后端框架与自动装配 |
| **ORM / 数据库**| MyBatis-Plus / MySQL | 3.5.12 / 8.4 | 数据库访问与高性能事务持久层 |
| **数据迁移** | Flyway | 10.x | 数据库表结构与种子数据版本化自动演进 |
| **分布式缓存** | Redis / Redisson / Caffeine | 7.4 / 3.45.0 / 3.1.8 | 多级读缓存、Lua 写缓冲、分布式互斥锁、Bitmap 签到 |
| **消息队列** | Apache RocketMQ | 5.3.1 | 异步削峰、批量写落库、事件解耦 |
| **对象存储** | AWS S3 SDK / 本地文件 | 2.29.50 | MinIO / 本地磁盘双模静态资源存储 |
| **安全与认证** | Spring Security / JJWT | 6.x / 0.12.6 | JWT 认证授权、BCrypt 加密、IP 防刷限流 |
| **前端框架** | React / TypeScript | 18.3.1 / 5.7.3 | 声明式组件化与类型安全 |
| **构建工具** | Vite | 6.1.0 | 极速前端热重载与模块打包 |
| **样式与图标** | Tailwind CSS / Lucide React | 3.4.17 / 0.475.0 | 人文雅致设计系统与图标库 |
| **反向代理** | Nginx | 1.27 Alpine | 动静分离、Gzip 压缩、后端集群负载均衡 |

---

## 🚀 快速开始与部署指南

### 方式 1：使用 Docker Compose 一键启动（推荐）

1. **克隆项目并配置环境变量**
   ```bash
   git clone https://github.com/your-username/luminous.git
   cd luminous
   cp .env.example .env
   ```

2. **一键启动全套服务**（MySQL、Redis、RocketMQ、MinIO、后端、前端）
   ```bash
   docker compose up -d
   ```

3. **访问应用**
   - 前端 Web 页面：[http://localhost:3000](http://localhost:3000)
   - 后端 API 地址：[http://localhost:8080](http://localhost:8080)
   - MinIO 控制台：[http://localhost:9001](http://localhost:9001)（默认账号：`luminous_admin` / `luminous_secret_password`）

---

### 方式 2：本地源码开发调试

#### 前置依赖
- JDK 21+
- Node.js 18+ & npm
- Maven 3.9+
- MySQL 8.4 & Redis 7.4 实例运行中

#### 1. 后端启动
```bash
cd backend
# 确保 application.yml 中数据库与 Redis 连接配置正确
mvn spring-boot:run
```
> Flyway 会在应用启动时自动检测并执行 `V1 ~ V9` 的 SQL 脚本完成建表与默认数据初始化。

#### 2. 前端启动
```bash
cd frontend
npm install
npm run dev
```
浏览器打开 [http://localhost:3000](http://localhost:3000) 即可开始体验。

---

### 方式 3：生产集群模拟部署（Nginx 负载均衡 + 后端多节点）

```bash
# 启动生产集群模式 (Nginx 80 网关 + 后端双节点负载均衡集群)
docker compose -f docker-compose.prod.yml --env-file .env up -d

# 访问标准生产地址:
http://localhost
```

---

## 📂 项目目录结构

```text
luminous/
├── .env.example                    # [配置] 环境变量配置模板
├── docker-compose.yml              # [编排] 标准容器编排定义
├── docker-compose.prod.yml         # [编排] 生产负载均衡集群编排定义
├── broker.conf                     # [配置] RocketMQ Broker 存储与网络配置
├── LICENSE                         # [协议] MIT 开源协议
├── README.md                       # [文档] 项目指南与开发手册
│
├── nginx/
│   └── nginx.prod.conf             # [配置] 生产 Nginx 动静分离与负载均衡配置
│
├── scripts/
│   ├── init-db.ps1 / .sh           # 数据库健康度与 Flyway 就绪探测脚本
│   ├── clean-test-data.ps1 / .sh   # 测试数据精准清理脚本
│   ├── run-e2e.ps1 / .sh           # 自动化 E2E 契约测试脚本
│   ├── start-prod-lab.ps1 / .sh    # 生产集群一键启动脚本
│   ├── stop-prod-lab.ps1 / .sh     # 生产集群安全停止脚本
│   └── run_benchmark.mjs           # 全场景基准性能与压力测试脚本
│
├── backend/                        # Spring Boot 后端
│   ├── Dockerfile                  # 后端多阶段构建 Dockerfile
│   ├── pom.xml                     # Maven 依赖管理
│   └── src/main/java/com/luminous/api/
│       ├── admin/                  # 管理员用户治理模块
│       ├── auth/                   # 认证、用户主页与社交关注模块
│       ├── cache/                  # Caffeine L1 + Redis L2 多级缓存与预热
│       ├── checkin/                # Redis Bitmap 社区每日签到模块
│       ├── common/                 # 全局统一响应、异常处理、雪花 ID 生成器
│       ├── community/              # 社区沙龙 CRUD 与成员管理模块
│       ├── config/                 # Security, Redisson, Storage, Web 等配置
│       ├── feed/                   # 关注动态流推拉结合与滚动分页
│       ├── job/                    # 定时数据对账补偿调度器
│       ├── mq/                     # RocketMQ 生产者/消费者与本地降级逻辑
│       ├── notification/           # 互动通知事件发布与消息中心
│       ├── post/                   # 帖子、评论、点赞、收藏核心业务
│       ├── security/               # JWT 过滤器、Redis IP 限流器、接口幂等拦截器
│       ├── storage/                # 本地磁盘与 MinIO 双模存储实现
│       └── upload/                 # 图片上传与 Tika 魔数探测模块
│
└── frontend/                       # React 18 + TypeScript + Vite 前端
    ├── Dockerfile                  # 前端 Nginx 容器化构建
    ├── package.json                # 前端依赖配置
    ├── tailwind.config.js          # Tailwind CSS 人文雅致设计系统
    └── src/
        ├── api/client.ts           # 统一 API 客户端 (自动 JWT 与幂等头注入)
        ├── components/             # 认证、社区、帖子、通知、搜索及基础 UI 组件
        ├── context/                # AuthContext, ThemeContext, ToastContext
        ├── pages/                  # 首页大厅、帖子详情、社区列表、社区详情、个人主页等
        └── types/                  # 全局 TypeScript 契约模型
```

---

## ⚙️ 环境变量配置说明

复制 `.env.example` 为 `.env` 即可完成默认配置。主要环境变量如下：

| 环境变量 | 默认值 | 作用说明 |
| :--- | :--- | :--- |
| `BACKEND_PORT` | `8080` | 后端服务对外暴露端口 |
| `FRONTEND_PORT` | `3000` | 前端服务对外暴露端口 |
| `MYSQL_PORT` | `3306` | MySQL 数据库端口 |
| `DB_NAME` | `luminous` | 数据库名 |
| `DB_USER` / `DB_PASSWORD` | `luminous` / `luminous_password` | 数据库连接凭据 |
| `REDIS_PORT` | `6379` | Redis 缓存端口 |
| `ROCKETMQ_ENABLED` | `true` | 是否启用 RocketMQ 异步削峰（设为 `false` 自动降级本地同步） |
| `STORAGE_TYPE` | `local` | 存储模式（`local` 本地磁盘 / `minio` 对象存储） |
| `SPRING_PROFILES_ACTIVE` | `prod` | Spring 运行 Profile |

---

## 📄 开源许可证

本项目采用 [MIT License](LICENSE) 许可证开源，欢迎自由使用、修改与分发。

