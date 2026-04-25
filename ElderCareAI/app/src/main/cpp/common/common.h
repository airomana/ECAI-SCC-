#ifndef ELDERCARE_AI_COMMON_H
#define ELDERCARE_AI_COMMON_H

#include <string>
#include <vector>
#include <memory>

// OCR结果结构
struct OCRResult {
    std::string text;
    float confidence;
    std::vector<std::pair<int, int>> box_points;
    
    OCRResult() : confidence(0.0f) {}
    OCRResult(const std::string& t, float conf) : text(t), confidence(conf) {}
};

// 物体检测结果结构
struct DetectionResult {
    std::string class_name;
    float confidence;
    int x, y, width, height;
    
    DetectionResult() : confidence(0.0f), x(0), y(0), width(0), height(0) {}
    DetectionResult(const std::string& name, float conf, int x_, int y_, int w, int h)
        : class_name(name), confidence(conf), x(x_), y(y_), width(w), height(h) {}
};

// 语音识别结果结构
struct TranscriptionResult {
    std::string text;
    float confidence;
    std::vector<std::string> emotion_keywords;
    
    TranscriptionResult() : confidence(0.0f) {}
    TranscriptionResult(const std::string& t, float conf) : text(t), confidence(conf) {}
};

// 通用工具函数
namespace ElderCareUtils {
    // 图像预处理
    bool preprocessImage(void* pixels, int width, int height, int channels);
    
    // 音频预处理
    bool preprocessAudio(float* audio_data, int length, int sample_rate);
    
    // 字符串工具
    std::vector<std::string> splitString(const std::string& str, char delimiter);
    std::string trimString(const std::string& str);
    
    // 文件工具
    bool fileExists(const std::string& path);
    std::string getFileExtension(const std::string& path);
    
    // 时间工具
    long long getCurrentTimestamp();
    std::string formatTimestamp(long long timestamp);
}

#endif // ELDERCARE_AI_COMMON_H