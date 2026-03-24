package com.eldercare.ai.data.network

import android.content.Context
import android.util.Log
import com.eldercare.ai.data.ElderCareDatabase
import com.eldercare.ai.data.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * 负责将本地Room数据库中的数据同步到远程服务器
 */
class SyncManager(private val context: Context) {
    
    // 修复：ElderCareDatabase 使用 getDatabase()，而不是 getInstance()
    private val db = ElderCareDatabase.getDatabase(context, kotlinx.coroutines.GlobalScope)
    private val apiService = ApiClient.apiService
    private val settingsManager = SettingsManager.getInstance(context)

    suspend fun syncEmotionLogs() = withContext(Dispatchers.IO) {
        try {
            val userId = settingsManager.getCurrentUserId() ?: return@withContext
            // 简单实现：获取本地所有情绪日志并上传（实际生产中应记录同步状态，避免重复上传）
            val logs = db.emotionLogDao().getAll().first()
            for (log in logs) {
                val request = EmotionLogRequest(
                    userId = userId,
                    emotion = log.dominantEmotion, // 使用 dominantEmotion
                    note = log.summary,            // 使用 summary 替代 note
                    timestamp = log.dayTimestamp   // 使用 dayTimestamp
                )
                apiService.uploadEmotionLog(request)
            }
            Log.d("SyncManager", "Successfully synced ${logs.size} emotion logs")
        } catch (e: Exception) {
            Log.e("SyncManager", "Failed to sync emotion logs", e)
        }
    }

    suspend fun fetchWeeklyReports(): List<WeeklyReportResponse> = withContext(Dispatchers.IO) {
        try {
            // 在测试环境中，假设子女ID为 2
            // 实际生产中应使用 settingsManager.getCurrentUserId()
            val childId = settingsManager.getCurrentUserId() ?: 2L 
            val reports = apiService.getWeeklyReports(childId)
            Log.d("SyncManager", "Successfully fetched ${reports.size} weekly reports")
            return@withContext reports
        } catch (e: Exception) {
            Log.e("SyncManager", "Failed to fetch weekly reports", e)
            return@withContext emptyList()
        }
    }

    // 手动触发服务端生成周报（仅供测试用）
    suspend fun triggerReportGeneration() = withContext(Dispatchers.IO) {
        try {
            apiService.triggerReport()
            Log.d("SyncManager", "Successfully triggered report generation")
        } catch (e: Exception) {
            Log.e("SyncManager", "Failed to trigger report", e)
        }
    }

    suspend fun syncHealthAndPermissions() = withContext(Dispatchers.IO) {
        try {
            val userId = settingsManager.getCurrentUserId() ?: return@withContext
            val role = settingsManager.getUserRole()
            
            if (role == "parent") {
                // 上传父母的健康和权限设置到云端
                val hp = db.healthProfileDao().getOnce()
                val ps = db.personalSituationDao().getOnce()
                
                val req = HealthProfileRequest(
                    userId = userId,
                    name = hp?.name,
                    age = hp?.age,
                    shareHealth = ps?.shareHealth,
                    shareDiet = ps?.shareDiet,
                    shareContacts = ps?.shareContacts,
                    diseases = hp?.diseases,
                    allergies = hp?.allergies
                )
                apiService.updateHealthProfile(req)
                Log.d("SyncManager", "Successfully pushed health/permissions to server")
            } else if (role == "child") {
                // 子女端：拉取父母的健康和权限设置
                val childUser = db.userDao().getById(userId)
                if (childUser?.familyId != null) {
                    // 找到父母ID
                    val parents = db.userDao().getByFamilyIdAndRole(childUser.familyId, "parent")
                    if (parents.isNotEmpty()) {
                        val parentId = parents[0].id
                        val res = apiService.getHealthProfile(parentId)
                        
                        // 保存到本地供子女端显示
                        val currentHp = db.healthProfileDao().getOnce() ?: com.eldercare.ai.data.entity.HealthProfile()
                        val newHp = currentHp.copy(
                            id = currentHp.id.takeIf { it != 0L } ?: 1L,
                            name = res.name ?: "",
                            age = res.age ?: 0,
                            diseases = res.diseases ?: emptyList(),
                            allergies = res.allergies ?: emptyList()
                        )
                        if (currentHp.id == 0L) {
                            db.healthProfileDao().insert(newHp)
                        } else {
                            db.healthProfileDao().update(newHp)
                        }
                        
                        val currentPs = db.personalSituationDao().getOnce() ?: com.eldercare.ai.data.entity.PersonalSituationEntity()
                        db.personalSituationDao().upsert(currentPs.copy(
                            id = currentPs.id.takeIf { it != 0L } ?: 1L,
                            shareHealth = res.shareHealth ?: false,
                            shareDiet = res.shareDiet ?: false,
                            shareContacts = res.shareContacts ?: false
                        ))
                        Log.d("SyncManager", "Successfully fetched parent health/permissions")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("SyncManager", "Failed to sync health/permissions", e)
        }
    }
}
