# 阿里通义千问 LLM 集成配置指南

> 更新时间：2026-01-26

## 📋 概述

已成功集成阿里云通义千问 LLM API，用于为知识库中没有的菜品生成"大白话"描述。

## 🔧 配置步骤

### 1. 获取阿里云 API 密钥

1. **登录阿里云控制台**
   - 访问：https://dashscope.console.aliyun.com/
   - 使用阿里云账号登录

2. **创建 API Key**
   - 进入"API-KEY管理"页面
   - 点击"创建新的API Key"
   - 复制生成的 API Key（格式类似：`sk-xxxxxxxxxxxxx`）

3. **查看服务开通状态**
   - 确保已开通"通义千问"服务
   - 检查账户余额（按量付费）

### 2. 配置 API 密钥

有两种方式配置 API 密钥：

#### 方式一：代码中直接配置（仅用于测试）

在 `LlmConfig.kt` 中直接设置：

```kotlin
object LlmConfig {
    var API_KEY: String = "sk-your-api-key-here"  // 替换为你的API Key
    // ...
}
```

⚠️ **注意**：这种方式不安全，不要提交到代码仓库！

#### 方式二：通过设置页面配置（推荐）

1. 在应用的设置页面添加"LLM配置"选项
2. 用户输入 API Key 后，调用：

```kotlin
LlmConfig.setApiKey(context, "sk-your-api-key-here")
```

3. API Key 会保存在 `SharedPreferences` 中

### 3. 配置模型（可选）

默认使用 `qwen-turbo`（快速响应），也可以切换到其他模型：

- `qwen-turbo`: 快速响应，适合实时场景（默认）
- `qwen-plus`: 平衡性能和效果
- `qwen-max`: 最强性能，适合复杂任务

在代码中配置：

```kotlin
LlmConfig.setModel(context, "qwen-plus")
```

或在 `LlmConfig.kt` 中直接修改：

```kotlin
var MODEL: String = "qwen-turbo"  // 改为 qwen-plus 或 qwen-max
```

### 4. 初始化配置

在应用启动时（如 `MainActivity` 的 `onCreate`）初始化：

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    
    // 初始化LLM配置
    LlmConfig.initialize(this)
    
    // 如果还没有配置API Key，可以在这里设置
    if (!LlmConfig.isConfigured()) {
        // 提示用户配置API Key
        // 或者从配置文件读取
    }
}
```

## 📝 使用说明

### 自动调用

当用户使用"拍菜单"功能时：

1. **OCR识别** → 提取菜名
2. **查询知识库** → 如果找到，使用知识库的大白话描述
3. **LLM生成** → 如果知识库中没有，自动调用LLM生成大白话描述
4. **降级处理** → 如果LLM调用失败，使用通用描述

### 手动控制

可以通过设置页面控制LLM功能：

```kotlin
// 启用/禁用LLM功能
SettingsManager.getInstance(context).setLlmEnabled(true)

// 检查是否启用
val isEnabled = SettingsManager.getInstance(context).isLlmEnabled()
```

## 🔍 代码结构

```
app/src/main/java/com/eldercare/ai/llm/
├── LlmConfig.kt              # 配置管理
├── LlmService.kt             # LLM服务封装
└── dashscope/
    └── DashScopeApi.kt       # 通义千问API接口定义
```

## 📊 API 请求示例

### 请求格式

```json
{
  "model": "qwen-turbo",
  "input": {
    "messages": [
      {
        "role": "system",
        "content": "你是一个专业的健康饮食助手..."
      },
      {
        "role": "user",
        "content": "请用简单易懂的'大白话'描述这道菜：麻辣香锅..."
      }
    ]
  },
  "parameters": {
    "temperature": 0.7,
    "max_tokens": 500,
    "top_p": 0.8
  }
}
```

### 响应格式

```json
{
  "output": {
    "choices": [
      {
        "message": {
          "role": "assistant",
          "content": "麻辣香锅就是各种蔬菜和肉类一起炒的，很辣很香，胃不好的人要少吃"
        }
      }
    ]
  },
  "usage": {
    "input_tokens": 150,
    "output_tokens": 50,
    "total_tokens": 200
  }
}
```

## ⚙️ 配置参数说明

| 参数 | 说明 | 默认值 |
|------|------|--------|
| `API_KEY` | 阿里云API密钥 | 空（需配置） |
| `API_ENDPOINT` | API端点地址 | `https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation` |
| `MODEL` | 模型名称 | `qwen-turbo` |
| `TIMEOUT_SECONDS` | 请求超时时间 | `30` 秒 |

## 🛡️ 错误处理

### 常见错误

1. **API Key 未配置**
   - 错误：`LLM配置不完整，无法调用API`
   - 解决：配置 API Key

2. **API Key 无效**
   - 错误：`API调用失败: 401`
   - 解决：检查 API Key 是否正确

3. **账户余额不足**
   - 错误：`API调用失败: 402`
   - 解决：充值账户

4. **网络超时**
   - 错误：`生成大白话描述时出错: SocketTimeoutException`
   - 解决：检查网络连接，或增加超时时间

### 降级策略

如果 LLM 调用失败，系统会自动降级：

1. 使用通用描述："从菜单识别到的菜名"
2. 仍然显示健康风险评估结果
3. 不影响其他功能

## 💰 费用说明

### 计费方式

- **按量付费**：根据实际使用的 token 数量计费
- **模型价格**（参考，以官方为准）：
  - `qwen-turbo`: 约 0.008 元/千 tokens
  - `qwen-plus`: 约 0.02 元/千 tokens
  - `qwen-max`: 约 0.12 元/千 tokens

### 预估成本

每次生成大白话描述约消耗：
- 输入 tokens: ~150
- 输出 tokens: ~50
- 总计: ~200 tokens

使用 `qwen-turbo` 模型：
- 单次成本：约 0.0016 元
- 1000 次调用：约 1.6 元

## 🔒 隐私保护

1. **本地优先**：优先使用本地知识库，减少API调用
2. **用户授权**：可通过设置禁用LLM功能
3. **数据安全**：API Key 存储在本地，不传输到第三方
4. **最小化数据**：只发送菜名和必要的健康信息

## 📚 参考文档

- [阿里云通义千问API文档](https://help.aliyun.com/zh/model-studio/developer-reference/api-details-9)
- [DashScope控制台](https://dashscope.console.aliyun.com/)
- [模型列表和价格](https://help.aliyun.com/zh/model-studio/product-overview/model-list)

## ✅ 检查清单

- [ ] 已获取阿里云 API Key
- [ ] 已在代码中配置 API Key（或通过设置页面配置）
- [ ] 已测试 LLM 调用是否正常
- [ ] 已检查账户余额
- [ ] 已了解费用情况
- [ ] 已设置降级策略

## 🚀 下一步

1. 在应用启动时初始化 `LlmConfig`
2. 在设置页面添加 API Key 配置入口
3. 测试 LLM 功能是否正常工作
4. 监控 API 调用量和费用

---

**提示**：首次使用建议先用测试 API Key 验证功能，确认无误后再使用正式 API Key。
