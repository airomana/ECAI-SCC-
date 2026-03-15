package com.eldercare.ai.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import android.widget.Toast
import com.eldercare.ai.rememberElderCareDatabase
import com.eldercare.ai.data.ElderCareDatabase
import com.eldercare.ai.data.SettingsManager
import com.eldercare.ai.data.entity.HealthProfile
import com.eldercare.ai.llm.LlmConfig
import com.eldercare.ai.tts.TtsService
import com.eldercare.ai.ui.components.ElderCareDimens
import com.eldercare.ai.ui.components.ElderCareScaffold
import com.eldercare.ai.ui.theme.ElderCareAITheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit = {},
    onNavigateToFamilyGuard: () -> Unit = {},
    onNavigateToPersonalSituation: () -> Unit = {},
    onLogout: () -> Unit = {}
) {
    val context = LocalContext.current
    var showInviteCodeDialog by remember { mutableStateOf(false) }
    var showLinkInviteCodeDialog by remember { mutableStateOf(false) }
    var showLlmConfigDialog by remember { mutableStateOf(false) }
    var showSmsGatewayDialog by remember { mutableStateOf(false) }
    val db = rememberElderCareDatabase()
    val scope = rememberCoroutineScope()
    val settingsManager = remember { SettingsManager.getInstance(context) }
    val ttsService = remember { TtsService.getInstance(context) }
    val userService = remember { 
        com.eldercare.ai.auth.UserService(db.userDao(), db.familyRelationDao(), db.familyLinkRequestDao(), settingsManager) 
    }
    
    // 读取TTS设置和角色
    var voiceEnabled by remember { mutableStateOf(settingsManager.ttsEnabled) }
    var vibrationEnabled by remember { mutableStateOf(settingsManager.vibrationEnabled) }
    var fontSize by remember { mutableStateOf(settingsManager.fontSize) }
    var currentRole by remember { mutableStateOf(settingsManager.getUserRole()) }
    var currentUser by remember { mutableStateOf<com.eldercare.ai.data.entity.User?>(null) }
    var llmEnabled by remember { mutableStateOf(settingsManager.isLlmEnabled()) }
    
    // 加载当前用户信息
    LaunchedEffect(Unit) {
        currentUser = userService.getCurrentUser()
    }
    
    ElderCareScaffold(
        title = "设置",
        onNavigateBack = onNavigateBack
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = ElderCareDimens.ScreenPadding, vertical = ElderCareDimens.SectionSpacing),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                SettingsSection(title = "个人信息") {
                    if (currentRole == "parent") {
                        SettingsItem(
                            icon = Icons.Default.Person,
                            title = "个人情况（详细）",
                            subtitle = "健康、饮食、守护等信息",
                            onClick = onNavigateToPersonalSituation
                        )
                    } else {
                        SettingsItem(
                            icon = Icons.Default.People,
                            title = "父母信息",
                            subtitle = "在子女守护中心查看已授权信息",
                            onClick = onNavigateToFamilyGuard
                        )
                    }
                }
            }
            
            item {
                SettingsSection(title = "账户设置") {
                    SettingsItem(
                        icon = Icons.Default.AccountCircle,
                        title = "当前身份",
                        subtitle = if (currentRole == "parent") "父母端" else "子女端",
                        onClick = { }
                    )
                    
                    // 父母端：查看邀请码
                    if (currentRole == "parent") {
                        SettingsItem(
                            icon = Icons.Default.Info,
                            title = "我的邀请码",
                            subtitle = currentUser?.inviteCode ?: "加载中...",
                            onClick = { showInviteCodeDialog = true }
                        )
                    }
                    
                    // 子女端：通过邀请码关联
                    if (currentRole == "child" && currentUser?.familyId == null) {
                        SettingsItem(
                            icon = Icons.Default.Link,
                            title = "关联家庭",
                            subtitle = "输入邀请码关联到父母",
                            onClick = { showLinkInviteCodeDialog = true }
                        )
                    }
                }
            }
            
            item {
                if (currentRole == "parent") {
                    // 父母端不显示子女守护中心入口
                } else {
                    SettingsSection(title = "家庭功能") {
                        SettingsItem(
                            icon = Icons.Default.People,
                            title = "子女守护中心",
                            subtitle = "查看父母饮食记录和周报",
                            onClick = onNavigateToFamilyGuard
                        )
                    }
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
                val apiKeyConfigured = settingsManager.getLlmApiKey().isNotBlank()
                SettingsSection(title = "大模型") {
                    SettingsItemWithSwitch(
                        icon = Icons.Default.SmartToy,
                        title = "启用大模型",
                        subtitle = if (llmEnabled) "已开启" else "已关闭",
                        checked = llmEnabled,
                        onCheckedChange = {
                            llmEnabled = it
                            settingsManager.setLlmEnabled(it)
                        }
                    )

                    SettingsItem(
                        icon = Icons.Default.Key,
                        title = "配置API Key",
                        subtitle = if (apiKeyConfigured) "已配置" else "未配置（不配置会识别失败）",
                        onClick = { showLlmConfigDialog = true }
                    )

                    SettingsItem(
                        icon = Icons.Default.Tune,
                        title = "模型名称",
                        subtitle = settingsManager.getLlmModel().takeIf { it.isNotBlank() } ?: "qwen-turbo",
                        onClick = { showLlmConfigDialog = true }
                    )
                }
            }

            item {
                val smsConfigured = settingsManager.getSmsGatewayUrl().isNotBlank()
                SettingsSection(title = "短信验证码") {
                    SettingsItem(
                        icon = Icons.Default.Sms,
                        title = "短信网关",
                        subtitle = if (smsConfigured) "已配置" else "未配置（无法发送验证码）",
                        onClick = { showSmsGatewayDialog = true }
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
                }
            }
            
            item {
                SettingsSection(title = "账户") {
                    SettingsItem(
                        icon = Icons.Default.PersonOff,
                        title = "退出登录",
                        subtitle = "退出当前账号",
                        onClick = {
                            settingsManager.logout()
                            onLogout()
                        }
                    )
                }
            }
        }
    }
    
    // 查看邀请码对话框（父母端）
    if (showInviteCodeDialog) {
        InviteCodeDialog(
            inviteCode = currentUser?.inviteCode ?: "",
            onDismiss = { showInviteCodeDialog = false }
        )
    }
    
    // 通过邀请码关联对话框（子女端）
    var linkErrorMessage by remember { mutableStateOf<String?>(null) }
    var showLinkPendingDialog by remember { mutableStateOf(false) }
    if (showLinkInviteCodeDialog) {
        LinkInviteCodeDialog(
            errorMessage = linkErrorMessage,
            onDismiss = { 
                showLinkInviteCodeDialog = false
                linkErrorMessage = null
            },
            onLink = { inviteCode ->
                scope.launch {
                    val userId = settingsManager.getCurrentUserId()
                    if (userId != null) {
                        val result = userService.linkFamilyByInviteCode(userId, inviteCode)
                        when (result) {
                            is com.eldercare.ai.auth.LinkResult.Pending -> {
                                showLinkInviteCodeDialog = false
                                linkErrorMessage = null
                                showLinkPendingDialog = true
                            }
                            is com.eldercare.ai.auth.LinkResult.Error -> {
                                linkErrorMessage = result.message
                            }
                        }
                    }
                }
            }
        )
    }

    if (showLinkPendingDialog) {
        AlertDialog(
            onDismissRequest = { showLinkPendingDialog = false },
            title = { Text("已提交") },
            text = { Text("绑定申请已提交，等待父母端确认后生效") },
            confirmButton = {
                TextButton(onClick = { showLinkPendingDialog = false }) {
                    Text("知道了")
                }
            }
        )
    }

    if (showLlmConfigDialog) {
        LlmConfigDialog(
            initialApiKey = settingsManager.getLlmApiKey(),
            initialModel = settingsManager.getLlmModel().takeIf { it.isNotBlank() } ?: "qwen-turbo",
            onDismiss = { showLlmConfigDialog = false },
            onSave = { apiKey, model ->
                LlmConfig.setApiKey(context, apiKey)
                LlmConfig.setModel(context, model)
                showLlmConfigDialog = false
            }
        )
    }

    if (showSmsGatewayDialog) {
        SmsGatewayDialog(
            initialUrl = settingsManager.getSmsGatewayUrl(),
            initialToken = settingsManager.getSmsGatewayToken(),
            onDismiss = { showSmsGatewayDialog = false },
            onSave = { url, token ->
                settingsManager.setSmsGatewayUrl(url)
                settingsManager.setSmsGatewayToken(token)
                showSmsGatewayDialog = false
            }
        )
    }
}

