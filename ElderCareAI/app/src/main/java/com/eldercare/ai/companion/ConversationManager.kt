package com.eldercare.ai.companion

import android.content.Context
import android.util.Log
import com.eldercare.ai.data.ElderCareDatabase
import com.eldercare.ai.data.entity.ConversationMessageEntity
import com.eldercare.ai.data.entity.ConversationSessionEntity
import com.eldercare.ai.data.entity.DiaryEntryEntity
import com.eldercare.ai.data.entity.EmotionLogEntity
import com.eldercare.ai.llm.LlmService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

/**
 * 对话管理器
 * 负责管理多轮对话会话、情绪记录、日志生成
 */
class ConversationManager private constructor(
    private val context: Context,
    private val db: ElderCareDatabase
) {
    private val llmService = LlmService.getInstance(context)
    private var currentSessionId: Long? = null

    companion object {
        private const val TAG = "ConversationManager"

        @Volatile
        private var INSTANCE: ConversationManager? = null

        fun getInstance(context: Context, db: ElderCareDatabase): ConversationManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ConversationManager(context.applicationContext, db).also { INSTANCE = it }
            }
        }
    }

    /** 开始新的对话会话 */
    suspend fun startSession(): Long = withContext(Dispatchers.IO) {
        val session = ConversationSessionEntity(startTime = System.currentTimeMillis())
        val id = db.conversationSessionDao().insert(session)
        currentSessionId = id
        Log.d(TAG, "Started new session: $id")
        id
    }

    /** 结束当前会话，汇总情绪 */
    suspend fun endSession(sessionId: Long) = withContext(Dispatchers.IO) {
        val session = db.conversationSessionDao().getById(sessionId) ?: return@withContext
        val messages = db.conversationMessageDao().getBySessionSync(sessionId)
        val userMessages = messages.filter { it.role == "user" }
        val emotions = userMessages.map { it.emotion }.filter { it.isNotBlank() }
        val dominant = EmotionAnalyzer.dominantEmotion(emotions)
        val avgIntensity = if (userMessages.isEmpty()) 50
            else userMessages.map { it.emotionIntensity }.average().toInt()

        // 生成会话摘要
        val summary = if (userMessages.isNotEmpty()) {
            val contentSummary = userMessages.take(3).joinToString("；") { it.content.take(20) }
            "本次对话${userMessages.size}轮，主要情绪：$dominant。内容摘要：$contentSummary"
        } else ""

        db.conversationSessionDao().update(
            session.copy(
                endTime = System.currentTimeMillis(),
                overallEmotion = dominant,
                emotionIntensity = avgIntensity,
                summary = summary,
                messageCount = messages.size
            )
        )

        // 同步更新今日情绪日志
        updateTodayEmotionLog()

        // 同步写入 DiaryEntry（兼容旧的子女端展示）
        if (userMessages.isNotEmpty()) {
            val combinedContent = userMessages.joinToString("；") { it.content.take(30) }
            val aiResponse = messages.lastOrNull { it.role == "assistant" }?.content ?: ""
            db.diaryEntryDao().insert(
                DiaryEntryEntity(
                    date = session.startTime,
                    content = combinedContent,
                    emotion = dominant,
                    aiResponse = aiResponse
                )
            )
        }

        if (currentSessionId == sessionId) currentSessionId = null
        Log.d(TAG, "Session $sessionId ended, emotion: $dominant")
    }

    /** 保存用户消息 */
    suspend fun saveUserMessage(sessionId: Long, content: String): ConversationMessageEntity =
        withContext(Dispatchers.IO) {
            val (emotion, intensity) = EmotionAnalyzer.analyze(content)
            val msg = ConversationMessageEntity(
                sessionId = sessionId,
                role = "user",
                content = content,
                emotion = emotion,
                emotionIntensity = intensity
            )
            val id = db.conversationMessageDao().insert(msg)
            msg.copy(id = id)
        }

    /** 保存 AI 回复消息 */
    suspend fun saveAssistantMessage(sessionId: Long, content: String): ConversationMessageEntity =
        withContext(Dispatchers.IO) {
            val msg = ConversationMessageEntity(
                sessionId = sessionId,
                role = "assistant",
                content = content
            )
            val id = db.conversationMessageDao().insert(msg)
            msg.copy(id = id)
        }

    /** 获取会话消息流 */
    fun getSessionMessages(sessionId: Long): Flow<List<ConversationMessageEntity>> {
        return db.conversationMessageDao().getBySession(sessionId)
    }

    /** 获取所有会话流 */
    fun getAllSessions(): Flow<List<ConversationSessionEntity>> {
        return db.conversationSessionDao().getAll()
    }

    /**
     * 生成 AI 回复（带多轮上下文）
     */
    suspend fun generateReply(
        sessionId: Long,
        userMessage: String,
        emotion: String,
        userName: String? = null
    ): String = withContext(Dispatchers.IO) {
        // 获取最近几轮历史消息作为上下文
        val history = db.conversationMessageDao().getBySessionSync(sessionId).takeLast(6)

        val reply = try {
            llmService.generateConversationReply(
                userMessage = userMessage,
                emotion = emotion,
                history = history,
                userName = userName
            ) ?: generateLocalReply(userMessage, emotion, userName)
        } catch (e: Exception) {
            Log.w(TAG, "LLM reply failed, using local fallback", e)
            generateLocalReply(userMessage, emotion, userName)
        }
        reply
    }

    /** 更新今日情绪日志 */
    private suspend fun updateTodayEmotionLog() {
        val todayStart = getTodayStartTimestamp()
        val todayEnd = todayStart + 24 * 60 * 60 * 1000L - 1

        val sessions = db.conversationSessionDao().getByTimeRange(todayStart, todayEnd)
        val messages = db.conversationMessageDao().getByTimeRange(todayStart, todayEnd)
        val userMessages = messages.filter { it.role == "user" }
        val emotions = userMessages.map { it.emotion }.filter { it.isNotBlank() }

        val dominant = EmotionAnalyzer.dominantEmotion(emotions)
        val distJson = EmotionAnalyzer.emotionDistributionJson(emotions)

        val existing = db.emotionLogDao().getByDay(todayStart)
        val log = EmotionLogEntity(
            id = existing?.id ?: 0,
            dayTimestamp = todayStart,
            dominantEmotion = dominant,
            emotionDistributionJson = distJson,
            conversationCount = sessions.size,
            totalMessages = messages.size,
            summary = "今日对话${sessions.size}次，主要情绪：$dominant",
            sentToFamily = existing?.sentToFamily ?: false,
            createdAt = existing?.createdAt ?: System.currentTimeMillis()
        )
        db.emotionLogDao().insert(log)
    }

    /**
     * 生成周报文本（供子女端使用）
     */
    suspend fun generateWeeklyReport(healthProfileName: String? = null): String =
        withContext(Dispatchers.IO) {
            val weekAgo = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L
            val now = System.currentTimeMillis()
            val logs = db.emotionLogDao().getByTimeRange(weekAgo, now)
            val entries = db.diaryEntryDao().getByTimeRange(weekAgo, now)

            if (entries.isEmpty() && logs.isEmpty()) {
                return@withContext "本周暂无陪伴记录。"
            }

            // 先尝试 LLM 生成
            val llmReport = try {
                llmService.generateWeeklyReport(entries, null)
            } catch (e: Exception) {
                null
            }

            if (!llmReport.isNullOrBlank()) return@withContext llmReport

            // 本地降级生成
            buildLocalWeeklyReport(logs, entries, healthProfileName)
        }

    private fun buildLocalWeeklyReport(
        logs: List<EmotionLogEntity>,
        entries: List<DiaryEntryEntity>,
        name: String?
    ): String {
        val sb = StringBuilder()
        val sdf = SimpleDateFormat("MM月dd日", Locale.getDefault())
        val weekStart = sdf.format(Date(System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L))
        val weekEnd = sdf.format(Date())
        val displayName = name?.takeIf { it.isNotBlank() } ?: "老人"

        sb.appendLine("【${displayName}情绪陪伴周报】")
        sb.appendLine("统计周期：$weekStart - $weekEnd")
        sb.appendLine()

        // 优先使用 EmotionLogEntity 的汇总数据
        val activeDays = logs.size
        val totalConversations = logs.sumOf { it.conversationCount }
        val totalMessages = logs.sumOf { it.totalMessages }
        sb.appendLine("✅ 陪伴天数：本周 $activeDays 天有对话记录")
        if (totalConversations > 0) sb.appendLine("💬 对话次数：共 $totalConversations 次对话，$totalMessages 条消息")

        // 情绪分布统计（从 EmotionLogEntity 汇总）
        val emotionCounts = mutableMapOf<String, Int>()
        logs.forEach { log ->
            // 统计每天的主要情绪
            emotionCounts[log.dominantEmotion] = (emotionCounts[log.dominantEmotion] ?: 0) + 1
        }
        val dominant = emotionCounts.maxByOrNull { it.value }?.key
            ?: EmotionAnalyzer.dominantEmotion(entries.map { it.emotion }.filter { it.isNotBlank() })
        sb.appendLine("😊 主要情绪：$dominant")

        // 情绪趋势（按天列出）
        if (logs.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("📅 每日情绪：")
            logs.sortedBy { it.dayTimestamp }.forEach { log ->
                val dayStr = sdf.format(Date(log.dayTimestamp))
                sb.appendLine("  $dayStr：${log.dominantEmotion}（对话${log.conversationCount}次）")
            }
        }

        // 关注点分析
        sb.appendLine()
        val lonelyDays = logs.count { it.dominantEmotion == "孤单" }
        val sadDays = logs.count { it.dominantEmotion == "难过" }
        val worryDays = logs.count { it.dominantEmotion == "担心" }
        val sickCount = entries.count { e ->
            listOf("疼", "痛", "不舒服", "生病", "头晕").any { e.content.contains(it) }
        }
        val happyDays = logs.count { it.dominantEmotion in listOf("开心", "满意") }

        if (happyDays >= 3) sb.appendLine("🌟 本周有 $happyDays 天情绪积极，状态良好！")
        if (lonelyDays >= 2) sb.appendLine("💔 关注：有 $lonelyDays 天主要情绪为孤单，建议多联系陪伴")
        if (sadDays >= 2) sb.appendLine("⚠️ 关注：有 $sadDays 天情绪低落，请多关心")
        if (worryDays >= 2) sb.appendLine("😟 关注：有 $worryDays 天表达担忧，建议打电话聊聊")
        if (sickCount > 0) sb.appendLine("🏥 健康：本周提及身体不适 $sickCount 次，请关注健康状况")
        if (activeDays == 0) sb.appendLine("📵 本周无对话记录，请关注老人状态")
        else if (activeDays < 3) sb.appendLine("📉 本周陪伴天数较少（$activeDays 天），建议增加互动频率")

        sb.appendLine()
        sb.appendLine("共记录 ${entries.size} 条对话，请继续保持关爱。")
        return sb.toString().trim()
    }

    private fun getTodayStartTimestamp(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun generateLocalReply(content: String, emotion: String, userName: String?): String {
        val name = userName?.takeIf { it.isNotBlank() } ?: "您"
        return when (emotion) {
            "开心", "满意" -> "听到${name}这么说，我也很高兴！继续保持好心情哦。"
            "担心" -> "别太担心了${name}，有什么事慢慢来，我陪着您呢。"
            "孤单" -> "我一直在这里陪着${name}，有空也可以给孩子们打个电话聊聊。"
            "难过" -> "我理解${name}的心情，难过的时候说出来会好一些，我在听。"
            "不适" -> "身体不舒服要注意休息，如果一直不好记得告诉家人或去看医生哦。"
            else -> "谢谢${name}的分享，我会一直陪着您的，有什么想聊的随时说。"
        }
    }
}
