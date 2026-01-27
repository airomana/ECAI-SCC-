package com.eldercare.ai.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.platform.LocalContext
import com.eldercare.ai.rememberElderCareDatabase
import com.eldercare.ai.data.ElderCareDatabase
import com.eldercare.ai.data.SettingsManager
import com.eldercare.ai.data.entity.HealthProfile
import com.eldercare.ai.tts.TtsService
import com.eldercare.ai.ui.theme.ElderCareAITheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    var showHealthProfileDialog by remember { mutableStateOf(false) }
    var showEmergencyContactDialog by remember { mutableStateOf(false) }
    val db = rememberElderCareDatabase()
    val scope = rememberCoroutineScope()
    val settingsManager = remember { SettingsManager(context) }
    val ttsService = remember { TtsService.getInstance(context) }
    
    // 读取TTS设置
    var voiceEnabled by remember { mutableStateOf(settingsManager.ttsEnabled) }
    var vibrationEnabled by remember { mutableStateOf(settingsManager.vibrationEnabled) }
    var fontSize by remember { mutableStateOf(settingsManager.fontSize) }
    
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
                text = "设置",
                style = MaterialTheme.typography.displayMedium,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.size(64.dp))
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // 设置项列表
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                SettingsSection(title = "个人信息") {
                    SettingsItem(
                        icon = Icons.Default.Person,
                        title = "健康档案",
                        subtitle = "设置您的健康信息",
                        onClick = { showHealthProfileDialog = true }
                    )
                    
                    SettingsItem(
                        icon = Icons.Default.ContactPhone,
                        title = "紧急联系人",
                        subtitle = "设置家人联系方式",
                        onClick = { showEmergencyContactDialog = true }
                    )
                }
            }
            
            item {
                SettingsSection(title = "使用设置") {
                    SettingsItemWithSwitch(
                        icon = Icons.Default.VolumeUp,
                        title = "语音播报",
                        subtitle = "开启后会语音播报识别结果",
                        checked = voiceEnabled,
                        onCheckedChange = { 
                            voiceEnabled = it
                            settingsManager.ttsEnabled = it
                            ttsService.setEnabled(it)
                        }
                    )
                    
                    SettingsItemWithSwitch(
                        icon = Icons.Default.Vibration,
                        title = "震动提醒",
                        subtitle = "重要提醒时震动",
                        checked = vibrationEnabled,
                        onCheckedChange = { 
                            vibrationEnabled = it
                            settingsManager.vibrationEnabled = it
                        }
                    )
                    
                    SettingsItemWithSlider(
                        icon = Icons.Default.TextFields,
                        title = "字体大小",
                        subtitle = "调整文字显示大小",
                        value = fontSize,
                        onValueChange = { 
                            fontSize = it
                            settingsManager.fontSize = it
                        },
                        valueRange = 1f..3f
                    )
                }
            }
            
            item {
                SettingsSection(title = "关于") {
                    SettingsItem(
                        icon = Icons.Default.Info,
                        title = "使用帮助",
                        subtitle = "查看使用说明",
                        onClick = { }
                    )
                    
                    SettingsItem(
                        icon = Icons.Default.Phone,
                        title = "联系客服",
                        subtitle = "400-123-4567",
                        onClick = { }
                    )
                    
                    SettingsItem(
                        icon = Icons.Default.Update,
                        title = "版本信息",
                        subtitle = "银发智膳助手 v1.0",
                        onClick = { }
                    )
                }
            }
        }
    }
    
    // 健康档案对话框
    if (showHealthProfileDialog) {
        HealthProfileDialog(
            db = db,
            onDismiss = { showHealthProfileDialog = false },
            onSave = { name, age, diseases ->
                scope.launch {
                    val existing = db.healthProfileDao().getOnce()
                    val ds = diseases.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    val profile = HealthProfile(
                        id = existing?.id ?: 0,
                        name = name,
                        age = age.toIntOrNull() ?: 0,
                        diseases = ds,
                        allergies = existing?.allergies ?: emptyList(),
                        updatedAt = System.currentTimeMillis()
                    )
                    if (existing != null) db.healthProfileDao().update(profile) else db.healthProfileDao().insert(profile)
                }
                showHealthProfileDialog = false
            }
        )
    }
    
    // 紧急联系人对话框
    if (showEmergencyContactDialog) {
        EmergencyContactDialog(
            onDismiss = { showEmergencyContactDialog = false },
            onSave = { 
                showEmergencyContactDialog = false
                // 保存紧急联系人
            }
        )
    }
}

@Composable
fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(4.dp)
            ) {
                content()
            }
        }
    }
}

@Composable
fun SettingsItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "进入",
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SettingsItemWithSwitch(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    }
}

@Composable
fun SettingsItemWithSlider(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = valueRange,
                steps = 1
            )
        }
    }
}

@Composable
fun HealthProfileDialog(
    db: ElderCareDatabase,
    onDismiss: () -> Unit,
    onSave: (name: String, age: String, diseases: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var age by remember { mutableStateOf("") }
    var diseases by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val p = db.healthProfileDao().getOnce()
        if (p != null) {
            name = p.name
            age = p.age.toString().takeIf { it != "0" } ?: ""
            diseases = p.diseases.joinToString(", ")
        }
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "健康档案",
                style = MaterialTheme.typography.headlineMedium
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("姓名") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                OutlinedTextField(
                    value = age,
                    onValueChange = { age = it },
                    label = { Text("年龄") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                OutlinedTextField(
                    value = diseases,
                    onValueChange = { diseases = it },
                    label = { Text("疾病（如：高血压、糖尿病）") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name, age, diseases) }) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
fun EmergencyContactDialog(
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    var contactName by remember { mutableStateOf("") }
    var contactPhone by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "紧急联系人",
                style = MaterialTheme.typography.headlineMedium
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = contactName,
                    onValueChange = { contactName = it },
                    label = { Text("联系人姓名") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                OutlinedTextField(
                    value = contactPhone,
                    onValueChange = { contactPhone = it },
                    label = { Text("电话号码") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onSave) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
fun SettingsScreenPreview() {
    ElderCareAITheme {
        SettingsScreen()
    }
}