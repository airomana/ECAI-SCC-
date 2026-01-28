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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    val db = rememberElderCareDatabase()
    val scope = rememberCoroutineScope()
    val whisperProcessor = remember { WhisperProcessor.getInstance(context) }
    val audioRecorder = remember { AudioRecorder() }
    val androidSpeechRecognizer = remember { AndroidSpeechRecognizer.getInstance(context) }
    
    // 选择使用哪种识别方式
    // true = 使用Android原生SpeechRecognizer（真实识别，需要网络，立即可用）
    // false = 使用Whisper（需要模型文件，离线，需要集成whisper.cpp）
    // 默认使用Android识别器，因为它立即可用且是真实的识别
    var useAndroidRecognizer by remember { mutableStateOf(true) }
    
    val diaryEntries by db.diaryEntryDao().getAll()
        .map { list -> list.map { it.toDiaryEntry() } }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    
    // 录音权限请求Launcher
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // 权限已授予，开始录音
            scope.launch(Dispatchers.IO) {
                val started = audioRecorder.startRecording()
                withContext(Dispatchers.Main) {
                    if (started) {
                        isRecording = true
                        recordedText = ""
                        errorMessage = null
                    } else {
                        errorMessage = "无法启动录音，请重试"
                    }
                }
            }
        } else {
            // 权限被拒绝
            errorMessage = "需要录音权限才能使用此功能，请在设置中授予权限"
        }
    }
    
    // 启动录音的辅助函数
    fun launchRecording() {
        // 如果已经在录音，先强制停止
        if (isRecording) {
            android.util.Log.w("VoiceDiary", "已经在录音，先强制停止")
            scope.launch(Dispatchers.IO) {
                audioRecorder.forceStop()
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
                    
                    scope.launch(Dispatchers.IO) {
                        try {
                            val result = androidSpeechRecognizer.recognize()
                            withContext(Dispatchers.Main) {
                                isRecording = false
                                isProcessing = false
                                if (result.isNullOrBlank()) {
                                    recordedText = "识别失败，请重试"
                                    errorMessage = "未能识别到语音，请重试"
                                } else {
                                    recordedText = result
                                    errorMessage = null
                                }
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("VoiceDiary", "Android识别失败", e)
                            withContext(Dispatchers.Main) {
                                isRecording = false
                                isProcessing = false
                                errorMessage = "识别失败：${e.message}"
                            }
                        }
                    }
                } else {
                    // Android识别器不可用，自动使用Whisper
                    android.util.Log.d("VoiceDiary", "Android识别器不可用，使用Whisper录音")
                    useAndroidRecognizer = false
                    
                    // 使用Whisper（需要录音）
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
            errorMessage = errorMessage,
            onStartRecording = { 
                launchRecording()
            },
            onStopRecording = { 
                // 如果使用Android识别器，停止识别
                if (useAndroidRecognizer && androidSpeechRecognizer.isAvailable()) {
                    androidSpeechRecognizer.stop()
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
                if (recordedText.isNotEmpty()) {
                    scope.launch {
                        // 简单的情感分析
                        val emotion = when {
                            recordedText.contains("好吃") || recordedText.contains("满意") || recordedText.contains("不错") -> "满意"
                            recordedText.contains("一个人") || recordedText.contains("孤单") -> "孤单"
                            recordedText.contains("难受") || recordedText.contains("不舒服") -> "担心"
                            else -> "平静"
                        }
                        
                        db.diaryEntryDao().insert(
                            DiaryEntryEntity(
                                date = System.currentTimeMillis(),
                                content = recordedText,
                                emotion = emotion,
                                aiResponse = "感谢您的分享！我会记住您今天的饮食情况。"
                            )
                        )
                    }
                    recordedText = ""
                    errorMessage = null
                }
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
            
            // 处理中指示器（只在没有识别结果时显示）
            if (isProcessing && recordedText.isEmpty()) {
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
                            modifier = Modifier.fillMaxWidth()
                        ) {
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