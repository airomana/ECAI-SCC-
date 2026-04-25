package com.eldercare.ai.ui.screens.voice

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import android.view.MotionEvent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eldercare.ai.companion.ConversationManager
import com.eldercare.ai.data.entity.EmotionLogEntity
import com.eldercare.ai.data.entity.ConversationMessageEntity
import com.eldercare.ai.network.ApiClient
import com.eldercare.ai.utils.createVoiceDiaryPerformanceTracker
import com.eldercare.ai.rememberElderCareDatabase
import com.eldercare.ai.tts.TtsService
import com.eldercare.ai.ui.components.ElderCareDimens
import com.eldercare.ai.ui.components.ElderCareScaffold
import com.eldercare.ai.whisper.HybridSpeechRecognizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

private const val TAG = "VoiceDiaryV2"

/**
 * 语音日记界面 V2 - 使用混合语音识别
 * 优先云端阿里云ASR，无网络回退到本地Whisper
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceDiaryScreenV2(
    onNavigateBack: () -> Unit = {},
    onNavigateToChatHistory: () -> Unit = {}
) {
    val context = LocalContext.current
    val db = rememberElderCareDatabase()
    val scope = rememberCoroutineScope()

    val hybridRecognizer = remember { HybridSpeechRecognizer.getInstance(context) }
    val ttsService = remember { TtsService.getInstance(context) }
    val settingsManager = remember { com.eldercare.ai.data.SettingsManager.getInstance(context) }
    val conversationManager = remember { ConversationManager.getInstance(context, db) }

    var currentSessionId by remember { mutableStateOf<Long?>(null) }
    val messages = remember(currentSessionId) {
        currentSessionId?.let { conversationManager.getSessionMessages(it) }
    }
    val messageList by (messages ?: kotlinx.coroutines.flow.flowOf(emptyList()))
        .collectAsStateWithLifecycle(initialValue = emptyList())

    var isRecording by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }
    var isReplying by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var partialText by remember { mutableStateOf("") }
    
    // 识别器状态
    var recognizerReady by remember { mutableStateOf(false) }
    var useCloudMode by remember { mutableStateOf(true) }
    var networkAvailable by remember { mutableStateOf(false) }
    
    // 情绪日志
    var showMyLogs by remember { mutableStateOf(false) }
    val emotionLogs by db.emotionLogDao().getAll().collectAsStateWithLifecycle(initialValue = emptyList())

    val listState = rememberLazyListState()

    LaunchedEffect(messageList.size) {
        if (messageList.isNotEmpty()) listState.animateScrollToItem(messageList.size - 1)
    }

    // 初始化识别器
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            Log.d(TAG, "=== Initializing Hybrid Recognizer ===")
            hybridRecognizer.setBackendUrl(ApiClient.BASE_URL.removeSuffix("/"))
            networkAvailable = hybridRecognizer.isNetworkAvailable()
            Log.d(TAG, "Network available: $networkAvailable")
            hybridRecognizer.initWhisper()
            withContext(Dispatchers.Main) {
                recognizerReady = true
                Log.d(TAG, "Recognizer ready")
            }
        }

        // 监听流式中间结果（partial/segment），用于实时显示
        scope.launch {
            hybridRecognizer.partialResult.collect { text ->
                partialText = text
            }
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (!granted) errorMessage = "需要录音权限才能使用此功能" }

    fun hasAudioPermission() = ContextCompat.checkSelfPermission(
        context, Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED

    suspend fun ensureSession(): Long =
        currentSessionId ?: conversationManager.startSession().also { currentSessionId = it }

    fun handleUserSpeech(text: String) {
        if (text.isBlank()) return
        val performanceTracker = createVoiceDiaryPerformanceTracker()
        
        scope.launch {
            val sessionId = ensureSession()
            isReplying = true
            errorMessage = null
            
            val saveStart = performanceTracker.mark()
            val userMsg = conversationManager.saveUserMessage(sessionId, text)
            val userName = try {
                val uid = settingsManager.getCurrentUserId() ?: 0L
                db.userDao().getById(uid)?.nickname
            } catch (_: Exception) { null }
            performanceTracker.logDuration("保存用户消息及情绪分析", saveStart)
            
            val llmStart = performanceTracker.mark()
            val reply = conversationManager.generateReply(
                sessionId = sessionId,
                userMessage = text,
                emotion = userMsg.emotion,
                userName = userName
            )
            performanceTracker.logDuration("大模型生成陪伴回复", llmStart)
            
            conversationManager.saveAssistantMessage(sessionId, reply)
            isReplying = false
            
            performanceTracker.logTotal("语音日记回复总耗时")
            
            ttsService.speak(reply, priority = 1)
        }
    }

    fun startRecording() {
        if (!hasAudioPermission()) {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        if (isRecording || isProcessing || isReplying) {
            Log.w(TAG, "startRecording blocked: busy")
            return
        }
        if (!networkAvailable && !hybridRecognizer.isWhisperReady()) {
            errorMessage = "离线状态下暂不可用，请连接网络后重试"
            return
        }
        
        errorMessage = null
        partialText = ""
        networkAvailable = hybridRecognizer.isNetworkAvailable()
        
        Log.d(TAG, "=== Starting recording, useCloud=$useCloudMode, network=$networkAvailable ===")
        
        // VAD 静音检测：触发时走与松开按钮相同的 stopRecording() 路径，避免双重调用
        // 注意：需要在设置回调之后再定义 stopRecording，所以这里先占位
        val stopRecordingRef: () -> Unit = {
            if (!isRecording) {
                Log.w(TAG, "stopRecording called but not recording")
            } else {
                isRecording = false
                isProcessing = true
                partialText = ""
                Log.d(TAG, "=== Stopping recording ===")
                
                scope.launch {
                    try {
                        val result = hybridRecognizer.stopRecognition()
                        isProcessing = false
                        partialText = ""
                        when (result) {
                            is HybridSpeechRecognizer.RecognitionResult.Final -> {
                                if (result.text.isNotBlank()) {
                                    handleUserSpeech(result.text)
                                } else {
                                    errorMessage = "未能识别到语音"
                                }
                            }
                            is HybridSpeechRecognizer.RecognitionResult.Error -> {
                                errorMessage = result.message
                            }
                            else -> {
                                errorMessage = "识别结果异常"
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error stopping recognition", e)
                        isProcessing = false
                        errorMessage = "识别失败: ${e.message ?: "未知错误"}"
                    }
                }
            }
        }
        
        hybridRecognizer.onVadSilence = {
            Log.d(TAG, "VAD silence → auto stopRecording")
            // 回调已经在主线程，直接检查并调用
            if (isRecording) {
                stopRecordingRef()
            }
        }
        
        val started = hybridRecognizer.startRecognition(
            useCloud = useCloudMode,
            onRms = { rms -> /* rmsLevel = rms */ }
        )
        if (started) {
            isRecording = true
            Log.d(TAG, "Recording started")
        } else {
            errorMessage = "无法启动录音"
            Log.e(TAG, "Failed to start recording")
        }
    }

    fun stopRecording() {
        if (!isRecording) {
            Log.w(TAG, "stopRecording called but not recording")
            return
        }
        
        isRecording = false
        isProcessing = true
        partialText = ""
        Log.d(TAG, "=== Stopping recording ===")
        
        scope.launch {
            try {
                val result = hybridRecognizer.stopRecognition()
                isProcessing = false
                partialText = ""
                when (result) {
                    is HybridSpeechRecognizer.RecognitionResult.Final -> {
                        if (result.text.isNotBlank()) {
                            handleUserSpeech(result.text)
                        } else {
                            errorMessage = "未能识别到语音"
                        }
                    }
                    is HybridSpeechRecognizer.RecognitionResult.Error -> {
                        errorMessage = result.message
                    }
                    else -> {
                        errorMessage = "识别结果异常"
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping recognition", e)
                isProcessing = false
                errorMessage = "识别失败: ${e.message ?: "未知错误"}"
            }
        }
    }

    fun endCurrentSession() {
        val sid = currentSessionId ?: return
        scope.launch {
            conversationManager.endSession(sid)
            currentSessionId = null
        }
    }

    // ── UI ──────────────────────────────────────────────────────────
    ElderCareScaffold(
        title = "语音陪伴",
        onNavigateBack = { 
            hybridRecognizer.cancelRecognition()
            endCurrentSession()
            onNavigateBack() 
        },
        actions = {
            IconButton(onClick = onNavigateToChatHistory) {
                Icon(Icons.Default.History, contentDescription = "对话记录")
            }
            IconButton(onClick = { showMyLogs = true }) {
                Icon(Icons.Default.EventNote, contentDescription = "我的日志")
            }
            if (currentSessionId != null) {
                TextButton(onClick = { endCurrentSession() }) {
                    Text("结束对话", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = ElderCareDimens.ScreenPadding),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                if (messageList.isEmpty() && currentSessionId == null) {
                    item { WelcomeCardV2(recognizerReady, networkAvailable, useCloudMode) }
                }
                items(messageList) { msg -> ChatBubble(message = msg) }
                
                // 显示部分识别结果
                if (partialText.isNotBlank()) {
                    item { PartialResultBubble(partialText) }
                }
                
                if (isProcessing || isReplying) {
                    item { ThinkingBubble(isProcessing = isProcessing) }
                }
            }

            if (errorMessage != null) {
                Text(
                    text = errorMessage!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = ElderCareDimens.ScreenPadding),
                    textAlign = TextAlign.Center
                )
            }

            RecordingControlBarV2(
                isRecording = isRecording,
                isProcessing = isProcessing,
                isReplying = isReplying,
                recognizerReady = recognizerReady,
                useCloudMode = useCloudMode,
                networkAvailable = networkAvailable,
                onPress = { startRecording() },
                onRelease = { stopRecording() }
            )
        }
    }
    
    // ── 情绪日志弹窗 ────────────────────────────────────────────────
    if (showMyLogs) {
        MyEmotionLogsDialog(
            emotionLogs = emotionLogs,
            onDismiss = { showMyLogs = false }
        )
    }
}

// ── 欢迎卡片 V2 ──────────────────────────────────────────────────────
@Composable
private fun WelcomeCardV2(ready: Boolean, networkAvailable: Boolean, useCloudMode: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Default.SmartToy, null,
                modifier = Modifier.size(48.dp).align(Alignment.CenterHorizontally),
                tint = MaterialTheme.colorScheme.primary)
            Text("您好，我是您的陪伴助手",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth())
            
            if (!ready) {
                Row(modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("语音识别初始化中...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            } else {
                if (!networkAvailable) {
                    Text("当前无网络，语音识别不可用",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth())
                } else {
                    Text("按住下方麦克风说话，松开发送",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

// ── 部分识别结果气泡 ──────────────────────────────────────────────────
@Composable
private fun PartialResultBubble(text: String) {
    Row(modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.Bottom) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
        ) {
            Text(text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
        }
        Spacer(Modifier.width(8.dp))
        Box(modifier = Modifier.size(36.dp).clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Person, null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(20.dp))
        }
    }
}

// ── 录音控制栏 V2 ────────────────────────────────────────────────────
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun RecordingControlBarV2(
    isRecording: Boolean,
    isProcessing: Boolean,
    isReplying: Boolean,
    recognizerReady: Boolean,
    useCloudMode: Boolean,
    networkAvailable: Boolean,
    onPress: () -> Unit,
    onRelease: () -> Unit
) {
    val busy = isProcessing || isReplying || !recognizerReady || (!networkAvailable)

    val pulseScale by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 1f,
        targetValue = if (isRecording) 1.12f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "pulseScale"
    )

    Surface(modifier = Modifier.fillMaxWidth(), shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)) {

            val statusText = when {
                !recognizerReady -> "语音识别初始化中..."
                !networkAvailable -> "无网络，语音识别不可用"
                isReplying       -> "AI 正在思考..."
                isProcessing     -> "正在识别语音..."
                isRecording      -> "松开发送"
                else             -> "按住说话"
            }
            
            Text(
                text = statusText,
                style = MaterialTheme.typography.titleMedium,
                color = if (isRecording) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .scale(if (isRecording) pulseScale else 1f)
                    .clip(CircleShape)
                    .background(when {
                        busy        -> MaterialTheme.colorScheme.surfaceVariant
                        isRecording -> MaterialTheme.colorScheme.error
                        else        -> MaterialTheme.colorScheme.primary
                    })
                    .pointerInteropFilter { event ->
                        if (busy) return@pointerInteropFilter false
                        when (event.action) {
                            MotionEvent.ACTION_DOWN -> { onPress(); true }
                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { onRelease(); true }
                            else -> false
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (busy) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant, strokeWidth = 3.dp)
                } else {
                    Icon(
                        imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                        contentDescription = if (isRecording) "松开发送" else "按住说话",
                        modifier = Modifier.size(36.dp),
                        tint = if (isRecording) MaterialTheme.colorScheme.onError
                               else MaterialTheme.colorScheme.onPrimary
                    )
                }
            }

            Text(
                text = when {
                    busy        -> ""
                    isRecording -> "正在录音中，松开即可发送"
                    else        -> "按住麦克风开始说话，松开发送"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// 复用原有的组件
@Composable
private fun ChatBubble(message: ConversationMessageEntity) {
    val isUser = message.role == "user"
    val sdf = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!isUser) {
            Box(modifier = Modifier.size(36.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center) {
                Icon(Icons.Default.SmartToy, null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(8.dp))
        }

        Column(
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(
                    topStart = 16.dp, topEnd = 16.dp,
                    bottomStart = if (isUser) 16.dp else 4.dp,
                    bottomEnd = if (isUser) 4.dp else 16.dp
                ),
                color = if (isUser) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(message.content,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isUser) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp)) {
                Text(sdf.format(Date(message.timestamp)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (isUser && message.emotion.isNotBlank() && message.emotion != "平静") {
                    Text("· ${message.emotion}",
                        style = MaterialTheme.typography.labelSmall,
                        color = emotionColor(message.emotion))
                }
            }
        }

        if (isUser) {
            Spacer(Modifier.width(8.dp))
            Box(modifier = Modifier.size(36.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Person, null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun emotionColor(emotion: String) = when (emotion) {
    "开心", "满意" -> MaterialTheme.colorScheme.primary
    "孤单", "难过" -> MaterialTheme.colorScheme.secondary
    "担心", "不适" -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun ThinkingBubble(isProcessing: Boolean) {
    Row(modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(36.dp).clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center) {
            Icon(Icons.Default.SmartToy, null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(8.dp))
        Surface(
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 4.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Text(if (isProcessing) "正在识别语音..." else "正在思考回复...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun MyEmotionLogsDialog(
    emotionLogs: List<EmotionLogEntity>,
    onDismiss: () -> Unit
) {
    val sdf = remember { SimpleDateFormat("MM月dd日", Locale.getDefault()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("我的情绪日志", style = MaterialTheme.typography.headlineSmall) },
        text = {
            if (emotionLogs.isEmpty()) {
                Text("暂无记录，多和我聊聊天吧～", style = MaterialTheme.typography.bodyLarge)
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.heightIn(max = 400.dp)
                ) {
                    items(emotionLogs.take(14)) { log ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                sdf.format(Date(log.dayTimestamp)),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = when (log.dominantEmotion) {
                                    "开心", "满意" -> MaterialTheme.colorScheme.primaryContainer
                                    "孤单", "难过" -> MaterialTheme.colorScheme.secondaryContainer
                                    "担心", "不适" -> MaterialTheme.colorScheme.errorContainer
                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                }
                            ) {
                                Text(
                                    log.dominantEmotion,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            Text(
                                "聊了${log.conversationCount}次",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭", style = MaterialTheme.typography.bodyLarge)
            }
        }
    )
}
