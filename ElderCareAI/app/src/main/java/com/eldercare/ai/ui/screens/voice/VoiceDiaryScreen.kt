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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eldercare.ai.rememberElderCareDatabase
import com.eldercare.ai.data.entity.DiaryEntryEntity
import com.eldercare.ai.ui.theme.ElderCareAITheme
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceDiaryScreen(
    onNavigateBack: () -> Unit = {}
) {
    var isRecording by remember { mutableStateOf(false) }
    var recordedText by remember { mutableStateOf("") }
    val db = rememberElderCareDatabase()
    val scope = rememberCoroutineScope()
    val diaryEntries by db.diaryEntryDao().getAll()
        .map { list -> list.map { it.toDiaryEntry() } }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    
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
            onStartRecording = { 
                isRecording = true
                recordedText = ""
            },
            onStopRecording = { 
                isRecording = false
                // 模拟语音识别结果
                recordedText = "今天中午吃了红烧肉，味道还不错，就是有点油腻。晚上喝了小米粥，挺清淡的。"
            },
            onSaveDiary = {
                if (recordedText.isNotEmpty()) {
                    scope.launch {
                        db.diaryEntryDao().insert(
                            DiaryEntryEntity(
                                date = System.currentTimeMillis(),
                                content = recordedText,
                                emotion = "满意",
                                aiResponse = "听起来您今天吃得不错！红烧肉确实比较油腻，配点清淡的粥很好。建议明天可以多吃点蔬菜哦～"
                            )
                        )
                    }
                    recordedText = ""
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
                text = if (isRecording) "正在录音..." else "点击开始录音",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            
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