@Composable
fun SmsGatewayDialog(
    initialUrl: String,
    initialToken: String,
    onDismiss: () -> Unit,
    onSave: (url: String, token: String) -> Unit
) {
    var url by remember { mutableStateOf(initialUrl) }
    var token by remember { mutableStateOf(initialToken) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("短信网关配置") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("网关URL") },
                    placeholder = { Text("https://...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text("Token（可选）") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Text(
                    text = "验证码将通过网关发送到用户手机",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(url.trim(), token.trim()) }) {
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
fun InviteCodeDialog(
    inviteCode: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("我的邀请码") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("请将以下邀请码分享给您的子女：")
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = inviteCode,
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                        OutlinedButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(inviteCode))
                                Toast.makeText(context, "邀请码已复制", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null)
                            Spacer(modifier = Modifier.size(8.dp))
                            Text("复制邀请码")
                        }
                    }
                }
                
                Text(
                    text = "子女端输入邀请码后会发起关联申请，需您确认后生效",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("知道了")
            }
        }
    )
}

@Composable
fun LinkInviteCodeDialog(
    errorMessage: String?,
    onDismiss: () -> Unit,
    onLink: (String) -> Unit
) {
    var inviteCode by remember { mutableStateOf("") }
    var localErrorMessage by remember { mutableStateOf<String?>(null) }
    
    // 同步外部错误信息
    LaunchedEffect(errorMessage) {
        localErrorMessage = errorMessage
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("关联家庭") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("请输入父母提供的邀请码：")
                
                OutlinedTextField(
                    value = inviteCode,
                    onValueChange = { 
                        inviteCode = it.trim().uppercase().take(24)
                        localErrorMessage = null
                    },
                    label = { Text("邀请码") },
                    placeholder = { Text("请输入邀请码") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Ascii
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = localErrorMessage != null
                )
                
                if (localErrorMessage != null) {
                    Text(
                        text = localErrorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                
                Text(
                    text = "关联后即可查看父母的饮食记录和健康档案",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (inviteCode.isBlank()) {
                        localErrorMessage = "请输入邀请码"
                    } else {
                        onLink(inviteCode)
                    }
                },
                enabled = inviteCode.isNotBlank()
            ) {
                Text("关联")
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

@Composable
fun LlmConfigDialog(
    initialApiKey: String,
    initialModel: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var apiKey by remember { mutableStateOf(initialApiKey) }
    var model by remember { mutableStateOf(initialModel) }
    var showApiKey by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("大模型配置") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("用于拍冰箱/拍菜单/语音日记等功能。")

                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("DashScope API Key") },
                    placeholder = { Text("sk-...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation()
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Checkbox(
                        checked = showApiKey,
                        onCheckedChange = { showApiKey = it }
                    )
                    Text("显示Key")
                }

                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text("文本模型名称") },
                    placeholder = { Text("qwen-turbo") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Text(
                    text = "提示：Key 配错会出现 401 InvalidApiKey。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(apiKey, model) },
                enabled = apiKey.isNotBlank()
            ) {
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
