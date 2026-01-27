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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
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
                val maxLlmCalls = 3   // 最多调用3次LLM
                
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
                        ttsService.speak(message, priority = 1)
                        
                        // 如果有高风险菜品，单独播报
                        results.filter { it.riskLevel == RiskLevel.HIGH }.take(2).forEach { result ->
                            val warningMessage = "${result.name}，${result.healthAdvice}"
                            ttsService.speak(warningMessage, priority = 1)
                        }
                    }
                } else if (text.isNotBlank()) {
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

        // 识别文字
        if (!recognizedText.isNullOrBlank()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "识别文字",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = recognizedText,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        }
        
        // 结果列表或空状态
        if (results.isEmpty()) {
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
                RiskLevel.HIGH -> MaterialTheme.colorScheme.errorContainer
                RiskLevel.MEDIUM -> MaterialTheme.colorScheme.secondaryContainer
                RiskLevel.LOW -> MaterialTheme.colorScheme.primaryContainer
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
 * 根据菜名中的关键词生成描述
 */
private fun generateSimpleDescription(dishName: String): String {
    // 根据菜名中的关键词生成简单描述
    return when {
        // 烹饪方式
        dishName.contains("红烧") -> "红烧的菜，味道浓郁"
        dishName.contains("清蒸") -> "清蒸的菜，清淡健康"
        dishName.contains("水煮") -> "水煮的菜，可能比较辣"
        dishName.contains("干煸") -> "干煸的菜，比较香"
        dishName.contains("糖醋") -> "糖醋口味，酸甜可口"
        dishName.contains("鱼香") -> "鱼香口味，下饭"
        dishName.contains("宫保") -> "宫保风味，微辣"
        dishName.contains("麻婆") -> "麻婆豆腐类，麻辣味"
        dishName.contains("炒") -> "炒菜"
        dishName.contains("蒸") -> "蒸的菜，清淡"
        dishName.contains("煮") -> "煮的菜"
        dishName.contains("炸") -> "油炸的菜，油比较大"
        dishName.contains("烤") -> "烤制的菜"
        dishName.contains("炖") -> "炖的菜，比较烂"
        dishName.contains("烧") -> "烧的菜"
        dishName.contains("煎") -> "煎的菜"
        dishName.contains("焖") -> "焖的菜，很入味"
        
        // 主食类
        dishName.contains("饭") -> "主食，米饭类"
        dishName.contains("面") -> "主食，面食类"
        dishName.contains("粥") -> "粥，易消化"
        dishName.contains("饺") -> "饺子类"
        dishName.contains("包") -> "包子类"
        dishName.contains("饼") -> "饼类"
        
        // 汤类
        dishName.contains("汤") -> "汤品，暖胃"
        dishName.contains("羹") -> "羹汤类"
        dishName.contains("锅") -> "锅类菜品"
        
        // 食材类
        dishName.contains("肉") -> "肉类菜品"
        dishName.contains("鸡") -> "鸡肉类菜品"
        dishName.contains("鸭") -> "鸭肉类菜品"
        dishName.contains("鱼") -> "鱼类菜品"
        dishName.contains("虾") -> "虾类菜品"
        dishName.contains("蛋") -> "蛋类菜品"
        dishName.contains("豆腐") -> "豆腐类菜品，蛋白质丰富"
        dishName.contains("青菜") || dishName.contains("白菜") -> "蔬菜类，健康"
        dishName.contains("茄") -> "茄子类菜品"
        dishName.contains("土豆") -> "土豆类菜品"
        
        // 默认
        else -> "菜单上的菜品"
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
                "${userName}，这个${dishName}可以适量食用，但需注意控制分量"
            }
            RiskLevel.LOW -> {
                "✅ ${userName}，这个${dishName}比较适合您，可以放心食用"
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MenuScanScreenPreview() {
    ElderCareAITheme {
        MenuScanScreen()
    }
}