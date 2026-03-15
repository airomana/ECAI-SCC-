package com.eldercare.ai.ui.screens.voice

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import com.eldercare.ai.rememberElderCareDatabase
import com.eldercare.ai.data.entity.DiaryEntryEntity
import com.eldercare.ai.ui.theme.ElderCareAITheme
import com.eldercare.ai.whisper.AudioRecorder
import com.eldercare.ai.whisper.WhisperProcessor
import com.eldercare.ai.whisper.AndroidSpeechRecognizer
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceDiaryScreen(
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    var isRecording by remember { mutableStateOf(false) }
    var recordedText by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }
    var isGeneratingResponse by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var startAfterPermission by remember { mutableStateOf(false) }
    var lastSavedContent by remember { mutableStateOf("") }
    var lastTranscribedSampleIndex by remember { mutableStateOf(0) }
    var streamingJob by remember { mutableStateOf<Job?>(null) }
    
    val db = rememberElderCareDatabase()
    val scope = rememberCoroutineScope()
    val whisperProcessor = remember { WhisperProcessor.getInstance(context) }
    val audioRecorder = remember { AudioRecorder() }
    val androidSpeechRecognizer = remember { AndroidSpeechRecognizer.getInstance(context) }
    val llmService = remember { com.eldercare.ai.llm.LlmService.getInstance(context) }
    val ttsService = remember { com.eldercare.ai.tts.TtsService.getInstance(context) }
    val settingsManager = remember { com.eldercare.ai.data.SettingsManager.getInstance(context) }
    val userService = remember { 
        try {
            com.eldercare.ai.auth.UserService(db.userDao(), db.familyRelationDao(), db.familyLinkRequestDao(), settingsManager)
        } catch (e: Exception) {
            android.util.Log.e("VoiceDiary", "Failed to create UserService", e)
            null
        }
    }

    // 选择使用哪种识别方式
    // true = 使用Android原生SpeechRecognizer（真实识别，需要网络，立即可用）
    // false = 使用Whisper（需要模型文件，离线，需要集成whisper.cpp）
    // 为避免部分设备上 SpeechRecognizer 主线程限制问题，
    // 默认关闭 Android 原生识别，统一走 Whisper 流程（未集成模型时会使用模拟文本）。
    var useAndroidRecognizer by remember { mutableStateOf(false) }

    // 避免在组合中直接调用 Flow 操作符，使用 remember 包裹 Flow 链
    val diaryEntriesFlow = remember(db) {
        db.diaryEntryDao().getAll()
            .map { list -> list.map { it.toDiaryEntry() } }
    }
    val diaryEntries by diaryEntriesFlow.collectAsState(initial = emptyList())
    
    // 录音权限请求Launcher
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startAfterPermission = true
        } else {
            // 权限被拒绝
            errorMessage = "需要录音权限才能使用此功能，请在设置中授予权限"
        }
    }
    
    fun saveDiary(content: String, auto: Boolean) {
        if (content.isBlank()) return
        if (content == lastSavedContent) {
            if (!auto) {
                errorMessage = "该内容已保存"
            }
            return
        }
        scope.launch {
            withContext(Dispatchers.Main) {
                isGeneratingResponse = true
            }
            val emotion = analyzeEmotionFromText(content)
            val currentUser = try {
                userService?.getCurrentUser()
            } catch (e: Exception) {
                android.util.Log.w("VoiceDiary", "获取用户信息失败", e)
                null
            }
            val userName = currentUser?.nickname?.takeIf { it.isNotBlank() }
            val aiResponse = try {
                llmService.generateDiaryResponse(
                    diaryContent = content,
                    emotion = emotion,
                    userName = userName
                ) ?: generateLocalDiaryResponse(content, emotion, userName)
            } catch (e: Exception) {
                android.util.Log.w("VoiceDiary", "LLM生成回应失败，使用本地模板", e)
                generateLocalDiaryResponse(content, emotion, userName)
            }
            withContext(Dispatchers.IO) {
                db.diaryEntryDao().insert(
                    DiaryEntryEntity(
                        date = System.currentTimeMillis(),
                        content = content,
                        emotion = emotion,
                        aiResponse = aiResponse
                    )
                )
            }
            withContext(Dispatchers.Main) {
                isGeneratingResponse = false
                lastSavedContent = content
                errorMessage = if (auto) "已自动生成记录" else null
                ttsService.speak(aiResponse, priority = 1)
            }
        }
    }

    // 启动录音的辅助函数
    fun launchRecording() {
        // 如果已经在录音，先强制停止
        if (isRecording) {
            android.util.Log.w("VoiceDiary", "已经在录音，先强制停止")
            scope.launch(Dispatchers.IO) {
                audioRecorder.forceStop()
                if (useAndroidRecognizer && androidSpeechRecognizer.isAvailable()) {
                    androidSpeechRecognizer.stop()
                }
                withContext(Dispatchers.Main) {
                    isRecording = false
                    isProcessing = false
                }
            }
            // 等待一小段时间让资源释放
            scope.launch {
                kotlinx.coroutines.delay(100)
                launchRecording()
            }
            return
        }
        
        when {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED -> {
                // 已有权限，根据选择的识别方式启动
                if (useAndroidRecognizer && androidSpeechRecognizer.isAvailable()) {
                    // 使用Android原生识别（不需要录音，直接识别）
                    isRecording = true
                    isProcessing = true
                    recordedText = ""
                    errorMessage = null
                    
                    scope.launch(Dispatchers.Main) {
                        try {
                            val result = androidSpeechRecognizer.recognize()
                            isRecording = false
                            isProcessing = false
                            if (result.isNullOrBlank()) {
                                recordedText = "识别失败，请重试"
                                errorMessage = "未能识别到语音，请重试"
                            } else {
                                recordedText = result
                                errorMessage = null
                                saveDiary(result, true)
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("VoiceDiary", "Android识别失败", e)
                            isRecording = false
                            isProcessing = false
                            errorMessage = "识别失败：${e.message}"
                        }
                    }
                } else {
                    // Android识别器不可用，自动使用Whisper
                    android.util.Log.d("VoiceDiary", "Android识别器不可用，使用Whisper录音")
                    useAndroidRecognizer = false
                    
                    // 使用Whisper（需要录音 + 伪流式识别）
                    scope.launch(Dispatchers.IO) {
                        // 确保之前的录音已停止
                        audioRecorder.forceStop()
                        kotlinx.coroutines.delay(50)  // 短暂延迟确保资源释放
                        
                        val started = audioRecorder.startRecording()
                        withContext(Dispatchers.Main) {
                            if (started) {
                                isRecording = true
                                recordedText = ""
                                errorMessage = null
                                lastTranscribedSampleIndex = 0
                                
                                // 启动伪流式识别协程：录音过程中每隔一段时间追加识别结果
                                streamingJob?.cancel()
                                streamingJob = scope.launch(Dispatchers.IO) {
                                    while (true) {
                                        // 轮询间隔，可根据需要微调
                                        kotlinx.coroutines.delay(1500)
                                        
                                        // 如果已经停止录音，则结束流式任务
                                        if (!isRecording) {
                                            break
                                        }
                                        
                                        // 只在Whisper真正可用时做流式识别，避免与模拟模式重复
                                        if (!whisperProcessor.isNativeLibraryLoaded() || !whisperProcessor.isInitialized()) {
                                            continue
                                        }
                                        
                                        val snapshot = audioRecorder.getSnapshot() ?: continue
                                        
                                        // 至少累积一定新数据再识别（这里约 0.5 秒）
                                        if (snapshot.size <= lastTranscribedSampleIndex + 8000) {
                                            continue
                                        }
                                        
                                        val start = maxOf(0, lastTranscribedSampleIndex - 16000) // 回看1秒上下文
                                        if (start >= snapshot.size) continue
                                        val segment = snapshot.copyOfRange(start, snapshot.size)
                                        
                                        val partial = try {
                                            whisperProcessor.transcribe(segment)
                                        } catch (e: Exception) {
                                            android.util.Log.e("VoiceDiary", "Streaming transcription failed", e)
                                            null
                                        }
                                        
                                        if (partial.isNullOrBlank()) {
                                            lastTranscribedSampleIndex = snapshot.size
                                            continue
                                        }
                                        
                                        withContext(Dispatchers.Main) {
                                            // 如果新结果以当前文本开头，只追加后缀，减少闪烁
                                            val newText = if (partial.startsWith(recordedText)) {
                                                partial.removePrefix(recordedText)
                                            } else {
                                                // 否则直接使用partial，避免越积越乱
                                                partial
                                            }
                                            
                                            if (newText.isNotBlank()) {
                                                recordedText = recordedText + newText
                                            }
                                        }
                                        
                                        lastTranscribedSampleIndex = snapshot.size
                                    }
                                }
                            } else {
                                errorMessage = "无法启动录音，请重试"
                            }
                        }
                    }
                }
            }
            else -> {
                // 请求权限
                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    LaunchedEffect(startAfterPermission) {
        if (startAfterPermission) {
            startAfterPermission = false
            launchRecording()
        }
    }
    
    // 初始化Whisper（延迟加载，避免阻塞UI）
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            if (!whisperProcessor.isInitialized()) {
                // 尝试从assets加载模型
                val initialized = whisperProcessor.initFromAssets(context)
                if (!initialized) {
                    android.util.Log.w("VoiceDiary", "Whisper模型未找到，将使用模拟数据")
                }
            }
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
                text = "今天吃了啥",
                style = MaterialTheme.typography.displayMedium,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.size(64.dp))
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // 录音区域
        VoiceRecordingSection(
            isRecording = isRecording,
            recordedText = recordedText,
            isProcessing = isProcessing,
            isGeneratingResponse = isGeneratingResponse,
            errorMessage = errorMessage,
            onStartRecording = { 
                launchRecording()
            },
            onStopRecording = { 
                // 如果使用Android识别器，停止识别
                if (useAndroidRecognizer && androidSpeechRecognizer.isAvailable()) {
                    androidSpeechRecognizer.stop()
                    audioRecorder.forceStop()
                    isRecording = false
                    isProcessing = false
                    return@VoiceRecordingSection
                }
                
                // 使用Whisper：立即更新UI状态（在主线程）
                if (!isRecording) {
                    android.util.Log.w("VoiceDiary", "未在录音，忽略停止请求")
                    return@VoiceRecordingSection
                }
                
                isRecording = false
                isProcessing = true
                errorMessage = null
                android.util.Log.d("VoiceDiary", "停止录音，设置isProcessing=true")
                
                // 停止流式识别任务
                streamingJob?.cancel()
                streamingJob = null
                
                // 在后台线程处理录音和识别
                scope.launch(Dispatchers.IO) {
                    try {
                        // 停止录音并获取音频数据
                        val audioData = audioRecorder.stopRecording()
                        
                        android.util.Log.d("VoiceDiary", "录音停止，获取到音频数据: ${audioData?.size ?: 0} samples")
                        
                        if (audioData == null || audioData.isEmpty()) {
                            android.util.Log.w("VoiceDiary", "录音数据为空")
                            withContext(Dispatchers.Main) {
                                errorMessage = "录音失败，请重试"
                                isProcessing = false
                                android.util.Log.d("VoiceDiary", "录音失败，设置isProcessing=false")
                            }
                            return@launch
                        }
                        
                        android.util.Log.d("VoiceDiary", "开始识别，音频数据大小: ${audioData.size} samples (${audioData.size / 16000f} 秒)")
                        android.util.Log.d("VoiceDiary", "Native库加载状态: ${whisperProcessor.isNativeLibraryLoaded()}, Whisper初始化状态: ${whisperProcessor.isInitialized()}")
                        
                        // 使用Whisper识别（带超时保护）
                        val transcription = try {
                            if (whisperProcessor.isNativeLibraryLoaded() && whisperProcessor.isInitialized()) {
                                android.util.Log.d("VoiceDiary", "Whisper已初始化，开始识别...")
                                
                                // 使用withTimeout确保不会无限等待
                                kotlinx.coroutines.withTimeout(30000) { // 30秒超时
                                    val result = whisperProcessor.transcribe(audioData)
                                    android.util.Log.d("VoiceDiary", "识别完成，结果长度: ${result?.length ?: 0}")
                                    result
                                }
                            } else {
                                // 如果Whisper未初始化或native库未加载，使用模拟数据
                                android.util.Log.w("VoiceDiary", "Whisper未就绪（native库: ${whisperProcessor.isNativeLibraryLoaded()}, 初始化: ${whisperProcessor.isInitialized()}），使用模拟数据")
                                // 添加短暂延迟，让用户看到"正在识别"状态
                                kotlinx.coroutines.delay(500)
                                "今天中午吃了红烧肉，味道还不错，就是有点油腻。晚上喝了小米粥，挺清淡的。"
                            }
                        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                            android.util.Log.e("VoiceDiary", "识别超时（30秒）", e)
                            null
                        } catch (e: Exception) {
                            android.util.Log.e("VoiceDiary", "识别过程出错", e)
                            null
                        }
                        
                        android.util.Log.d("VoiceDiary", "识别结果处理完成，transcription: ${transcription?.take(50)}...")
                        
                        // 确保在主线程更新UI
                        withContext(Dispatchers.Main) {
                            if (transcription.isNullOrBlank()) {
                                recordedText = "识别失败，请重试"
                                errorMessage = "语音识别失败，可能是模型未加载或识别超时。如果Whisper模型未配置，将使用模拟数据。"
                                android.util.Log.w("VoiceDiary", "识别失败，使用fallback")
                                
                                // 如果Whisper未初始化，至少显示模拟数据
                                if (!whisperProcessor.isInitialized()) {
                                    recordedText = "今天中午吃了红烧肉，味道还不错，就是有点油腻。晚上喝了小米粥，挺清淡的。"
                                    errorMessage = "提示：Whisper模型未加载，当前为模拟数据。请确保模型文件已正确配置。"
                                }
                            } else {
                                recordedText = transcription
                                errorMessage = null
                                saveDiary(transcription, true)
                            }
                            isProcessing = false
                            android.util.Log.d("VoiceDiary", "UI更新完成，isProcessing=false, recordedText长度=${recordedText.length}, recordedText内容: ${recordedText.take(30)}...")
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("VoiceDiary", "录音或识别失败", e)
                        withContext(Dispatchers.Main) {
                            errorMessage = "处理失败：${e.message}"
                            isProcessing = false
                            android.util.Log.d("VoiceDiary", "异常处理完成，设置isProcessing=false")
                        }
                    }
                }
            },
            onSaveDiary = {
                saveDiary(recordedText, false)
            }
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // 历史记录标题
        Text(
            text = "最近的饮食记录",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 历史记录列表
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(diaryEntries) { entry ->
                DiaryEntryCard(entry = entry)
            }
        }
    }
}

@Composable
fun VoiceRecordingSection(
    isRecording: Boolean,
    recordedText: String,
    isProcessing: Boolean = false,
    isGeneratingResponse: Boolean = false,
    errorMessage: String? = null,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onSaveDiary: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "说说今天吃了什么吧～",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                textAlign = TextAlign.Center
            )
            
            // 录音按钮
            Button(
                onClick = if (isRecording) onStopRecording else onStartRecording,
                modifier = Modifier.size(100.dp),
                shape = RoundedCornerShape(50.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                    contentDescription = if (isRecording) "停止录音" else "开始录音",
                    modifier = Modifier.size(40.dp)
                )
            }
            
            Text(
                text = when {
                    isGeneratingResponse -> "AI正在思考..."
                    isRecording -> "正在录音..."
                    isProcessing && recordedText.isEmpty() -> "正在识别..."
                    else -> "点击开始录音"
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            
            // 错误提示
            if (errorMessage != null) {
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
            
            // 处理中指示器（识别或生成回应时显示）
            if ((isProcessing && recordedText.isEmpty()) || isGeneratingResponse) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            
            // 显示识别的文字
            if (recordedText.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "您说的是：",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = recordedText,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        
                        Button(
                            onClick = onSaveDiary,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isGeneratingResponse
                        ) {
                            if (isGeneratingResponse) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Text(
                                    text = "保存记录",
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DiaryEntryCard(entry: DiaryEntry) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 日期和情绪
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = SimpleDateFormat("MM月dd日 HH:mm", Locale.getDefault()).format(entry.date),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Mood,
                        contentDescription = "情绪",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = entry.emotion,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            // 用户说的内容
            Text(
                text = entry.content,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            // AI回复
            if (entry.aiResponse.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SmartToy,
                            contentDescription = "AI回复",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = entry.aiResponse,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

data class DiaryEntry(
    val id: Long,
    val date: Date,
    val content: String,
    val emotion: String,
    val aiResponse: String
)

/**
 * 情感分析函数 - 基于关键词检测
 */
/**
 * 生成本地模板的AI回应（降级方案）
 * 当LLM不可用时使用
 */
private fun generateLocalDiaryResponse(content: String, emotion: String, userName: String? = null): String {
    val lowerContent = content.lowercase()
    val name = userName?.takeIf { it.isNotBlank() } ?: "您"
    
    // 提取食物关键词（简单匹配）
    val foodKeywords = listOf("肉", "鱼", "菜", "粥", "饭", "汤", "蛋", "鸡", "鸭", "虾", "豆腐", "青菜", "萝卜", "土豆", "番茄")
    val mentionedFoods = foodKeywords.filter { lowerContent.contains(it) }
    
    // 提取具体食物名称（更精确的匹配）
    val specificFoods = when {
        lowerContent.contains("红烧肉") -> "红烧肉"
        lowerContent.contains("清蒸鱼") -> "清蒸鱼"
        lowerContent.contains("番茄鸡蛋") || lowerContent.contains("西红柿鸡蛋") -> "番茄鸡蛋"
        lowerContent.contains("小米粥") -> "小米粥"
        lowerContent.contains("白粥") -> "白粥"
        lowerContent.contains("青菜") -> "青菜"
        mentionedFoods.isNotEmpty() -> mentionedFoods.first()
        else -> null
    }
    
    return when (emotion) {
        "满意" -> {
            when {
                specificFoods != null -> {
                    "太好了${name}！听起来您今天吃的${specificFoods}很不错呢，很有营养。继续保持这样的好习惯，身体会越来越好的！"
                }
                lowerContent.contains("好吃") || lowerContent.contains("美味") || lowerContent.contains("很好吃") -> {
                    "真为您高兴${name}！吃得开心最重要，我会记住您今天的好心情。继续保持哦！"
                }
                lowerContent.contains("不错") || lowerContent.contains("挺好") -> {
                    "很好${name}！听起来您今天吃得挺满意的。继续保持这样的好习惯！"
                }
                else -> {
                    "感谢您的分享${name}！我会记住您今天的饮食情况，继续保持哦！"
                }
            }
        }
        "担心" -> {
            when {
                lowerContent.contains("油腻") || lowerContent.contains("油") || lowerContent.contains("肥") -> {
                    "我理解您的担心${name}。确实，太油腻的食物对身体不太好，特别是对血压和血脂。下次可以试试清淡一点的菜，比如清蒸或者水煮的，对身体更好。"
                }
                lowerContent.contains("咸") || lowerContent.contains("盐") -> {
                    "确实${name}，太咸了对身体不好，特别是对血压。下次可以提醒厨师少放点盐，或者多喝点水，帮助身体排出多余的盐分。"
                }
                lowerContent.contains("甜") || lowerContent.contains("糖") -> {
                    "我明白您的担心${name}。糖分高的食物确实要控制，特别是对血糖。可以少吃一点，或者选择其他替代品，比如用水果代替甜点。"
                }
                lowerContent.contains("辣") -> {
                    "我理解${name}，太辣的食物对肠胃不太好。下次可以提醒厨师少放点辣椒，或者选择不辣的菜。"
                }
                else -> {
                    "别太担心${name}，偶尔吃一次没关系的。下次注意一下就好，我会帮您记住的。"
                }
            }
        }
        "孤单" -> {
            when {
                lowerContent.contains("一个人") || lowerContent.contains("独自") -> {
                    "我理解您的心情${name}。一个人吃饭确实有点冷清，但您并不孤单，我会一直陪着您。有空的时候，可以多给孩子们打个电话，他们一定很想念您。"
                }
                lowerContent.contains("没人") || lowerContent.contains("冷清") -> {
                    "我明白${name}，一个人在家确实有点冷清。但您要记住，孩子们虽然不在身边，但心里一直惦记着您。有空多联系联系，或者找邻居聊聊天，心情会好很多。"
                }
                else -> {
                    "我理解您的心情${name}。虽然现在是一个人，但您并不孤单。我会一直陪着您，记录您的每一天。记得多和孩子们联系，他们一定很想念您。"
                }
            }
        }
        else -> {
            when {
                specificFoods != null -> {
                    "好的${name}，我记住了您今天吃了${specificFoods}。感谢您的分享！"
                }
                mentionedFoods.isNotEmpty() -> {
                    "好的${name}，我记住了您今天吃了${mentionedFoods.first()}。感谢您的分享！"
                }
                else -> {
                    "感谢您的分享${name}！我会记住您今天的饮食情况。"
                }
            }
        }
    }
}

private fun analyzeEmotionFromText(text: String): String {
    if (text.isEmpty()) return "平静"
    
    val lowerText = text.lowercase()
    
    // 积极情感关键词（扩展列表，包含更多变体）
    val positiveKeywords = listOf(
        // 好吃相关
        "好吃", "很好吃", "特别好吃", "非常好吃", "真好吃", "太好吃了", "超级好吃",
        "美味", "很美味", "特别美味", "非常美味", "真美味", "太美味了",
        "香", "很香", "特别香", "非常香", "真香", "太香了",
        "甜", "很甜", "特别甜", "非常甜", "真甜", "太甜了",
        // 情绪表达
        "开心", "很开心", "特别开心", "非常开心", "真开心", "太开心了",
        "高兴", "很高兴", "特别高兴", "非常高兴", "真高兴", "太高兴了",
        "满意", "很满意", "特别满意", "非常满意", "真满意", "太满意了",
        "不错", "很不错", "特别不错", "非常不错", "真不错", "太不错了",
        "挺好", "挺好的", "特别挺好", "非常挺好",
        "喜欢", "很喜欢", "特别喜欢", "非常喜欢", "真喜欢", "太喜欢了",
        "舒服", "很舒服", "特别舒服", "非常舒服", "真舒服", "太舒服了",
        "温暖", "很温暖", "特别温暖", "非常温暖", "真温暖", "太温暖了",
        "幸福", "很幸福", "特别幸福", "非常幸福", "真幸福", "太幸福了",
        "快乐", "很快乐", "特别快乐", "非常快乐", "真快乐", "太快乐了",
        "愉快", "很愉快", "特别愉快", "非常愉快", "真愉快", "太愉快了",
        "很棒", "很很棒", "特别很棒", "非常很棒", "真很棒", "太棒了",
        "很好", "很好很好", "特别好", "非常好", "真好", "太好了",
        "满足", "很满足", "特别满足", "非常满足", "真满足", "太满足了",
        "享受", "很享受", "特别享受", "非常享受", "真享受", "太享受了",
        "可口", "很可口", "特别可口", "非常可口", "真可口", "太可口了",
        "香甜", "很香甜", "特别香甜", "非常香甜", "真香甜", "太香甜了",
        // 其他积极表达
        "赞", "很赞", "特别赞", "非常赞", "真赞", "太赞了",
        "棒", "很棒", "特别棒", "非常棒", "真棒", "太棒了",
        "好", "很好", "特别好", "非常好", "真好", "太好了"
    )
    
    // 消极情感关键词
    val negativeKeywords = listOf(
        "难吃", "很难吃", "特别难吃", "非常难吃", "真难吃", "太难吃了",
        "苦", "很苦", "特别苦", "非常苦", "真苦", "太苦了",
        "咸", "很咸", "特别咸", "非常咸", "真咸", "太咸了",
        "淡", "很淡", "特别淡", "非常淡", "真淡", "太淡了",
        "不好", "很不好", "特别不好", "非常不好", "真不好", "太不好了",
        "难受", "很难受", "特别难受", "非常难受", "真难受", "太难受了",
        "不舒服", "很不舒服", "特别不舒服", "非常不舒服", "真不舒服", "太不舒服了",
        "生气", "很生气", "特别生气", "非常生气", "真生气", "太生气了",
        "烦", "很烦", "特别烦", "非常烦", "真烦", "太烦了",
        "累", "很累", "特别累", "非常累", "真累", "太累了",
        "疼", "很疼", "特别疼", "非常疼", "真疼", "太疼了",
        "不开心", "很不开心", "特别不开心", "非常不开心", "真不开心", "太不开心了",
        "失望", "很失望", "特别失望", "非常失望", "真失望", "太失望了",
        "讨厌", "很讨厌", "特别讨厌", "非常讨厌", "真讨厌", "太讨厌了",
        "不喜欢", "很不喜欢", "特别不喜欢", "非常不喜欢", "真不喜欢", "太不喜欢了",
        "糟糕", "很糟糕", "特别糟糕", "非常糟糕", "真糟糕", "太糟糕了",
        "痛苦", "很痛苦", "特别痛苦", "非常痛苦", "真痛苦", "太痛苦了",
        "难过", "很难过", "特别难过", "非常难过", "真难过", "太难过了",
        "伤心", "很伤心", "特别伤心", "非常伤心", "真伤心", "太伤心了",
        "烦躁", "很烦躁", "特别烦躁", "非常烦躁", "真烦躁", "太烦躁了",
        "疲惫", "很疲惫", "特别疲惫", "非常疲惫", "真疲惫", "太疲惫了"
    )
    
    // 孤独情感关键词
    val lonelyKeywords = listOf(
        "一个人", "一个人吃", "一个人住", "一个人在家",
        "孤单", "很孤单", "特别孤单", "非常孤单", "真孤单", "太孤单了",
        "寂寞", "很寂寞", "特别寂寞", "非常寂寞", "真寂寞", "太寂寞了",
        "没人", "没人陪", "没人管", "没人理", "没人关心",
        "独自", "独自一人", "独自在家", "独自生活",
        "冷清", "很冷清", "特别冷清", "非常冷清", "真冷清", "太冷清了",
        "想念", "很想念", "特别想念", "非常想念", "真想念", "太想念了",
        "思念", "很思念", "特别思念", "非常思念", "真思念", "太思念了",
        "想家", "很想家", "特别想家", "非常想家", "真想家", "太想家了",
        "想孩子", "很想孩子", "特别想孩子", "非常想孩子", "真想孩子", "太想孩子了",
        "想老伴", "很想老伴", "特别想老伴", "非常想老伴", "真想老伴", "太想老伴了",
        "孤独", "很孤独", "特别孤独", "非常孤独", "真孤独", "太孤独了",
        "独自一人", "独自一人在家", "独自一人生活",
        "没人陪", "没人陪我", "没人陪伴", "没人陪伴我"
    )
    
    // 计算匹配的关键词数量
    val positiveCount = positiveKeywords.count { lowerText.contains(it) }
    val negativeCount = negativeKeywords.count { lowerText.contains(it) }
    val lonelyCount = lonelyKeywords.count { lowerText.contains(it) }
    
    // 添加调试日志
    android.util.Log.d("EmotionAnalysis", "文本: $text")
    android.util.Log.d("EmotionAnalysis", "积极词数: $positiveCount, 消极词数: $negativeCount, 孤独词数: $lonelyCount")
    
    // 根据匹配的关键词数量决定情感
    val emotion = when {
        lonelyCount > 0 -> "孤单"
        negativeCount > positiveCount && negativeCount > 0 -> "担心"
        positiveCount > 0 -> "满意"
        else -> "平静"
    }
    
    android.util.Log.d("EmotionAnalysis", "识别结果: $emotion")
    return emotion
}

private fun DiaryEntryEntity.toDiaryEntry() = DiaryEntry(
    id = id,
    date = Date(date),
    content = content,
    emotion = emotion,
    aiResponse = aiResponse
)

fun getSampleDiaryEntries(): List<DiaryEntry> {
    val now = Date()
    return listOf(
        DiaryEntry(
            id = 1,
            date = Date(now.time - 24 * 60 * 60 * 1000),
            content = "昨天晚上吃了女儿做的红烧排骨，特别香，就是有点咸。还喝了银耳汤，很清甜。",
            emotion = "开心",
            aiResponse = "女儿的手艺真不错！红烧排骨确实容易偏咸，下次可以提醒女儿少放点盐。银耳汤很适合您，润燥又营养。"
        ),
        DiaryEntry(
            id = 2,
            date = Date(now.time - 2 * 24 * 60 * 60 * 1000),
            content = "今天一个人吃饭，随便煮了点面条，没什么胃口。",
            emotion = "孤单",
            aiResponse = "一个人吃饭确实容易没胃口，但也要好好照顾自己哦。可以给面条加个鸡蛋，营养会更好。要不要给子女打个电话聊聊天？"
        )
    )
}

@Preview(showBackground = true)
@Composable
fun VoiceDiaryScreenPreview() {
    ElderCareAITheme {
        VoiceDiaryScreen()
    }
}
