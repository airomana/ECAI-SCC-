# 下载whisper.cpp源码的脚本

$whisperDir = "app\src\main\cpp\whisper.cpp"
$whisperUrl = "https://github.com/ggerganov/whisper.cpp.git"

Write-Host "正在下载whisper.cpp源码..." -ForegroundColor Green
Write-Host "目标目录: $whisperDir" -ForegroundColor Yellow

# 检查是否已存在
if (Test-Path $whisperDir) {
    Write-Host "whisper.cpp已存在，跳过下载" -ForegroundColor Yellow
    Write-Host "如果要从头开始，请先删除目录: $whisperDir" -ForegroundColor Yellow
    exit 0
}

# 检查git是否可用
try {
    $gitVersion = git --version
    Write-Host "Git可用: $gitVersion" -ForegroundColor Green
    
    # 使用git clone
    Write-Host "使用git clone下载whisper.cpp..." -ForegroundColor Yellow
    git clone $whisperUrl $whisperDir
    
    if (Test-Path $whisperDir) {
        Write-Host "下载成功！" -ForegroundColor Green
        Write-Host "whisper.cpp源码已下载到: $whisperDir" -ForegroundColor Green
    } else {
        Write-Host "下载失败，请手动下载：" -ForegroundColor Red
        Write-Host "1. 访问: https://github.com/ggerganov/whisper.cpp" -ForegroundColor Yellow
        Write-Host "2. 下载ZIP并解压到: $whisperDir" -ForegroundColor Yellow
    }
} catch {
    Write-Host "Git不可用，请手动下载：" -ForegroundColor Red
    Write-Host "1. 访问: https://github.com/ggerganov/whisper.cpp" -ForegroundColor Yellow
    Write-Host "2. 点击'Code' -> 'Download ZIP'" -ForegroundColor Yellow
    Write-Host "3. 解压到: $whisperDir" -ForegroundColor Yellow
}
