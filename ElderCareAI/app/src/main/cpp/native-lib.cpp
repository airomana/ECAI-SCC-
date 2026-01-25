#include <jni.h>
#include <string>
#include <android/log.h>
#include <android/bitmap.h>
#include "common/common.h"
#include "ocr/paddle_ocr.h"
#include "yolo/yolo_detector.h"
#include "whisper/whisper_processor.h"

#define TAG "ElderCareAI-Native"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

// 全局对象
static PaddleOCR* g_paddle_ocr = nullptr;
static YoloDetector* g_yolo_detector = nullptr;
static WhisperProcessor* g_whisper_processor = nullptr;

extern "C" {

// 初始化OCR模块
JNIEXPORT jboolean JNICALL
Java_com_eldercare_ai_ocr_PaddleOCRProcessor_nativeInit(JNIEnv *env, jobject thiz,
                                                        jstring model_dir) {
    const char* model_dir_cstr = env->GetStringUTFChars(model_dir, nullptr);
    
    try {
        if (g_paddle_ocr == nullptr) {
            g_paddle_ocr = new PaddleOCR();
        }
        
        bool result = g_paddle_ocr->init(model_dir_cstr);
        env->ReleaseStringUTFChars(model_dir, model_dir_cstr);
        
        LOGI("PaddleOCR init result: %s", result ? "success" : "failed");
        return result;
    } catch (const std::exception& e) {
        LOGE("PaddleOCR init error: %s", e.what());
        env->ReleaseStringUTFChars(model_dir, model_dir_cstr);
        return false;
    }
}

// OCR识别
JNIEXPORT jobjectArray JNICALL
Java_com_eldercare_ai_ocr_PaddleOCRProcessor_nativeDetectText(JNIEnv *env, jobject thiz,
                                                             jobject bitmap) {
    if (g_paddle_ocr == nullptr) {
        LOGE("PaddleOCR not initialized");
        return nullptr;
    }
    
    try {
        // 将Android Bitmap转换为OpenCV Mat
        AndroidBitmapInfo info;
        void* pixels;
        
        if (AndroidBitmap_getInfo(env, bitmap, &info) < 0) {
            LOGE("Failed to get bitmap info");
            return nullptr;
        }
        
        if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0) {
            LOGE("Failed to lock bitmap pixels");
            return nullptr;
        }
        
        // 执行OCR识别
        std::vector<OCRResult> results = g_paddle_ocr->detect(pixels, info.width, info.height);
        
        AndroidBitmap_unlockPixels(env, bitmap);
        
        // 将结果转换为Java数组
        jclass string_class = env->FindClass("java/lang/String");
        jobjectArray result_array = env->NewObjectArray(results.size(), string_class, nullptr);
        
        for (size_t i = 0; i < results.size(); i++) {
            jstring text = env->NewStringUTF(results[i].text.c_str());
            env->SetObjectArrayElement(result_array, i, text);
            env->DeleteLocalRef(text);
        }
        
        LOGI("OCR detected %zu text regions", results.size());
        return result_array;
        
    } catch (const std::exception& e) {
        LOGE("OCR detection error: %s", e.what());
        return nullptr;
    }
}

// 初始化YOLO模块
JNIEXPORT jboolean JNICALL
Java_com_eldercare_ai_yolo_YoloDetector_nativeInit(JNIEnv *env, jobject thiz,
                                                   jstring model_path) {
    const char* model_path_cstr = env->GetStringUTFChars(model_path, nullptr);
    
    try {
        if (g_yolo_detector == nullptr) {
            g_yolo_detector = new YoloDetector();
        }
        
        bool result = g_yolo_detector->init(model_path_cstr);
        env->ReleaseStringUTFChars(model_path, model_path_cstr);
        
        LOGI("YOLO init result: %s", result ? "success" : "failed");
        return result;
    } catch (const std::exception& e) {
        LOGE("YOLO init error: %s", e.what());
        env->ReleaseStringUTFChars(model_path, model_path_cstr);
        return false;
    }
}

// YOLO物体检测
JNIEXPORT jobjectArray JNICALL
Java_com_eldercare_ai_yolo_YoloDetector_nativeDetectObjects(JNIEnv *env, jobject thiz,
                                                           jobject bitmap) {
    if (g_yolo_detector == nullptr) {
        LOGE("YOLO detector not initialized");
        return nullptr;
    }
    
    try {
        // 将Android Bitmap转换为检测输入
        AndroidBitmapInfo info;
        void* pixels;
        
        if (AndroidBitmap_getInfo(env, bitmap, &info) < 0) {
            LOGE("Failed to get bitmap info");
            return nullptr;
        }
        
        if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0) {
            LOGE("Failed to lock bitmap pixels");
            return nullptr;
        }
        
        // 执行物体检测
        std::vector<DetectionResult> results = g_yolo_detector->detect(pixels, info.width, info.height);
        
        AndroidBitmap_unlockPixels(env, bitmap);
        
        // 将结果转换为Java数组
        jclass string_class = env->FindClass("java/lang/String");
        jobjectArray result_array = env->NewObjectArray(results.size(), string_class, nullptr);
        
        for (size_t i = 0; i < results.size(); i++) {
            jstring object_name = env->NewStringUTF(results[i].class_name.c_str());
            env->SetObjectArrayElement(result_array, i, object_name);
            env->DeleteLocalRef(object_name);
        }
        
        LOGI("YOLO detected %zu objects", results.size());
        return result_array;
        
    } catch (const std::exception& e) {
        LOGE("YOLO detection error: %s", e.what());
        return nullptr;
    }
}

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

// 清理资源
JNIEXPORT void JNICALL
Java_com_eldercare_ai_MainActivity_nativeCleanup(JNIEnv *env, jobject thiz) {
    LOGI("Cleaning up native resources");
    
    if (g_paddle_ocr != nullptr) {
        delete g_paddle_ocr;
        g_paddle_ocr = nullptr;
    }
    
    if (g_yolo_detector != nullptr) {
        delete g_yolo_detector;
        g_yolo_detector = nullptr;
    }
    
    if (g_whisper_processor != nullptr) {
        delete g_whisper_processor;
        g_whisper_processor = nullptr;
    }
}

} // extern "C"