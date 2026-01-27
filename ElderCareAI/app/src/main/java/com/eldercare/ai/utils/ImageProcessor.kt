package com.eldercare.ai.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import kotlin.math.max
import kotlin.math.min

/**
 * 图片处理工具类
 * 用于OCR前的图片预处理：裁剪、旋转、亮度调整等
 */
object ImageProcessor {
    
    /**
     * 裁剪图片
     * @param bitmap 原始图片
     * @param left 左边界（0-1比例）
     * @param top 上边界（0-1比例）
     * @param right 右边界（0-1比例）
     * @param bottom 下边界（0-1比例）
     */
    fun cropBitmap(
        bitmap: Bitmap,
        left: Float = 0f,
        top: Float = 0f,
        right: Float = 1f,
        bottom: Float = 1f
    ): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        
        val cropLeft = (left * width).toInt().coerceIn(0, width)
        val cropTop = (top * height).toInt().coerceIn(0, height)
        val cropRight = (right * width).toInt().coerceIn(cropLeft, width)
        val cropBottom = (bottom * height).toInt().coerceIn(cropTop, height)
        
        return Bitmap.createBitmap(
            bitmap,
            cropLeft,
            cropTop,
            cropRight - cropLeft,
            cropBottom - cropTop
        )
    }
    
    /**
     * 旋转图片
     * @param bitmap 原始图片
     * @param degrees 旋转角度（0, 90, 180, 270）
     */
    fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        if (degrees == 0f) return bitmap
        
        val matrix = Matrix().apply {
            postRotate(degrees)
        }
        
        return Bitmap.createBitmap(
            bitmap,
            0,
            0,
            bitmap.width,
            bitmap.height,
            matrix,
            true
        )
    }
    
    /**
     * 调整图片亮度
     * @param bitmap 原始图片
     * @param brightness 亮度调整值（-100到100，0为不变）
     */
    fun adjustBrightness(bitmap: Bitmap, brightness: Int): Bitmap {
        if (brightness == 0) return bitmap
        
        val adjustedBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, bitmap.config)
        val canvas = Canvas(adjustedBitmap)
        val paint = Paint().apply {
            colorFilter = android.graphics.ColorMatrixColorFilter(
                android.graphics.ColorMatrix().apply {
                    val adjust = brightness / 100f
                    set(floatArrayOf(
                        1f, 0f, 0f, 0f, adjust * 255f,
                        0f, 1f, 0f, 0f, adjust * 255f,
                        0f, 0f, 1f, 0f, adjust * 255f,
                        0f, 0f, 0f, 1f, 0f
                    ))
                }
            )
        }
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return adjustedBitmap
    }
    
    /**
     * 调整图片对比度
     * @param bitmap 原始图片
     * @param contrast 对比度（0.5-2.0，1.0为不变）
     */
    fun adjustContrast(bitmap: Bitmap, contrast: Float): Bitmap {
        if (contrast == 1f) return bitmap
        
        val adjustedBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, bitmap.config)
        val canvas = Canvas(adjustedBitmap)
        val paint = Paint().apply {
            colorFilter = android.graphics.ColorMatrixColorFilter(
                android.graphics.ColorMatrix().apply {
                    val scale = contrast
                    val translate = (-.5f * scale + .5f) * 255f
                    set(floatArrayOf(
                        scale, 0f, 0f, 0f, translate,
                        0f, scale, 0f, 0f, translate,
                        0f, 0f, scale, 0f, translate,
                        0f, 0f, 0f, 1f, 0f
                    ))
                }
            )
        }
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return adjustedBitmap
    }
    
    /**
     * 缩放图片（保持宽高比）
     * @param bitmap 原始图片
     * @param maxWidth 最大宽度
     * @param maxHeight 最大高度
     */
    fun scaleBitmap(bitmap: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        
        if (width <= maxWidth && height <= maxHeight) {
            return bitmap
        }
        
        val scale = min(
            maxWidth.toFloat() / width,
            maxHeight.toFloat() / height
        )
        
        val newWidth = (width * scale).toInt()
        val newHeight = (height * scale).toInt()
        
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }
    
    /**
     * 检测图片质量
     * @return 质量评分（0-100）
     */
    fun assessImageQuality(bitmap: Bitmap): ImageQuality {
        val width = bitmap.width
        val height = bitmap.height
        
        // 1. 分辨率检查
        val resolutionScore = when {
            width >= 1920 && height >= 1080 -> 30
            width >= 1280 && height >= 720 -> 25
            width >= 640 && height >= 480 -> 20
            else -> 10
        }
        
        // 2. 亮度检查（简单估算）
        val brightnessScore = estimateBrightness(bitmap)
        
        // 3. 清晰度检查（边缘检测，简化版）
        val sharpnessScore = estimateSharpness(bitmap)
        
        val totalScore = resolutionScore + brightnessScore + sharpnessScore
        
        return ImageQuality(
            score = totalScore,
            resolution = "$width x $height",
            brightness = brightnessScore,
            sharpness = sharpnessScore,
            suggestions = generateSuggestions(totalScore, brightnessScore, sharpnessScore)
        )
    }
    
    private fun estimateBrightness(bitmap: Bitmap): Int {
        // 采样检查（简化版）
        val sampleSize = 10
        var totalBrightness = 0L
        var sampleCount = 0
        
        for (x in 0 until bitmap.width step sampleSize) {
            for (y in 0 until bitmap.height step sampleSize) {
                val pixel = bitmap.getPixel(x, y)
                val r = android.graphics.Color.red(pixel)
                val g = android.graphics.Color.green(pixel)
                val b = android.graphics.Color.blue(pixel)
                totalBrightness += (r + g + b) / 3
                sampleCount++
            }
        }
        
        val avgBrightness = (totalBrightness / sampleCount).toInt()
        
        return when {
            avgBrightness in 100..180 -> 35 // 理想亮度
            avgBrightness in 80..200 -> 25 // 可接受
            avgBrightness < 80 -> 15 // 太暗
            else -> 20 // 太亮
        }
    }
    
    private fun estimateSharpness(bitmap: Bitmap): Int {
        // 简化版清晰度检测（基于边缘对比度）
        // 实际应用中可以使用Sobel算子等
        var edgeCount = 0
        val threshold = 30
        
        for (x in 1 until bitmap.width - 1) {
            for (y in 1 until bitmap.height - 1) {
                val center = bitmap.getPixel(x, y)
                val right = bitmap.getPixel(x + 1, y)
                val bottom = bitmap.getPixel(x, y + 1)
                
                val diff1 = kotlin.math.abs(
                    android.graphics.Color.red(center) - android.graphics.Color.red(right)
                )
                val diff2 = kotlin.math.abs(
                    android.graphics.Color.red(center) - android.graphics.Color.red(bottom)
                )
                
                if (diff1 > threshold || diff2 > threshold) {
                    edgeCount++
                }
            }
        }
        
        val edgeRatio = edgeCount.toFloat() / (bitmap.width * bitmap.height)
        
        return when {
            edgeRatio > 0.1f -> 35 // 清晰
            edgeRatio > 0.05f -> 25 // 一般
            else -> 15 // 模糊
        }
    }
    
    private fun generateSuggestions(
        totalScore: Int,
        brightnessScore: Int,
        sharpnessScore: Int
    ): List<String> {
        val suggestions = mutableListOf<String>()
        
        if (totalScore < 60) {
            suggestions.add("图片质量较低，可能影响识别准确率")
        }
        
        if (brightnessScore < 20) {
            suggestions.add("图片较暗，建议在光线充足的地方重新拍摄")
        }
        
        if (sharpnessScore < 20) {
            suggestions.add("图片可能模糊，建议保持手机稳定并重新拍摄")
        }
        
        if (suggestions.isEmpty()) {
            suggestions.add("图片质量良好")
        }
        
        return suggestions
    }
}

/**
 * 图片质量评估结果
 */
data class ImageQuality(
    val score: Int, // 总分（0-100）
    val resolution: String,
    val brightness: Int,
    val sharpness: Int,
    val suggestions: List<String>
)