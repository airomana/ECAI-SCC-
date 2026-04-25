#ifndef ELDERCARE_AI_PADDLE_OCR_H
#define ELDERCARE_AI_PADDLE_OCR_H

#include "../common/common.h"
#include <string>
#include <vector>

class PaddleOCR {
public:
    PaddleOCR();
    ~PaddleOCR();
    
    // 初始化OCR模型
    bool init(const std::string& model_dir);
    
    // 执行OCR识别
    std::vector<OCRResult> detect(void* image_data, int width, int height);
    
    // 释放资源
    void release();
    
private:
    bool initialized_;
    void* predictor_;  // PaddleLite预测器指针
    
    // 图像预处理
    bool preprocessImage(void* src, int width, int height, void** dst, int* dst_width, int* dst_height);
    
    // 后处理
    std::vector<OCRResult> postprocess(void* output_data, int output_size);
};

#endif // ELDERCARE_AI_PADDLE_OCR_H