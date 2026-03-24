package com.eldercare.server.service;

import com.eldercare.server.entity.DiaryEntry;
import com.eldercare.server.entity.EmotionLog;
import com.eldercare.server.entity.FamilyRelation;
import com.eldercare.server.entity.WeeklyReport;
import com.eldercare.server.repository.FamilyRelationRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class WeeklyReportScheduler {

    private final FamilyRelationRepository familyRelationRepository;
    private final DiaryService diaryService;
    private final EmotionLogService emotionLogService;
    private final LlmService llmService;
    private final WeeklyReportService weeklyReportService;
    private final PushNotificationService pushNotificationService;

    public WeeklyReportScheduler(
            FamilyRelationRepository familyRelationRepository,
            DiaryService diaryService,
            EmotionLogService emotionLogService,
            LlmService llmService,
            WeeklyReportService weeklyReportService,
            PushNotificationService pushNotificationService) {
        this.familyRelationRepository = familyRelationRepository;
        this.diaryService = diaryService;
        this.emotionLogService = emotionLogService;
        this.llmService = llmService;
        this.weeklyReportService = weeklyReportService;
        this.pushNotificationService = pushNotificationService;
    }

    // Run every Sunday at 20:00
    @Scheduled(cron = "0 0 20 * * SUN")
    public void generateAndPushWeeklyReports() {
        System.out.println("Starting weekly report generation...");
        List<FamilyRelation> relations = familyRelationRepository.findAll();
        
        long oneWeekAgo = System.currentTimeMillis() - (7L * 24 * 60 * 60 * 1000);

        for (FamilyRelation relation : familyRelationRepository.findAll()) {
            Long parentId = relation.getParentUserId();
            Long childId = relation.getChildUserId();

            List<DiaryEntry> recentDiaries = diaryService.findByUserId(parentId).stream()
                    .filter(d -> d.getDate() != null && d.getDate() >= oneWeekAgo).toList();
            List<EmotionLog> recentEmotions = emotionLogService.getRecentLogs(parentId, oneWeekAgo);

            String prompt = buildPrompt(recentDiaries, recentEmotions);
            String reportContent = llmService.generateWeeklyReport(prompt);

            WeeklyReport report = new WeeklyReport();
            report.setParentId(parentId);
            report.setChildId(childId);
            report.setReportContent(reportContent);
            report.setGeneratedAt(System.currentTimeMillis());
            
            weeklyReportService.save(report);

            pushNotificationService.sendPushNotification(childId, 
                    "本周健康报告已生成", 
                    "您的父母本周的健康与情绪周报已生成，请点击查看。");
        }
        System.out.println("Weekly report generation completed.");
    }

    private String buildPrompt(List<DiaryEntry> diaries, List<EmotionLog> emotions) {
        StringBuilder sb = new StringBuilder();
        sb.append("以下是老人的本周饮食记录：\n");
        for (DiaryEntry d : diaries) {
            sb.append("- ").append(d.getContent()).append("\n");
        }
        sb.append("以下是老人的本周情绪记录：\n");
        for (EmotionLog e : emotions) {
            sb.append("- 情绪：").append(e.getEmotion()).append("，备注：").append(e.getNote()).append("\n");
        }
        sb.append("请根据以上数据，生成一份温暖、关怀的健康情绪周报，总结饮食和情绪情况，并给出简单的建议。");
        return sb.toString();
    }
}
