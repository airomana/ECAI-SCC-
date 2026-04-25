#!/bin/bash

# ElderCare AI 构建脚本
# 用于快速构建和部署应用

echo "🚀 开始构建银发智膳助手..."

# 检查环境
echo "📋 检查构建环境..."

# 检查Android SDK
if [ -z "$ANDROID_HOME" ]; then
    echo "❌ 错误: ANDROID_HOME 环境变量未设置"
    echo "请设置 ANDROID_HOME 指向您的 Android SDK 目录"
    exit 1
fi

# 检查NDK
if [ ! -d "$ANDROID_HOME/ndk" ]; then
    echo "⚠️  警告: 未找到 NDK，请确保已安装 NDK"
fi

# 清理之前的构建
echo "🧹 清理之前的构建..."
./gradlew clean

# 检查模型文件
echo "📦 检查模型文件..."
ASSETS_DIR="app/src/main/assets"

if [ ! -d "$ASSETS_DIR" ]; then
    echo "📁 创建 assets 目录..."
    mkdir -p "$ASSETS_DIR"/{ocr,yolo,whisper}
fi

# 检查必要的模型文件
echo "🔍 检查模型文件是否存在..."
MODEL_FILES=(
    "$ASSETS_DIR/ocr/ch_ppocr_mobile_v2.0_det_infer.nb"
    "$ASSETS_DIR/ocr/ch_ppocr_mobile_v2.0_rec_infer.nb"
    "$ASSETS_DIR/yolo/yolov8n.ncnn.param"
    "$ASSETS_DIR/yolo/yolov8n.ncnn.bin"
    "$ASSETS_DIR/whisper/ggml-base-q8_0.bin"
)

MISSING_FILES=()
for file in "${MODEL_FILES[@]}"; do
    if [ ! -f "$file" ]; then
        MISSING_FILES+=("$file")
    fi
done

if [ ${#MISSING_FILES[@]} -gt 0 ]; then
    echo "⚠️  警告: 以下模型文件缺失:"
    for file in "${MISSING_FILES[@]}"; do
        echo "   - $file"
    done
    echo "应用将使用模拟数据运行，实际部署前请下载相应模型文件"
fi

# 构建项目
echo "🔨 开始构建项目..."

# 构建Debug版本
if [ "$1" = "release" ]; then
    echo "📦 构建Release版本..."
    ./gradlew assembleRelease
    
    if [ $? -eq 0 ]; then
        echo "✅ Release版本构建成功!"
        echo "📱 APK位置: app/build/outputs/apk/release/"
        
        # 查找生成的APK文件
        APK_FILE=$(find app/build/outputs/apk/release/ -name "*.apk" | head -1)
        if [ -n "$APK_FILE" ]; then
            APK_SIZE=$(du -h "$APK_FILE" | cut -f1)
            echo "📊 APK大小: $APK_SIZE"
        fi
    else
        echo "❌ Release版本构建失败!"
        exit 1
    fi
else
    echo "🔧 构建Debug版本..."
    ./gradlew assembleDebug
    
    if [ $? -eq 0 ]; then
        echo "✅ Debug版本构建成功!"
        echo "📱 APK位置: app/build/outputs/apk/debug/"
        
        # 查找生成的APK文件
        APK_FILE=$(find app/build/outputs/apk/debug/ -name "*.apk" | head -1)
        if [ -n "$APK_FILE" ]; then
            APK_SIZE=$(du -h "$APK_FILE" | cut -f1)
            echo "📊 APK大小: $APK_SIZE"
        fi
        
        # 如果连接了设备，询问是否安装
        if adb devices | grep -q "device$"; then
            echo "📱 检测到已连接的设备"
            read -p "是否要安装到设备? (y/n): " -n 1 -r
            echo
            if [[ $REPLY =~ ^[Yy]$ ]]; then
                echo "🗑️  先卸载旧版本（如果存在）..."
                adb uninstall com.eldercare.ai 2>/dev/null
                echo "📲 正在安装到设备..."
                adb install "$APK_FILE"
                if [ $? -eq 0 ]; then
                    echo "✅ 安装成功!"
                else
                    echo "❌ 安装失败!"
                    echo "💡 提示: 如果安装失败，请尝试:"
                    echo "   1. 手动卸载应用: adb uninstall com.eldercare.ai"
                    echo "   2. 检查MIUI安全设置，允许安装未知来源应用"
                    echo "   3. 重新运行安装: adb install $APK_FILE"
                fi
            fi
        fi
    else
        echo "❌ Debug版本构建失败!"
        exit 1
    fi
fi

echo "🎉 构建完成!"
echo ""
echo "📋 项目信息:"
echo "   - 应用名称: 银发智膳助手"
echo "   - 包名: com.eldercare.ai"
echo "   - 最低SDK: API 24 (Android 7.0)"
echo "   - 目标SDK: API 34 (Android 14)"
echo ""
echo "🔧 开发提示:"
echo "   - 使用 './build.sh' 构建Debug版本"
echo "   - 使用 './build.sh release' 构建Release版本"
echo "   - 确保已下载必要的AI模型文件"
echo "   - 推荐在ARM64设备上测试以获得最佳性能"