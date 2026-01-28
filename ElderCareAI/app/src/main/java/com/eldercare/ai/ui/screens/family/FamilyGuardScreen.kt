package com.eldercare.ai.ui.screens.family

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eldercare.ai.rememberElderCareDatabase
import com.eldercare.ai.data.entity.DiaryEntryEntity
import com.eldercare.ai.data.entity.HealthProfile
import com.eldercare.ai.ui.theme.ElderCareAITheme
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * 子女远程守护中心
 * 功能：
 * 1. 查看父母每日饮食记录
 * 2. 设置饮食禁忌
 * 3. 查看周报
 * 4. 异常警报
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamilyGuardScreen(
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val db = rememberElderCareDatabase()
    val scope = rememberCoroutineScope()
    
    // 数据状态
    val diaryEntries by db.diaryEntryDao().getAll().collectAsStateWithLifecycle(initialValue = emptyList())
    var healthProfile by remember { mutableStateOf<HealthProfile?>(null) }
    var showWeeklyReport by remember { mutableStateOf(false) }
    var showAlerts by remember { mutableStateOf(false) }
    
    // 加载健康档案
    LaunchedEffect(Unit) {
        healthProfile = db.healthProfileDao().getOnce()
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("子女守护中心") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { showWeeklyReport = true }) {
                        Icon(Icons.Default.Assessment, contentDescription = "周报")
                    }
                    IconButton(onClick = { showAlerts = true }) {
                        Icon(Icons.Default.Warning, contentDescription = "警报")
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
            HealthProfileCard(
                healthProfile = healthProfile,
                onEditClick = {
                    // TODO: 打开编辑健康档案界面
                }
            )
            
            // 本周统计卡片
            WeeklyStatsCard(diaryEntries = diaryEntries)
            
            // 异常警报卡片
            AlertsCard(diaryEntries = diaryEntries, healthProfile = healthProfile)
            
            // 最近饮食记录
            Text(
                text = "最近饮食记录",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(diaryEntries.take(10)) { entry ->
                    DiaryEntryCard(entry = entry)
                }
            }
        }
    }
    
    // 周报对话框
    if (showWeeklyReport) {
        WeeklyReportDialog(
            diaryEntries = diaryEntries,
            onDismiss = { showWeeklyReport = false }
        )
    }
    
    // 警报对话框
    if (showAlerts) {
        AlertsDialog(
            diaryEntries = diaryEntries,
            healthProfile = healthProfile,
            onDismiss = { showAlerts = false }
        )
    }
}

@Composable
fun HealthProfileCard(
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
fun WeeklyStatsCard(diaryEntries: List<DiaryEntryEntity>) {
    val now = System.currentTimeMillis()
    val weekAgo = now - 7 * 24 * 60 * 60 * 1000L
    val weeklyEntries = diaryEntries.filter { it.date >= weekAgo }
    
    val emotionStats = weeklyEntries.groupBy { it.emotion }
    val mostCommonEmotion = emotionStats.maxByOrNull { it.value.size }?.key ?: "无"
    
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
            Text("记录天数：${weeklyEntries.size}/7")
            Text("主要情绪：$mostCommonEmotion")
            Text("总记录数：${weeklyEntries.size}条")
        }
    }
}

@Composable
fun AlertsCard(
    diaryEntries: List<DiaryEntryEntity>,
    healthProfile: HealthProfile?
) {
    val alerts = remember(diaryEntries, healthProfile) {
        generateAlerts(diaryEntries, healthProfile)
    }
    
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
                
                alerts.forEach { alert ->
                    Text("⚠️ $alert")
                }
            }
        }
    }
}

@Composable
fun DiaryEntryCard(entry: DiaryEntryEntity) {
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
            
            if (entry.aiResponse.isNotEmpty()) {
                Text(
                    text = "AI回复：${entry.aiResponse}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun WeeklyReportDialog(
    diaryEntries: List<DiaryEntryEntity>,
    onDismiss: () -> Unit
) {
    val report = remember(diaryEntries) {
        generateWeeklyReport(diaryEntries)
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("本周饮食报告") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                report.forEach { line ->
                    Text(line)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}

@Composable
fun AlertsDialog(
    diaryEntries: List<DiaryEntryEntity>,
    healthProfile: HealthProfile?,
    onDismiss: () -> Unit
) {
    val alerts = remember(diaryEntries, healthProfile) {
        generateAlerts(diaryEntries, healthProfile)
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("异常警报") },
        text = {
            if (alerts.isEmpty()) {
                Text("暂无异常情况")
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    alerts.forEach { alert ->
                        Text("⚠️ $alert")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}

/**
 * 生成周报
 */
private fun generateWeeklyReport(diaryEntries: List<DiaryEntryEntity>): List<String> {
    val now = System.currentTimeMillis()
    val weekAgo = now - 7 * 24 * 60 * 60 * 1000L
    val weeklyEntries = diaryEntries.filter { it.date >= weekAgo }
    
    val report = mutableListOf<String>()
    report.add("【本周饮食报告】")
    report.add("")
    
    // 饮食规律性
    val daysWithEntries = weeklyEntries.map { 
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(it.date))
    }.distinct().size
    report.add("✅ 饮食规律性：本周${daysWithEntries}天有记录")
    
    // 情感观察
    val emotionStats = weeklyEntries.groupBy { it.emotion }
    val lonelyCount = emotionStats["孤单"]?.size ?: 0
    if (lonelyCount > 0) {
        report.add("💬 情感观察：父母说了${lonelyCount}次\"孤单\"相关的话，可能有些孤单，有空多陪陪他哦")
    }
    
    // 营养提醒
    val highFatKeywords = listOf("红烧", "油炸", "油", "肉", "肥")
    val highFatCount = weeklyEntries.count { entry ->
        highFatKeywords.any { entry.content.contains(it) }
    }
    if (highFatCount >= 3) {
        report.add("⚠️ 营养提醒：本周高油高盐食物较多（${highFatCount}次），建议提醒父母注意饮食清淡")
    }
    
    return report
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
