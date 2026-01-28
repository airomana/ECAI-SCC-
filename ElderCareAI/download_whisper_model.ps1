# Whisper模型文件下载脚本
# 使用方法：在PowerShell中运行 .\download_whisper_model.ps1

$modelUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny-q8_0.bin"
$outputDir = "app\src\main\assets\whisper"
$outputFile = "$outputDir\ggml-base-q8_0.bin"

Write-Host "正在下载Whisper模型文件..." -ForegroundColor Green
Write-Host "URL: $modelUrl" -ForegroundColor Yellow
Write-Host "保存到: $outputFile" -ForegroundColor Yellow

# 创建目录
if (-not (Test-Path $outputDir)) {
    New-Item -ItemType Directory -Force -Path $outputDir | Out-Null
    Write-Host "已创建目录: $outputDir" -ForegroundColor Green
}

# 下载文件
try {
    $ProgressPreference = 'SilentlyContinue'
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    
    Write-Host "开始下载（约42MB），请稍候..." -ForegroundColor Yellow
    
    $client = New-Object System.Net.WebClient
    $client.DownloadFile($modelUrl, $outputFile)
    
    $fileSize = (Get-Item $outputFile).Length / 1MB
    Write-Host "下载完成！文件大小: $([math]::Round($fileSize, 2)) MB" -ForegroundColor Green
    Write-Host "文件已保存到: $outputFile" -ForegroundColor Green
    
} catch {
    Write-Host "下载失败: $($_.Exception.Message)" -ForegroundColor Red
    Write-Host "请手动下载模型文件：" -ForegroundColor Yellow
    Write-Host "1. 访问: https://huggingface.co/ggerganov/whisper.cpp/tree/main" -ForegroundColor Yellow
    Write-Host "2. 下载: ggml-tiny-q8_0.bin (约42MB)" -ForegroundColor Yellow
    Write-Host "3. 重命名为: ggml-base-q8_0.bin" -ForegroundColor Yellow
    Write-Host "4. 放置到: $outputDir\" -ForegroundColor Yellow
    exit 1
}

Write-Host "`n完成！现在可以编译项目了。" -ForegroundColor Green
