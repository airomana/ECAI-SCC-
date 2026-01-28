#include <jni.h>
#include <string>
#include <android/log.h>
// #include <android/bitmap.h>  // 暂时不需要
#include "common/common.h"
// #include "ocr/paddle_ocr.h"  // 暂时不需要，需要OpenCV
// #include "yolo/yolo_detector.h"  // 暂时不需要，需要OpenCV和NCNN
#include "whisper/whisper_processor.h"

#define TAG "ElderCareAI-Native"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

// 全局对象
// static PaddleOCR* g_paddle_ocr = nullptr;  // 暂时不需要
// static YoloDetector* g_yolo_detector = nullptr;  // 暂时不需要
static WhisperProcessor* g_whisper_processor = nullptr;

extern "C" {

// OCR和YOLO模块暂时注释，需要OpenCV等依赖
// 等实现时再取消注释

/*
// 初始化OCR模块
JNIEXPORT jboolean JNICALL
Java_com_eldercare_ai_ocr_PaddleOCRProcessor_nativeInit(JNIEnv *env, jobject thiz,
                                                        jstring model_dir) {
    // 暂时注释
    return false;
}

// OCR识别
JNIEXPORT jobjectArray JNICALL
Java_com_eldercare_ai_ocr_PaddleOCRProcessor_nativeDetectText(JNIEnv *env, jobject thiz,
                                                             jobject bitmap) {
    // 暂时注释
    return nullptr;
}

// 初始化YOLO模块
JNIEXPORT jboolean JNICALL
Java_com_eldercare_ai_yolo_YoloDetector_nativeInit(JNIEnv *env, jobject thiz,
                                                   jstring model_path) {
    // 暂时注释
    return false;
}

// YOLO物体检测
JNIEXPORT jobjectArray JNICALL
Java_com_eldercare_ai_yolo_YoloDetector_nativeDetectObjects(JNIEnv *env, jobject thiz,
                                                           jobject bitmap) {
    // 暂时注释
    return nullptr;
}
*/

// 初始化Whisper模块
JNIEXPORT jboolean JNICALL
Java_com_eldercare_ai_whisper_WhisperProcessor_nativeInit(JNIEnv *env, jobject thiz,
                                                         jstring model_path) {
    const char* model_path_cstr = env->GetStringUTFChars(model_path, nullptr);
    
    try {
        if (g_whisper_processor == nullptr) {
            g_whisper_processor = new WhisperProcessor();
        }
        
        bool result = g_whisper_processor->init(model_path_cstr);
        env->ReleaseStringUTFChars(model_path, model_path_cstr);
        
        LOGI("Whisper init result: %s", result ? "success" : "failed");
        return result;
    } catch (const std::exception& e) {
        LOGE("Whisper init error: %s", e.what());
        env->ReleaseStringUTFChars(model_path, model_path_cstr);
        return false;
    }
}

// Whisper语音识别
JNIEXPORT jstring JNICALL
Java_com_eldercare_ai_whisper_WhisperProcessor_nativeTranscribe(JNIEnv *env, jobject thiz,
                                                               jfloatArray audio_data) {
    if (g_whisper_processor == nullptr) {
        LOGE("Whisper processor not initialized");
        return nullptr;
    }
    
    try {
        jsize length = env->GetArrayLength(audio_data);
        jfloat* audio_buffer = env->GetFloatArrayElements(audio_data, nullptr);
        
        // 执行语音识别
        std::string result = g_whisper_processor->transcribe(audio_buffer, length);
        
        env->ReleaseFloatArrayElements(audio_data, audio_buffer, JNI_ABORT);
        
        LOGI("Whisper transcription result: %s", result.c_str());
        return env->NewStringUTF(result.c_str());
        
    } catch (const std::exception& e) {
        LOGE("Whisper transcription error: %s", e.what());
        return nullptr;
    }
}

// 释放Whisper资源
JNIEXPORT void JNICALL
Java_com_eldercare_ai_whisper_WhisperProcessor_nativeRelease(JNIEnv *env, jobject thiz) {
    LOGI("Releasing Whisper processor");
    
    if (g_whisper_processor != nullptr) {
        g_whisper_processor->release();
        // 注意：这里不删除g_whisper_processor，因为它是全局单例
    }
}

// 清理资源
JNIEXPORT void JNICALL
Java_com_eldercare_ai_MainActivity_nativeCleanup(JNIEnv *env, jobject thiz) {
    LOGI("Cleaning up native resources");
    
    // if (g_paddle_ocr != nullptr) {  // 暂时注释
    //     delete g_paddle_ocr;
    //     g_paddle_ocr = nullptr;
    // }
    
    // if (g_yolo_detector != nullptr) {  // 暂时注释
    //     delete g_yolo_detector;
    //     g_yolo_detector = nullptr;
    // }
    
    if (g_whisper_processor != nullptr) {
        delete g_whisper_processor;
        g_whisper_processor = nullptr;
    }
}

} // extern "C"