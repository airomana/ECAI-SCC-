package com.eldercare.ai.ui.screens.menu

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import kotlin.math.max
import kotlin.math.min
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.eldercare.ai.data.ElderCareDatabase
import com.eldercare.ai.data.SettingsManager
import com.eldercare.ai.data.entity.Dish
import com.eldercare.ai.health.HealthRiskEvaluator
import com.eldercare.ai.llm.LlmService
import com.eldercare.ai.ocr.DishNameExtractor
import com.eldercare.ai.ocr.DishKnowledgeMatcher
import com.eldercare.ai.ocr.MlKitOcrProcessor
import com.eldercare.ai.rememberElderCareDatabase
import com.eldercare.ai.tts.TtsService
import com.eldercare.ai.ui.theme.ElderCareAITheme
import com.eldercare.ai.utils.ImageProcessor
import com.eldercare.ai.utils.ImageQuality
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuScanScreen(
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    var isScanning by remember { mutableStateOf(false) }
    var scanResults by remember { mutableStateOf<List<DishResult>>(emptyList()) }
    var recognizedText by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var photoFile by remember { mutableStateOf<File?>(null) }
    var imageQuality by remember { mutableStateOf<ImageQuality?>(null) }
    var showCropDialog by remember { mutableStateOf(false) }
    var debugInfo by remember { mutableStateOf<String?>(null) }  // 调试信息

    val scope = rememberCoroutineScope()
    val db = rememberElderCareDatabase()
    val settingsManager = remember { SettingsManager.getInstance(context) }
    val ocrProcessor = remember { MlKitOcrProcessor() }
    val riskEvaluator = remember { HealthRiskEvaluator() }
    val ttsService = remember { TtsService.getInstance(context) }
    val llmService = remember { LlmService.getInstance(context) }
    
    // 读取健康档案和TTS设置
    var healthProfile by remember { mutableStateOf<com.eldercare.ai.data.entity.HealthProfile?>(null) }
    LaunchedEffect(Unit) {
        healthProfile = db.healthProfileDao().getOnce()
        // 初始化TTS状态
        ttsService.setEnabled(settingsManager.ttsEnabled)
    }

    DisposableEffect(Unit) {
        onDispose { 
            ocrProcessor.close()
        }
    }

    // 创建临时照片文件
    fun createPhotoFile(): File {
        val storageDir = context.cacheDir
        // 确保目录存在
        if (!storageDir.exists()) {
            storageDir.mkdirs()
        }
        val file = File.createTempFile(
            "menu_photo_${System.currentTimeMillis()}",
            ".jpg",
            storageDir
        )
        photoFile = file
        return file
    }

    // 高清拍照（使用FileProvider）
    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (!success || photoFile == null) return@rememberLauncherForActivityResult

        val bitmap = BitmapFactory.decodeFile(photoFile!!.absolutePath)
        if (bitmap == null) {
            errorMessage = "无法读取照片"
            return@rememberLauncherForActivityResult
        }

        capturedBitmap = bitmap
        showCropDialog = true  // 显示裁剪界面
        errorMessage = null
        recognizedText = null
        scanResults = emptyList()
    }
    
    // 处理图片（OCR识别和菜品分析）
    fun processImage(bitmap: Bitmap) {
        isScanning = true
        errorMessage = null
        recognizedText = null
        scanResults = emptyList()
        
        scope.launch {
            try {
                // 图片质量检查
                val quality = withContext(Dispatchers.Default) {
                    ImageProcessor.assessImageQuality(bitmap)
                }
                imageQuality = quality
                
                // 图片预处理（如果质量不佳，进行优化）
                var processedBitmap = bitmap
                if (quality.score < 70) {
                    // 自动调整亮度和对比度
                    processedBitmap = withContext(Dispatchers.Default) {
                        var adjusted = ImageProcessor.adjustBrightness(processedBitmap, 10)
                        adjusted = ImageProcessor.adjustContrast(adjusted, 1.1f)
                        // 如果图片太大，适当缩放（OCR不需要太高分辨率）
                        ImageProcessor.scaleBitmap(adjusted, 1920, 1920)
                    }
                } else {
                    // 即使质量好，也适当缩放以加快OCR速度
                    processedBitmap = withContext(Dispatchers.Default) {
                        ImageProcessor.scaleBitmap(processedBitmap, 1920, 1920)
                    }
                }
                
                // OCR识别
                val text = ocrProcessor.recognize(processedBitmap)
                recognizedText = text
                
                // 提取菜名
                val dishCandidates = DishNameExtractor.extractDishCandidates(text)
                
                // 限制处理数量（避免太多API调用）
                val limitedCandidates = dishCandidates.take(10)
                
                // 生成调试信息（初始）
                var debugInfoBuilder = StringBuilder().apply {
                    appendLine("【调试信息】")
                    appendLine("OCR文本长度: ${text.length}")
                    appendLine("行数: ${text.split('\n').filter { it.isNotBlank() }.size}")
                    appendLine("提取到菜名数: ${dishCandidates.size}")
                    appendLine("处理菜名数: ${limitedCandidates.size}")
                }
                
                // 查询菜品知识库并生成智能建议
                val results = mutableListOf<DishResult>()
                var llmCallCount = 0  // 限制LLM调用次数
                val maxLlmCalls = 10  // 最多调用10次LLM（覆盖所有处理的菜品）
                
                for (candidate in limitedCandidates) {
                    try {
                        // 查询知识库（多级匹配策略）
                        val dishInfo = withContext(Dispatchers.IO) {
                            try {
                                // 1. 精确匹配
                                db.dishDao().getByName(candidate.name)
                                    // 2. 模糊搜索（SQL LIKE）
                                    ?: db.dishDao().searchByName(candidate.name, limit = 5).firstOrNull()
                                    // 3. 智能匹配（获取所有菜品进行相似度匹配）
                                    ?: run {
                                        val allDishes = db.dishDao().getAllOnce()
                                        DishKnowledgeMatcher.matchDish(candidate.name, allDishes)
                                    }
                            } catch (e: Exception) {
                                null
                            }
                        }
                        
                        // 评估健康风险
                        val riskResult = riskEvaluator.evaluateRisk(candidate.name, healthProfile)
                        
                        // 生成大白话描述
                        val plainDescription = if (dishInfo != null) {
                            // 知识库中有，直接使用
                            dishInfo.plainDescription
                        } else if (llmCallCount < maxLlmCalls) {
                            // 知识库中没有，调用LLM生成（限制次数）
                            llmCallCount++
                            try {
                                // 检查LLM是否配置和启用
                                if (com.eldercare.ai.llm.LlmConfig.isConfigured() && 
                                    com.eldercare.ai.llm.LlmConfig.isEnabled(context)) {
                                    llmService.generatePlainDescription(candidate.name, healthProfile)
                                        ?: generateSimpleDescription(candidate.name)
                                } else {
                                    // LLM未配置或未启用，直接使用本地描述
                                    generateSimpleDescription(candidate.name)
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("MenuScan", "LLM调用失败: ${candidate.name}", e)
                                // LLM失败时使用本地描述，不影响结果展示
                                generateSimpleDescription(candidate.name)
                            }
                        } else {
                            // 超过LLM调用限制，使用本地描述
                            generateSimpleDescription(candidate.name)
                        }
                        
                        // 生成个性化健康建议
                        val personalizedAdvice = generatePersonalizedAdvice(
                            dishInfo = dishInfo,
                            dishName = candidate.name,
                            healthProfile = healthProfile,
                            riskResult = riskResult
                        )
                        
                        // 调试日志：确保风险等级和建议匹配
                        android.util.Log.d("MenuScan", "菜品: ${candidate.name}, 风险等级: ${riskResult.riskLevel}, 建议: $personalizedAdvice")
                        
                        results.add(DishResult(
                            name = candidate.name,
                            description = plainDescription,
                            healthAdvice = personalizedAdvice,
                            riskLevel = riskResult.riskLevel,
                            reasons = riskResult.reasons,
                            dishInfo = dishInfo
                        ))
                    } catch (e: Exception) {
                        // 单个菜品处理失败，跳过继续处理其他
                        android.util.Log.e("MenuScan", "处理菜品失败: ${candidate.name}", e)
                    }
                }
                
                // 确保至少有一些结果（即使处理失败）
                android.util.Log.d("MenuScan", "处理完成，结果数量: ${results.size}")
                if (results.isEmpty() && limitedCandidates.isNotEmpty()) {
                    android.util.Log.w("MenuScan", "警告：提取到${limitedCandidates.size}个菜名，但处理结果为空")
                }
                
                scanResults = results
                
                // 更新调试信息（显示最终结果）
                debugInfo = debugInfoBuilder.apply {
                    appendLine("成功处理结果数: ${results.size}")
                    appendLine("LLM调用次数: $llmCallCount")
                    if (results.isNotEmpty()) {
                        appendLine("结果列表: ${results.map { it.name }.joinToString(", ")}")
                    } else if (limitedCandidates.isNotEmpty()) {
                        appendLine("⚠️ 警告：提取到菜名但处理失败")
                        appendLine("前3个菜名: ${limitedCandidates.take(3).map { it.name }.joinToString(", ")}")
                    }
                }.toString()
                
                // TTS播报（使用大白话）
                android.util.Log.d("MenuScan", "准备TTS播报，结果数量: ${results.size}")
                if (results.isNotEmpty()) {
                    val highRiskCount = results.count { it.riskLevel == RiskLevel.HIGH }
                    val userName = healthProfile?.name?.takeIf { it.isNotBlank() } ?: "您"
                    
                    if (results.size == 1) {
                        // 单道菜，详细播报
                        val result = results.first()
                        val message = buildString {
                            append("${userName}，识别到${result.name}。")
                            append(result.description)
                            append("。")
                            append(result.healthAdvice)
                        }
                        android.util.Log.d("MenuScan", "TTS播报单道菜: $message")
                        ttsService.speak(message, priority = 1)
                    } else {
                        // 多道菜，摘要播报
                        val message = buildString {
                            append("${userName}，识别到${results.size}道菜")
                            if (highRiskCount > 0) {
                                append("，其中${highRiskCount}道菜需要特别注意")
                            }
                            append("，可以查看详细建议")
                        }
                        android.util.Log.d("MenuScan", "TTS播报多道菜: $message")
                        ttsService.speak(message, priority = 1)
                        
                        // 如果有高风险菜品，单独播报
                        results.filter { it.riskLevel == RiskLevel.HIGH }.take(2).forEach { result ->
                            val warningMessage = "${result.name}，${result.healthAdvice}"
                            android.util.Log.d("MenuScan", "TTS播报高风险: $warningMessage")
                            ttsService.speak(warningMessage, priority = 1)
                        }
                    }
                } else if (text.isNotBlank()) {
                    android.util.Log.d("MenuScan", "TTS播报：未提取到菜名")
                    ttsService.speak("已识别文字，但未提取到菜名，请查看识别结果")
                }
                
            } catch (e: Exception) {
                errorMessage = "识别失败：${e.message ?: "未知错误"}"
                ttsService.speak("识别失败，请重试")
            } finally {
                isScanning = false
            }
        }
    }

    // 启动相机的辅助函数（必须在takePictureLauncher之后定义）
    fun launchCamera() {
        try {
            val file = createPhotoFile()
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            takePictureLauncher.launch(uri)
        } catch (e: Exception) {
            errorMessage = "无法创建照片文件：${e.message}"
            ttsService.speak("拍照失败，请重试")
        }
    }

    // 相机权限请求
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // 权限授予后，启动相机
            launchCamera()
        } else {
            // 权限被拒绝
            errorMessage = "需要相机权限才能拍照，请在设置中授予权限"
            ttsService.speak("需要相机权限才能使用此功能")
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        // 顶部导航栏
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onNavigateBack,
                modifier = Modifier.size(64.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "返回",
                    modifier = Modifier.size(48.dp)
                )
            }
            
            Text(
                text = "拍菜单",
                style = MaterialTheme.typography.displayMedium,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.size(64.dp))
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // 裁剪对话框
        if (showCropDialog && capturedBitmap != null) {
            CropImageDialog(
                bitmap = capturedBitmap!!,
                onCropConfirm = { croppedBitmap ->
                    showCropDialog = false
                    capturedBitmap = croppedBitmap
                    // 开始处理裁剪后的图片
                    processImage(croppedBitmap)
                },
                onCropCancel = {
                    showCropDialog = false
                    capturedBitmap = null
                    photoFile?.delete()
                    photoFile = null
                }
            )
        }
        
        val hasAnyResult = (recognizedText != null) || (errorMessage != null)
        if (!hasAnyResult) {
            // 拍照界面
            CameraSection(
                isScanning = isScanning,
                capturedBitmap = capturedBitmap,
                onStartScan = {
                    // 检查相机权限
                    when {
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED -> {
                            // 已有权限，直接启动相机
                            launchCamera()
                        }
                        else -> {
                            // 请求权限
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    }
                }
            )
        } else {
            // 结果展示界面
            ResultsSection(
                capturedBitmap = capturedBitmap,
                results = scanResults,
                recognizedText = recognizedText,
                errorMessage = errorMessage,
                imageQuality = imageQuality,
                debugInfo = debugInfo,
                isScanning = isScanning,
                onRescan = {
                    scanResults = emptyList()
                    recognizedText = null
                    errorMessage = null
                    capturedBitmap = null
                    imageQuality = null
                    debugInfo = null
                    photoFile?.delete()
                    photoFile = null
                }
            )
        }
    }
}

