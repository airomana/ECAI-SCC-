package com.eldercare.ai.ui.screens.menu

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
import com.eldercare.ai.ui.theme.ElderCareAITheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuScanScreen(
    onNavigateBack: () -> Unit = {}
) {
    var isScanning by remember { mutableStateOf(false) }
    var scanResults by remember { mutableStateOf<List<DishResult>>(emptyList()) }
    
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
                text = "拍菜单",
                style = MaterialTheme.typography.displayMedium,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.size(64.dp))
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        if (scanResults.isEmpty()) {
            // 拍照界面
            CameraSection(
                isScanning = isScanning,
                onStartScan = { 
                    isScanning = true
                    // 模拟扫描结果 - 使用简单的延时
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        scanResults = listOf(
                            DishResult(
                                name = "红烧肉",
                                description = "五花肉用糖和酱油烧的，很香但是油大",
                                healthAdvice = "您有高血压，这个菜油比较多，建议少吃一点",
                                riskLevel = RiskLevel.HIGH
                            ),
                            DishResult(
                                name = "清蒸鱼",
                                description = "新鲜鱼用蒸的方式做的，很清淡",
                                healthAdvice = "这个菜很适合您，蛋白质丰富，做法清淡",
                                riskLevel = RiskLevel.LOW
                            ),
                            DishResult(
                                name = "地三鲜",
                                description = "茄子土豆青椒一起炸的",
                                healthAdvice = "这个菜是油炸的，您要少吃哦",
                                riskLevel = RiskLevel.MEDIUM
                            )
                        )
                        isScanning = false
                    }, 3000)
                }
            )
        } else {
            // 结果展示界面
            ResultsSection(
                results = scanResults,
                onRescan = { scanResults = emptyList() }
            )
        }
    }
}

@Composable
fun CameraSection(
    isScanning: Boolean,
    onStartScan: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // 相机预览区域（占位）
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                if (isScanning) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(64.dp),
                            strokeWidth = 6.dp
                        )
                        Text(
                            text = "正在识别菜单...",
                            style = MaterialTheme.typography.headlineMedium
                        )
                    }
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = "相机",
                            modifier = Modifier.size(80.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "对准菜单拍照",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(48.dp))
        
        // 拍照按钮
        if (!isScanning) {
            Button(
                onClick = onStartScan,
                modifier = Modifier
                    .size(120.dp),
                shape = RoundedCornerShape(60.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = "拍照",
                    modifier = Modifier.size(48.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // 提示文字
        Text(
            text = if (isScanning) "请稍等，正在分析菜单..." else "请将菜单放在相机前，点击拍照按钮",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun ResultsSection(
    results: List<DishResult>,
    onRescan: () -> Unit
) {
    Column {
        // 重新扫描按钮
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onRescan) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "重新扫描",
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "重新扫描",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 结果列表
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(results) { result ->
                DishResultCard(result = result)
            }
        }
    }
}

@Composable
fun DishResultCard(result: DishResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (result.riskLevel) {
                RiskLevel.HIGH -> MaterialTheme.colorScheme.errorContainer
                RiskLevel.MEDIUM -> MaterialTheme.colorScheme.secondaryContainer
                RiskLevel.LOW -> MaterialTheme.colorScheme.primaryContainer
            }
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 菜名和风险等级
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = result.name,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Icon(
                    imageVector = when (result.riskLevel) {
                        RiskLevel.HIGH -> Icons.Default.Warning
                        RiskLevel.MEDIUM -> Icons.Default.Info
                        RiskLevel.LOW -> Icons.Default.CheckCircle
                    },
                    contentDescription = result.riskLevel.name,
                    tint = when (result.riskLevel) {
                        RiskLevel.HIGH -> MaterialTheme.colorScheme.error
                        RiskLevel.MEDIUM -> MaterialTheme.colorScheme.secondary
                        RiskLevel.LOW -> MaterialTheme.colorScheme.primary
                    },
                    modifier = Modifier.size(32.dp)
                )
            }
            
            // 菜品描述
            Text(
                text = result.description,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            // 健康建议
            Text(
                text = result.healthAdvice,
                style = MaterialTheme.typography.bodyLarge,
                color = when (result.riskLevel) {
                    RiskLevel.HIGH -> MaterialTheme.colorScheme.error
                    RiskLevel.MEDIUM -> MaterialTheme.colorScheme.secondary
                    RiskLevel.LOW -> MaterialTheme.colorScheme.primary
                }
            )
        }
    }
}

data class DishResult(
    val name: String,
    val description: String,
    val healthAdvice: String,
    val riskLevel: RiskLevel
)

enum class RiskLevel {
    LOW, MEDIUM, HIGH
}

@Composable
fun ScanningSection() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(64.dp),
                        strokeWidth = 6.dp
                    )
                    Text(
                        text = "正在识别菜单...",
                        style = MaterialTheme.typography.headlineMedium
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Text(
            text = "请稍等，正在分析菜单...",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Preview(showBackground = true)
@Composable
fun MenuScanScreenPreview() {
    ElderCareAITheme {
        MenuScanScreen()
    }
}