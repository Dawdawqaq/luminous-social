# Luminous - 现代社区探索大厅 (Premium Full-Stack Platform)

> **Luminous** 是一款社区发现与社交分享平台。
> 项目目前已完成 **Java 21 / Spring Boot 3 后端** 与 **React 18 / Vite 前端** 的全链路深度贯通，实现了零门槛的物理磁盘静态图片托管与 Flyway 数据库全自动演进。


---

## 核心 Premium 级技术亮点

### ❶ 三维混合全局大一统检索
*   **智能多维搜索**：顶栏搜索框原生打通了“帖子 (Posts)”、“社区 (Communities)”与“用户 (Users)”的三路并发混合检索。
*   **ID 精确探测 + 模糊匹配合一**：后端注入了智能转换模块。用户输入名称时进行 `like` 模糊查询；若输入可解析为 Long 的数值，自动激活对主键 ID 的精确等值（`equals`）校验。

### ❷ 拖动人机校验 & 实时格式防线
*   **流光滑动人机校验 (Slider CAPTCHA)**：前端完全基于 React state 与 Vanilla CSS 纯手工打磨了高保真、零依赖的拖动滑块校验。滑块滑过 90% 的瞬间触发彩虹渐变波纹动效并解锁提交，从物理层彻底拦截黑客暴力破译与自动化刷单！
*   **用户名失焦防抖去重**：用户名输入框强力注入正则表达式校验气泡，并在失去焦点（Blur）时无感防抖异步探测 `/api/users/{username}` 接口，秒级反馈 ID 可用性或占用警告。
*   **密码彩虹强度槽**：三阶（红、橙、绿）发光色彩条实时探测密码复杂度，保障用户账户安全。

### ❸ 解耦式高并发物理文件托管
*   **零侵入图片存储**：上传的头像、背景与插图完全写入本地磁盘物理目录（如 `./uploads`），避开庞大的 BLOB 二进制存储，保障关系型数据库性能处于巅峰状态。
*   **Tomcat 静态资源虚拟映射**：后端在 `WebConfig` 中配置了物理路径资源映射规则，浏览器直接通过轻量级 GET 静态流请求，无缝支持未来直接平滑迁移到 CDN 或者是阿里云 OSS / 腾讯云 COS / AWS S3。

---

## 全栈硬核技术栈

| 层次 | 技术选型 | 版本及细节 |
| :--- | :--- | :--- |
| **前端核心** | **React 18** + **Vite** + **TypeScript** | 极致顺滑的单页应用构建体验 |
| **前端美学** | **Tailwind CSS** + **Motion (Framer Motion)** | 精准定制 of HSL 柔和色板与呼吸动效 |
| **后端核心** | **Java 21** + **Spring Boot 3.5.0** | 现代化 JDK 特性与最新 Spring Boot 容器 |
| **数据持久** | **MyBatis-Plus** + **MySQL (8.0+)** | 工业级、高性能的开源关系型数据库 |
| **自动迁移** | **Flyway Migration** | 启动时全自动执行 SQL 演进，预置高质量社交种子数据 |
| **身份防线** | **Spring Security** + **JWT 无状态令牌** | 安全可靠的跨域会话管理与端点放行守卫 |

---

<<<<<<< HEAD
##  快速本地从 0 搭建与拉起指南 

>  **项目采用全自动 Flyway 迁移架构，别的 PC 从零搭建时，完全不需要您手动运行繁重的 SQL 脚本来建表！您只需在本地创建一个空的 MySQL 数据库并配置好连接，后端拉起时会自动接管一切建表与初始数据**
=======
##  快速本地从 0 搭建与拉起指南 (协作 PC 必读)

>  **项目采用全自动 Flyway 迁移架构，别的 PC 从零搭建时，完全不需要您手动运行繁重的 SQL 脚本来建表！您只需在本地创建一个空的 MySQL 数据库并配置好连接，后端拉起时会自动接管一切建表与初始数据灌入！**
>>>>>>> ce170dc5db2402fc0372571c76795905e0ba0e87

###  前提条件
在开始搭建前，请确保您的 PC 环境已配妥以下基础链：
*   **JDK 21** (已配置环境变量 `JAVA_HOME`)
*   **Maven 3.9.x+**
*   **Node.js 18+** & **npm**
*   **MySQL 8.0+** (本地或局域网运行中)

---

