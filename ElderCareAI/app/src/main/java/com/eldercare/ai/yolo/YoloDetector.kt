package com.eldercare.ai.yolo

import android.graphics.Bitmap
import android.util.Log

/**
 * 端侧食材识别检测器 (基于 NCNN / YOLO)
 */
class YoloDetector {

    companion object {
        private const val TAG = "YoloDetector"
        
        init {
            try {
                System.loadLibrary("eldercare-ai")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load eldercare-ai native library", e)
            }
        }
    }

    /**
     * 初始化本地模型
     * @param modelPath 模型文件目录或路径
     * @return 是否初始化成功
     */
    external fun nativeInit(modelPath: String): Boolean

    /**
     * 本地识别图片中的食材
     * @param bitmap 待识别的图片
     * @return 识别结果数组
     */
    external fun nativeDetectObjects(bitmap: Bitmap): Array<DetectionResult>?

    /**
     * 识别结果数据类 (与 native-lib.cpp 对应)
     */
    data class DetectionResult(
        val className: String,
        val confidence: Float,
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int
    )
}
