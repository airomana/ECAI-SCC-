# ElderCare AI - Java 服务端周报推送功能技术文档

## 1. 背景与目标
为了实现“服务端生成并主动推送周报”的功能，需要将现有的客户端本地数据（情绪日志、饮食日记等）上报至云端，并利用服务端的定时任务聚合近7天数据，调用大模型（DashScope）生成周报，最后通过消息推送服务将周报发送给关联的子女端。

## 2. 核心架构与功能模块新增

### 2.1 数据库实体扩展 (Entity)
为支持服务端收集数据与周报持久化，新增了以下实体类和数据库表映射：
- **`EmotionLog`**: 记录老人的情绪日志。包含 `userId`, `emotion` (情绪状态), `note` (备注), `timestamp` (记录时间)。
- **`WeeklyReport`**: 记录生成的周报。包含 `parentId` (老人ID), `childId` (子女ID), `reportContent` (大模型生成的报告内容), `generatedAt` (生成时间)。

### 2.2 数据访问与接口 (Repository & Controller)
- **`EmotionLogRepository`**: 提供查询老人近期情绪记录的接口 (`findByUserIdAndTimestampAfter`)。
- **`WeeklyReportRepository`**: 提供查询子女周报列表的接口 (`findByChildIdOrderByGeneratedAtDesc`)。
- **`EmotionController`**: `POST /api/emotion` 供客户端同步上传情绪记录；`GET /api/emotion` 供子女拉取记录。
- **`ReportController`**: `GET /api/report/weekly` 供子女端查询历史周报列表。

### 2.3 大模型对接 (LlmService)
- 新增 `LlmService.java` 服务类，使用 `RestTemplate` 封装对阿里云通义千问 (DashScope) `qwen-turbo` 模型的 HTTP POST 请求。
- 通过传入聚合的 prompt（包含近7天的饮食和情绪记录），模型返回温暖、关怀的健康情绪周报。
- 需在 `application.properties` 中配置 `dashscope.api.key`。

### 2.4 消息推送服务 (PushNotificationService)
- 新增 `PushNotificationService.java` 作为推送服务的接口抽象（当前为 Mock 实现）。
- 生产环境需对接极光推送、FCM 等三方推送平台。根据 `userId` 找到目标设备 Token，发起离线推送。

### 2.5 定时任务与调度 (WeeklyReportScheduler)
- 新增 `WeeklyReportScheduler.java` 服务类。
- 在 `ElderCareServerApplication` 启动类中加上了 `@EnableScheduling` 注解。
- 核心方法 `generateAndPushWeeklyReports()` 使用 `@Scheduled(cron = "0 0 20 * * SUN")` 定时在**每周日晚 20:00** 触发。
- **调度逻辑**：
  1. 遍历 `FamilyRelation` 中所有的家庭绑定关系。
  2. 获取每位老人 (`parentId`) 近7天的 `DiaryEntry` 和 `EmotionLog` 数据。
  3. 拼接 Prompt，调用 `LlmService` 生成报告。
  4. 将生成的周报存入 `WeeklyReport`。
  5. 调用 `PushNotificationService` 发送推送通知给对应子女 (`childId`)。

## 3. 服务端项目目录结构变化

```text
server/src/main/java/com/eldercare/server/
├── ElderCareServerApplication.java   # 新增 @EnableScheduling
├── controller/
│   ├── AuthController.java
│   ├── DiaryController.java
│   ├── EmotionController.java        # [新增] 情绪数据同步接口
│   ├── FridgeController.java
│   ├── HealthController.java
│   └── ReportController.java         # [新增] 周报查询接口
├── entity/
│   ├── DiaryEntry.java
│   ├── EmotionLog.java               # [新增] 情绪实体
│   ├── FamilyRelation.java
│   ├── FridgeItem.java
│   ├── HealthProfile.java
│   ├── User.java
│   └── WeeklyReport.java             # [新增] 周报实体
├── repository/
│   ├── EmotionLogRepository.java     # [新增]
│   ├── WeeklyReportRepository.java   # [新增]
│   └── ... (其他仓库)
└── service/
    ├── DiaryService.java
    ├── EmotionLogService.java        # [新增] 情绪数据服务
    ├── LlmService.java               # [新增] DashScope 大模型调用服务
    ├── PushNotificationService.java  # [新增] 消息推送服务(Mock)
    ├── WeeklyReportScheduler.java    # [新增] 周报定时生成与推送调度器
    ├── WeeklyReportService.java      # [新增] 周报数据服务
    └── ... (其他服务)
```

## 4. 后续协同开发建议

1. **客户端网络层改造**：
   - 客户端目前尚未实现 Retrofit/OkHttp 的请求封装，需要开发人员在 Android 端实现统一的 `SyncManager`，将本地 Room 数据库的 `EmotionLogEntity` 与 `DiaryEntryEntity` 同步调用服务端的 `/api/emotion` 和 `/api/diary`。
2. **大模型 API Key 配置**：
   - 在服务端 `src/main/resources/application.properties` (或 application.yml) 中配置真实有效的 DashScope API Key: 
     `dashscope.api.key=sk-xxxxxxx`。
3. **真实推送渠道接入**：
   - 客户端需集成第三方 Push SDK 获取 DeviceToken。
   - 服务端扩展 `User` 表，增加 `deviceToken` 字段；`PushNotificationService` 需替换为调用第三方推送 API 的真实代码。
