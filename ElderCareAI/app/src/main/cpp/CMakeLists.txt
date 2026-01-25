# CMakeLists.txt for ElderCare AI
cmake_minimum_required(VERSION 3.22.1)

project("eldercare-ai")

# 设置C++标准
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# 添加编译选项
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++17 -frtti -fexceptions -Wall -Wextra")
set(CMAKE_CXX_FLAGS_RELEASE "${CMAKE_CXX_FLAGS_RELEASE} -O3 -DNDEBUG")
set(CMAKE_CXX_FLAGS_DEBUG "${CMAKE_CXX_FLAGS_DEBUG} -O0 -g")

# 查找必要的库
find_library(log-lib log)
find_library(android-lib android)
find_library(jnigraphics-lib jnigraphics)
find_library(camera2ndk-lib camera2ndk)
find_library(mediandk-lib mediandk)

# OpenCV配置
set(OpenCV_DIR ${CMAKE_SOURCE_DIR}/opencv/sdk/native/jni)
find_package(OpenCV REQUIRED)

# PaddleLite配置
set(PADDLE_LITE_DIR ${CMAKE_SOURCE_DIR}/paddlelite)
include_directories(${PADDLE_LITE_DIR}/cxx/include)

# NCNN配置
set(NCNN_DIR ${CMAKE_SOURCE_DIR}/ncnn)
include_directories(${NCNN_DIR}/include)

# Whisper.cpp配置
set(WHISPER_DIR ${CMAKE_SOURCE_DIR}/whisper)
include_directories(${WHISPER_DIR}/include)

# 添加头文件目录
include_directories(
    ${CMAKE_SOURCE_DIR}
    ${CMAKE_SOURCE_DIR}/common
    ${CMAKE_SOURCE_DIR}/ocr
    ${CMAKE_SOURCE_DIR}/yolo
    ${CMAKE_SOURCE_DIR}/whisper
    ${CMAKE_SOURCE_DIR}/utils
)

# 源文件
set(SOURCES
    # 主要JNI接口
    native-lib.cpp
    
    # 通用工具
    common/common.cpp
    utils/image_utils.cpp
    utils/audio_utils.cpp
    
    # OCR模块
    ocr/paddle_ocr.cpp
    ocr/ocr_processor.cpp
    
    # YOLO模块
    yolo/yolo_detector.cpp
    yolo/object_detector.cpp
    
    # Whisper模块
    whisper/whisper_processor.cpp
    whisper/audio_recorder.cpp
)

# 创建共享库
add_library(eldercare-ai SHARED ${SOURCES})

# 链接库
target_link_libraries(eldercare-ai
    ${log-lib}
    ${android-lib}
    ${jnigraphics-lib}
    ${camera2ndk-lib}
    ${mediandk-lib}
    ${OpenCV_LIBS}
    # PaddleLite库将在运行时动态加载
    # NCNN库将在运行时动态加载
    # Whisper库将在运行时动态加载
)

# 编译选项
target_compile_definitions(eldercare-ai PRIVATE
    ANDROID
    __ANDROID_API__=${ANDROID_PLATFORM_LEVEL}
)

# 优化选项
if(CMAKE_BUILD_TYPE STREQUAL "Release")
    target_compile_definitions(eldercare-ai PRIVATE NDEBUG)
    set_target_properties(eldercare-ai PROPERTIES
        ANDROID_ARM_NEON TRUE
    )
endif()

# 设置输出目录
set_target_properties(eldercare-ai PROPERTIES
    LIBRARY_OUTPUT_DIRECTORY ${CMAKE_LIBRARY_OUTPUT_DIRECTORY}
)