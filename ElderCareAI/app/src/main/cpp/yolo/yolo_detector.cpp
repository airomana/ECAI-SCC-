#include "yolo_detector.h"
#include <android/log.h>
#include <algorithm>
#include <numeric>
#include <cmath>

#define TAG "YoloDetector-Native"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

// ── 类别名称 ──────────────────────────────────────────────────────────────────
// category 模型：out0 shape [41, 8400]，即 4+37 类
const std::vector<std::string> YoloDetector::CATEGORY_NAMES = {
    "苹果","香蕉","橙子","梨","葡萄","草莓","西瓜","芒果","桃子","柠檬",
    "鸡蛋","牛奶","酸奶","奶酪","黄油",
    "猪肉","牛肉","鸡肉","鱼","虾",
    "白菜","菠菜","胡萝卜","西红柿","黄瓜","土豆","洋葱","大蒜","辣椒","茄子",
    "豆腐","蘑菇","玉米",
    "食材34","食材35","食材36","食材37"
};

// freshness 模型：out0 shape [21, 8400]，即 4+17 类
const std::vector<std::string> YoloDetector::FRESHNESS_NAMES = {
    "新鲜苹果","腐烂苹果",
    "新鲜香蕉","腐烂香蕉",
    "新鲜橙子","腐烂橙子",
    "新鲜草莓","腐烂草莓",
    "新鲜番茄","腐烂番茄",
    "新鲜黄瓜","腐烂黄瓜",
    "新鲜土豆",
    "新鲜度14","新鲜度15","新鲜度16","新鲜度17"
};

const float YoloDetector::CONF_THRESHOLD = 0.20f;
const float YoloDetector::NMS_THRESHOLD  = 0.45f;

// ── 构造 / 析构 ───────────────────────────────────────────────────────────────
YoloDetector::YoloDetector() : initialized_(false) {}

YoloDetector::~YoloDetector() { release(); }

// ── init ──────────────────────────────────────────────────────────────────────
bool YoloDetector::init(const std::string& model_dir) {
    LOGI("YoloDetector::init  model_dir=%s", model_dir.c_str());

#ifdef NCNN_AVAILABLE
    // 关闭 GPU（避免 Vulkan 初始化失败导致崩溃）
    category_net_.opt.use_vulkan_compute  = false;
    freshness_net_.opt.use_vulkan_compute = false;
    category_net_.opt.num_threads  = 4;
    freshness_net_.opt.num_threads  = 4;

    std::string cat_param = model_dir + "/category.bin.param";
    std::string cat_bin   = model_dir + "/category.bin";
    std::string fre_param = model_dir + "/freshness_fruit_and_vegetables.param";
    std::string fre_bin   = model_dir + "/freshness_fruit_and_vegetables.bin";

    if (category_net_.load_param(cat_param.c_str()) != 0) {
        LOGE("Failed to load category param: %s", cat_param.c_str());
        return false;
    }
    if (category_net_.load_model(cat_bin.c_str()) != 0) {
        LOGE("Failed to load category bin: %s", cat_bin.c_str());
        return false;
    }
    LOGI("category model loaded OK");

    if (freshness_net_.load_param(fre_param.c_str()) != 0) {
        LOGE("Failed to load freshness param: %s", fre_param.c_str());
        return false;
    }
    if (freshness_net_.load_model(fre_bin.c_str()) != 0) {
        LOGE("Failed to load freshness bin: %s", fre_bin.c_str());
        return false;
    }
    LOGI("freshness model loaded OK");

    initialized_ = true;
    return true;
#else
    LOGE("NCNN not compiled in, cannot init YoloDetector");
    return false;
#endif
}

// ── detect ────────────────────────────────────────────────────────────────────
std::vector<DetectionResult> YoloDetector::detect(void* image_data, int width, int height) {
    std::vector<DetectionResult> results;

    if (!initialized_) {
        LOGE("YoloDetector not initialized");
        return results;
    }

    LOGI("Detecting objects in %dx%d image", width, height);

#ifdef NCNN_AVAILABLE
    ncnn::Mat in = bitmapToMat(image_data, width, height);

    // ── 1. 食材分类 ──────────────────────────────────────────────────────────
    {
        ncnn::Extractor ex = category_net_.create_extractor();
        ex.input("in0", in);
        ncnn::Mat out;
        ex.extract("out0", out);
        // out shape: [37, 8400]  (channels=37, w=8400)
        auto det = postprocess(out, (int)CATEGORY_NAMES.size(), CATEGORY_NAMES, width, height);
        results.insert(results.end(), det.begin(), det.end());
        LOGI("category detected %zu objects", det.size());
    }

    // ── 2. 新鲜度检测（仅对水果蔬菜有效）────────────────────────────────────
    {
        ncnn::Extractor ex = freshness_net_.create_extractor();
        ex.input("in0", in);
        ncnn::Mat out;
        ex.extract("out0", out);
        auto det = postprocess(out, (int)FRESHNESS_NAMES.size(), FRESHNESS_NAMES, width, height);
        results.insert(results.end(), det.begin(), det.end());
        LOGI("freshness detected %zu objects", det.size());
    }
#endif

    return results;
}

