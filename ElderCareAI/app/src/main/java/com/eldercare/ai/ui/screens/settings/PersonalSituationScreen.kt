package com.eldercare.ai.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eldercare.ai.data.SettingsManager
import com.eldercare.ai.data.entity.EmergencyContactEntity
import com.eldercare.ai.data.entity.HealthProfile
import com.eldercare.ai.data.entity.PersonalSituationEntity
import com.eldercare.ai.data.entity.ProfileEditRequestEntity
import com.eldercare.ai.data.model.ProfileEditPayload
import com.eldercare.ai.rememberElderCareDatabase
import com.google.gson.Gson
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonalSituationScreen(
    onNavigateBack: () -> Unit = {}
) {
    val db = rememberElderCareDatabase()
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsManager = remember { SettingsManager.getInstance(context) }
    val role = remember { settingsManager.getUserRole() }
    val currentUserId = remember { settingsManager.getCurrentUserId() ?: 0L }
    val gson = remember { Gson() }

    val healthProfile by db.healthProfileDao().get().collectAsStateWithLifecycle(initialValue = null)
    val personalSituation by db.personalSituationDao().get().collectAsStateWithLifecycle(initialValue = null)
    val contacts by db.emergencyContactDao().getAll().collectAsStateWithLifecycle(initialValue = emptyList())
    val pendingRequests by db.profileEditRequestDao().getByStatus("pending").collectAsStateWithLifecycle(initialValue = emptyList())

    var showBasicDialog by remember { mutableStateOf(false) }
    var showHealthDialog by remember { mutableStateOf(false) }
    var showDietDialog by remember { mutableStateOf(false) }
    var showLivingDialog by remember { mutableStateOf(false) }
    var contactEditing by remember { mutableStateOf<EmergencyContactEntity?>(null) }
    var showContactDialog by remember { mutableStateOf(false) }
    var showDeleteContactConfirm by remember { mutableStateOf<EmergencyContactEntity?>(null) }
    var requestHandling by remember { mutableStateOf<ProfileEditRequestEntity?>(null) }

    val completion = remember(healthProfile, personalSituation, contacts) {
        var filled = 0
        val total = 10
        if (!healthProfile?.name.isNullOrBlank()) filled++
        if (!healthProfile?.sex.isNullOrBlank()) filled++
        if ((healthProfile?.age ?: 0) > 0 || (healthProfile?.birthYear ?: 0) > 0) filled++
        if (!healthProfile?.diseases.isNullOrEmpty()) filled++
        if (!healthProfile?.allergies.isNullOrEmpty()) filled++
        if (!healthProfile?.dietRestrictions.isNullOrEmpty()) filled++
        if (!personalSituation?.city.isNullOrBlank()) filled++
        if (!personalSituation?.tastePreferences.isNullOrEmpty()) filled++
        if (!personalSituation?.chewLevel.isNullOrBlank()) filled++
        if (contacts.isNotEmpty()) filled++
        (filled * 100 / total).coerceIn(0, 100)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("个人情况（详细）") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "完善度：$completion%",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = "完善后，拍菜单会更准确提醒您能不能吃、怎么吃",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (role == "parent" && pendingRequests.isNotEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = "待处理",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                    Text("子女有 ${pendingRequests.size} 条修改建议待您确认")
                                }
                                TextButton(onClick = { requestHandling = pendingRequests.firstOrNull() }) {
                                    Text("去处理")
                                }
                            }
                        }
                    }
                }
            }

            item {
                SectionCard(
                    title = "基础信息",
                    subtitle = listOfNotNull(
                        healthProfile?.name?.takeIf { it.isNotBlank() }?.let { "姓名：$it" },
                        healthProfile?.sex?.takeIf { it.isNotBlank() }?.let { "性别：$it" },
                        healthProfile?.age?.takeIf { it > 0 }?.let { "年龄：${it}岁" }
                            ?: healthProfile?.birthYear?.takeIf { it > 0 }?.let { "出生年：$it" },
                        personalSituation?.city?.takeIf { it.isNotBlank() }?.let { "常住地：$it" }
                    ).joinToString("  ").ifBlank { "未填写" },
                    onEdit = { showBasicDialog = true }
                )
            }

            item {
                SectionCard(
                    title = "健康与禁忌",
                    subtitle = buildString {
                        val ds = healthProfile?.diseases?.takeIf { it.isNotEmpty() }?.joinToString("、")
                        val asx = healthProfile?.allergies?.takeIf { it.isNotEmpty() }?.joinToString("、")
                        val rs = healthProfile?.dietRestrictions?.takeIf { it.isNotEmpty() }?.joinToString("、")
                        if (ds != null) append("慢病：$ds  ")
                        if (asx != null) append("过敏/禁忌：$asx  ")
                        if (rs != null) append("忌口：$rs")
                        if (length == 0) append("未填写")
                    },
                    onEdit = { showHealthDialog = true }
                )
            }

            item {
                SectionCard(
                    title = "饮食与咀嚼",
                    subtitle = buildString {
                        val taste = personalSituation?.tastePreferences?.takeIf { it.isNotEmpty() }?.joinToString("、")
                        val chew = personalSituation?.chewLevel?.takeIf { it.isNotBlank() }
                        val soft = personalSituation?.preferSoftFood == true
                        if (taste != null) append("口味：$taste  ")
                        if (chew != null) append("咀嚼：$chew  ")
                        if (soft) append("偏软烂")
                        if (length == 0) append("未填写")
                    },
                    onEdit = { showDietDialog = true }
                )
            }

            item {
                SectionCard(
                    title = "生活与守护",
                    subtitle = buildString {
                        val alone = personalSituation?.livingAlone == true
                        val bp = personalSituation?.bloodPressureStatus?.takeIf { it.isNotBlank() }
                        val bs = personalSituation?.bloodSugarStatus?.takeIf { it.isNotBlank() }
                        if (alone) append("独居  ")
                        if (bp != null) append("血压：$bp  ")
                        if (bs != null) append("血糖：$bs  ")
                        if (contacts.isNotEmpty()) append("紧急联系人：${contacts.size}位")
                        if (length == 0) append("未填写")
                    },
                    onEdit = { showLivingDialog = true }
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Security, contentDescription = "授权")
                                Text("共享授权", style = MaterialTheme.typography.titleLarge)
                            }
                        }

                        val shareHealth = personalSituation?.shareHealth ?: false
                        val shareDiet = personalSituation?.shareDiet ?: false
                        val shareContacts = personalSituation?.shareContacts ?: false

                        ToggleRow(
                            title = "共享健康与禁忌",
                            checked = shareHealth,
                            onCheckedChange = { checkedValue ->
                                scope.launch {
                                    upsertPersonalSituation(db, personalSituation) { entity ->
                                        entity.copy(shareHealth = checkedValue, updatedAt = System.currentTimeMillis())
                                    }
                                }
                            }
                        )
                        ToggleRow(
                            title = "共享饮食偏好",
                            checked = shareDiet,
                            onCheckedChange = { checkedValue ->
                                scope.launch {
                                    upsertPersonalSituation(db, personalSituation) { entity ->
                                        entity.copy(shareDiet = checkedValue, updatedAt = System.currentTimeMillis())
                                    }
                                }
                            }
                        )
                        ToggleRow(
                            title = "共享紧急联系人",
                            checked = shareContacts,
                            onCheckedChange = { checkedValue ->
                                scope.launch {
                                    upsertPersonalSituation(db, personalSituation) { entity ->
                                        entity.copy(shareContacts = checkedValue, updatedAt = System.currentTimeMillis())
                                    }
                                }
                            }
                        )

                        Text(
                            text = "您可以随时关闭共享，子女端将不再显示相关信息",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.People, contentDescription = "联系人")
                        Text("紧急联系人", style = MaterialTheme.typography.titleLarge)
                    }
                    Button(
                        onClick = {
                            contactEditing = null
                            showContactDialog = true
                        }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "新增", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("新增")
                    }
                }
            }

            if (contacts.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("未设置紧急联系人")
                            Text(
                                text = "建议至少设置1位家人，方便紧急情况下快速联系",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(contacts) { c ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = c.name.ifBlank { "未命名" } + if (c.isPrimary) "（首选）" else "",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = c.phone.ifBlank { "未填写电话" },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (c.relation.isNotBlank()) {
                                    Text(
                                        text = c.relation,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = {
                                        contactEditing = c
                                        showContactDialog = true
                                    }
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = "编辑")
                                }
                                IconButton(onClick = { showDeleteContactConfirm = c }) {
                                    Icon(Icons.Default.Delete, contentDescription = "删除")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showBasicDialog) {
        BasicInfoDialog(
            healthProfile = healthProfile,
            personalSituation = personalSituation,
            onDismiss = { showBasicDialog = false },
            onSave = { name, sex, age, birthYear, city ->
                scope.launch {
                    val existing = db.healthProfileDao().getOnce()
                    val merged = (existing ?: HealthProfile()).copy(
                        id = existing?.id ?: 0,
                        name = name.trim(),
                        sex = sex.trim(),
                        age = age,
                        birthYear = birthYear,
                        updatedAt = System.currentTimeMillis()
                    )
                    if (existing == null) db.healthProfileDao().insert(merged) else db.healthProfileDao().update(merged)
                    upsertPersonalSituation(db, personalSituation) { it.copy(city = city.trim(), updatedAt = System.currentTimeMillis()) }
                }
                showBasicDialog = false
            }
        )
    }

    if (showHealthDialog) {
        HealthInfoDialog(
            healthProfile = healthProfile,
            onDismiss = { showHealthDialog = false },
            onSave = { diseases, allergies, restrictions ->
                scope.launch {
                    val existing = db.healthProfileDao().getOnce()
                    val merged = (existing ?: HealthProfile()).copy(
                        id = existing?.id ?: 0,
                        diseases = diseases,
                        allergies = allergies,
                        dietRestrictions = restrictions,
                        updatedAt = System.currentTimeMillis()
                    )
                    if (existing == null) db.healthProfileDao().insert(merged) else db.healthProfileDao().update(merged)
                }
                showHealthDialog = false
            }
        )
    }

    if (showDietDialog) {
        DietInfoDialog(
            personalSituation = personalSituation,
            onDismiss = { showDietDialog = false },
            onSave = { taste, chewLevel, preferSoft, symptoms ->
                scope.launch {
                    upsertPersonalSituation(db, personalSituation) {
                        it.copy(
                            tastePreferences = taste,
                            chewLevel = chewLevel,
                            preferSoftFood = preferSoft,
                            symptoms = symptoms,
                            updatedAt = System.currentTimeMillis()
                        )
                    }
                }
                showDietDialog = false
            }
        )
    }

    if (showLivingDialog) {
        LivingInfoDialog(
            personalSituation = personalSituation,
            onDismiss = { showLivingDialog = false },
            onSave = { livingAlone, bpStatus, bsStatus ->
                scope.launch {
                    upsertPersonalSituation(db, personalSituation) {
                        it.copy(
                            livingAlone = livingAlone,
                            bloodPressureStatus = bpStatus,
                            bloodSugarStatus = bsStatus,
                            updatedAt = System.currentTimeMillis()
                        )
                    }
                }
                showLivingDialog = false
            }
        )
    }

    if (showContactDialog) {
        EmergencyContactDialogV2(
            initial = contactEditing,
            onDismiss = { showContactDialog = false },
            onSave = { name, phone, relation, isPrimary ->
                scope.launch {
                    val now = System.currentTimeMillis()
                    if (isPrimary) db.emergencyContactDao().clearPrimary()
                    val entity = (contactEditing ?: EmergencyContactEntity()).copy(
                        name = name.trim(),
                        phone = phone.trim(),
                        relation = relation.trim(),
                        isPrimary = isPrimary,
                        updatedAt = now
                    )
                    if (entity.id == 0L) db.emergencyContactDao().insert(entity) else db.emergencyContactDao().update(entity)
                }
                showContactDialog = false
            }
        )
    }

    val deleteTarget = showDeleteContactConfirm
    if (deleteTarget != null) {
        AlertDialog(
            onDismissRequest = { showDeleteContactConfirm = null },
            title = { Text("删除联系人") },
            text = { Text("确定删除 ${deleteTarget.name.ifBlank { "该联系人" }} 吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch { db.emergencyContactDao().deleteById(deleteTarget.id) }
                        showDeleteContactConfirm = null
                    }
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteContactConfirm = null }) { Text("取消") }
            }
        )
    }

    val req = requestHandling
    if (role == "parent" && req != null) {
        val payload = remember(req.payloadJson) {
            runCatching { gson.fromJson(req.payloadJson, ProfileEditPayload::class.java) }.getOrNull()
        }
        if (payload != null) {
            ProfileEditRequestDialog(
                request = req,
                payload = payload,
                onDismiss = { requestHandling = null },
                onReject = {
                    scope.launch {
                        db.profileEditRequestDao().update(req.copy(status = "rejected", handledAt = System.currentTimeMillis()))
                    }
                    requestHandling = null
                },
                onAccept = {
                    scope.launch {
                        val existing = db.healthProfileDao().getOnce()
                        val merged = (existing ?: HealthProfile()).copy(
                            id = existing?.id ?: 0,
                            name = payload.name.ifBlank { existing?.name ?: "" },
                            sex = payload.sex.ifBlank { existing?.sex ?: "" },
                            age = payload.age.takeIf { it > 0 } ?: (existing?.age ?: 0),
                            birthYear = payload.birthYear.takeIf { it > 0 } ?: (existing?.birthYear ?: 0),
                            diseases = payload.diseases,
                            allergies = payload.allergies,
                            dietRestrictions = payload.dietRestrictions,
                            updatedAt = System.currentTimeMillis()
                        )
                        if (existing == null) db.healthProfileDao().insert(merged) else db.healthProfileDao().update(merged)
                        db.profileEditRequestDao().update(req.copy(status = "accepted", handledAt = System.currentTimeMillis()))
                    }
                    requestHandling = null
                }
            )
        } else {
            AlertDialog(
                onDismissRequest = { requestHandling = null },
                title = { Text("修改建议") },
                text = { Text("无法读取修改内容") },
                confirmButton = {
                    TextButton(onClick = { requestHandling = null }) { Text("知道了") }
                }
            )
        }
    }

    LaunchedEffect(role, healthProfile, personalSituation) {
        if (role == "child") {
            val p = personalSituation
            val shareHealth = p?.shareHealth ?: false
            if (!shareHealth) return@LaunchedEffect
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    subtitle: String,
    onEdit: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
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
                Text(title, style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = onEdit) { Text("编辑") }
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BasicInfoDialog(
    healthProfile: HealthProfile?,
    personalSituation: PersonalSituationEntity?,
    onDismiss: () -> Unit,
    onSave: (name: String, sex: String, age: Int, birthYear: Int, city: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var sex by remember { mutableStateOf("") }
    var age by remember { mutableStateOf("") }
    var birthYear by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }

    LaunchedEffect(healthProfile, personalSituation) {
        name = healthProfile?.name ?: ""
        sex = healthProfile?.sex ?: ""
        age = healthProfile?.age?.takeIf { it > 0 }?.toString() ?: ""
        birthYear = healthProfile?.birthYear?.takeIf { it > 0 }?.toString() ?: ""
        city = personalSituation?.city ?: ""
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("基础信息") },
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
                    label = { Text("性别（男/女）") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = age,
                    onValueChange = { age = it.filter { ch -> ch.isDigit() }.take(3) },
                    label = { Text("年龄") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = birthYear,
                    onValueChange = { birthYear = it.filter { ch -> ch.isDigit() }.take(4) },
                    label = { Text("出生年（可不填）") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = city,
                    onValueChange = { city = it },
                    label = { Text("常住地（城市）") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val ageInt = age.toIntOrNull() ?: 0
                    val byInt = birthYear.toIntOrNull() ?: 0
                    onSave(name, sex, ageInt, byInt, city)
                }
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun HealthInfoDialog(
    healthProfile: HealthProfile?,
    onDismiss: () -> Unit,
    onSave: (diseases: List<String>, allergies: List<String>, restrictions: List<String>) -> Unit
) {
    val diseaseOptions = remember {
        listOf("高血压", "糖尿病", "高血脂", "痛风", "冠心病", "肾病", "脂肪肝", "胃病")
    }
    val allergyOptions = remember {
        listOf("花生", "海鲜", "牛奶", "鸡蛋", "大豆", "小麦", "坚果", "芒果", "菠萝")
    }
    val restrictionOptions = remember {
        listOf("低盐", "控糖", "低脂", "低嘌呤", "少油", "少辛辣", "少生冷", "戒酒")
    }

    var selectedDiseases by remember { mutableStateOf(emptySet<String>()) }
    var selectedAllergies by remember { mutableStateOf(emptySet<String>()) }
    var selectedRestrictions by remember { mutableStateOf(emptySet<String>()) }
    var customDisease by remember { mutableStateOf("") }
    var customAllergy by remember { mutableStateOf("") }
    var customRestriction by remember { mutableStateOf("") }

    LaunchedEffect(healthProfile) {
        selectedDiseases = (healthProfile?.diseases ?: emptyList()).toSet()
        selectedAllergies = (healthProfile?.allergies ?: emptyList()).toSet()
        selectedRestrictions = (healthProfile?.dietRestrictions ?: emptyList()).toSet()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("健康与禁忌") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("慢病", style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    diseaseOptions.forEach { option ->
                        FilterChip(
                            selected = selectedDiseases.contains(option),
                            onClick = {
                                selectedDiseases = selectedDiseases.toggle(option)
                            },
                            label = { Text(option) }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = customDisease,
                        onValueChange = { customDisease = it.take(10) },
                        label = { Text("自定义慢病") },
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            val v = customDisease.trim()
                            if (v.isNotEmpty()) selectedDiseases = selectedDiseases + v
                            customDisease = ""
                        }
                    ) { Text("添加") }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text("过敏/禁忌", style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    allergyOptions.forEach { option ->
                        FilterChip(
                            selected = selectedAllergies.contains(option),
                            onClick = {
                                selectedAllergies = selectedAllergies.toggle(option)
                            },
                            label = { Text(option) }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = customAllergy,
                        onValueChange = { customAllergy = it.take(10) },
                        label = { Text("自定义禁忌") },
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            val v = customAllergy.trim()
                            if (v.isNotEmpty()) selectedAllergies = selectedAllergies + v
                            customAllergy = ""
                        }
                    ) { Text("添加") }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text("忌口/医嘱限制", style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    restrictionOptions.forEach { option ->
                        FilterChip(
                            selected = selectedRestrictions.contains(option),
                            onClick = {
                                selectedRestrictions = selectedRestrictions.toggle(option)
                            },
                            label = { Text(option) }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = customRestriction,
                        onValueChange = { customRestriction = it.take(10) },
                        label = { Text("自定义忌口") },
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            val v = customRestriction.trim()
                            if (v.isNotEmpty()) selectedRestrictions = selectedRestrictions + v
                            customRestriction = ""
                        }
                    ) { Text("添加") }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        selectedDiseases.toList().sorted(),
                        selectedAllergies.toList().sorted(),
                        selectedRestrictions.toList().sorted()
                    )
                }
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun DietInfoDialog(
    personalSituation: PersonalSituationEntity?,
    onDismiss: () -> Unit,
    onSave: (taste: List<String>, chewLevel: String, preferSoft: Boolean, symptoms: List<String>) -> Unit
) {
    val tasteOptions = remember { listOf("清淡", "偏咸", "偏甜", "偏辣") }
    val chewOptions = remember { listOf("牙口好", "一般", "较差") }
    val symptomOptions = remember { listOf("反酸", "便秘", "腹胀", "没胃口", "牙疼") }

    var taste by remember { mutableStateOf(emptySet<String>()) }
    var chewLevel by remember { mutableStateOf("") }
    var preferSoft by remember { mutableStateOf(false) }
    var symptoms by remember { mutableStateOf(emptySet<String>()) }

    LaunchedEffect(personalSituation) {
        taste = (personalSituation?.tastePreferences ?: emptyList()).toSet()
        chewLevel = personalSituation?.chewLevel ?: ""
        preferSoft = personalSituation?.preferSoftFood ?: false
        symptoms = (personalSituation?.symptoms ?: emptyList()).toSet()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("饮食与咀嚼") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("口味偏好", style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    tasteOptions.forEach { option ->
                        FilterChip(
                            selected = taste.contains(option),
                            onClick = { taste = taste.toggle(option) },
                            label = { Text(option) }
                        )
                    }
                }

                Text("咀嚼情况", style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    chewOptions.forEach { option ->
                        FilterChip(
                            selected = chewLevel == option,
                            onClick = { chewLevel = option },
                            label = { Text(option) }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("偏软烂", style = MaterialTheme.typography.bodyLarge)
                    Switch(checked = preferSoft, onCheckedChange = { preferSoft = it })
                }

                Text("常见不适", style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    symptomOptions.forEach { option ->
                        FilterChip(
                            selected = symptoms.contains(option),
                            onClick = { symptoms = symptoms.toggle(option) },
                            label = { Text(option) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        taste.toList().sorted(),
                        chewLevel,
                        preferSoft,
                        symptoms.toList().sorted()
                    )
                }
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun LivingInfoDialog(
    personalSituation: PersonalSituationEntity?,
    onDismiss: () -> Unit,
    onSave: (livingAlone: Boolean, bloodPressureStatus: String, bloodSugarStatus: String) -> Unit
) {
    val bpOptions = remember { listOf("正常", "偏高", "不清楚") }
    val bsOptions = remember { listOf("正常", "偏高", "不清楚") }

    var livingAlone by remember { mutableStateOf(false) }
    var bp by remember { mutableStateOf("") }
    var bs by remember { mutableStateOf("") }

    LaunchedEffect(personalSituation) {
        livingAlone = personalSituation?.livingAlone ?: false
        bp = personalSituation?.bloodPressureStatus ?: ""
        bs = personalSituation?.bloodSugarStatus ?: ""
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("生活与守护") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("是否独居", style = MaterialTheme.typography.bodyLarge)
                    Switch(checked = livingAlone, onCheckedChange = { livingAlone = it })
                }

                Text("血压情况", style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    bpOptions.forEach { option ->
                        FilterChip(
                            selected = bp == option,
                            onClick = { bp = option },
                            label = { Text(option) }
                        )
                    }
                }

                Text("血糖情况", style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    bsOptions.forEach { option ->
                        FilterChip(
                            selected = bs == option,
                            onClick = { bs = option },
                            label = { Text(option) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(livingAlone, bp, bs) }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EmergencyContactDialogV2(
    initial: EmergencyContactEntity?,
    onDismiss: () -> Unit,
    onSave: (name: String, phone: String, relation: String, isPrimary: Boolean) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var relation by remember { mutableStateOf("") }
    var isPrimary by remember { mutableStateOf(false) }

    LaunchedEffect(initial) {
        name = initial?.name ?: ""
        phone = initial?.phone ?: ""
        relation = initial?.relation ?: ""
        isPrimary = initial?.isPrimary ?: false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "新增联系人" else "编辑联系人") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("姓名") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it.filter { ch -> ch.isDigit() || ch == '+' || ch == '-' }.take(20) },
                    label = { Text("电话") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = relation,
                    onValueChange = { relation = it },
                    label = { Text("关系（如：女儿）") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("设为首选", style = MaterialTheme.typography.bodyLarge)
                    Switch(checked = isPrimary, onCheckedChange = { isPrimary = it })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name, phone, relation, isPrimary) }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileEditRequestDialog(
    request: ProfileEditRequestEntity,
    payload: ProfileEditPayload,
    onDismiss: () -> Unit,
    onReject: () -> Unit,
    onAccept: () -> Unit
) {
    val time = remember(request.createdAt) {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(request.createdAt))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("子女修改建议") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("时间：$time", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (payload.diseases.isNotEmpty()) Text("慢病：${payload.diseases.joinToString("、")}")
                if (payload.allergies.isNotEmpty()) Text("过敏/禁忌：${payload.allergies.joinToString("、")}")
                if (payload.dietRestrictions.isNotEmpty()) Text("忌口：${payload.dietRestrictions.joinToString("、")}")
                if (payload.name.isNotBlank()) Text("姓名：${payload.name}")
                if (payload.sex.isNotBlank()) Text("性别：${payload.sex}")
                if (payload.age > 0) Text("年龄：${payload.age}岁")
                if (payload.birthYear > 0) Text("出生年：${payload.birthYear}")
            }
        },
        confirmButton = {
            TextButton(onClick = onAccept) { Text("同意") }
        },
        dismissButton = {
            TextButton(onClick = onReject) { Text("拒绝") }
        }
    )
}

private suspend fun upsertPersonalSituation(
    db: com.eldercare.ai.data.ElderCareDatabase,
    current: PersonalSituationEntity?,
    transform: (PersonalSituationEntity) -> PersonalSituationEntity
) {
    val base = current ?: PersonalSituationEntity()
    db.personalSituationDao().upsert(transform(base))
}

private fun Set<String>.toggle(value: String): Set<String> {
    return if (contains(value)) this - value else this + value
}
