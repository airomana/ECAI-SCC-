package com.eldercare.ai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.eldercare.ai.llm.LlmConfig
import com.eldercare.ai.ui.navigation.ElderCareNavigation
import com.eldercare.ai.ui.theme.ElderCareAITheme

class MainActivity : ComponentActivity() {
    
    init {
        // 加载native库（Whisper、YOLO等）
        try {
            System.loadLibrary("eldercare-ai")
            android.util.Log.d("MainActivity", "Native library loaded successfully")
        } catch (e: UnsatisfiedLinkError) {
            android.util.Log.e("MainActivity", "Failed to load native library", e)
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        android.util.Log.d("MainActivity", "onCreate started")
        
        try {
            // 初始化LLM配置
            LlmConfig.initialize(this)
            
            // 设置API密钥（如果还没有配置）
            if (!LlmConfig.isConfigured()) {
                LlmConfig.setApiKey(this, "sk-634807447c514841a647f2e90b244389")
            }
            
            enableEdgeToEdge()
            setContent {
                ElderCareAITheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        ElderCareApp()
                    }
                }
            }
            android.util.Log.d("MainActivity", "onCreate completed successfully")
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error in onCreate", e)
            throw e
        }
    }
}

@Composable
fun ElderCareApp() {
    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        ElderCareNavigation(
            modifier = Modifier.padding(innerPadding)
        )
    }
}

@Preview(showBackground = true)
@Composable
fun ElderCareAppPreview() {
    ElderCareAITheme {
        ElderCareApp()
    }
}