// ── release ───────────────────────────────────────────────────────────────────
void YoloDetector::release() {
#ifdef NCNN_AVAILABLE
    category_net_.clear();
    freshness_net_.clear();
#endif
    initialized_ = false;
}

// ── 私有：Bitmap ARGB → ncnn::Mat RGB 640x640 ─────────────────────────────────
#ifdef NCNN_AVAILABLE
ncnn::Mat YoloDetector::bitmapToMat(void* argb_data, int width, int height) {
    // 计算 letterbox 缩放比例
    float scale = std::min((float)INPUT_SIZE / width, (float)INPUT_SIZE / height);
    int new_w = (int)(width  * scale);
    int new_h = (int)(height * scale);
    int pad_x = (INPUT_SIZE - new_w) / 2;
    int pad_y = (INPUT_SIZE - new_h) / 2;

    // 创建 640x640 RGB Mat，填充灰色(114)
    ncnn::Mat in(INPUT_SIZE, INPUT_SIZE, 3);
    in.fill(114.f / 255.f);

    // 将 ARGB bitmap 缩放并写入 Mat
    uint8_t* src = (uint8_t*)argb_data;
    float* r_plane = in.channel(0);
    float* g_plane = in.channel(1);
    float* b_plane = in.channel(2);

    for (int y = 0; y < new_h; y++) {
        int src_y = (int)(y / scale);
        src_y = std::min(src_y, height - 1);
        for (int x = 0; x < new_w; x++) {
            int src_x = (int)(x / scale);
            src_x = std::min(src_x, width - 1);

            // Android Bitmap ARGB_8888: byte order A R G B
            int idx = (src_y * width + src_x) * 4;
            // idx+0 = alpha（忽略）
            uint8_t r = src[idx + 1];
            uint8_t g = src[idx + 2];
            uint8_t b = src[idx + 3];

            int dst_idx = (y + pad_y) * INPUT_SIZE + (x + pad_x);
            r_plane[dst_idx] = r / 255.f;
            g_plane[dst_idx] = g / 255.f;
            b_plane[dst_idx] = b / 255.f;
        }
    }
    return in;
}

