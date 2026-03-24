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
import com.eldercare.ai.companion.EmotionAnalyzer
import com.eldercare.ai.data.entity.EmotionLogEntity
import com.eldercare.ai.data.entity.ConversationMessageEntity
import com.eldercare.ai.rememberElderCareDatabase
import com.eldercare.ai.tts.TtsService
import com.eldercare.ai.ui.components.ElderCareDimens
import com.eldercare.ai.ui.components.ElderCareScaffold
import com.eldercare.ai.whisper.AudioRecorder
import com.eldercare.ai.whisper.WhisperProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.text.SimpleDateFormat
import java.util.*

private const val TAG = "VoiceDiary"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceDiaryScreen(onNavigateBack: () -> Unit = {}) {
    val context = LocalContext.current
    val db = rememberElderCareDatabase()
    val scope = rememberCoroutineScope()

    val whisperProcessor = remember { WhisperProcessor.getInstance(context) }
    val audioRecorder = remember { AudioRecorder() }
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
    var rmsLevel by remember { mutableStateOf(0f) }
    // Whisper 初始化状态
    var whisperReady by remember { mutableStateOf(false) }
    // 情绪日志
    var showMyLogs by remember { mutableStateOf(false) }
    val emotionLogs by db.emotionLogDao().getAll().collectAsStateWithLifecycle(initialValue = emptyList())

    val listState = rememberLazyListState()

    LaunchedEffect(messageList.size) {
        if (messageList.isNotEmpty()) listState.animateScrollToItem(messageList.size - 1)
    }

    // 后台初始化 Whisper
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            Log.d(TAG, "=== Whisper Init Start ===")
            Log.d(TAG, "nativeLibLoaded=${whisperProcessor.isNativeLibraryLoaded()}, alreadyInit=${whisperProcessor.isInitialized()}")
            if (!whisperProcessor.isInitialized()) {
                val ok = whisperProcessor.initFromAssets(context)
                Log.d(TAG, "initFromAssets result=$ok, nativeLib=${whisperProcessor.isNativeLibraryLoaded()}, initialized=${whisperProcessor.isInitialized()}")
            } else {
                Log.d(TAG, "Whisper already initialized, skipping")
            }
            withContext(Dispatchers.Main) {
                whisperReady = whisperProcessor.isInitialized() && whisperProcessor.isNativeLibraryLoaded()
                Log.d(TAG, "whisperReady=$whisperReady")
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
        scope.launch {
            val sessionId = ensureSession()
            isReplying = true
            errorMessage = null
            val userMsg = conversationManager.saveUserMessage(sessionId, text)
            val userName = try {
                val uid = settingsManager.getCurrentUserId() ?: 0L
                db.userDao().getById(uid)?.nickname
            } catch (_: Exception) { null }
            val reply = conversationManager.generateReply(
                sessionId = sessionId,
                userMessage = text,
                emotion = userMsg.emotion,
                userName = userName
            )
            conversationManager.saveAssistantMessage(sessionId, reply)
            isReplying = false
            ttsService.speak(reply, priority = 1)
        }
    }

    // ── Push-to-Talk（纯 Whisper 本地识别）──────────────────────────

    // 将转写逻辑抽出，startRecording/releaseRecording/静音回调共用
    fun doTranscribe() {
        if (!isRecording) return
        isRecording = false
        rmsLevel = 0f
        isProcessing = true
        Log.d(TAG, "=== doTranscribe: stopping recorder, starting transcription ===")

        scope.launch(Dispatchers.IO) {
            try {
                val audioData = audioRecorder.stopRecording()
                Log.d(TAG, "audioData=${audioData?.size ?: "null"} samples (${(audioData?.size ?: 0) / 16000f}s)")

                if (audioData == null || audioData.size < 1600) {
                    Log.w(TAG, "Audio too short or null: ${audioData?.size} samples")
                    withContext(Dispatchers.Main) {
                        errorMessage = "录音太短，请重试"
                        isProcessing = false
                    }
                    return@launch
                }

                Log.d(TAG, "whisperReady=$whisperReady, nativeLib=${whisperProcessor.isNativeLibraryLoaded()}, initialized=${whisperProcessor.isInitialized()}")

                val transcription = if (whisperReady) {
                    try {
                        val t0 = System.currentTimeMillis()
                        val result = withTimeout(30_000) { whisperProcessor.transcribe(audioData) }
                        val elapsed = System.currentTimeMillis() - t0
                        Log.d(TAG, "Whisper transcribe done in ${elapsed}ms, result='$result'")
                        result
                    } catch (e: Exception) {
                        Log.e(TAG, "Whisper transcribe failed", e)
                        null
                    }
                } else {
                    Log.w(TAG, "Whisper not ready")
                    null
                }

                withContext(Dispatchers.Main) {
                    isProcessing = false
                    if (!transcription.isNullOrBlank()) {
                        Log.d(TAG, "Transcription success: '$transcription'")
                        handleUserSpeech(transcription)
                    } else {
                        val msg = when {
                            !whisperProcessor.isNativeLibraryLoaded() -> "Native库未加载，请检查so文件"
                            !whisperProcessor.isInitialized() -> "语音识别初始化中，请稍后再试"
                            else -> "未能识别到语音，请重试"
                        }
                        Log.w(TAG, "Transcription empty/null: $msg")
                        errorMessage = msg
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "doTranscribe error", e)
                withContext(Dispatchers.Main) {
                    errorMessage = "处理失败：${e.message}"
                    isProcessing = false
                }
            }
        }
    }

    fun startRecording() {
        if (!hasAudioPermission()) {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        if (isRecording || isProcessing || isReplying) {
            Log.w(TAG, "startRecording blocked: isRecording=$isRecording, isProcessing=$isProcessing, isReplying=$isReplying")
            return
        }
        errorMessage = null
        Log.d(TAG, "=== startRecording called, whisperReady=$whisperReady ===")

        scope.launch(Dispatchers.IO) {
            audioRecorder.forceStop()
            delay(30)
            // VAD 静音自动触发：停顿超过 1s 自动停止并识别
            audioRecorder.silenceTimeoutMs = 1000L
            audioRecorder.onSilenceDetected = {
                Log.d(TAG, "VAD silence detected, auto-triggering transcription")
                scope.launch(Dispatchers.Main) { doTranscribe() }
            }
            audioRecorder.onRmsChanged = { rms ->
                scope.launch(Dispatchers.Main) { rmsLevel = rms.coerceIn(0f, 1f) }
            }
            val started = audioRecorder.startRecording()
            Log.d(TAG, "AudioRecorder.startRecording() = $started")
            withContext(Dispatchers.Main) {
                if (started) {
                    isRecording = true
                    Log.d(TAG, "Recording started, isRecording=true")
                } else {
                    errorMessage = "无法启动录音，请重试"
                    Log.e(TAG, "Failed to start recording")
                }
            }
        }
    }

    fun releaseRecording() {
        if (!isRecording) {
            Log.w(TAG, "releaseRecording called but isRecording=false, ignoring")
            return
        }
        // 用户松开按钮，直接触发转写（与VAD路径相同）
        doTranscribe()
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
        onNavigateBack = { endCurrentSession(); onNavigateBack() },
        actions = {
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
                    item { WelcomeCard(whisperReady) }
                }
                items(messageList) { msg -> ChatBubble(message = msg) }
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

            RecordingControlBar(
                isRecording = isRecording,
                isProcessing = isProcessing,
                isReplying = isReplying,
                whisperReady = whisperReady,
                rmsLevel = rmsLevel,
                onPress = { startRecording() },
                onRelease = { releaseRecording() }
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

// ── 我的情绪日志弹窗（老人端）──────────────────────────────────────
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
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
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
@Composable
private fun WelcomeCard(whisperReady: Boolean) {
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
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary)
            Text("您好，我是您的陪伴助手",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                textAlign = TextAlign.Center)
            if (!whisperReady) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary)
                    Text("语音识别加载中...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            } else {
                Text("按住下方麦克风说话，松开发送",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    textAlign = TextAlign.Center)
            }
        }
    }
}

// ── 聊天气泡 ────────────────────────────────────────────────────────
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

// ── 思考中气泡 ──────────────────────────────────────────────────────
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

// ── 录音控制栏（Push-to-Talk，纯 Whisper）──────────────────────────
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun RecordingControlBar(
    isRecording: Boolean,
    isProcessing: Boolean,
    isReplying: Boolean,
    whisperReady: Boolean,
    rmsLevel: Float,
    onPress: () -> Unit,
    onRelease: () -> Unit
) {
    val busy = isProcessing || isReplying || !whisperReady

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

            Text(
                text = when {
                    !whisperReady -> "语音识别加载中..."
                    isReplying    -> "AI 正在思考..."
                    isProcessing  -> "正在识别语音..."
                    isRecording   -> "松开发送"
                    else          -> "按住说话"
                },
                style = MaterialTheme.typography.titleMedium,
                color = if (isRecording) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (isRecording) VolumeBar(rmsLevel = rmsLevel)

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

// ── 音量条 ──────────────────────────────────────────────────────────
@Composable
private fun VolumeBar(rmsLevel: Float) {
    val barCount = 20
    Row(modifier = Modifier.fillMaxWidth().height(32.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically) {
        repeat(barCount) { i ->
            val active = rmsLevel > i.toFloat() / barCount
            Box(modifier = Modifier
                .width(8.dp)
                .fillMaxHeight(if (active) 0.8f else 0.3f)
                .clip(RoundedCornerShape(4.dp))
                .background(
                    if (active) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant
                ))
        }
    }
}

// ── 兼容旧代码 ───────────────────────────────────────────────────────
data class DiaryEntry(val id: Long, val date: Date, val content: String,
                      val emotion: String, val aiResponse: String)

private fun analyzeEmotionFromText(text: String): String {
    val (emotion, _) = EmotionAnalyzer.analyze(text)
    return emotion
}
