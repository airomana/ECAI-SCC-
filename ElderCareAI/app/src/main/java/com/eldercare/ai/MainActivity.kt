package com.eldercare.ai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.eldercare.ai.llm.LlmConfig
import com.eldercare.ai.notification.DailyReminderReceiver
import com.eldercare.ai.notification.DailyReminderScheduler
import com.eldercare.ai.ui.navigation.ElderCareNavigation
import com.eldercare.ai.ui.theme.ElderCareAITheme

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        android.util.Log.d("MainActivity", "POST_NOTIFICATIONS granted=$granted")
    }
    
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

            // 初始化通知渠道 + 注册每日提醒
            DailyReminderScheduler.createNotificationChannel(this)
            val settings = com.eldercare.ai.data.SettingsManager.getInstance(this)
            if (settings.isParentRole()) {
                DailyReminderScheduler.schedule(this)
            }

            // Android 13+ 申请通知权限
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                val granted = androidx.core.content.ContextCompat.checkSelfPermission(
                    this, android.Manifest.permission.POST_NOTIFICATIONS
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                if (!granted) {
                    notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }
            }

            // 检查是否从通知点击进入，需要直接跳转语音界面
            val openVoice = intent?.getBooleanExtra(DailyReminderReceiver.EXTRA_OPEN_VOICE, false) ?: false
            
            enableEdgeToEdge()
            setContent {
                ElderCareAITheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        ElderCareApp(openVoiceOnStart = openVoice)
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
fun ElderCareApp(openVoiceOnStart: Boolean = false) {
    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        ElderCareNavigation(
            modifier = Modifier.padding(innerPadding),
            openVoiceOnStart = openVoiceOnStart
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
