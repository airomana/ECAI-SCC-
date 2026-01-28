# 下载 ggml-tiny-q8_0.bin 模型

## 📥 下载步骤

### 方式一：使用浏览器下载（推荐）

1. **访问 Hugging Face**：
   - 访问：https://huggingface.co/ggerganov/whisper.cpp
   - 或者直接下载：https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny-q8_0.bin

2. **下载文件**：
   - 点击 "ggml-tiny-q8_0.bin" 文件
   - 点击 "Download" 按钮
   - 文件大小约：**75 MB**（比 base 模型的 142 MB 小很多）

3. **放置文件**：
   - 将下载的 `ggml-tiny-q8_0.bin` 文件移动到：
   - `ElderCareAI/app/src/main/assets/whisper/ggml-tiny-q8_0.bin`

### 方式二：使用 PowerShell 下载

```powershell
cd ElderCareAI\app\src\main\assets\whisper

# 下载 tiny 模型
$url = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny-q8_0.bin"
$output = "ggml-tiny-q8_0.bin"

Invoke-WebRequest -Uri $url -OutFile $output

Write-Host "模型下载完成！文件大小：" -ForegroundColor Green
Get-Item $output | Select-Object Name, @{Name="Size(MB)";Expression={[math]::Round($_.Length/1MB, 2)}}
```

## ✅ 验证

下载完成后，检查文件是否存在：

```powershell
Test-Path "ElderCareAI\app\src\main\assets\whisper\ggml-tiny-q8_0.bin"
# 应该返回 True
```

## 🚀 使用

下载完成后，重新编译并安装：

```powershell
.\gradlew assembleDebug
```

安装到手机后，应用会自动使用 `ggml-tiny-q8_0.bin` 模型。

## 📊 模型对比

| 模型 | 大小 | 速度 | 准确度 |
|------|------|------|--------|
| **tiny** | 75 MB | ⚡⚡⚡ 最快 | ⭐⭐ 较低 |
| base | 142 MB | ⚡⚡ 较快 | ⭐⭐⭐ 中等 |
| small | 466 MB | ⚡ 较慢 | ⭐⭐⭐⭐ 较高 |

**tiny 模型特点**：
- ✅ 识别速度快（通常比 base 快 2-3 倍）
- ✅ 内存占用小
- ⚠️ 准确度略低于 base 模型
- ✅ 适合实时语音识别场景

## 💡 提示

如果 tiny 模型的准确度不够，可以：
1. 继续使用 base 模型（已下载）
2. 或者尝试 `ggml-base.en-q8_0.bin`（仅英文，但更快）
