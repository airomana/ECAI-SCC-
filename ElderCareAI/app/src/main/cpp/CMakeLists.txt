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

# OpenCV配置（可选，用于OCR和YOLO）
# set(OpenCV_DIR ${CMAKE_SOURCE_DIR}/opencv/sdk/native/jni)
# find_package(OpenCV)

# PaddleLite配置（可选，用于OCR）
# set(PADDLE_LITE_DIR ${CMAKE_SOURCE_DIR}/paddlelite)
# include_directories(${PADDLE_LITE_DIR}/cxx/include)

# NCNN配置（可选，用于YOLO）
# set(NCNN_DIR ${CMAKE_SOURCE_DIR}/ncnn)
# include_directories(${NCNN_DIR}/include)

# Whisper.cpp配置 - 使用add_subdirectory集成
set(WHISPER_CPP_DIR ${CMAKE_SOURCE_DIR}/whisper.cpp)
if(EXISTS ${WHISPER_CPP_DIR}/CMakeLists.txt)
    message(STATUS "Found whisper.cpp at: ${WHISPER_CPP_DIR}")
    set(WHISPER_CPP_AVAILABLE TRUE)
    
    # 禁用whisper.cpp的测试和示例构建（我们只需要库）
    set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
    set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
    set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)
    
    # 禁用不需要的功能
    set(WHISPER_CURL OFF CACHE BOOL "" FORCE)
    set(WHISPER_SDL2 OFF CACHE BOOL "" FORCE)
    set(WHISPER_COREML OFF CACHE BOOL "" FORCE)
    set(WHISPER_OPENVINO OFF CACHE BOOL "" FORCE)
    
    # 添加whisper.cpp作为子目录
    add_subdirectory(${WHISPER_CPP_DIR} whisper_cpp_build)
    
    message(STATUS "Whisper.cpp integrated as subdirectory, enabling real recognition")
else()
    message(WARNING "whisper.cpp not found at: ${WHISPER_CPP_DIR}")
    message(WARNING "Please download whisper.cpp: git clone https://github.com/ggerganov/whisper.cpp.git ${WHISPER_CPP_DIR}")
    set(WHISPER_CPP_AVAILABLE FALSE)
endif()

# 添加头文件目录
include_directories(
    ${CMAKE_SOURCE_DIR}
    ${CMAKE_SOURCE_DIR}/common
    # ${CMAKE_SOURCE_DIR}/ocr  # 暂时不需要
    # ${CMAKE_SOURCE_DIR}/yolo  # 暂时不需要
    ${CMAKE_SOURCE_DIR}/whisper
    # ${CMAKE_SOURCE_DIR}/utils  # 暂时不需要
)

# 如果whisper.cpp可用，添加其头文件目录
if(WHISPER_CPP_AVAILABLE)
    include_directories(
        ${WHISPER_CPP_DIR}/include
        ${WHISPER_CPP_DIR}/ggml/include
        ${WHISPER_CPP_DIR}/ggml/src
    )
    add_definitions(-DWHISPER_CPP_AVAILABLE)
    message(STATUS "Whisper.cpp include directories added")
endif()

# 源文件（当前只编译Whisper相关）
set(SOURCES
    # 主要JNI接口
    native-lib.cpp
    
    # 通用工具
    common/common.cpp
    # utils/image_utils.cpp  # 需要OpenCV，暂时注释
    # utils/audio_utils.cpp  # 暂时不需要
    
    # OCR模块（暂时注释，需要OpenCV和PaddleLite）
    # ocr/paddle_ocr.cpp
    # ocr/ocr_processor.cpp
    
    # YOLO模块（暂时注释，需要OpenCV和NCNN）
    # yolo/yolo_detector.cpp
    # yolo/object_detector.cpp
    
    # Whisper模块
    whisper/whisper_processor.cpp
    # whisper/audio_recorder.cpp  # 如果不存在则注释
)

# whisper.cpp通过add_subdirectory集成，不需要手动添加源文件

# 创建共享库
add_library(eldercare-ai SHARED ${SOURCES})

# 链接库
target_link_libraries(eldercare-ai
    ${log-lib}
    ${android-lib}
    ${jnigraphics-lib}
    # ${camera2ndk-lib}  # 暂时不需要
    ${mediandk-lib}
    # ${OpenCV_LIBS}  # 暂时不需要
    # PaddleLite库将在运行时动态加载
    # NCNN库将在运行时动态加载
)

# 如果whisper.cpp可用，链接whisper库
if(WHISPER_CPP_AVAILABLE)
    target_link_libraries(eldercare-ai whisper)
    message(STATUS "Linking whisper library to eldercare-ai")
endif()

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