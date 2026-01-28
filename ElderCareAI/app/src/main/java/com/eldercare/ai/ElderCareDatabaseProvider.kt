package com.eldercare.ai

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.eldercare.ai.data.ElderCareDatabase

/**
 * 在 Composable 中获取单例 ElderCareDatabase
 */
@Composable
fun rememberElderCareDatabase(): ElderCareDatabase {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return remember {
        try {
            android.util.Log.d("ElderCareDatabaseProvider", "Getting database instance...")
            ElderCareDatabase.getDatabase(context.applicationContext, scope)
        } catch (e: Exception) {
            android.util.Log.e("ElderCareDatabaseProvider", "Error getting database", e)
            throw e
        }
    }
}
