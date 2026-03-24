package com.eldercare.ai.ui.screens.family

import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eldercare.ai.rememberElderCareDatabase
import com.eldercare.ai.companion.ConversationManager
import com.eldercare.ai.companion.EmotionAnalyzer
import com.eldercare.ai.data.SettingsManager
import com.eldercare.ai.data.entity.DiaryEntryEntity
import com.eldercare.ai.data.entity.EmergencyContactEntity
import com.eldercare.ai.data.entity.EmotionLogEntity
import com.eldercare.ai.data.entity.HealthProfile
import com.eldercare.ai.data.entity.PersonalSituationEntity
import com.eldercare.ai.data.entity.ProfileEditRequestEntity
import com.eldercare.ai.data.model.ProfileEditPayload
import com.eldercare.ai.ui.components.ElderCareDimens
import com.eldercare.ai.ui.components.ElderCareScaffold
import com.eldercare.ai.ui.theme.ElderCareAITheme
import com.google.gson.Gson
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import android.content.Intent
import android.net.Uri

/**
 * 子女远程守护中心
 * 功能：
 * 1. 查看父母每日聊天陪伴记录
 * 2. 设置健康档案
 * 3. 查看情绪周报
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
    val settingsManager = remember { SettingsManager.getInstance(context) }
    val gson = remember { Gson() }
    val currentUserId = settingsManager.getCurrentUserId() ?: 0L
    val currentUser by db.userDao().getByIdFlow(currentUserId).collectAsStateWithLifecycle(initialValue = null)
    val pendingLinkRequests by db.familyLinkRequestDao().getByChildAndStatus(currentUserId, "pending").collectAsStateWithLifecycle(initialValue = emptyList())
    val isLinked = currentUser?.familyId != null
    
    // 数据状态
    val diaryEntries by db.diaryEntryDao().getAll().collectAsStateWithLifecycle(initialValue = emptyList())
    val healthProfile by db.healthProfileDao().get().collectAsStateWithLifecycle(initialValue = null)
    val personalSituation by db.personalSituationDao().get().collectAsStateWithLifecycle(initialValue = null)
    val contacts by db.emergencyContactDao().getAll().collectAsStateWithLifecycle(initialValue = emptyList())
    val emotionLogs by db.emotionLogDao().getAll().collectAsStateWithLifecycle(initialValue = emptyList())
    var showWeeklyReport by remember { mutableStateOf(false) }
    var showAlerts by remember { mutableStateOf(false) }
    var showSuggestDialog by remember { mutableStateOf(false) }
    var showSubmittedDialog by remember { mutableStateOf(false) }
    val shareHealth = isLinked && (personalSituation?.shareHealth ?: false)
    val shareContacts = isLinked && (personalSituation?.shareContacts ?: false)
    
    // 每次进入页面时，同步一下最新的用户状态（看父母是否已确认绑定）
    LaunchedEffect(currentUserId) {
        if (!isLinked && currentUserId > 0) {
            try {
                val userService = com.eldercare.ai.auth.UserService(
                    db.userDao(),
                    db.familyRelationDao(),
                    db.familyLinkRequestDao(),
                    settingsManager
                )
                val syncManager = com.eldercare.ai.data.network.SyncManager(context)
                syncManager.syncHealthAndPermissions()
                userService.syncCurrentUserStatus()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    ElderCareScaffold(
        title = "子女守护中心",
        onNavigateBack = onNavigateBack,
        actions = {
            IconButton(onClick = { showWeeklyReport = true }) {
                Icon(Icons.Default.Assessment, contentDescription = "周报")
            }
            IconButton(onClick = { showAlerts = true }) {
                Icon(Icons.Default.Warning, contentDescription = "警报")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = ElderCareDimens.ScreenPadding, vertical = ElderCareDimens.SectionSpacing),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (!isLinked) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("尚未绑定父母", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(
                            text = if (pendingLinkRequests.isNotEmpty()) "绑定申请已提交，等待父母确认" else "请在设置中输入父母邀请码发起绑定申请",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = onNavigateBack,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("返回")
                        }
                    }
                }
                return@Column
            }

            // 健康档案卡片
            HealthProfileCard(
                healthProfile = if (shareHealth) healthProfile else null,
                shareHealth = shareHealth,
                onEditClick = {
                    if (shareHealth) showSuggestDialog = true
                }
            )

            if (shareContacts) {
                ChildEmergencyContactsCard(contacts = contacts)
            }
            
            // 本周统计卡片
            WeeklyStatsCard(diaryEntries = diaryEntries)
            
            // 情绪日志卡片
            EmotionLogCard(emotionLogs = emotionLogs)

            // 异常警报卡片
            AlertsCard(diaryEntries = diaryEntries, healthProfile = healthProfile)
            
            // 最近陪伴记录
            Text(
                text = "最近陪伴记录",
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

    if (showSuggestDialog) {
        ChildSuggestProfileDialog(
            healthProfile = healthProfile,
            personalSituation = personalSituation,
            shareDiet = personalSituation?.shareDiet ?: false,
            onDismiss = { showSuggestDialog = false },
            onSubmit = { payload ->
                scope.launch {
                    val json = gson.toJson(payload)
                    val userId = settingsManager.getCurrentUserId() ?: 0L
                    db.profileEditRequestDao().insert(
                        ProfileEditRequestEntity(
                            status = "pending",
                            proposerUserId = userId,
                            proposerRole = "child",
                            payloadJson = json,
                            createdAt = System.currentTimeMillis()
                        )
                    )
                }
                showSuggestDialog = false
                showSubmittedDialog = true
            }
        )
    }

    if (showSubmittedDialog) {
        AlertDialog(
            onDismissRequest = { showSubmittedDialog = false },
            title = { Text("已提交") },
            text = { Text("修改建议已提交，等待父母确认") },
            confirmButton = {
                TextButton(onClick = { showSubmittedDialog = false }) { Text("知道了") }
            }
        )
    }
}

@Composable
fun HealthProfileCard(
    healthProfile: HealthProfile?,
    shareHealth: Boolean,
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
                    text = "父母健康信息",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = onEditClick) {
                    Text(if (shareHealth) "建议修改" else "未授权")
                }
            }
            
            if (healthProfile != null) {
                Text("姓名：${healthProfile.name}")
                if (healthProfile.sex.isNotBlank()) {
                    Text("性别：${healthProfile.sex}")
                }
                if (healthProfile.age > 0) {
                    Text("年龄：${healthProfile.age}岁")
                } else if (healthProfile.birthYear > 0) {
                    Text("出生年：${healthProfile.birthYear}")
                }
                if (healthProfile.diseases.isNotEmpty()) {
                    Text("疾病：${healthProfile.diseases.joinToString("、")}")
                }
                if (healthProfile.allergies.isNotEmpty()) {
                    Text(
                        text = "禁忌：${healthProfile.allergies.joinToString("、")}",
                        color = MaterialTheme.colorScheme.error
                    )
                }
                if (healthProfile.dietRestrictions.isNotEmpty()) {
                    Text("忌口：${healthProfile.dietRestrictions.joinToString("、")}")
                }
            } else {
                Text(
                    text = if (shareHealth) "未设置健康信息" else "父母暂未授权共享健康信息",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ChildEmergencyContactsCard(
    contacts: List<EmergencyContactEntity>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("紧急联系人", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (contacts.isEmpty()) {
                Text("未设置", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                contacts.take(5).forEach { c ->
                    Text("${c.name.ifBlank { "未命名" }}：${c.phone}")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChildSuggestProfileDialog(
    healthProfile: HealthProfile?,
    personalSituation: PersonalSituationEntity?,
    shareDiet: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (ProfileEditPayload) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var sex by remember { mutableStateOf("") }
    var age by remember { mutableStateOf("") }
    var birthYear by remember { mutableStateOf("") }
    var diseases by remember { mutableStateOf("") }
    var allergies by remember { mutableStateOf("") }
    var restrictions by remember { mutableStateOf("") }

    LaunchedEffect(healthProfile) {
        name = healthProfile?.name ?: ""
        sex = healthProfile?.sex ?: ""
        age = healthProfile?.age?.takeIf { it > 0 }?.toString() ?: ""
        birthYear = healthProfile?.birthYear?.takeIf { it > 0 }?.toString() ?: ""
        diseases = healthProfile?.diseases?.joinToString(", ") ?: ""
        allergies = healthProfile?.allergies?.joinToString(", ") ?: ""
        restrictions = healthProfile?.dietRestrictions?.joinToString(", ") ?: ""
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("建议父母修改") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("姓名") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = sex,
                    onValueChange = { sex = it },
                    label = { Text("性别") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = age,
                        onValueChange = { age = it.filter { ch -> ch.isDigit() }.take(3) },
                        label = { Text("年龄") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = birthYear,
                        onValueChange = { birthYear = it.filter { ch -> ch.isDigit() }.take(4) },
                        label = { Text("出生年") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = diseases,
                    onValueChange = { diseases = it },
                    label = { Text("慢病（逗号分隔）") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
                OutlinedTextField(
                    value = allergies,
                    onValueChange = { allergies = it },
                    label = { Text("过敏/禁忌（逗号分隔）") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
                OutlinedTextField(
                    value = restrictions,
                    onValueChange = { restrictions = it },
                    label = { Text("忌口/医嘱（逗号分隔）") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
                if (!shareDiet) {
                    Text(
                        text = "父母暂未授权共享饮食偏好，此处仅提交健康相关建议",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val ds = diseases.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    val als = allergies.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    val rs = restrictions.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    onSubmit(
                        ProfileEditPayload(
                            name = name.trim(),
                            sex = sex.trim(),
                            age = age.toIntOrNull() ?: 0,
                            birthYear = birthYear.toIntOrNull() ?: 0,
                            diseases = ds,
                            allergies = als,
                            dietRestrictions = rs
                        )
                    )
                }
            ) { Text("提交") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
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
    val context = LocalContext.current
    val db = rememberElderCareDatabase()
    val scope = rememberCoroutineScope()
    val conversationManager = remember { ConversationManager.getInstance(context, db) }
    val healthProfile by db.healthProfileDao().get().collectAsStateWithLifecycle(initialValue = null)
    val contacts by db.emergencyContactDao().getAll().collectAsStateWithLifecycle(initialValue = emptyList())
    var isGenerating by remember { mutableStateOf(true) }
    var reportContent by remember { mutableStateOf<String?>(null) }
    var reportLogs by remember { mutableStateOf<List<EmotionLogEntity>>(emptyList()) }
    var showSendOptions by remember { mutableStateOf(false) }

    LaunchedEffect(diaryEntries) {
        val weekAgo = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L
        reportLogs = db.emotionLogDao().getByTimeRange(weekAgo, System.currentTimeMillis())
        val report = conversationManager.generateWeeklyReport(healthProfile?.name)
        reportContent = report
        isGenerating = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("智能情绪陪伴周报") },
        text = {
            if (isGenerating) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator()
                    Text("正在生成智能周报...")
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(reportContent ?: "暂无内容")
                    if (!reportContent.isNullOrBlank()) {
                        Button(
                            onClick = { showSendOptions = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("发送给子女")
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )

    // 发送方式选择
    if (showSendOptions && !reportContent.isNullOrBlank()) {
        SendReportDialog(
            reportContent = reportContent!!,
            contacts = contacts,
            onDismiss = { showSendOptions = false },
            onSent = {
                // 标记本周所有日志为已发送
                scope.launch {
                    reportLogs.filter { !it.sentToFamily }.forEach { log ->
                        db.emotionLogDao().markAsSent(log.id)
                    }
                }
                showSendOptions = false
            }
        )
    }
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
 * 发送周报对话框（短信/分享）
 */
