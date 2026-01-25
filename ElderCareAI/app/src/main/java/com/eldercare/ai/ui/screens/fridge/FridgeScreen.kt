package com.eldercare.ai.ui.screens.fridge

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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eldercare.ai.rememberElderCareDatabase
import com.eldercare.ai.data.entity.FridgeItemEntity
import com.eldercare.ai.ui.theme.ElderCareAITheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FridgeScreen(
    onNavigateBack: () -> Unit = {}
) {
    var isScanning by remember { mutableStateOf(false) }
    val db = rememberElderCareDatabase()
    val scope = rememberCoroutineScope()
    val fridgeItems by db.fridgeItemDao().getAll()
        .map { list -> list.map { it.toFridgeItem() } }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    
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
                text = "拍冰箱",
                style = MaterialTheme.typography.displayMedium,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.size(64.dp))
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // 拍照按钮
        Card(
            onClick = { 
                isScanning = true
                scope.launch {
                    delay(2000)
                    isScanning = false
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondary
            ),
            shape = RoundedCornerShape(20.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = "拍冰箱",
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSecondary
                )
                
                Column {
                    Text(
                        text = if (isScanning) "正在识别..." else "拍冰箱",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onSecondary
                    )
                    Text(
                        text = if (isScanning) "请稍等" else "看看有什么食材",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSecondary.copy(alpha = 0.8f)
                    )
                }
                
                if (isScanning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        color = MaterialTheme.colorScheme.onSecondary
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // 食材列表标题
        Text(
            text = "冰箱里的食材",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 食材列表
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(fridgeItems) { item ->
                FridgeItemCard(
                    item = item,
                    onRemove = { scope.launch { db.fridgeItemDao().deleteById(item.id) } }
                )
            }
        }
    }
}

@Composable
fun FridgeItemCard(
    item: FridgeItem,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (item.status) {
                FridgeItemStatus.EXPIRED -> MaterialTheme.colorScheme.errorContainer
                FridgeItemStatus.EXPIRING_SOON -> MaterialTheme.colorScheme.secondaryContainer
                FridgeItemStatus.FRESH -> MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Text(
                    text = item.statusText,
                    style = MaterialTheme.typography.bodyLarge,
                    color = when (item.status) {
                        FridgeItemStatus.EXPIRED -> MaterialTheme.colorScheme.error
                        FridgeItemStatus.EXPIRING_SOON -> MaterialTheme.colorScheme.secondary
                        FridgeItemStatus.FRESH -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                
                if (item.advice.isNotEmpty()) {
                    Text(
                        text = item.advice,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = when (item.status) {
                        FridgeItemStatus.EXPIRED -> Icons.Default.Warning
                        FridgeItemStatus.EXPIRING_SOON -> Icons.Default.Schedule
                        FridgeItemStatus.FRESH -> Icons.Default.CheckCircle
                    },
                    contentDescription = item.status.name,
                    tint = when (item.status) {
                        FridgeItemStatus.EXPIRED -> MaterialTheme.colorScheme.error
                        FridgeItemStatus.EXPIRING_SOON -> MaterialTheme.colorScheme.secondary
                        FridgeItemStatus.FRESH -> MaterialTheme.colorScheme.primary
                    },
                    modifier = Modifier.size(24.dp)
                )
                
                if (item.status == FridgeItemStatus.EXPIRED) {
                    IconButton(
                        onClick = onRemove,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "删除",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

data class FridgeItem(
    val id: Long,
    val name: String,
    val purchaseDate: Date,
    val expiryDate: Date,
    val status: FridgeItemStatus,
    val statusText: String,
    val advice: String
)

/** 将 Room 实体转为 UI 模型，并计算 status/statusText/advice */
private fun FridgeItemEntity.toFridgeItem(): FridgeItem {
    val now = System.currentTimeMillis()
    val dayMs = 24L * 60 * 60 * 1000
    val status = when {
        now > expiryAt -> FridgeItemStatus.EXPIRED
        expiryAt - now < 2 * dayMs -> FridgeItemStatus.EXPIRING_SOON
        else -> FridgeItemStatus.FRESH
    }
    val statusText = when (status) {
        FridgeItemStatus.EXPIRED -> "已经过期${(now - expiryAt) / dayMs}天了"
        FridgeItemStatus.EXPIRING_SOON -> "很快要过期了"
        FridgeItemStatus.FRESH -> "还能放${(expiryAt - now) / dayMs}天"
    }
    val advice = when (status) {
        FridgeItemStatus.EXPIRED -> "别吃了，容易拉肚子"
        FridgeItemStatus.EXPIRING_SOON -> "赶紧吃掉"
        FridgeItemStatus.FRESH -> "可以放心吃"
    }
    return FridgeItem(
        id = id,
        name = name,
        purchaseDate = Date(addedAt),
        expiryDate = Date(expiryAt),
        status = status,
        statusText = statusText,
        advice = advice
    )
}

enum class FridgeItemStatus {
    FRESH, EXPIRING_SOON, EXPIRED
}

fun getSampleFridgeItems(): List<FridgeItem> {
    val now = Date()
    val day = 24L * 60 * 60 * 1000
    return listOf(
        FridgeItem(id = 1L, name = "青菜", purchaseDate = Date(now.time - 4 * day), expiryDate = Date(now.time - 1 * day), status = FridgeItemStatus.EXPIRED, statusText = "已经过期1天了", advice = "别吃了，容易拉肚子"),
        FridgeItem(id = 2L, name = "鸡蛋", purchaseDate = Date(now.time - 10 * day), expiryDate = Date(now.time + 1 * day), status = FridgeItemStatus.EXPIRING_SOON, statusText = "很快要过期了", advice = "赶紧吃掉，可以做个番茄鸡蛋汤"),
        FridgeItem(id = 3L, name = "牛奶", purchaseDate = Date(now.time - 1 * day), expiryDate = Date(now.time + 5 * day), status = FridgeItemStatus.FRESH, statusText = "还能放5天", advice = "可以放心喝"),
        FridgeItem(id = 4L, name = "苹果", purchaseDate = Date(now.time - 2 * day), expiryDate = Date(now.time + 3 * day), status = FridgeItemStatus.FRESH, statusText = "还能放3天", advice = "每天吃一个，营养好")
    )
}

@Preview(showBackground = true)
@Composable
fun FridgeScreenPreview() {
    ElderCareAITheme {
        FridgeScreen()
    }
}