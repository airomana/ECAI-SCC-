package com.eldercare.ai.ui.screens.home

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eldercare.ai.auth.UserService
import com.eldercare.ai.data.SettingsManager
import com.eldercare.ai.network.ApiClient
import com.eldercare.ai.network.BackendApi
import com.eldercare.ai.network.FamilyLinkRequestData
import com.eldercare.ai.rememberElderCareDatabase
import com.eldercare.ai.ui.components.ElderCareCard
import com.eldercare.ai.ui.components.ElderCareDimens
import com.eldercare.ai.ui.components.ElderCareScaffold
import com.eldercare.ai.ui.theme.ElderCareAITheme
import kotlinx.coroutines.launch
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToMenuScan: () -> Unit = {},
    onNavigateToFridge: () -> Unit = {},
    onNavigateToVoiceDiary: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToChatHistory: () -> Unit = {}
) {
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager.getInstance(context) }
    val db = rememberElderCareDatabase()
    val scope = rememberCoroutineScope()
    val currentUserId = settingsManager.getCurrentUserId() ?: 0L
    val currentUser by db.userDao().getByIdFlow(currentUserId).collectAsStateWithLifecycle(initialValue = null)

    // 绑定申请相关
    var pendingRequests by remember { mutableStateOf<List<FamilyLinkRequestData>>(emptyList()) }
    var showLinkDialog by remember { mutableStateOf(false) }
    var linkHandling by remember { mutableStateOf(false) }

    // 进入时拉取待审批的绑定申请
    LaunchedEffect(Unit) {
        val token = settingsManager.getJwtToken()
        if (token.isBlank()) return@LaunchedEffect
        try {
            val api = ApiClient.create(context, BackendApi::class.java)
            val resp = api.getPendingRequests()
            if (resp.isSuccessful) {
                pendingRequests = resp.body()?.data ?: emptyList()
            }
        } catch (_: Exception) {}
    }

    val greetings = remember {
        listOf(
            "您好！今天想吃什么呢？\n我来帮您看看菜单吧",
            "吃饭别着急～\n我先帮您把菜单看清楚",
            "想吃得放心？\n拍一下菜单，我来提醒能不能吃",
            "今天胃口怎么样？\n拍菜单我帮您挑得更合适",
            "想清淡一点还是想解馋？\n拍菜单我来给您建议",
            "怕盐多、糖多、油多？\n我来帮您把关",
            "记得按时吃饭哦～\n拍菜单，我帮您看看",
            "今天想吃热乎的还是清爽的？\n拍菜单我来参考参考",
            "别担心看不清字\n我来帮您读菜单、讲明白",
            "想吃得均衡一些？\n拍菜单我来搭配建议",
            "今天要不要少油少盐？\n拍菜单我来提醒",
            "点菜前先拍一下\n我帮您看看哪些更适合"
        )
    }

    val greetingIndex = remember(currentUser?.lastLoginAt) {
        val seed = currentUser?.lastLoginAt ?: System.currentTimeMillis()
        abs((seed % greetings.size).toInt())
    }
    val greetingText = greetings[greetingIndex]

    ElderCareScaffold(
        title = "银发智膳助手",
        onNavigateBack = null,
        titleTextStyle = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
        actions = {
            IconButton(
                onClick = onNavigateToSettings,
                modifier = Modifier.size(ElderCareDimens.IconButtonSize)
            ) {
                Icon(imageVector = Icons.Default.Settings, contentDescription = "设置")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = ElderCareDimens.ScreenPadding, vertical = ElderCareDimens.SectionSpacing),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            ElderCareCard(
                modifier = Modifier.fillMaxWidth(),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Text(
                    text = greetingText,
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(ElderCareDimens.CardPadding),
                    textAlign = TextAlign.Center
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // 绑定申请提示
                if (pendingRequests.isNotEmpty()) {
                    Card(
                        onClick = { showLinkDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Default.PersonAdd, contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(32.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("有${pendingRequests.size}个子女绑定申请",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold)
                                Text("点击查看并处理",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = null)
                        }
                    }
                }

                ElderFunctionButton(
                    text = "拍菜单",
                    subtitle = "看得懂、能不能吃、怎么吃",
                    icon = Icons.Default.CameraAlt,
                    onClick = onNavigateToMenuScan,
                    backgroundColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )

                ElderFunctionButton(
                    text = "拍冰箱",
                    subtitle = "临期提醒，放心不浪费",
                    icon = Icons.Default.Kitchen,
                    onClick = onNavigateToFridge,
                    backgroundColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary
                )

                ElderFunctionButton(
                    text = "聊天陪伴",
                    subtitle = "聊聊天，我来陪您",
                    icon = Icons.Default.Mic,
                    onClick = onNavigateToVoiceDiary,
                    backgroundColor = MaterialTheme.colorScheme.tertiary,
                    contentColor = MaterialTheme.colorScheme.onTertiary
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = "需要帮助可到设置里查看",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
    // 绑定申请审批弹窗
    if (showLinkDialog && pendingRequests.isNotEmpty()) {
        val req = pendingRequests.first()
        AlertDialog(
            onDismissRequest = { showLinkDialog = false },
            title = { Text("子女绑定申请") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("有子女申请与您绑定，是否同意？")
                    Text("申请ID：${req.id}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        linkHandling = true
                        scope.launch {
                            try {
                                val userService = UserService(
                                    db.userDao(), db.familyRelationDao(),
                                    db.familyLinkRequestDao(), settingsManager, context
                                )
                                userService.handleLinkRequest(req.id, true)
                                pendingRequests = pendingRequests.drop(1)
                            } catch (_: Exception) {}
                            linkHandling = false
                            showLinkDialog = false
                        }
                    },
                    enabled = !linkHandling
                ) { Text("同意") }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        linkHandling = true
                        scope.launch {
                            try {
                                val userService = UserService(
                                    db.userDao(), db.familyRelationDao(),
                                    db.familyLinkRequestDao(), settingsManager, context
                                )
                                userService.handleLinkRequest(req.id, false)
                                pendingRequests = pendingRequests.drop(1)
                            } catch (_: Exception) {}
                            linkHandling = false
                            showLinkDialog = false
                        }
                    },
                    enabled = !linkHandling
                ) { Text("拒绝") }
            }
        )
    }
}

@Composable
fun ElderFunctionButton(
    text: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    backgroundColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(112.dp),
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor
        ),
        shape = MaterialTheme.shapes.extraLarge,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = text,
                modifier = Modifier.size(52.dp),
                tint = contentColor
            )
            
            Column(
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.headlineLarge,
                    color = contentColor
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor.copy(alpha = 0.9f)
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    ElderCareAITheme {
        HomeScreen()
    }
}