###  第一步：初始化 MySQL 数据库与配置

1.  **在您的本地 MySQL 中，创建一个干净的空数据库**：
    您可以使用命令行、Navicat 或 DataGrip 连上您的本地 MySQL，然后执行以下 SQL：
    ```sql
    CREATE DATABASE luminous CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
    ```
    *(注：只需建一个空库即可，千万不要手动导入任何 SQL 文件！)*

2.  **配置数据库连接指向**：
    后端的 `application.yml` 默认数据库连接地址设置为 `${DB_HOST:192.168.132.130}`，默认账号密码为 `root` / `root`。
    **如果您的本地 MySQL 运行在 `localhost` 上**，您可以选择以下任一方式让后端顺畅连上本地库：
<<<<<<< HEAD
    
    *   **方式-直接改配置**：打开 [backend/src/main/resources/application.yml](file:///d:/javaProjects/luminous/backend/src/main/resources/application.yml#L7-L14) 文件，直接将默认 the IP `192.168.132.130` 修改为您的本地 IP（如 `localhost` 或 `127.0.0.1`），并将 username 和 password 修正为您的本地 root 账号及密码。
=======
    *   **方式 A (零代码侵入 - 用方式B)**：在您的 PC 上配置系统环境变量（或在 IDEA 启动配置中增加 Environment Variables）：
        ```bash
        DB_HOST=localhost
        DB_PORT=3306
        DB_NAME=luminous
        ```
    *   **方式 B (直接改配置)**：打开 [backend/src/main/resources/application.yml](file:///d:/javaProjects/luminous/backend/src/main/resources/application.yml#L7-L14) 文件，直接将默认 the IP `192.168.132.130` 修改为您的本地 IP（如 `localhost` 或 `127.0.0.1`），并将 username 和 password 修正为您的本地 root 账号及密码。
>>>>>>> ce170dc5db2402fc0372571c76795905e0ba0e87

---

###  第二步：编译并启动后端服务 (Backend)

1.  **进入后端子项目目录**：
    ```bash
    cd backend
    ```
2.  **执行 Maven 清理与编译构建**：
    ```bash
    mvn clean install -DskipTests
    ```
3.  **运行 Spring Boot 应用程序**：
    在开发工具中运行 `com.luminous.api.LuminousApiApplication.java` 的 `main` 方法，或者在终端通过 Maven 指令直接运行：
    ```bash
    mvn spring-boot:run
    ```
    *   **启动成功标志**：终端打印 `Tomcat started on port 8080 (http) with context path '/'`
    *   **Flyway 自动演进完毕**：在启动瞬间，后端检测到您的本地 MySQL 数据库为空，会自动扫描并执行 `db/migration` 下的所有脚本。这将全自动建立数据库表结构，并预设 3 名初始测试用户以及 Pixel 8 吧、Futurism 探索者吧两大种子社区！

---

###  第三步：拉起前端页面 (Frontend)

1.  **打开全新的终端，进入前端子项目目录**：
    ```bash
    cd frontend
    ```
2.  **安装项目依赖**：
    ```bash
    npm install
    ```
3.  **拉起本地开发 Vite 服务器**：
    ```bash
    npm run dev
    ```
4.  **访问平台**：
    在浏览器中输入 **`http://localhost:3000`**

---

##  默认测试账号 (Default Seed Users)

启动项目后，您可以直接使用以下预设的测试用户登录体验，也可自行注册检测功能：

*   **测试账户 A**：
    *   **用户名 (ID)**: `felix_the_maker` (显示名称: 飞力创作)
    *   **登录密码**: `password`
*   **测试账户 B**：
    *   **用户名 (ID)**: `DesignSeeker` (显示名称: Design Seeker)
    *   **登录密码**: `password`

---

##  GitHub 仓库提交与推送速查

如果您是本项目的合作开发者，将本地修改推送至 GitHub 仓库的方法如下：

1.  **初始化本地 Git 仓库**（若尚未初始化）：
    ```bash
    git init
    ```
2.  **关联远程 GitHub 仓库**：
    ```bash
    git remote add origin https://github.com/Dawdawqaq/Luminous-byzx.git
    ```
3.  **暂存并提交修改**：
    ```bash
    git add .
    git commit -m "feat: 极致重塑全局用户搜索大厅与拖动验证码防线"
    ```
4.  **推送至主分支**：
    ```bash
    git push -u origin main
    ```
