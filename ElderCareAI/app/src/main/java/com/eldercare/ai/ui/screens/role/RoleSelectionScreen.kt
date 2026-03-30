package com.eldercare.ai.ui.screens.role

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.eldercare.ai.data.SettingsManager
import com.eldercare.ai.ui.components.ElderCareDimens
import com.eldercare.ai.ui.theme.ElderCareAITheme

/**
 * 角色选择界面
 * 用户首次打开应用时选择身份：父母端 或 子女端
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoleSelectionScreen(
    onRoleSelected: () -> Unit
) {
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager.getInstance(context) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(ElderCareDimens.ScreenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // 标题
        Text(
            text = "欢迎使用银发智膳助手",
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        Text(
            text = "请选择您的身份",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 48.dp)
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // 父母端卡片
        RoleCard(
            title = "我是父母",
            subtitle = "使用拍菜单、拍冰箱、语音日记等功能",
            icon = Icons.Default.Person,
            backgroundColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            onClick = {
                settingsManager.setUserRole("parent")
                onRoleSelected()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // 子女端卡片
        RoleCard(
            title = "我是子女",
            subtitle = "查看父母饮食记录、健康档案和周报",
            icon = Icons.Default.People,
            backgroundColor = MaterialTheme.colorScheme.secondary,
            contentColor = MaterialTheme.colorScheme.onSecondary,
            onClick = {
                settingsManager.setUserRole("child")
                onRoleSelected()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
        )
        
        Spacer(modifier = Modifier.height(48.dp))
        
        // 提示文字
        Text(
            text = "您可以在设置中随时切换身份",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun RoleCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    backgroundColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor
        ),
        shape = MaterialTheme.shapes.extraLarge,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                modifier = Modifier.size(64.dp),
                tint = contentColor
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = contentColor,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = contentColor.copy(alpha = 0.9f),
                textAlign = TextAlign.Center
            )
        }
    }
}
