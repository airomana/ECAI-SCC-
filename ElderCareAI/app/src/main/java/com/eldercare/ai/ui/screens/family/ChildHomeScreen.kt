package com.eldercare.ai.ui.screens.family

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eldercare.ai.rememberElderCareDatabase
import com.eldercare.ai.data.entity.DiaryEntryEntity
import com.eldercare.ai.data.entity.HealthProfile
import com.eldercare.ai.ui.theme.ElderCareAITheme
import java.text.SimpleDateFormat
import java.util.*

/**
 * 子女端首页
 * 显示父母饮食记录概览、健康档案、异常警报等
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChildHomeScreen(
    onNavigateToFamilyGuard: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {}
) {
    val db = rememberElderCareDatabase()
    val diaryEntries by db.diaryEntryDao().getAll().collectAsStateWithLifecycle(initialValue = emptyList())
    var healthProfile by remember { mutableStateOf<HealthProfile?>(null) }
    
    LaunchedEffect(Unit) {
        healthProfile = db.healthProfileDao().getOnce()
    }
    
    // 计算统计数据
    val now = System.currentTimeMillis()
    val weekAgo = now - 7 * 24 * 60 * 60 * 1000L
    val weeklyEntries = diaryEntries.filter { it.date >= weekAgo }
    val emotionStats = weeklyEntries.groupBy { it.emotion }
    val mostCommonEmotion = emotionStats.maxByOrNull { it.value.size }?.key ?: "无"
    val alerts = remember(diaryEntries, healthProfile) {
        generateAlerts(diaryEntries, healthProfile)
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("子女守护中心") },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "设置")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 健康档案卡片
            ChildHealthProfileCard(
                healthProfile = healthProfile,
                onEditClick = {
                    // TODO: 打开编辑健康档案界面
                }
            )
            
            // 本周统计卡片
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "本周统计",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text("记录天数：${weeklyEntries.distinctBy { 
                        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(it.date))
                    }.size}/7")
                    Text("主要情绪：$mostCommonEmotion")
                    Text("总记录数：${weeklyEntries.size}条")
                }
            }
            
            // 异常警报卡片
            if (alerts.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = "异常提醒",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        
                        alerts.take(3).forEach { alert ->
                            Text("⚠️ $alert")
                        }
                    }
                }
            }
            
            // 查看详细记录按钮
            Button(
                onClick = onNavigateToFamilyGuard,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(Icons.Default.List, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("查看详细记录")
            }
            
            // 最近3条记录
            Text(
                text = "最近记录",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            
            if (weeklyEntries.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Text(
                        text = "暂无记录",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                weeklyEntries.take(3).forEach { entry ->
                    ChildDiaryEntryCard(entry = entry)
                }
            }
        }
    }
}

@Composable
private fun ChildHealthProfileCard(
    healthProfile: HealthProfile?,
    onEditClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "父母健康档案",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = onEditClick) {
                    Text("编辑")
                }
            }
            
            if (healthProfile != null) {
                Text("姓名：${healthProfile.name}")
                Text("年龄：${healthProfile.age}岁")
                if (healthProfile.diseases.isNotEmpty()) {
                    Text("疾病：${healthProfile.diseases.joinToString("、")}")
                }
                if (healthProfile.allergies.isNotEmpty()) {
                    Text(
                        text = "禁忌：${healthProfile.allergies.joinToString("、")}",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            } else {
                Text("未设置健康档案，点击编辑进行设置")
            }
        }
    }
}

@Composable
private fun ChildDiaryEntryCard(entry: DiaryEntryEntity) {
    val dateFormat = SimpleDateFormat("MM月dd日 HH:mm", Locale.getDefault())
    val dateStr = dateFormat.format(Date(entry.date))
    
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = dateStr,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (entry.emotion.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = when (entry.emotion) {
                            "满意" -> MaterialTheme.colorScheme.primaryContainer
                            "孤单" -> MaterialTheme.colorScheme.secondaryContainer
                            "担心" -> MaterialTheme.colorScheme.errorContainer
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    ) {
                        Text(
                            text = entry.emotion,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
            
            Text(
                text = entry.content,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

/**
 * 生成异常警报
 */
private fun generateAlerts(
    diaryEntries: List<DiaryEntryEntity>,
    healthProfile: HealthProfile?
): List<String> {
    val alerts = mutableListOf<String>()
    val now = System.currentTimeMillis()
    val threeDaysAgo = now - 3 * 24 * 60 * 60 * 1000L
    val recentEntries = diaryEntries.filter { it.date >= threeDaysAgo }
    
    // 检查连续3天高油高盐
    val highFatKeywords = listOf("红烧", "油炸", "油", "肉", "肥")
    val highFatCount = recentEntries.count { entry ->
        highFatKeywords.any { entry.content.contains(it) }
    }
    if (highFatCount >= 3) {
        alerts.add("连续3天高油高盐食物较多，建议提醒父母注意饮食")
    }
    
    // 检查孤独信号
    val lonelyCount = recentEntries.count { it.emotion == "孤单" }
    if (lonelyCount >= 3) {
        alerts.add("父母连续3天表达孤独情绪，建议多陪伴")
    }
    
    // 检查禁忌食物
    if (healthProfile != null && healthProfile.allergies.isNotEmpty()) {
        recentEntries.forEach { entry ->
            healthProfile.allergies.forEach { allergy ->
                if (entry.content.contains(allergy)) {
                    alerts.add("父母可能摄入了禁忌食物：$allergy")
                }
            }
        }
    }
    
    return alerts
}
