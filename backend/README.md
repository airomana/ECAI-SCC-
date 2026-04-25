# ElderCare 后端服务

## 环境要求
- JDK 17+
- MySQL 8.0+
- Redis 6+
- Maven 3.8+

## 快速启动

### 1. 创建数据库
```sql
CREATE DATABASE eldercare CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

### 2. 修改配置
编辑 `src/main/resources/application.yml`：
```yaml
spring:
  datasource:
    password: your_mysql_password   # 改为你的 MySQL 密码
llm:
  dashscope:
    api-key: sk-xxxx                # 改为你的 DashScope API Key
```

### 3. 编译运行
```bash
cd backend
mvn spring-boot:run
```
服务启动在 `http://localhost:8080`

### 4. 修改 Android 端服务器地址
编辑 `ElderCareAI/app/src/main/java/com/eldercare/ai/network/ApiClient.kt`：
```kotlin
const val BASE_URL = "http://your-server-ip:8080/"
```

---

## API 接口列表

### 认证（无需 Token）
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/auth/send-code | 发送验证码 |
| POST | /api/auth/register | 注册 |
| POST | /api/auth/login | 登录 |

### 用户（需要 Token）
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/user/me | 获取当前用户信息 |
| PATCH | /api/user/nickname | 更新昵称 |

### 家庭绑定（需要 Token）
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/family/link | 子女端发起绑定申请 |
| GET | /api/family/requests | 父母端查看待处理申请 |
| POST | /api/family/requests/{id}/handle | 父母端审批申请 |
| GET | /api/family/members | 获取家庭成员 |
| GET | /api/family/my-requests | 子女端查询自己的申请状态 |

### 日记同步（需要 Token）
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/diary/sync | 父母端上传日记 |
| POST | /api/diary/emotion-log | 父母端上传情绪日志 |
| GET | /api/diary/parent | 子女端获取父母日记 |
| GET | /api/diary/parent/emotion-logs | 子女端获取父母情绪日志 |

### LLM 代理（需要 Token）
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/llm/text | 文本生成（对话/菜品/周报） |
| POST | /api/llm/multimodal | 多模态（图片识别食材） |

---

## 测试模式
`application.yml` 中 `sms.test-mode: true` 时，验证码不发真实短信，
而是打印到日志并通过 `/api/auth/send-code` 接口的 `data.debugCode` 字段返回给 App。
