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
import com.eldercare.ai.ui.navigation.ElderCareNavigation
import com.eldercare.ai.ui.theme.ElderCareAITheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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