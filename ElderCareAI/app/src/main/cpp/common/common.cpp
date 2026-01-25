#include "common.h"
#include <algorithm>
#include <sstream>
#include <fstream>
#include <chrono>
#include <cstring>

namespace ElderCareUtils {

bool preprocessImage(void* pixels, int width, int height, int channels) {
    if (pixels == nullptr || width <= 0 || height <= 0 || channels <= 0) {
        return false;
    }
    
    // 基本的图像预处理
    // 这里可以添加去噪、增强对比度等操作
    // 针对老年人拍照常见的模糊、反光等问题进行优化
    
    return true;
}

bool preprocessAudio(float* audio_data, int length, int sample_rate) {
    if (audio_data == nullptr || length <= 0 || sample_rate <= 0) {
        return false;
    }
    
    // 音频预处理
    // 降噪、音量归一化等
    float max_val = 0.0f;
    for (int i = 0; i < length; i++) {
        max_val = std::max(max_val, std::abs(audio_data[i]));
    }
    
    if (max_val > 0.0f) {
        float scale = 1.0f / max_val;
        for (int i = 0; i < length; i++) {
            audio_data[i] *= scale;
        }
    }
    
    return true;
}

std::vector<std::string> splitString(const std::string& str, char delimiter) {
    std::vector<std::string> tokens;
    std::stringstream ss(str);
    std::string token;
    
    while (std::getline(ss, token, delimiter)) {
        if (!token.empty()) {
            tokens.push_back(token);
        }
    }
    
    return tokens;
}

std::string trimString(const std::string& str) {
    size_t start = str.find_first_not_of(" \t\n\r");
    if (start == std::string::npos) {
        return "";
    }
    
    size_t end = str.find_last_not_of(" \t\n\r");
    return str.substr(start, end - start + 1);
}

bool fileExists(const std::string& path) {
    std::ifstream file(path);
    return file.good();
}

std::string getFileExtension(const std::string& path) {
    size_t dot_pos = path.find_last_of('.');
    if (dot_pos == std::string::npos) {
        return "";
    }
    return path.substr(dot_pos + 1);
}

long long getCurrentTimestamp() {
    auto now = std::chrono::system_clock::now();
    auto timestamp = std::chrono::duration_cast<std::chrono::milliseconds>(
        now.time_since_epoch()).count();
    return timestamp;
}

std::string formatTimestamp(long long timestamp) {
    auto time_point = std::chrono::system_clock::from_time_t(timestamp / 1000);
    auto time_t = std::chrono::system_clock::to_time_t(time_point);
    
    char buffer[100];
    std::strftime(buffer, sizeof(buffer), "%Y-%m-%d %H:%M:%S", std::localtime(&time_t));
    
    return std::string(buffer);
}

} // namespace ElderCareUtils