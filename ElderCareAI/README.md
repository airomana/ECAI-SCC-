# 智能生活服务工具 · ElderCare AI

> 银发智慧膳食管家 —— 让科技消除代沟，用温度守护健康

面向银发族的智能饮食管理 Android 应用，通过**拍照识菜单**、**冰箱管理**、**语音日记**与**适老化交互**，帮助老年人吃得懂、吃得对，并为子女提供远程关怀视角。

---

## 功能概览

| 功能 | 说明 |
|------|------|
| **智能菜单识别** | 拍菜单 → PaddleOCR 识别菜名 → 本地/云端知识匹配 → 结合健康档案给出「大白话」说明与语音播报 |
| **冰箱智能管理** | 拍冰箱内容 → 识别食材与保质期 → 语音提醒不宜食用项 → 推荐可做的家常菜 |
| **语音情感日记** | 语音记录「今天吃了啥」→ Whisper 转写 → 情感关键词分析 → AI 回复与 TTS 播报，支持周报给子女 |

---

## 技术架构

- **端侧**：PaddleOCR（菜单文字识别）、Whisper（语音转文字）、本地 TTS、Room 数据库、健康规则引擎  
- **云侧**：通义千问（大白话生成/日记回复）、按需调用  
- **应用层**：Kotlin + Jetpack Compose，minSdk 24，targetSdk 34  

详见仓库内 `项目计划书.md`。

---

## 环境要求

- **JDK**：11 或以上  
- **Android Studio**：推荐 Ladybug (2024.2.1) 或更新，或命令行构建  
- **Android SDK**：API 24–34，NDK 27.x（Whisper 原生编译需要）  
- **网络**：构建时拉取依赖；运行时可选配置通义千问 API 以使用云端能力  

---

## 快速开始

### 1. 克隆仓库

```bash
git clone <仓库地址>
cd intelligent-life-service-tool
```

### 2. 下载 PaddleOCR 模型（菜单识别依赖）

模型不随代码提交，需单独下载到应用 assets 目录：

```powershell
cd ElderCareAI
.\download_paddle_ocr_models.ps1
```

脚本会将 `det`/`rec`/`cls` 模型及 `ppocr_keys_v1.txt` 下载并解压到 `app/src/main/assets/paddle_ocr/`。  
若在非 Windows 环境，请参考 `ElderCareAI/app/src/main/assets/paddle_ocr/README.txt` 手动下载并放置同名文件。

### 3. 构建与安装

**命令行：**

```bash
cd ElderCareAI
./gradlew assembleDebug
# 生成的 APK：app/build/outputs/apk/debug/app-debug.apk
# 安装到已连接设备：./gradlew installDebug
```

**Android Studio：**  
用 Android Studio 打开 `ElderCareAI` 目录，同步 Gradle 后选择 Run。

### 4. 可选：配置云端能力

- 通义千问（大白话/日记回复）：在应用内或配置处填写有效的 API Key；未配置时使用本地模板回复。  
- 其他密钥、环境变量等敏感信息请勿提交到仓库，参见 `.gitignore`。

---

## 项目结构（简要）

```
intelligent-life-service-tool/
├── ElderCareAI/                    # Android 工程
│   ├── app/src/main/
│   │   ├── java/.../               # Kotlin 源码
│   │   │   ├── ui/                 # 界面与导航
│   │   │   ├── data/               # 数据库、DAO、实体
│   │   │   ├── ocr/                # PaddleOCR 封装、菜品匹配
│   │   │   ├── llm/                # 通义千问调用
│   │   │   ├── tts/                # TTS 播报
│   │   │   ├── whisper/            # 语音识别
│   │   │   └── health/             # 健康规则
│   │   ├── assets/
│   │   │   ├── paddle_ocr/         # PaddleOCR 模型（需下载）
│   │   │   └── whisper/            # Whisper 模型
│   │   ├── res/                    # 资源
│   │   └── cpp/                    # 原生代码（如 Whisper）
│   ├── download_paddle_ocr_models.ps1
│   └── build.gradle.kts
├── 方案.md                         # 方案与设计
├── 功能模块详述.md
├── 模块交互图与思维导图.md
├── 代码提交清单.md
└── README.md                       # 本文件
```

---

## 使用说明

1. **拍菜单**：首页进入「拍菜单」→ 对准菜单拍照 → 等待识别与健康匹配 → 听语音说明或点击单道菜查看详情。  
2. **冰箱管理**：从入口进入「拍冰箱」→ 拍摄冰箱内食材 → 查看保质期提醒与推荐食谱。  
3. **语音日记**：进入「今天吃了啥」→ 按住说话记录饮食与心情 → 保存后可听 AI 回复播报。  
4. **设置**：可配置健康档案、紧急联系人等，便于健康匹配与子女关怀。

---

## 开源与许可

- 本项目部分功能依赖 PaddleOCR、Whisper 等开源组件，请遵循各自许可协议。  
- 开源组件与自研部分说明见 `项目计划书-开源与自研说明.md`。

---

## 参与贡献

1. Fork 本仓库  
2. 新建分支（如 `feature/xxx`）  
3. 提交代码并推送到分支  
4. 提交 Pull Request  

如有问题或建议，可通过仓库 Issue 反馈。
