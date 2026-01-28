# 重新安装应用的PowerShell脚本
# 用于解决签名不匹配或安装失败的问题

Write-Host "🔧 重新安装银发智膳助手..." -ForegroundColor Cyan

# 检查ADB是否可用
$adbCheck = adb version 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Host "❌ 错误: 未找到 adb 命令，请确保 Android SDK Platform Tools 已添加到 PATH" -ForegroundColor Red
    exit 1
}

# 检查设备连接
Write-Host "📱 检查设备连接..." -ForegroundColor Yellow
$devices = adb devices | Select-String "device$"
if (-not $devices) {
    Write-Host "❌ 错误: 未检测到已连接的设备" -ForegroundColor Red
    Write-Host "请确保:" -ForegroundColor Yellow
    Write-Host "   1. 手机已通过USB连接到电脑" -ForegroundColor Yellow
    Write-Host "   2. 已启用USB调试" -ForegroundColor Yellow
    Write-Host "   3. 已授权此电脑进行USB调试" -ForegroundColor Yellow
    exit 1
}

Write-Host "✅ 检测到设备" -ForegroundColor Green

# 卸载现有应用
Write-Host "🗑️  卸载现有应用（如果存在）..." -ForegroundColor Yellow
adb uninstall com.eldercare.ai 2>&1 | Out-Null
if ($LASTEXITCODE -eq 0) {
    Write-Host "✅ 已卸载旧版本" -ForegroundColor Green
} else {
    Write-Host "ℹ️  未找到已安装的应用（这是正常的，如果是首次安装）" -ForegroundColor Gray
}

# 清理构建
Write-Host "🧹 清理构建缓存..." -ForegroundColor Yellow
if (Test-Path ".\gradlew.bat") {
    .\gradlew.bat clean
} elseif (Test-Path ".\gradlew") {
    bash .\gradlew clean
} else {
    Write-Host "⚠️  未找到 gradlew，跳过清理步骤" -ForegroundColor Yellow
}

# 查找APK文件
Write-Host "🔍 查找APK文件..." -ForegroundColor Yellow
$apkPath = Get-ChildItem -Path "app\build\outputs\apk\debug" -Filter "*.apk" -ErrorAction SilentlyContinue | 
    Sort-Object LastWriteTime -Descending | 
    Select-Object -First 1

if (-not $apkPath) {
    Write-Host "❌ 未找到APK文件，请先构建项目" -ForegroundColor Red
    Write-Host "运行: .\gradlew.bat assembleDebug" -ForegroundColor Yellow
    exit 1
}

Write-Host "📦 找到APK: $($apkPath.FullName)" -ForegroundColor Green

# 安装应用
Write-Host "📲 正在安装应用..." -ForegroundColor Yellow
adb install "$($apkPath.FullName)"

if ($LASTEXITCODE -eq 0) {
    Write-Host "✅ 安装成功!" -ForegroundColor Green
    Write-Host ""
    Write-Host "💡 提示:" -ForegroundColor Cyan
    Write-Host "   如果应用仍然无法启动，请检查:" -ForegroundColor Yellow
    Write-Host "   1. MIUI安全中心 -> 应用管理 -> 权限 -> 自启动管理" -ForegroundColor Yellow
    Write-Host "   2. 设置 -> 应用 -> 银发智膳助手 -> 权限 -> 授予所有必要权限" -ForegroundColor Yellow
    Write-Host "   3. 开发者选项 -> USB调试（安全设置）" -ForegroundColor Yellow
} else {
    Write-Host "❌ 安装失败!" -ForegroundColor Red
    Write-Host ""
    Write-Host "💡 可能的解决方案:" -ForegroundColor Cyan
    Write-Host "   1. 检查MIUI安全设置，允许安装未知来源应用" -ForegroundColor Yellow
    Write-Host "   2. 在手机上手动卸载应用后重试" -ForegroundColor Yellow
    Write-Host "   3. 检查手机存储空间是否充足" -ForegroundColor Yellow
    Write-Host "   4. 尝试使用: adb install -r -d $($apkPath.FullName)" -ForegroundColor Yellow
}
