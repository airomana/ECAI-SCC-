package com.eldercare.ai.utils

import android.util.Log

/**
 * 统一的性能测试日志工具。
 * 用于在 Logcat 中输出模块开始、阶段耗时和总耗时。
 */
class ModulePerformanceTracker internal constructor(
    private val tag: String,
    private val startMessage: String
) {
    private val moduleStartTime = System.currentTimeMillis()

    init {
        Log.d(tag, "==== 性能测试：$startMessage ====")
    }

    fun mark(): Long = System.currentTimeMillis()

    fun logDuration(label: String, startTime: Long) {
        val cost = System.currentTimeMillis() - startTime
        Log.d(tag, "==== 性能测试：${label}耗时：${cost} ms ====")
    }

    fun logTotal(label: String) {
        val cost = System.currentTimeMillis() - moduleStartTime
        Log.d(tag, "==== 性能测试：${label}：${cost} ms ====")
    }
}

fun createWeeklyReportPerformanceTracker(): ModulePerformanceTracker {
    return ModulePerformanceTracker(
        tag = "WeeklyReport",
        startMessage = "周报生成开始"
    )
}

fun createMenuScanPerformanceTracker(): ModulePerformanceTracker {
    return ModulePerformanceTracker(
        tag = "MenuScan",
        startMessage = "拍菜单开始处理"
    )
}

fun createVoiceDiaryPerformanceTracker(): ModulePerformanceTracker {
    return ModulePerformanceTracker(
        tag = "VoiceDiary",
        startMessage = "语音日记收到识别文本，开始生成回复"
    )
}

fun createFridgeScanPerformanceTracker(): ModulePerformanceTracker {
    return ModulePerformanceTracker(
        tag = "FridgeScan",
        startMessage = "拍冰箱开始处理"
    )
}
