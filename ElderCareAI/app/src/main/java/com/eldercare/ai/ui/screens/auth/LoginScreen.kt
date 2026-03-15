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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.eldercare.ai.ui.components.ElderCareCard
import com.eldercare.ai.ui.components.ElderCareDimens
import com.eldercare.ai.ui.components.ElderCarePrimaryButton
import com.eldercare.ai.ui.components.ElderCareScaffold
import com.eldercare.ai.ui.components.ElderCareSecondaryButton
import com.eldercare.ai.ui.components.ElderCareTextField
import com.eldercare.ai.data.SettingsManager
import android.widget.Toast
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
    
    ElderCareScaffold(title = null) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = ElderCareDimens.ScreenPadding, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "银发智膳助手",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 10.dp)
            )
            Text(
                text = if (isRegisterMode) "注册账号" else "登录",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 22.dp)
            )

            ElderCareTextField(
                value = phone,
                onValueChange = {
                    phone = it
                    errorMessage = null
                    showVerificationCode = false
                },
                label = "手机号",
                placeholder = "请输入11位手机号",
                leadingIcon = Icons.Default.Phone,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp),
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
                ElderCareTextField(
                    value = verificationCode,
                    onValueChange = {
                        verificationCode = it
                        errorMessage = null
                    },
                    label = "验证码",
                    placeholder = "请输入6位验证码",
                    leadingIcon = Icons.Default.Lock,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
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
                    modifier = Modifier.height(ElderCareDimens.ButtonHeight),
                    shape = MaterialTheme.shapes.large
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
            ElderCareSecondaryButton(
                text = if (isSendingCode) "正在发送…" else if (countdown > 0) "${countdown}秒后重发" else "获取验证码",
                leadingIcon = Icons.Default.Sms,
                enabled = !isSendingCode && countdown == 0 && phone.isNotBlank() && userService.isValidPhone(phone),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ElderCareDimens.ButtonHeight)
                    .padding(bottom = 14.dp),
                onClick = {
                    if (phone.isBlank() || !userService.isValidPhone(phone)) {
                        errorMessage = "请先输入正确的手机号"
                        return@ElderCareSecondaryButton
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
                }
            )
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
                ElderCareCard(
                    onClick = {
                        selectedRole = "parent"
                        showInviteCode = false
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(120.dp),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
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
                ElderCareCard(
                    onClick = {
                        selectedRole = "child"
                        showInviteCode = true
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(120.dp),
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
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
            ElderCareTextField(
                value = inviteCode,
                onValueChange = { 
                    inviteCode = it.trim().uppercase().take(24)
                    errorMessage = null
                },
                label = "邀请码",
                placeholder = "请输入邀请码",
                leadingIcon = Icons.Default.Info,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
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
        ElderCarePrimaryButton(
            onClick = {
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
            text = if (isRegisterMode) "注册" else "登录",
            modifier = Modifier
                .fillMaxWidth()
                .height(ElderCareDimens.ButtonHeight),
            enabled = !isLoading && !isSendingCode &&
                phone.isNotBlank() &&
                verificationCode.isNotBlank() &&
                (!isRegisterMode || selectedRole != null) &&
                (!isRegisterMode || selectedRole != "child" || inviteCode.isNotBlank()),
            leadingIcon = if (isRegisterMode) Icons.Default.PersonAdd else Icons.Default.Login
        )
        
        if (isLoading) {
            Spacer(modifier = Modifier.height(12.dp))
            CircularProgressIndicator()
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
    }

    if (showParentInviteDialog) {
        val clipboardManager = LocalClipboardManager.current
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
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = registeredInviteCode,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Center
                            )
                            IconButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(registeredInviteCode))
                                    Toast.makeText(context, "邀请码已复制", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "复制邀请码")
                            }
                        }
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
                        scope.launch {
                            try {
                                userService.login(phone)
                                Toast.makeText(context, "已自动登录，请完善个人情况", Toast.LENGTH_SHORT).show()
                            } catch (_: Exception) {
                                Toast.makeText(context, "已创建账号，请完善个人情况", Toast.LENGTH_SHORT).show()
                            } finally {
                                showParentInviteDialog = false
                                onAuthSuccess("parent_onboarding")
                            }
                        }
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
                        scope.launch {
                            try {
                                userService.login(phone)
                                Toast.makeText(context, "已自动登录，等待父母确认绑定", Toast.LENGTH_SHORT).show()
                            } catch (_: Exception) {
                                Toast.makeText(context, "已创建账号，等待父母确认绑定", Toast.LENGTH_SHORT).show()
                            } finally {
                                showChildPendingDialog = false
                                onAuthSuccess("auto")
                            }
                        }
                    }
                ) { Text("进入子女端") }
            }
        )
    }
}
