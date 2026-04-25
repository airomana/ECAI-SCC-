package com.eldercare.ai.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eldercare.ai.data.entity.ConversationMessageEntity
import com.eldercare.ai.data.entity.ConversationSessionEntity
import com.eldercare.ai.rememberElderCareDatabase
import com.eldercare.ai.ui.components.ElderCareScaffold
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ChatHistoryScreen(
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val db = rememberElderCareDatabase()
    val sessions by db.conversationSessionDao().getAll()
        .collectAsStateWithLifecycle(initialValue = emptyList())

    var selectedSession by remember { mutableStateOf<ConversationSessionEntity?>(null) }

    if (selectedSession != null) {
        ChatSessionDetailScreen(
            session = selectedSession!!,
            onNavigateBack = { selectedSession = null }
        )
    } else {
        ElderCareScaffold(
            title = "聊天记录",
            onNavigateBack = onNavigateBack
        ) { padding ->
            if (sessions.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "暂无聊天记录",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(sessions.sortedByDescending { it.startTime }) { session ->
                        SessionCard(session = session, onClick = { selectedSession = session })
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionCard(
    session: ConversationSessionEntity,
    onClick: () -> Unit
) {
    val sdf = SimpleDateFormat("MM月dd日 HH:mm", Locale.getDefault())
    val dateStr = sdf.format(Date(session.startTime))
    val endTime: Long = session.endTime ?: 0L
    val durationMin = if (endTime > 0L) {
        ((endTime - session.startTime) / 60000).toInt()
    } else 0

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(dateStr, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (session.summary.isNotBlank()) {
                    Text(
                        session.summary.take(40),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (session.overallEmotion.isNotBlank()) {
                        SurfaceChip(session.overallEmotion)
                    }
                    if (session.messageCount > 0) {
                        SurfaceChip("${session.messageCount}条消息")
                    }
                    if (durationMin > 0) {
                        SurfaceChip("${durationMin}分钟")
                    }
                }
            }
        }
    }
}

@Composable
private fun SurfaceChip(text: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun ChatSessionDetailScreen(
    session: ConversationSessionEntity,
    onNavigateBack: () -> Unit
) {
    val db = rememberElderCareDatabase()
    val messages by db.conversationMessageDao().getBySession(session.id)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val listState = rememberLazyListState()
    val sdf = SimpleDateFormat("MM月dd日 HH:mm", Locale.getDefault())

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    ElderCareScaffold(
        title = sdf.format(Date(session.startTime)),
        onNavigateBack = onNavigateBack
    ) { padding ->
        if (messages.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("暂无消息记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(messages) { msg ->
                    MessageBubble(msg)
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(msg: ConversationMessageEntity) {
    val isUser = msg.role == "user"
    val timeSdf = SimpleDateFormat("HH:mm", Locale.getDefault())

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!isUser) {
            Icon(
                Icons.Default.SmartToy,
                contentDescription = null,
                modifier = Modifier.size(28.dp).padding(end = 4.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }

        Column(
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Box(
                modifier = Modifier
                    .background(
                        color = if (isUser) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(
                            topStart = 16.dp, topEnd = 16.dp,
                            bottomStart = if (isUser) 16.dp else 4.dp,
                            bottomEnd = if (isUser) 4.dp else 16.dp
                        )
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(
                    text = msg.content,
                    color = if (isUser) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    timeSdf.format(Date(msg.timestamp)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (isUser && msg.emotion.isNotBlank()) {
                    Text(
                        msg.emotion,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        if (isUser) {
            Icon(
                Icons.Default.Person,
                contentDescription = null,
                modifier = Modifier.size(28.dp).padding(start = 4.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}
