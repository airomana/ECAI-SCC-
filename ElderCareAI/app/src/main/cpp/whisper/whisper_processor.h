#ifndef ELDERCARE_AI_WHISPER_PROCESSOR_H
#define ELDERCARE_AI_WHISPER_PROCESSOR_H

#include "../common/common.h"
#include <string>
#include <vector>

class WhisperProcessor {
public:
    WhisperProcessor();
    ~WhisperProcessor();
    
    // 初始化Whisper模型
    bool init(const std::string& model_path);
    
    // 执行语音识别
    std::string transcribe(const float* audio_data, int length);
    
    // 执行语音识别并分析情感关键词
    TranscriptionResult transcribeWithEmotion(const float* audio_data, int length);
    
    // 释放资源
    void release();
    
private:
    bool initialized_;
    void* whisper_ctx_;  // Whisper上下文指针
    
    // 音频预处理
    bool preprocessAudio(const float* src, int length, float** dst, int* dst_length);
    
    // 后处理和情感分析
    TranscriptionResult postprocess(const std::string& text);
    
    // 情感关键词检测
    std::vector<std::string> detectEmotionKeywords(const std::string& text);
    
    // 初始化情感关键词词典
    void initEmotionKeywords();
    
    std::vector<std::string> positive_keywords_;
    std::vector<std::string> negative_keywords_;
    std::vector<std::string> lonely_keywords_;
};

#endif // ELDERCARE_AI_WHISPER_PROCESSOR_H