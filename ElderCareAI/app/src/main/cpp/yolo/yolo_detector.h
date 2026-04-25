#ifndef ELDERCARE_AI_YOLO_DETECTOR_H
#define ELDERCARE_AI_YOLO_DETECTOR_H

#include "../common/common.h"
#include <string>
#include <vector>

#ifdef NCNN_AVAILABLE
#include "ncnn/net.h"
#endif

/**
 * YOLOv8 NCNN 检测器
 * 支持两个模型：
 *   category.bin/.param       - 食材分类（37类，out0 shape [41,8400]）
 *   freshness_fruit_and_vegetables.bin/.param - 新鲜度检测（17类，out0 shape [21,8400]）
 */
class YoloDetector {
public:
    YoloDetector();
    ~YoloDetector();

    bool init(const std::string& model_dir);
    std::vector<DetectionResult> detect(void* image_data, int width, int height);
    void release();

private:
    bool initialized_;

#ifdef NCNN_AVAILABLE
    ncnn::Net category_net_;
    ncnn::Net freshness_net_;
#endif

    // 食材类别（category 模型，37类）
    static const std::vector<std::string> CATEGORY_NAMES;
    // 新鲜度类别（freshness 模型，17类）
    static const std::vector<std::string> FRESHNESS_NAMES;

    // 输入尺寸（YOLOv8 标准 640x640）
    static const int INPUT_SIZE = 640;
    static const float CONF_THRESHOLD;
    static const float NMS_THRESHOLD;

#ifdef NCNN_AVAILABLE
    // 从 ARGB bitmap 转换为 ncnn::Mat（RGB，归一化）
    ncnn::Mat bitmapToMat(void* argb_data, int width, int height);

    // YOLOv8 后处理：解析 out0 输出，返回检测框
    // out0 shape: [num_classes+4, 8400]
    std::vector<DetectionResult> postprocess(
        const ncnn::Mat& out,
        int num_classes,
        const std::vector<std::string>& class_names,
        int orig_w, int orig_h);
#endif
};

#endif // ELDERCARE_AI_YOLO_DETECTOR_H
