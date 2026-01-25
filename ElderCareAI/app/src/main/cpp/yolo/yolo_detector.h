#ifndef ELDERCARE_AI_YOLO_DETECTOR_H
#define ELDERCARE_AI_YOLO_DETECTOR_H

#include "../common/common.h"
#include <string>
#include <vector>

class YoloDetector {
public:
    YoloDetector();
    ~YoloDetector();
    
    // 初始化YOLO模型
    bool init(const std::string& model_path);
    
    // 执行物体检测
    std::vector<DetectionResult> detect(void* image_data, int width, int height);
    
    // 释放资源
    void release();
    
private:
    bool initialized_;
    void* net_;  // NCNN网络指针
    
    // 食材类别名称
    std::vector<std::string> class_names_;
    
    // 图像预处理
    bool preprocessImage(void* src, int width, int height, void** dst);
    
    // 后处理
    std::vector<DetectionResult> postprocess(void* output_data, int width, int height);
    
    // 初始化食材类别
    void initClassNames();
};

#endif // ELDERCARE_AI_YOLO_DETECTOR_H