// ── 私有：YOLOv8 后处理 ────────────────────────────────────────────────────────
// YOLOv8 NCNN 输出 out0 shape: [4+num_classes, 8400]
// 每列是一个候选框：[cx, cy, w, h, cls0_score, cls1_score, ...]
std::vector<DetectionResult> YoloDetector::postprocess(
        const ncnn::Mat& out,
        int num_classes,
        const std::vector<std::string>& class_names,
        int orig_w, int orig_h) {

    std::vector<DetectionResult> results;

    // out: channels = 4+num_classes, w = 8400
    int num_anchors = out.w;   // 8400
    int num_ch      = out.h;   // 4 + num_classes

    if (num_ch != 4 + num_classes) {
        LOGE("postprocess: unexpected output shape h=%d expected %d, w=%d c=%d",
             num_ch, 4 + num_classes, out.w, out.c);
        // 尝试转置布局：w=4+num_classes, h=8400
        if (out.w == 4 + num_classes) {
            LOGI("postprocess: trying transposed layout w=%d h=%d", out.w, out.h);
            int num_anchors_t = out.h;
            float scale = std::min((float)INPUT_SIZE / orig_w, (float)INPUT_SIZE / orig_h);
            int pad_x = (INPUT_SIZE - (int)(orig_w * scale)) / 2;
            int pad_y = (INPUT_SIZE - (int)(orig_h * scale)) / 2;

            std::vector<std::vector<float>> boxes;
            std::vector<float>              scores;
            std::vector<int>                class_ids;

            for (int i = 0; i < num_anchors_t; i++) {
                const float* row = out.row(i);
                float max_score = -1.f;
                int   max_cls   = -1;
                for (int c = 0; c < num_classes; c++) {
                    float s = row[4 + c];
                    if (s > max_score) { max_score = s; max_cls = c; }
                }
                if (max_score < CONF_THRESHOLD) continue;

                float cx = row[0], cy = row[1], bw = row[2], bh = row[3];
                float x1 = (cx - bw/2 - pad_x) / scale;
                float y1 = (cy - bh/2 - pad_y) / scale;
                float x2 = (cx + bw/2 - pad_x) / scale;
                float y2 = (cy + bh/2 - pad_y) / scale;
                x1 = std::max(0.f, std::min(x1, (float)orig_w));
                y1 = std::max(0.f, std::min(y1, (float)orig_h));
                x2 = std::max(0.f, std::min(x2, (float)orig_w));
                y2 = std::max(0.f, std::min(y2, (float)orig_h));
                boxes.push_back({x1, y1, x2, y2});
                scores.push_back(max_score);
                class_ids.push_back(max_cls);
            }

            std::vector<int> order(scores.size());
            std::iota(order.begin(), order.end(), 0);
            std::sort(order.begin(), order.end(), [&](int a, int b){ return scores[a] > scores[b]; });
            std::vector<bool> suppressed(scores.size(), false);
            for (size_t i = 0; i < order.size(); i++) {
                int idx = order[i];
                if (suppressed[idx]) continue;
                auto& b = boxes[idx];
                std::string name = (class_ids[idx] < (int)class_names.size())
                                   ? class_names[class_ids[idx]] : "unknown";
                results.push_back(DetectionResult(name, scores[idx],
                    (int)b[0], (int)b[1], (int)(b[2]-b[0]), (int)(b[3]-b[1])));
                for (size_t j = i+1; j < order.size(); j++) {
                    int jdx = order[j];
                    if (suppressed[jdx]) continue;
                    auto& bj = boxes[jdx];
                    float ix1 = std::max(b[0],bj[0]), iy1 = std::max(b[1],bj[1]);
                    float ix2 = std::min(b[2],bj[2]), iy2 = std::min(b[3],bj[3]);
                    float inter = std::max(0.f,ix2-ix1)*std::max(0.f,iy2-iy1);
                    float iou = inter/((b[2]-b[0])*(b[3]-b[1])+(bj[2]-bj[0])*(bj[3]-bj[1])-inter+1e-6f);
                    if (iou > NMS_THRESHOLD) suppressed[jdx] = true;
                }
            }
            return results;
        }
        return results;
    }

    // letterbox 参数（与 bitmapToMat 一致）
    float scale = std::min((float)INPUT_SIZE / orig_w, (float)INPUT_SIZE / orig_h);
    int pad_x = (INPUT_SIZE - (int)(orig_w * scale)) / 2;
    int pad_y = (INPUT_SIZE - (int)(orig_h * scale)) / 2;

    // 收集候选框
    std::vector<std::vector<float>> boxes;
    std::vector<float>              scores;
    std::vector<int>                class_ids;

    float max_score_global = -1.f;
    for (int i = 0; i < num_anchors; i++) {
        // 找最高置信度类别
        float max_score = -1.f;
        int   max_cls   = -1;
        for (int c = 0; c < num_classes; c++) {
            float s = out.row(4 + c)[i];
            if (s > max_score) { max_score = s; max_cls = c; }
        }
        if (max_score > max_score_global) max_score_global = max_score;
        if (max_score < CONF_THRESHOLD) continue;

        // cx cy w h（相对于 640x640 输入）
        float cx = out.row(0)[i];
        float cy = out.row(1)[i];
        float bw = out.row(2)[i];
        float bh = out.row(3)[i];

        // 转换回原图坐标
        float x1 = (cx - bw / 2 - pad_x) / scale;
        float y1 = (cy - bh / 2 - pad_y) / scale;
        float x2 = (cx + bw / 2 - pad_x) / scale;
        float y2 = (cy + bh / 2 - pad_y) / scale;

        x1 = std::max(0.f, std::min(x1, (float)orig_w));
        y1 = std::max(0.f, std::min(y1, (float)orig_h));
        x2 = std::max(0.f, std::min(x2, (float)orig_w));
        y2 = std::max(0.f, std::min(y2, (float)orig_h));

        boxes.push_back({x1, y1, x2, y2});
        scores.push_back(max_score);
        class_ids.push_back(max_cls);
    }

    LOGI("postprocess: max_score=%.4f candidates=%zu", max_score_global, boxes.size());

    // NMS（简单贪心）
    std::vector<int> order(scores.size());
    std::iota(order.begin(), order.end(), 0);
    std::sort(order.begin(), order.end(), [&](int a, int b){ return scores[a] > scores[b]; });

    std::vector<bool> suppressed(scores.size(), false);
    for (size_t i = 0; i < order.size(); i++) {
        int idx = order[i];
        if (suppressed[idx]) continue;

        auto& b = boxes[idx];
        std::string name = (class_ids[idx] < (int)class_names.size())
                           ? class_names[class_ids[idx]] : "unknown";
        results.push_back(DetectionResult(
            name, scores[idx],
            (int)b[0], (int)b[1],
            (int)(b[2] - b[0]), (int)(b[3] - b[1])
        ));

        for (size_t j = i + 1; j < order.size(); j++) {
            int jdx = order[j];
            if (suppressed[jdx]) continue;
            auto& bj = boxes[jdx];
            // IoU
            float ix1 = std::max(b[0], bj[0]);
            float iy1 = std::max(b[1], bj[1]);
            float ix2 = std::min(b[2], bj[2]);
            float iy2 = std::min(b[3], bj[3]);
            float inter = std::max(0.f, ix2 - ix1) * std::max(0.f, iy2 - iy1);
            float area_i = (b[2]-b[0])*(b[3]-b[1]);
            float area_j = (bj[2]-bj[0])*(bj[3]-bj[1]);
            float iou = inter / (area_i + area_j - inter + 1e-6f);
            if (iou > NMS_THRESHOLD) suppressed[jdx] = true;
        }
    }

    return results;
}
#endif
