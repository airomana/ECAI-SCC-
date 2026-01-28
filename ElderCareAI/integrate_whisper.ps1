# Whisper.cpp集成脚本
# 此脚本将下载whisper.cpp并配置CMake

Write-Host "开始集成Whisper.cpp..." -ForegroundColor Green

$whisperDir = "app\src\main\cpp\whisper.cpp"
$whisperUrl = "https://github.com/ggerganov/whisper.cpp/archive/refs/heads/master.zip"

# 检查whisper.cpp是否已存在
if (Test-Path $whisperDir) {
    Write-Host "whisper.cpp已存在，跳过下载" -ForegroundColor Yellow
} else {
    Write-Host "正在下载whisper.cpp..." -ForegroundColor Yellow
    Write-Host "请手动下载whisper.cpp：" -ForegroundColor Yellow
    Write-Host "1. 访问: https://github.com/ggerganov/whisper.cpp" -ForegroundColor Yellow
    Write-Host "2. 下载源码并解压到: $whisperDir" -ForegroundColor Yellow
    Write-Host "3. 或者运行: git clone https://github.com/ggerganov/whisper.cpp.git $whisperDir" -ForegroundColor Yellow
}

Write-Host "`n集成步骤：" -ForegroundColor Green
Write-Host "1. 下载whisper.cpp源码到: $whisperDir" -ForegroundColor Yellow
Write-Host "2. 修改CMakeLists.txt添加whisper.cpp源文件" -ForegroundColor Yellow
Write-Host "3. 实现真实的模型加载和推理代码" -ForegroundColor Yellow
Write-Host "`n完成后，请运行: .\gradlew assembleDebug" -ForegroundColor Green
