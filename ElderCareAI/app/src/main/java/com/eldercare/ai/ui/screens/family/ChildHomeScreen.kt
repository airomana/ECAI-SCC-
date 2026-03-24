package com.eldercare.ai.ui.screens.family

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eldercare.ai.rememberElderCareDatabase
import com.eldercare.ai.data.SettingsManager
import com.eldercare.ai.data.entity.DiaryEntryEntity
import com.eldercare.ai.data.entity.EmergencyContactEntity
import com.eldercare.ai.data.entity.HealthProfile
import com.eldercare.ai.data.entity.PersonalSituationEntity
import com.eldercare.ai.data.entity.ProfileEditRequestEntity
import com.eldercare.ai.data.model.ProfileEditPayload
import com.eldercare.ai.ui.components.ElderCareDimens
import com.eldercare.ai.ui.components.ElderCareScaffold
import com.eldercare.ai.ui.theme.ElderCareAITheme
import com.google.gson.Gson
import kotlinx.coroutines.launch
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
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager.getInstance(context) }
    val gson = remember { Gson() }
    val scope = rememberCoroutineScope()
    val currentUserId = settingsManager.getCurrentUserId() ?: 0L
    val currentUser by db.userDao().getByIdFlow(currentUserId).collectAsStateWithLifecycle(initialValue = null)
    val pendingLinkRequests by db.familyLinkRequestDao().getByChildAndStatus(currentUserId, "pending").collectAsStateWithLifecycle(initialValue = emptyList())
    val isLinked = currentUser?.familyId != null
    val diaryEntries by db.diaryEntryDao().getAll().collectAsStateWithLifecycle(initialValue = emptyList())
    val healthProfile by db.healthProfileDao().get().collectAsStateWithLifecycle(initialValue = null)
    val personalSituation by db.personalSituationDao().get().collectAsStateWithLifecycle(initialValue = null)
    val contacts by db.emergencyContactDao().getAll().collectAsStateWithLifecycle(initialValue = emptyList())
    val pendingRequests by db.profileEditRequestDao().getByStatus("pending").collectAsStateWithLifecycle(initialValue = emptyList())
    val shareHealth = isLinked && (personalSituation?.shareHealth ?: false)
    val shareDiet = isLinked && (personalSituation?.shareDiet ?: false)
    val shareContacts = isLinked && (personalSituation?.shareContacts ?: false)
    var showSuggestDialog by remember { mutableStateOf(false) }
    var showSubmittedDialog by remember { mutableStateOf(false) }
    var showBindDialog by remember { mutableStateOf(false) }
    var bindInviteCode by remember { mutableStateOf("") }
    var bindError by remember { mutableStateOf<String?>(null) }
    var isBinding by remember { mutableStateOf(false) }
    
    // 周报相关状态
    var showWeeklyReportDialog by remember { mutableStateOf(false) }
    var isGeneratingReport by remember { mutableStateOf(false) }
    var generatedReport by remember { mutableStateOf<String?>(null) }
    val llmService = remember { com.eldercare.ai.llm.LlmService.getInstance(context) }
    
    // 计算统计数据
    val now = System.currentTimeMillis()
    val weekAgo = now - 7 * 24 * 60 * 60 * 1000L
    val weeklyEntries = diaryEntries.filter { it.date >= weekAgo }
    val emotionStats = weeklyEntries.groupBy { it.emotion }
    val mostCommonEmotion = emotionStats.maxByOrNull { it.value.size }?.key ?: "无"
    val alerts = remember(diaryEntries, healthProfile) {
        generateAlerts(diaryEntries, healthProfile)
    }
    
    ElderCareScaffold(
        title = "子女守护中心",
        onNavigateBack = null,
        actions = {
            IconButton(onClick = onNavigateToSettings) {
                Icon(Icons.Default.Settings, contentDescription = "设置")
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
                // 未绑定：只显示绑定卡片
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("尚未绑定父母", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        if (pendingLinkRequests.isNotEmpty()) {
                            Text(
                                text = "绑定申请已提交，等待父母确认",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(
                                text = "输入父母的邀请码发起绑定申请",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedTextField(
                                value = bindInviteCode,
                                onValueChange = {
                                    bindInviteCode = it.trim().uppercase().take(24)
                                    bindError = null
                                },
                                label = { Text("邀请码") },
                                placeholder = { Text("请输入父母的邀请码") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                isError = bindError != null,
                                supportingText = bindError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } }
                            )
                            Button(
                                onClick = {
                                    if (bindInviteCode.isBlank()) {
                                        bindError = "请输入邀请码"
                                        return@Button
                                    }
                                    isBinding = true
                                    scope.launch {
                                        val userService = com.eldercare.ai.auth.UserService(
                                            db.userDao(),
                                            db.familyRelationDao(),
                                            db.familyLinkRequestDao(),
                                            settingsManager
                                        )
                                        val result = userService.linkFamilyByInviteCode(currentUserId, bindInviteCode)
                                        isBinding = false
                                        when (result) {
                                            is com.eldercare.ai.auth.LinkResult.Pending -> {
                                                bindInviteCode = ""
                                                bindError = null
                                            }
                                            is com.eldercare.ai.auth.LinkResult.Error -> {
                                                bindError = result.message
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !isBinding
                            ) {
                                if (isBinding) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                } else {
                                    Text("提交绑定申请")
                                }
                            }
                        }
                    }
                }
            } else {
                // 已绑定：显示完整内容
                ChildHealthProfileCard(
                    healthProfile = if (shareHealth) healthProfile else null,
                    shareHealth = shareHealth,
                    onEditClick = {
                        if (shareHealth) showSuggestDialog = true
                    }
                )

                if (shareContacts) {
                    ChildEmergencyContactsCard(contacts = contacts)
                }

                if (pendingRequests.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("修改建议已提交", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                text = "等待父母确认后生效",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "本周陪伴统计",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Button(
                                onClick = {
                                    showWeeklyReportDialog = true
                                    if (generatedReport == null) {
                                        isGeneratingReport = true
                                        scope.launch {
                                            val report = llmService.generateWeeklyReport(weeklyEntries, healthProfile)
                                            generatedReport = report ?: "周报生成失败或模型未配置，请稍后再试或配置模型。"
                                            isGeneratingReport = false
                                        }
                                    }
                                },
                                enabled = weeklyEntries.isNotEmpty()
                            ) {
                                Text("生成智能周报")
                            }
                        }
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
                    text = "最近陪伴记录",
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

    if (showSuggestDialog) {
        ChildSuggestProfileDialog(
            healthProfile = healthProfile,
            personalSituation = personalSituation,
            shareDiet = shareDiet,
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
                TextButton(onClick = { showSubmittedDialog = false }) {
                    Text("知道了")
                }
            }
        )
    }

    if (showWeeklyReportDialog) {
        AlertDialog(
            onDismissRequest = { showWeeklyReportDialog = false },
            title = { Text("智能情绪陪伴周报") },
            text = {
                if (isGeneratingReport) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator()
                        Text("正在生成智能周报...")
                    }
                } else {
                    Text(text = generatedReport ?: "暂无内容")
                }
            },
            confirmButton = {
                TextButton(onClick = { showWeeklyReportDialog = false }) {
                    Text("关闭")
                }
            }
        )
    }
}

@Composable
private fun ChildHealthProfileCard(
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
                contacts.take(3).forEach { c ->
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
