package com.example.eldercareai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.eldercareai.ui.components.ElderButton
import com.example.eldercareai.ui.components.ElderCard
import com.example.eldercareai.ui.theme.ElderCareAITheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ElderCareAITheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    HomeScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // 标题
        Text(
            text = "智慧养老助手",
            style = MaterialTheme.typography.headlineLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 32.dp, bottom = 16.dp)
        )
        
        // 功能卡片
        ElderCard {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "欢迎使用",
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    text = "这是一个为老年人设计的智能助手应用，采用超大字体和高对比度界面。",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        // 功能按钮
        ElderButton(
            text = "拍照识别菜品",
            onClick = { /* TODO: 实现拍照功能 */ }
        )
        
        ElderButton(
            text = "查看健康档案",
            onClick = { /* TODO: 实现健康档案功能 */ }
        )
        
        ElderButton(
            text = "营养建议",
            onClick = { /* TODO: 实现营养建议功能 */ }
        )
        
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    ElderCareAITheme {
        HomeScreen()
    }
}