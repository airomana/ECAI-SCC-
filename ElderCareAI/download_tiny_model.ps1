# 下载 ggml-tiny-q8_0.bin 模型

$modelDir = "app\src\main\assets\whisper"
$modelFile = "$modelDir\ggml-tiny-q8_0.bin"
$modelUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny-q8_0.bin"

Write-Host "正在下载 ggml-tiny-q8_0.bin 模型..." -ForegroundColor Green
Write-Host "目标位置: $modelFile" -ForegroundColor Yellow

# 检查目录是否存在
if (-not (Test-Path $modelDir)) {
    New-Item -ItemType Directory -Path $modelDir -Force | Out-Null
    Write-Host "创建目录: $modelDir" -ForegroundColor Yellow
}

# 检查文件是否已存在
if (Test-Path $modelFile) {
    $fileSize = (Get-Item $modelFile).Length / 1MB
    Write-Host "模型文件已存在: $modelFile" -ForegroundColor Yellow
    Write-Host "文件大小: $([math]::Round($fileSize, 2)) MB" -ForegroundColor Yellow
    $overwrite = Read-Host "是否重新下载？(y/n)"
    if ($overwrite -ne "y") {
        Write-Host "跳过下载" -ForegroundColor Green
        exit 0
    }
}

# 下载文件
try {
    Write-Host "开始下载（约75MB）..." -ForegroundColor Yellow
    Invoke-WebRequest -Uri $modelUrl -OutFile $modelFile -UseBasicParsing
    
    if (Test-Path $modelFile) {
        $fileSize = (Get-Item $modelFile).Length / 1MB
        Write-Host "`n下载成功！" -ForegroundColor Green
        Write-Host "文件位置: $modelFile" -ForegroundColor Green
        Write-Host "文件大小: $([math]::Round($fileSize, 2)) MB" -ForegroundColor Green
        Write-Host "`n现在可以重新编译应用了：" -ForegroundColor Yellow
        Write-Host ".\gradlew assembleDebug" -ForegroundColor Cyan
    } else {
        Write-Host "下载失败，文件不存在" -ForegroundColor Red
    }
} catch {
    Write-Host "下载失败: $_" -ForegroundColor Red
    Write-Host "`n请手动下载：" -ForegroundColor Yellow
    Write-Host "1. 访问: https://huggingface.co/ggerganov/whisper.cpp" -ForegroundColor Yellow
    Write-Host "2. 下载 ggml-tiny-q8_0.bin" -ForegroundColor Yellow
    Write-Host "3. 放置到: $modelFile" -ForegroundColor Yellow
}