@Composable
fun CameraSection(
    isScanning: Boolean,
    capturedBitmap: Bitmap?,
    onStartScan: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // 相机预览区域
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                if (isScanning) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(64.dp),
                            strokeWidth = 6.dp
                        )
                        Text(
                            text = "正在识别菜单...",
                            style = MaterialTheme.typography.headlineMedium
                        )
                    }
                } else {
                    if (capturedBitmap != null) {
                        Image(
                            bitmap = capturedBitmap.asImageBitmap(),
                            contentDescription = "菜单照片",
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = "相机",
                                modifier = Modifier.size(80.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "对准菜单拍照",
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(48.dp))
        
        // 拍照按钮
        if (!isScanning) {
            Button(
                onClick = onStartScan,
                modifier = Modifier
                    .size(120.dp),
                shape = RoundedCornerShape(60.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = "拍照",
                    modifier = Modifier.size(48.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // 提示文字
        Text(
            text = if (isScanning) "请稍等，正在分析菜单..." else "请将菜单放在相机前，点击拍照按钮",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun ResultsSection(
    capturedBitmap: Bitmap?,
    results: List<DishResult>,
    recognizedText: String?,
    errorMessage: String?,
    imageQuality: ImageQuality?,
    debugInfo: String?,
    isScanning: Boolean,
    onRescan: () -> Unit
) {
    // 使用LazyColumn实现可滚动
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 重新扫描按钮
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onRescan) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "重新扫描",
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "重新扫描",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }

        // 照片
        if (capturedBitmap != null) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Image(
                        bitmap = capturedBitmap.asImageBitmap(),
                        contentDescription = "菜单照片",
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        // 图片质量提示
        if (imageQuality != null && imageQuality.score < 70) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "质量提示",
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Text(
                                text = "图片质量：${imageQuality.score}分",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        imageQuality.suggestions.forEach { suggestion ->
                            Text(
                                text = "• $suggestion",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }
        }

        // 错误消息
        if (errorMessage != null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = errorMessage,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        // 结果列表或空状态
        if (isScanning) {
            // 正在处理中，显示加载状态
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(48.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "正在分析菜单，请稍候...",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else if (results.isEmpty()) {
            // 处理完成但没有结果，显示空状态
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "提示",
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(28.dp)
                            )
                            Text(
                                text = "未识别到菜品",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                        Text(
                            text = "可能原因：",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Text(
                            text = "• 照片不够清晰\n• 菜单文字太小\n• 拍摄角度倾斜\n• 不是常见的菜单格式",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Text(
                            text = "建议：请重新拍摄一张清晰的菜单照片，确保文字清晰可见",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }
        } else {
            // 结果列表
            items(results) { result ->
                DishResultCard(result = result)
            }
        }
    }
}

@Composable
fun DishResultCard(result: DishResult) {
    var expanded by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (result.riskLevel) {
                RiskLevel.HIGH -> MaterialTheme.colorScheme.errorContainer  // 红色
                RiskLevel.MEDIUM -> MaterialTheme.colorScheme.secondaryContainer  // 黄色
                RiskLevel.LOW -> Color(0xFFE8F5E9)  // 明确的浅绿色
            }
        ),
        shape = RoundedCornerShape(16.dp),
        onClick = { expanded = !expanded }
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 菜名和风险等级
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = result.name,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Icon(
                    imageVector = when (result.riskLevel) {
                        RiskLevel.HIGH -> Icons.Default.Warning
                        RiskLevel.MEDIUM -> Icons.Default.Info
                        RiskLevel.LOW -> Icons.Default.CheckCircle
                    },
                    contentDescription = result.riskLevel.name,
                    tint = when (result.riskLevel) {
                        RiskLevel.HIGH -> MaterialTheme.colorScheme.error
                        RiskLevel.MEDIUM -> MaterialTheme.colorScheme.secondary
                        RiskLevel.LOW -> MaterialTheme.colorScheme.primary
                    },
                    modifier = Modifier.size(32.dp)
                )
            }
            
            // 菜品描述（大白话）
            Text(
                text = result.description,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(vertical = 4.dp)
            )
            
            // 提示：点击查看详情
            if (!expanded) {
                Text(
                    text = "点击查看详细信息",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            
            // 健康建议
            Text(
                text = result.healthAdvice,
                style = MaterialTheme.typography.bodyLarge,
                color = when (result.riskLevel) {
                    RiskLevel.HIGH -> MaterialTheme.colorScheme.error
                    RiskLevel.MEDIUM -> MaterialTheme.colorScheme.secondary
                    RiskLevel.LOW -> MaterialTheme.colorScheme.primary
                }
            )
            
            // 展开显示详细信息
            if (expanded) {
                Column(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 知识库详细信息
                    if (result.dishInfo != null) {
                        Divider()
                        
                        // 主要成分
                        if (result.dishInfo.ingredients.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "主要成分：",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = result.dishInfo.ingredients.joinToString("、"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        
                        // 烹饪方式
                        if (result.dishInfo.cookingMethod.isNotBlank()) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "做法：",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = result.dishInfo.cookingMethod,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        
                        // 营养信息
                        if (result.dishInfo.nutrients.isNotBlank()) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "营养：",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = result.dishInfo.nutrients,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        
                        // 标签
                        if (result.dishInfo.tags.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "特点：",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    result.dishInfo.tags.forEach { tag ->
                                        Surface(
                                            color = MaterialTheme.colorScheme.secondaryContainer,
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text(
                                                text = tag,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        
                        Divider()
                    }
                    
                    // 详细原因
                    if (result.reasons.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "详细原因：",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            result.reasons.forEach { reason ->
                                Text(
                                    text = "• $reason",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

data class DishResult(
    val name: String,
    val description: String,
    val healthAdvice: String,
    val riskLevel: RiskLevel,
    val reasons: List<String> = emptyList(),
    val dishInfo: Dish? = null // 知识库信息
)

enum class RiskLevel {
    LOW, MEDIUM, HIGH
}

/**
 * 生成简单的本地描述（不调用网络API）
 * 根据菜名中的关键词生成更详细的描述
 */
private fun generateSimpleDescription(dishName: String): String {
    // 组合多个关键词生成更详细的描述
    val cookingMethod = when {
        dishName.contains("红烧") -> "红烧的"
        dishName.contains("清蒸") -> "清蒸的"
        dishName.contains("水煮") -> "水煮的，可能比较辣"
        dishName.contains("干煸") -> "干煸的，比较香"
        dishName.contains("糖醋") -> "糖醋口味的，酸甜"
        dishName.contains("鱼香") -> "鱼香口味的，下饭"
        dishName.contains("宫保") -> "宫保风味的，微辣"
        dishName.contains("麻婆") -> "麻婆风味的，麻辣"
        dishName.contains("炒") -> "炒的"
        dishName.contains("蒸") -> "清蒸的，清淡"
        dishName.contains("煮") -> "煮的"
        dishName.contains("炸") -> "油炸的，油比较大"
        dishName.contains("烤") -> "烤制的"
        dishName.contains("炖") -> "炖的，比较烂"
        dishName.contains("烧") -> "烧的"
        dishName.contains("煎") -> "煎的"
        dishName.contains("焖") -> "焖的，很入味"
        dishName.contains("凉拌") -> "凉拌的，清爽"
        else -> null
    }
    
    val mainIngredient = when {
        dishName.contains("肉") && !dishName.contains("鸡肉") && !dishName.contains("鸭肉") -> "肉类"
        dishName.contains("鸡肉") || dishName.contains("鸡") -> "鸡肉"
        dishName.contains("鸭肉") || dishName.contains("鸭") -> "鸭肉"
        dishName.contains("鱼") -> "鱼"
        dishName.contains("虾") -> "虾"
        dishName.contains("蛋") -> "蛋"
        dishName.contains("豆腐") -> "豆腐，蛋白质丰富"
        dishName.contains("青菜") || dishName.contains("白菜") -> "蔬菜"
        dishName.contains("茄子") || dishName.contains("茄") -> "茄子"
        dishName.contains("土豆") -> "土豆"
        dishName.contains("黄瓜") -> "黄瓜"
        dishName.contains("西红柿") || dishName.contains("番茄") -> "西红柿"
        dishName.contains("玉米") -> "玉米"
        dishName.contains("韭菜") -> "韭菜"
        dishName.contains("酸菜") -> "酸菜"
        dishName.contains("粉丝") -> "粉丝"
        else -> null
    }
    
    val dishType = when {
        dishName.contains("饭") -> "主食，米饭类"
        dishName.contains("面") -> "主食，面食类"
        dishName.contains("粥") -> "粥，易消化"
        dishName.contains("饺") -> "饺子"
        dishName.contains("包") -> "包子"
        dishName.contains("饼") -> "饼"
        dishName.contains("汤") -> "汤品，暖胃"
        dishName.contains("羹") -> "羹汤"
        dishName.contains("锅") -> "锅类菜品"
        else -> null
    }
    
    // 组合描述
    return buildString {
        when {
            dishType != null -> {
                append(dishType)
            }
            cookingMethod != null && mainIngredient != null -> {
                append("${mainIngredient}${cookingMethod}")
                if (cookingMethod.contains("油")) {
                    append("，油比较大")
                } else if (cookingMethod.contains("清淡") || cookingMethod.contains("清蒸")) {
                    append("，比较健康")
                }
            }
            mainIngredient != null -> {
                append("${mainIngredient}类菜品")
            }
            cookingMethod != null -> {
                append(cookingMethod)
                if (cookingMethod.contains("油")) {
                    append("，油比较大")
                }
            }
            else -> {
                append("菜单上的菜品")
            }
        }
    }
}

/**
 * 生成个性化健康建议（结合知识库和健康档案）
 * 实现"大白话"翻译引擎的核心逻辑
 */
private fun generatePersonalizedAdvice(
    dishInfo: Dish?,
    dishName: String,
    healthProfile: com.eldercare.ai.data.entity.HealthProfile?,
    riskResult: com.eldercare.ai.health.HealthRiskResult
): String {
    val userName = healthProfile?.name?.takeIf { it.isNotBlank() } ?: "您"
    
    // 如果有知识库信息，使用更详细的建议
    if (dishInfo != null) {
        val diseases = healthProfile?.diseases ?: emptyList()
        val allergies = healthProfile?.allergies ?: emptyList()
        
        // 检查是否不适合
        val notSuitable = dishInfo.notSuitableFor.any { disease ->
            diseases.any { it.contains(disease, ignoreCase = true) }
        }
        
        // 检查过敏
        val hasAllergy = allergies.any { allergy ->
            dishInfo.ingredients.any { it.contains(allergy, ignoreCase = true) }
        }
        
        return when {
            hasAllergy -> {
                "⚠️ ${userName}，这个${dishName}可能含有您过敏的成分，建议不要吃"
            }
            notSuitable -> {
                val disease = diseases.firstOrNull { d ->
                    dishInfo.notSuitableFor.any { it.contains(d, ignoreCase = true) }
                } ?: "您的疾病"
                "⚠️ ${userName}，这个${dishName}${dishInfo.plainDescription}，您有${disease}，建议少吃或不吃"
            }
            riskResult.riskLevel == RiskLevel.HIGH -> {
                val reason = riskResult.reasons.firstOrNull() ?: "不太适合"
                "${userName}，这个${dishName}${dishInfo.plainDescription}，${reason}，建议少吃"
            }
            riskResult.riskLevel == RiskLevel.MEDIUM -> {
                "${userName}，这个${dishName}${dishInfo.plainDescription}，可以适量吃一点，但要注意控制分量"
            }
            else -> {
                "✅ ${userName}，这个${dishName}${dishInfo.plainDescription}，比较适合您，可以放心吃"
            }
        }
    } else {
        // 没有知识库信息，使用风险评估结果
        return when (riskResult.riskLevel) {
            RiskLevel.HIGH -> {
                val reason = riskResult.reasons.firstOrNull() ?: "不太适合"
                "⚠️ ${userName}，这个${dishName}${reason}，建议少吃或不吃"
            }
            RiskLevel.MEDIUM -> {
                val reason = riskResult.reasons.firstOrNull()
                if (reason != null) {
                    "${userName}，这个${dishName}${reason}，可以适量食用，但需注意控制分量"
                } else {
                    "${userName}，这个${dishName}可以适量食用，但需注意控制分量"
                }
            }
            RiskLevel.LOW -> {
                "✅ ${userName}，这个${dishName}比较适合您，可以放心食用"
            }
        }
    }
}

/**
 * 图片裁剪对话框
 * 允许用户拖拽选择要识别的区域
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CropImageDialog(
    bitmap: Bitmap,
    onCropConfirm: (Bitmap) -> Unit,
    onCropCancel: () -> Unit
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    
    // 图片显示尺寸（相对于Box，因为Image使用FillMaxSize，所以就是Box的尺寸）
    var imageSize by remember { mutableStateOf(Size.Zero) }
    
    // 裁剪框位置（相对于图片显示区域的比例，0-1）
    var cropRect by remember { 
        mutableStateOf(
            // 默认选择中间80%的区域
            Rect(0.1f, 0.1f, 0.9f, 0.9f)
        )
    }
    
    // 拖拽状态
    var dragState by remember { mutableStateOf<DragState?>(null) }
    
    Dialog(onDismissRequest = onCropCancel) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // 标题栏
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "选择识别区域",
                        style = MaterialTheme.typography.titleLarge
                    )
                    IconButton(onClick = onCropCancel) {
                        Icon(Icons.Default.Close, "取消")
                    }
                }
                
                // 图片显示区域（可拖拽选择）
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(16.dp)
                ) {
                    // 显示图片
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "待裁剪图片",
                        modifier = Modifier
                            .fillMaxSize()
                            .onGloballyPositioned { coordinates ->
                                imageSize = Size(
                                    coordinates.size.width.toFloat(),
                                    coordinates.size.height.toFloat()
                                )
                            },
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit
                    )
                    
                    // 裁剪框覆盖层（半透明遮罩 + 可拖拽的裁剪框）
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        // 计算点击位置相对于图片的位置（0-1）
                                        // 因为Canvas和Image都在同一个Box中，offset就是相对于Box的坐标
                                        if (imageSize.width > 0 && imageSize.height > 0) {
                                            val relativeX = (offset.x / imageSize.width).coerceIn(0f, 1f)
                                            val relativeY = (offset.y / imageSize.height).coerceIn(0f, 1f)
                                            
                                            val cropX = cropRect.left * imageSize.width
                                            val cropY = cropRect.top * imageSize.height
                                            val cropWidth = (cropRect.right - cropRect.left) * imageSize.width
                                            val cropHeight = (cropRect.bottom - cropRect.top) * imageSize.height
                                            
                                            val cropLeft = cropX
                                            val cropTop = cropY
                                            val cropRight = cropX + cropWidth
                                            val cropBottom = cropY + cropHeight
                                            
                                            val margin = with(density) { 20.dp.toPx() }
                                            
                                            // 判断拖拽类型
                                            dragState = when {
                                                // 拖拽左上角
                                                offset.x in (cropLeft - margin)..(cropLeft + margin) &&
                                                offset.y in (cropTop - margin)..(cropTop + margin) -> 
                                                    DragState.ResizeTopLeft(offset)
                                                // 拖拽右上角
                                                offset.x in (cropRight - margin)..(cropRight + margin) &&
                                                offset.y in (cropTop - margin)..(cropTop + margin) -> 
                                                    DragState.ResizeTopRight(offset)
                                                // 拖拽左下角
                                                offset.x in (cropLeft - margin)..(cropLeft + margin) &&
                                                offset.y in (cropBottom - margin)..(cropBottom + margin) -> 
                                                    DragState.ResizeBottomLeft(offset)
                                                // 拖拽右下角
                                                offset.x in (cropRight - margin)..(cropRight + margin) &&
                                                offset.y in (cropBottom - margin)..(cropBottom + margin) -> 
                                                    DragState.ResizeBottomRight(offset)
                                                // 拖拽整个框
                                                offset.x in cropLeft..cropRight &&
                                                offset.y in cropTop..cropBottom -> 
                                                    DragState.Move(offset)
                                                else -> null
                                            }
                                        }
                                    },
                                    onDrag = { change, _ ->
                                        dragState?.let { state ->
                                            // 计算相对位置（0-1），offset就是相对于Box的坐标
                                            val relativeX = (change.position.x / imageSize.width).coerceIn(0f, 1f)
                                            val relativeY = (change.position.y / imageSize.height).coerceIn(0f, 1f)
                                            
                                            val newRect = when (state) {
                                                is DragState.Move -> {
                                                    val deltaX = (change.position.x - state.startOffset.x) / imageSize.width
                                                    val deltaY = (change.position.y - state.startOffset.y) / imageSize.height
                                                    val cropWidth = cropRect.right - cropRect.left
                                                    val cropHeight = cropRect.bottom - cropRect.top
                                                    Rect(
                                                        (cropRect.left + deltaX).coerceIn(0f, 1f - cropWidth),
                                                        (cropRect.top + deltaY).coerceIn(0f, 1f - cropHeight),
                                                        (cropRect.right + deltaX).coerceIn(cropWidth, 1f),
                                                        (cropRect.bottom + deltaY).coerceIn(cropHeight, 1f)
                                                    )
                                                }
                                                is DragState.ResizeTopLeft -> {
                                                    val minSize = 0.1f
                                                    Rect(
                                                        relativeX.coerceIn(0f, cropRect.right - minSize),
                                                        relativeY.coerceIn(0f, cropRect.bottom - minSize),
                                                        cropRect.right,
                                                        cropRect.bottom
                                                    )
                                                }
                                                is DragState.ResizeTopRight -> {
                                                    val minSize = 0.1f
                                                    Rect(
                                                        cropRect.left,
                                                        relativeY.coerceIn(0f, cropRect.bottom - minSize),
                                                        relativeX.coerceIn(cropRect.left + minSize, 1f),
                                                        cropRect.bottom
                                                    )
                                                }
                                                is DragState.ResizeBottomLeft -> {
                                                    val minSize = 0.1f
                                                    Rect(
                                                        relativeX.coerceIn(0f, cropRect.right - minSize),
                                                        cropRect.top,
                                                        cropRect.right,
                                                        relativeY.coerceIn(cropRect.top + minSize, 1f)
                                                    )
                                                }
                                                is DragState.ResizeBottomRight -> {
                                                    val minSize = 0.1f
                                                    Rect(
                                                        cropRect.left,
                                                        cropRect.top,
                                                        relativeX.coerceIn(cropRect.left + minSize, 1f),
                                                        relativeY.coerceIn(cropRect.top + minSize, 1f)
                                                    )
                                                }
                                            }
                                            cropRect = newRect
                                            dragState = state.copy(offset = change.position)
                                        }
                                    },
                                    onDragEnd = {
                                        dragState = null
                                    }
                                )
                            }
                    ) {
                        // 绘制半透明遮罩
                        val cropX = cropRect.left * imageSize.width
                        val cropY = cropRect.top * imageSize.height
                        val cropWidth = (cropRect.right - cropRect.left) * imageSize.width
                        val cropHeight = (cropRect.bottom - cropRect.top) * imageSize.height
                        
                        // 创建裁剪路径（排除裁剪框区域）
                        val cropPath = Path().apply {
                            // 外部矩形
                            addRect(androidx.compose.ui.geometry.Rect(Offset.Zero, imageSize))
                            // 减去裁剪框
                            addRect(androidx.compose.ui.geometry.Rect(
                                Offset(cropX, cropY),
                                Size(cropWidth, cropHeight)
                            ))
                            fillType = PathFillType.EvenOdd
                        }
                        
                        // 绘制半透明遮罩
                        clipPath(cropPath) {
                            drawRect(
                                color = Color.Black.copy(alpha = 0.6f),
                                size = imageSize
                            )
                        }
                        
                        // 绘制裁剪框边框
                        drawRect(
                            color = Color.White,
                            topLeft = Offset(cropX, cropY),
                            size = Size(cropWidth, cropHeight),
                            style = Stroke(width = with(density) { 3.dp.toPx() })
                        )
                        
                        // 绘制四个角的控制点
                        val cornerSize = with(density) { 20.dp.toPx() }
                        val cornerOffset = cornerSize / 2
                        
                        val strokeWidth = with(density) { 3.dp.toPx() }
                        // 左上角
                        drawRect(
                            color = Color.White,
                            topLeft = Offset(cropX - cornerOffset, cropY - cornerOffset),
                            size = Size(cornerSize, cornerSize),
                            style = Stroke(width = strokeWidth)
                        )
                        // 右上角
                        drawRect(
                            color = Color.White,
                            topLeft = Offset(cropX + cropWidth - cornerOffset, cropY - cornerOffset),
                            size = Size(cornerSize, cornerSize),
                            style = Stroke(width = strokeWidth)
                        )
                        // 左下角
                        drawRect(
                            color = Color.White,
                            topLeft = Offset(cropX - cornerOffset, cropY + cropHeight - cornerOffset),
                            size = Size(cornerSize, cornerSize),
                            style = Stroke(width = strokeWidth)
                        )
                        // 右下角
                        drawRect(
                            color = Color.White,
                            topLeft = Offset(cropX + cropWidth - cornerOffset, cropY + cropHeight - cornerOffset),
                            size = Size(cornerSize, cornerSize),
                            style = Stroke(width = strokeWidth)
                        )
                    }
                }
                
                // 提示文字
                Text(
                    text = "拖拽边框调整区域，拖拽中心移动位置",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                // 底部按钮
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedButton(
                        onClick = onCropCancel,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("取消")
                    }
                    Button(
                        onClick = {
                            // 根据裁剪框比例裁剪图片
                            val croppedBitmap = com.eldercare.ai.utils.ImageProcessor.cropBitmap(
                                bitmap = bitmap,
                                left = cropRect.left,
                                top = cropRect.top,
                                right = cropRect.right,
                                bottom = cropRect.bottom
                            )
                            onCropConfirm(croppedBitmap)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("确认裁剪")
                    }
                }
            }
        }
    }
}

/**
 * 拖拽状态
 */
private sealed class DragState {
    abstract val startOffset: Offset
    abstract var currentOffset: Offset
    abstract fun copy(offset: Offset): DragState
    
    data class Move(
        override val startOffset: Offset,
        override var currentOffset: Offset = startOffset
    ) : DragState() {
        override fun copy(offset: Offset) = Move(startOffset, offset)
    }
    
    data class ResizeTopLeft(
        override val startOffset: Offset,
        override var currentOffset: Offset = startOffset
    ) : DragState() {
        override fun copy(offset: Offset) = ResizeTopLeft(startOffset, offset)
    }
    
    data class ResizeTopRight(
        override val startOffset: Offset,
        override var currentOffset: Offset = startOffset
    ) : DragState() {
        override fun copy(offset: Offset) = ResizeTopRight(startOffset, offset)
    }
    
    data class ResizeBottomLeft(
        override val startOffset: Offset,
        override var currentOffset: Offset = startOffset
    ) : DragState() {
        override fun copy(offset: Offset) = ResizeBottomLeft(startOffset, offset)
    }
    
    data class ResizeBottomRight(
        override val startOffset: Offset,
        override var currentOffset: Offset = startOffset
    ) : DragState() {
        override fun copy(offset: Offset) = ResizeBottomRight(startOffset, offset)
    }
}

@Preview(showBackground = true)
@Composable
fun MenuScanScreenPreview() {
    ElderCareAITheme {
        MenuScanScreen()
    }
}