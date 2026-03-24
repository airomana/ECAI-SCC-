# ElderCareAI 后端服务端 (Server)

这是 ElderCareAI 项目的后端服务端代码，基于 **Java 17** 和 **Spring Boot 3.2** 构建。主要负责处理 Android 客户端（父母端与子女端）的数据同步、跨设备家庭账号绑定，以及基于阿里云 DashScope 大模型的长辈健康与情绪周报生成。

## 🛠 技术栈
*   **核心框架**: Spring Boot 3.2.0
*   **构建工具**: Maven
*   **语言**: Java 17
*   **数据持久化**: Spring Data JPA + Hibernate
*   **数据库**: H2 内存数据库（开发与联调环境），可平滑切换至 MySQL
*   **安全与鉴权**: Spring Security (目前已配置为放行测试)
*   **工具库**: Lombok
*   **AI 大模型**: 阿里云 DashScope (通义千问 `qwen-turbo`)

## 📂 核心功能模块

*   **账号与跨设备互联体系 (`AuthController`, `UserService`)**
    *   父母端注册生成全局唯一 `inviteCode` 和 `familyId`。
    *   子女端提交 `inviteCode` 发起绑定申请，存入 `family_link_request`。
    *   父母端审批（同意/拒绝）子女请求，同意后确立父子账号关联。
*   **健康数据与权限同步 (`HealthController`)**
    *   接收父母端上传的健康档案（疾病、过敏史）以及隐私授权开关（共享健康、共享饮食等）。
    *   供子女端拉取已授权的父母健康信息。
*   **日常数据收集 (`DiaryController`, `EmotionController`)**
    *   接收并存储老人的日常语音日记转化文本和情绪打卡记录。
*   **AI 周报生成系统 (`WeeklyReportScheduler`, `LlmService`)**
    *   定时任务每周触发一次，聚合长辈过去一周的日记和情绪记录。
    *   构建 Prompt 调用阿里云大模型，生成针对性的健康与情绪分析周报。
    *   供子女端通过 `/api/report/weekly` 接口拉取展示。

## 🚀 本地开发与打包指南

### 前置要求
*   安装 JDK 17
*   建议使用项目根目录下的 Maven Wrapper 进行构建，避免本地环境差异。

### 编译打包
在 **项目根目录 (`ElderCareAI`)** 下执行以下命令：

**Windows:**
```powershell
.\mvnw.cmd -f server\pom.xml clean package -DskipTests
```

**Linux / macOS:**
```bash
./mvnw -f server/pom.xml clean package -DskipTests
```

构建成功后，可执行的 Jar 包将生成在 `server/target/eldercare-server-1.0.0.jar`。

## ☁️ 云服务器部署指南 (Ubuntu 22.04)

1.  **安装 Java 运行环境:**
    ```bash
    sudo apt update
    sudo apt install openjdk-17-jre-headless -y
    ```
2.  **上传 Jar 包到服务器:**
    在本地终端使用 SCP 命令将打包好的 jar 传至服务器（假设目标为 `/root/`）：
    ```bash
    scp server\target\eldercare-server-1.0.0.jar root@<你的服务器IP>:/root/
    ```
3.  **后台运行服务:**
    为了防止 2H2G 等小规格服务器内存溢出，建议限制 JVM 内存：
    ```bash
    nohup java -Xms512m -Xmx1024m -jar /root/eldercare-server-1.0.0.jar > /root/server.log 2>&1 &
    ```
4.  **查看运行日志:**
    ```bash
    tail -f /root/server.log
    ```
5.  **防火墙配置:**
    确保在云服务商（如腾讯云、阿里云）控制台的安全组中，**放行 TCP 8080 端口**的入站规则。

## ⚙️ 核心配置说明

主要配置文件位于 `src/main/resources/application.properties`：

*   `server.address=0.0.0.0`: 允许外网访问。
*   `server.port=8080`: 服务监听端口。
*   `spring.datasource.url=jdbc:h2:mem:eldercaredb`: 默认使用 H2 内存数据库。
*   `dashscope.api.key`: 阿里云大模型 API 密钥。**（生产环境请替换为真实有效的 Key，或配置为环境变量）**。