@Composable
fun SendReportDialog(
    reportContent: String,
    contacts: List<EmergencyContactEntity>,
    onDismiss: () -> Unit,
    onSent: () -> Unit = {}
) {
    val context = LocalContext.current
    var phoneNumber by remember { mutableStateOf("") }

    // 预填第一个联系人号码
    LaunchedEffect(contacts) {
        if (phoneNumber.isBlank() && contacts.isNotEmpty()) {
            phoneNumber = contacts.first().phone
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("发送周报给子女") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("选择发送方式：", style = MaterialTheme.typography.bodyMedium)

                // 快速选择联系人
                if (contacts.isNotEmpty()) {
                    Text("快速选择：", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    contacts.take(5).forEach { contact ->
                        OutlinedButton(
                            onClick = { phoneNumber = contact.phone },
                            modifier = Modifier.fillMaxWidth(),
                            colors = if (phoneNumber == contact.phone)
                                ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                            else ButtonDefaults.outlinedButtonColors()
                        ) {
                            Text("${contact.name.ifBlank { "联系人" }}  ${contact.phone}")
                        }
                    }
                }

                // 手动输入号码
                OutlinedTextField(
                    value = phoneNumber,
                    onValueChange = { phoneNumber = it.filter { c -> c.isDigit() }.take(11) },
                    label = { Text("子女手机号") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = KeyboardType.Phone
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Button(
                    onClick = {
                        if (phoneNumber.length >= 7) {
                            val intent = Intent(Intent.ACTION_SENDTO).apply {
                                data = Uri.parse("smsto:$phoneNumber")
                                putExtra("sms_body", reportContent)
                            }
                            try {
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                android.util.Log.e("SendReport", "SMS failed", e)
                            }
                        }
                        onSent()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = phoneNumber.length >= 7
                ) {
                    Icon(Icons.Default.Sms, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("发送短信")
                }

                // 系统分享
                OutlinedButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, reportContent)
                            putExtra(Intent.EXTRA_SUBJECT, "父母情绪陪伴周报")
                        }
                        context.startActivity(Intent.createChooser(intent, "分享周报"))
                        onSent()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("其他方式分享")
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/**
 * 情绪日志卡片（展示近7天情绪趋势，含简单条形图）
 */
@Composable
fun EmotionLogCard(emotionLogs: List<EmotionLogEntity>) {
    if (emotionLogs.isEmpty()) return
    val sdf = remember { SimpleDateFormat("MM/dd", Locale.getDefault()) }
    val recent7 = remember(emotionLogs) { emotionLogs.sortedBy { it.dayTimestamp }.takeLast(7) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("近期情绪日志", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            // 情绪趋势条形图
            if (recent7.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(60.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    recent7.forEach { log ->
                        val barColor = when (log.dominantEmotion) {
                            "开心", "满意" -> MaterialTheme.colorScheme.primary
                            "孤单", "难过" -> MaterialTheme.colorScheme.secondary
                            "担心", "不适" -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.4f)
                        }
                        val heightFraction = when (log.dominantEmotion) {
                            "开心", "满意" -> 1.0f
                            "平静" -> 0.6f
                            "孤单", "担心" -> 0.5f
                            "难过", "不适" -> 0.4f
                            else -> 0.5f
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Bottom
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(heightFraction)
                                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                    .background(barColor)
                            )
                        }
                    }
                }
                // 日期标签
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    recent7.forEach { log ->
                        Text(
                            sdf.format(Date(log.dayTimestamp)),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }

            // 文字列表
            recent7.reversed().forEach { log ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        sdf.format(Date(log.dayTimestamp)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = when (log.dominantEmotion) {
                            "开心", "满意" -> MaterialTheme.colorScheme.primaryContainer
                            "孤单", "难过" -> MaterialTheme.colorScheme.secondaryContainer
                            "担心", "不适" -> MaterialTheme.colorScheme.errorContainer
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    ) {
                        Text(
                            log.dominantEmotion,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    Text(
                        "对话${log.conversationCount}次",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    // 已发送标记
                    if (log.sentToFamily) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "已发送",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

/**
 * 生成本地周报
 */
private fun generateLocalWeeklyReport(weeklyEntries: List<DiaryEntryEntity>): List<String> {
    val report = mutableListOf<String>()
    report.add("【本周陪伴报告】")
    report.add("")
    
    // 陪伴规律性
    val daysWithEntries = weeklyEntries.map { 
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(it.date))
    }.distinct().size
    report.add("✅ 记录天数：本周${daysWithEntries}天有陪伴记录")
    
    // 情感观察
    val emotionStats = weeklyEntries.groupBy { it.emotion }
    val lonelyCount = emotionStats["孤单"]?.size ?: 0
    val worryCount = emotionStats["担心"]?.size ?: 0
    
    if (lonelyCount > 0) {
        report.add("💬 情感观察：父母说了${lonelyCount}次感到\"孤单\"的话，建议多抽空联系、陪伴。")
    }
    if (worryCount > 0) {
        report.add("⚠️ 心理关怀：父母表达了${worryCount}次\"担心\"或焦虑，记得打电话关心一下。")
    }
    if (lonelyCount == 0 && worryCount == 0) {
        report.add("😊 情感状态：父母本周情绪比较稳定，继续保持哦！")
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
    
    // 检查连续3天高油高盐饮食提及
    val highFatKeywords = listOf("红烧", "油炸", "油", "肉", "肥")
    val highFatCount = recentEntries.count { entry ->
        highFatKeywords.any { entry.content.contains(it) }
    }
    if (highFatCount >= 3) {
        alerts.add("连续3天提及高油高盐食物，建议提醒父母注意饮食")
    }
    
    // 检查孤独信号
    val lonelyCount = recentEntries.count { it.emotion == "孤单" }
    if (lonelyCount >= 3) {
        alerts.add("父母连续3天表达孤独情绪，建议多陪伴和联系")
    }
    
    // 检查负面情绪信号
    val worryCount = recentEntries.count { it.emotion == "担心" }
    if (worryCount >= 3) {
        alerts.add("父母最近可能有些焦虑或担心，建议打电话关心一下")
    }
    
    // 检查身体不适关键词
    val sickKeywords = listOf("疼", "痛", "不舒服", "难受", "生病", "头晕", "乏力")
    val sickCount = recentEntries.count { entry ->
        sickKeywords.any { entry.content.contains(it) }
    }
    if (sickCount > 0) {
        alerts.add("父母最近提及了身体不适，请及时关注健康状况")
    }
    
    // 检查禁忌食物
    if (healthProfile != null && healthProfile.allergies.isNotEmpty()) {
        recentEntries.forEach { entry ->
            healthProfile.allergies.forEach { allergy ->
                if (entry.content.contains(allergy)) {
                    alerts.add("父母聊天中提及了可能的禁忌食物：$allergy")
                }
            }
        }
    }
    
    return alerts.distinct()
}
