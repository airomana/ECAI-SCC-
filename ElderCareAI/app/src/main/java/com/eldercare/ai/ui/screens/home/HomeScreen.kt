package com.eldercare.ai.ui.screens.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.eldercare.ai.ui.theme.ElderCareAITheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToMenuScan: () -> Unit = {},
    onNavigateToFridge: () -> Unit = {},
    onNavigateToVoiceDiary: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(32.dp)
    ) {
        // 顶部标题和设置按钮
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "银发智膳助手",
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.primary
            )
            
            IconButton(
                onClick = onNavigateToSettings,
                modifier = Modifier.size(64.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "设置",
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        
        // 问候语
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                text = "您好！今天想吃什么呢？\n让我来帮您看看菜单吧～",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(24.dp),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        
        // 主要功能按钮
        Column(
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // 拍菜单按钮 - 最重要的功能
            ElderFunctionButton(
                text = "拍菜单",
                subtitle = "看看今天有什么菜",
                icon = Icons.Default.CameraAlt,
                onClick = onNavigateToMenuScan,
                backgroundColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
            
            // 拍冰箱按钮
            ElderFunctionButton(
                text = "拍冰箱",
                subtitle = "看看哪些菜要过期了",
                icon = Icons.Default.Kitchen,
                onClick = onNavigateToFridge,
                backgroundColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary
            )
            
            // 语音日记按钮
            ElderFunctionButton(
                text = "今天吃了啥",
                subtitle = "说说今天的饮食",
                icon = Icons.Default.Mic,
                onClick = onNavigateToVoiceDiary,
                backgroundColor = MaterialTheme.colorScheme.tertiary,
                contentColor = MaterialTheme.colorScheme.onTertiary
            )
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        // 底部提示
        Text(
            text = "有问题可以点击右上角设置",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
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
            .height(120.dp),
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor
        ),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = text,
                modifier = Modifier.size(64.dp),
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
                    style = MaterialTheme.typography.bodyLarge,
                    color = contentColor.copy(alpha = 0.8f)
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