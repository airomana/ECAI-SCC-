package com.eldercare.ai.ocr

import android.content.Context
import android.graphics.Bitmap
import com.equationl.fastdeployocr.OCR
import com.equationl.fastdeployocr.OcrConfig
import com.equationl.fastdeployocr.RunPrecision
import com.equationl.fastdeployocr.RunType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 基于 PaddleOCR（equationl/paddleocr4android FastDeploy）的端侧文字识别。
 * 需在 assets 下放置模型：见 [PADDLE_OCR_ASSETS_PATH]。
 */
class PaddleOcrProcessor(private val context: Context) {

    companion object {
        /** 模型在 assets 中的目录，需包含 det/rec/cls 的 .pdmodel、.pdiparams 及 ppocr_keys_v1.txt */
        const val PADDLE_OCR_ASSETS_PATH = "paddle_ocr"
        private const val TAG = "PaddleOcrProcessor"
    }

    private val ocr = OCR(context)
    private var initialized = false

    /**
     * 初始化 PaddleOCR 模型（从 assets 复制并加载）。
     * 首次调用 [recognize] 时会自动执行；也可提前调用以缩短首次识别时间。
     */
    suspend fun init(): Boolean = withContext(Dispatchers.IO) {
        if (initialized) return@withContext true
        val config = OcrConfig().apply {
            modelPath = PADDLE_OCR_ASSETS_PATH
            detModelFileName = "det"
            recModelFileName = "rec"
            clsModelFileName = "cls"
            labelPath = "ppocr_keys_v1.txt"
            runType = RunType.All
            recRunPrecision = RunPrecision.LiteFp16
            detRunPrecision = RunPrecision.LiteFp16
            clsRunPrecision = RunPrecision.LiteFp16
            isDrwwTextPositionBox = false
        }
        ocr.initModelSync(config).fold(
            onSuccess = {
                initialized = it
                it
            },
            onFailure = {
                android.util.Log.e(TAG, "PaddleOCR init failed", it)
                false
            }
        )
    }

    /**
     * 对位图进行 OCR 识别，返回拼接后的文本（多行用换行符连接）。
     */
    suspend fun recognize(bitmap: Bitmap): String = withContext(Dispatchers.IO) {
        if (!initialized) {
            init()
        }
        if (!initialized) {
            return@withContext ""
        }
        ocr.runSync(bitmap).fold(
            onSuccess = { result -> result.simpleText.trim() },
            onFailure = { e ->
                android.util.Log.e(TAG, "PaddleOCR recognize failed", e)
                ""
            }
        )
    }

    fun close() {
        ocr.releaseModel()
        initialized = false
    }
}
