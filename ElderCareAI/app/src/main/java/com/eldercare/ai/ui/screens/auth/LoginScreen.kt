package com.eldercare.ai.ui.screens.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eldercare.ai.data.SettingsManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 登录/注册界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onAuthSuccess: (next: String) -> Unit,
    userService: com.eldercare.ai.auth.UserService
) {
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager.getInstance(context) }
    var phone by remember { mutableStateOf("") }
    var verificationCode by remember { mutableStateOf("") }
    var inviteCode by remember { mutableStateOf("") }
    var selectedRole by remember { mutableStateOf<String?>(null) }
    var isRegisterMode by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var isSendingCode by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showInviteCode by remember { mutableStateOf(false) }
    var countdown by remember { mutableStateOf(0) }
    var showVerificationCode by remember { mutableStateOf(false) }
    var showParentInviteDialog by remember { mutableStateOf(false) }
    var registeredInviteCode by remember { mutableStateOf("") }
    var showChildPendingDialog by remember { mutableStateOf(false) }
    
    val scope = rememberCoroutineScope()
    val codeService = remember { com.eldercare.ai.auth.VerificationCodeService.getInstance(settingsManager) }
    
    // 倒计时
    LaunchedEffect(countdown) {
        if (countdown > 0) {
            delay(1000)
            countdown--
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // 标题
        Text(
            text = if (isRegisterMode) "注册账号" else "登录",
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 48.dp)
        )
        
        // 手机号输入
        OutlinedTextField(
            value = phone,
            onValueChange = { 
                phone = it
                errorMessage = null
                showVerificationCode = false
            },
            label = { Text("手机号") },
            placeholder = { Text("请输入11位手机号") },
            leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            singleLine = true,
            enabled = !isLoading && !isSendingCode
        )
        
        // 验证码输入和发送
        if (showVerificationCode || verificationCode.isNotBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = verificationCode,
                    onValueChange = { 
                        verificationCode = it
                        errorMessage = null
                    },
                    label = { Text("验证码") },
                    placeholder = { Text("请输入6位验证码") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    enabled = !isLoading && !isSendingCode
                )
                
                Button(
                    onClick = {
                        if (phone.isBlank() || !userService.isValidPhone(phone)) {
                            errorMessage = "请先输入正确的手机号"
                            return@Button
                        }
                        
                        scope.launch {
                            isSendingCode = true
                            errorMessage = null
                            
                            try {
                                val result = codeService.sendCode(phone)
                                when (result) {
                                    is com.eldercare.ai.auth.SendCodeResult.Success -> {
                                        errorMessage = result.debugCode?.let { "验证码已发送（测试：$it）" } ?: "验证码已发送"
                                        countdown = 60
                                        showVerificationCode = true
                                    }
                                    is com.eldercare.ai.auth.SendCodeResult.Error -> {
                                        errorMessage = result.message
                                    }
                                }
                            } catch (e: Exception) {
                                errorMessage = "发送失败：${e.message}"
                            } finally {
                                isSendingCode = false
                            }
                        }
                    },
                    enabled = !isSendingCode && countdown == 0 && phone.isNotBlank() && userService.isValidPhone(phone),
                    modifier = Modifier.height(56.dp)
                ) {
                    if (isSendingCode) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text(if (countdown > 0) "${countdown}秒" else "发送验证码")
                    }
                }
            }
        } else {
            // 获取验证码按钮（首次显示）
            OutlinedButton(
                onClick = {
                    if (phone.isBlank() || !userService.isValidPhone(phone)) {
                        errorMessage = "请先输入正确的手机号"
                        return@OutlinedButton
                    }
                    
                    scope.launch {
                        isSendingCode = true
                        errorMessage = null
                        
                        try {
                            val result = codeService.sendCode(phone)
                            when (result) {
                                is com.eldercare.ai.auth.SendCodeResult.Success -> {
                                    errorMessage = result.debugCode?.let { "验证码已发送（测试：$it）" } ?: "验证码已发送"
                                    countdown = 60
                                    showVerificationCode = true
                                }
                                is com.eldercare.ai.auth.SendCodeResult.Error -> {
                                    errorMessage = result.message
                                }
                            }
                        } catch (e: Exception) {
                            errorMessage = "发送失败：${e.message}"
                        } finally {
                            isSendingCode = false
                        }
                    }
                },
                enabled = !isSendingCode && countdown == 0 && phone.isNotBlank() && userService.isValidPhone(phone),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                if (isSendingCode) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    Text(if (countdown > 0) "${countdown}秒后重发" else "获取验证码")
                }
            }
        }
        
        // 注册模式：选择角色
        if (isRegisterMode && selectedRole == null) {
            Text(
                text = "请选择您的身份",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            )
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 父母端按钮
                Card(
                    onClick = { 
                        selectedRole = "parent"
                        showInviteCode = false
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(120.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("父母端", fontWeight = FontWeight.Bold)
                    }
                }
                
                // 子女端按钮
                Card(
                    onClick = { 
                        selectedRole = "child"
                        showInviteCode = true
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(120.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.People,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("子女端", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        
        // 邀请码输入（子女端注册时）
        if (isRegisterMode && selectedRole == "child" && showInviteCode) {
            OutlinedTextField(
                value = inviteCode,
                onValueChange = { 
                    inviteCode = it.trim().uppercase().take(24)
                    errorMessage = null
                },
                label = { Text("邀请码") },
                placeholder = { Text("请输入邀请码") },
                    leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                singleLine = true,
                enabled = !isLoading
            )
            
            Text(
                text = "邀请码由父母端生成，请向父母获取",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            )
        }
        
        // 错误提示
        if (errorMessage != null) {
            Text(
                text = errorMessage!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            )
        }
        
        // 登录/注册按钮
        Button(
            onClick = {
                if (isLoading) return@Button
                
                scope.launch {
                    isLoading = true
                    errorMessage = null
                    
                    try {
                        // 验证验证码
                        if (!codeService.verifyCode(phone, verificationCode)) {
                            errorMessage = "验证码错误或已过期"
                            isLoading = false
                            return@launch
                        }
                        
                        if (isRegisterMode) {
                            if (selectedRole == null) {
                                errorMessage = "请选择身份"
                                isLoading = false
                                return@launch
                            }
                            
                            val result = userService.register(
                                phone = phone,
                                role = selectedRole!!,
                                inviteCode = if (selectedRole == "child") inviteCode else null
                            )
                            
                            when (result) {
                                is com.eldercare.ai.auth.RegisterResult.Success -> {
                                    if (result.inviteCode != null) {
                                        registeredInviteCode = result.inviteCode
                                        showParentInviteDialog = true
                                    } else if (result.linkPending) {
                                        showChildPendingDialog = true
                                    } else {
                                        onAuthSuccess("auto")
                                    }
                                }
                                is com.eldercare.ai.auth.RegisterResult.Error -> {
                                    errorMessage = result.message
                                }
                            }
                        } else {
                            val result = userService.login(phone)
                            
                            when (result) {
                                is com.eldercare.ai.auth.LoginResult.Success -> {
                                    onAuthSuccess("auto")
                                }
                                is com.eldercare.ai.auth.LoginResult.Error -> {
                                    errorMessage = result.message
                                }
                            }
                        }
                    } catch (e: Exception) {
                        errorMessage = "操作失败：${e.message}"
                    } finally {
                        isLoading = false
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            enabled = !isLoading && !isSendingCode && 
                     phone.isNotBlank() && 
                     verificationCode.isNotBlank() &&
                     (!isRegisterMode || selectedRole != null) &&
                     (!isRegisterMode || selectedRole != "child" || inviteCode.isNotBlank())
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text(
                    text = if (isRegisterMode) "注册" else "登录",
                    fontSize = 18.sp
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 切换登录/注册模式
        TextButton(
            onClick = {
                isRegisterMode = !isRegisterMode
                selectedRole = null
                inviteCode = ""
                verificationCode = ""
                errorMessage = null
                showInviteCode = false
                showVerificationCode = false
                countdown = 0
            }
        ) {
            Text(
                text = if (isRegisterMode) "已有账号？去登录" else "没有账号？去注册"
            )
        }
    }

    if (showParentInviteDialog) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("注册成功") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("请把邀请码分享给子女：")
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = registeredInviteCode,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(16.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                    Text(
                        text = "下一步完善个人情况，拍菜单会更准确提醒您能不能吃、怎么吃",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showParentInviteDialog = false
                        onAuthSuccess("parent_onboarding")
                    }
                ) { Text("去完善个人情况") }
            }
        )
    }

    if (showChildPendingDialog) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("注册成功") },
            text = { Text("已提交绑定申请，等待父母端确认后即可查看父母信息") },
            confirmButton = {
                Button(
                    onClick = {
                        showChildPendingDialog = false
                        onAuthSuccess("auto")
                    }
                ) { Text("进入子女端") }
            }
        )
    }
}
