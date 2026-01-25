@echo off
echo 正在查找Android SDK路径...
echo.

REM 检查环境变量ANDROID_HOME
if defined ANDROID_HOME (
    echo 找到环境变量 ANDROID_HOME: %ANDROID_HOME%
    if exist "%ANDROID_HOME%\platform-tools\adb.exe" (
        echo ✅ ANDROID_HOME 路径有效
        echo sdk.dir=%ANDROID_HOME:\=\\% > local.properties
        echo 已更新 local.properties 文件
        goto :end
    ) else (
        echo ❌ ANDROID_HOME 路径无效，继续查找...
    )
)

REM 检查常见的Android SDK安装路径
set "COMMON_PATHS[0]=%USERPROFILE%\AppData\Local\Android\Sdk"
set "COMMON_PATHS[1]=%LOCALAPPDATA%\Android\Sdk"
set "COMMON_PATHS[2]=C:\Android\Sdk"
set "COMMON_PATHS[3]=%PROGRAMFILES%\Android\Android Studio\sdk"
set "COMMON_PATHS[4]=%PROGRAMFILES(X86)%\Android\android-sdk"

echo 检查常见安装路径...
for /L %%i in (0,1,4) do (
    call set "path=%%COMMON_PATHS[%%i]%%"
    call :check_path "%%path%%"
)

echo.
echo ❌ 未找到Android SDK，请手动设置：
echo.
echo 1. 如果你已安装Android Studio，SDK通常在：
echo    %USERPROFILE%\AppData\Local\Android\Sdk
echo.
echo 2. 请手动编辑 local.properties 文件，设置正确的sdk.dir路径
echo.
echo 3. 或者设置环境变量 ANDROID_HOME 指向你的SDK目录
echo.
goto :end

:check_path
if exist "%~1\platform-tools\adb.exe" (
    echo ✅ 找到Android SDK: %~1
    echo sdk.dir=%~1 > local.properties
    echo 已更新 local.properties 文件
    exit /b 0
) else if exist "%~1" (
    echo ⚠️  目录存在但不是有效的SDK: %~1
) else (
    echo ❌ 目录不存在: %~1
)
exit /b 1

:end
echo.
echo 完成！请检查 local.properties 文件中的sdk.dir设置
pause