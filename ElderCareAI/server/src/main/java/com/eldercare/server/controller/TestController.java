package com.eldercare.server.controller;

import com.eldercare.server.entity.DiaryEntry;
import com.eldercare.server.entity.EmotionLog;
import com.eldercare.server.entity.FamilyRelation;
import com.eldercare.server.entity.User;
import com.eldercare.server.repository.DiaryEntryRepository;
import com.eldercare.server.repository.EmotionLogRepository;
import com.eldercare.server.repository.FamilyRelationRepository;
import com.eldercare.server.repository.UserRepository;
import com.eldercare.server.service.WeeklyReportScheduler;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/test")
public class TestController {

    private final UserRepository userRepository;
    private final FamilyRelationRepository familyRelationRepository;
    private final DiaryEntryRepository diaryEntryRepository;
    private final EmotionLogRepository emotionLogRepository;
    private final WeeklyReportScheduler weeklyReportScheduler;

    public TestController(UserRepository userRepository,
                          FamilyRelationRepository familyRelationRepository,
                          DiaryEntryRepository diaryEntryRepository,
                          EmotionLogRepository emotionLogRepository,
                          WeeklyReportScheduler weeklyReportScheduler) {
        this.userRepository = userRepository;
        this.familyRelationRepository = familyRelationRepository;
        this.diaryEntryRepository = diaryEntryRepository;
        this.emotionLogRepository = emotionLogRepository;
        this.weeklyReportScheduler = weeklyReportScheduler;
    }

    @PostMapping("/init-data")
    public ResponseEntity<String> initTestData() {
        familyRelationRepository.deleteAll();
        diaryEntryRepository.deleteAll();
        emotionLogRepository.deleteAll();
        userRepository.deleteAll();

        User parent = new User();
        parent.setPhone("13800138000");
        parent.setRole("parent");
        parent.setFamilyId("FAM_TEST_001");
        parent.setInviteCode("TEST_CODE_01");
        parent.setCreatedAt(System.currentTimeMillis());
        parent = userRepository.save(parent);
        Long parentId = parent.getId();

        User child = new User();
        child.setPhone("13900139000");
        child.setRole("child");
        child.setFamilyId("FAM_TEST_001");
        child.setCreatedAt(System.currentTimeMillis());
        child = userRepository.save(child);
        Long childId = child.getId();

        FamilyRelation relation = new FamilyRelation();
        relation.setFamilyId(parent.getFamilyId());
        relation.setParentUserId(parentId);
        relation.setChildUserId(childId);
        relation.setLinkedAt(System.currentTimeMillis());
        familyRelationRepository.save(relation);

        long now = System.currentTimeMillis();
        long oneDay = 24 * 60 * 60 * 1000L;
        
        DiaryEntry diary1 = new DiaryEntry();
        diary1.setUserId(parentId);
        diary1.setContent("早上吃了一碗白粥加咸菜，中午吃了红烧肉，晚上吃了一点面条。");
        diary1.setDate(now - 2 * oneDay);
        diary1.setEmotion("开心");
        diaryEntryRepository.save(diary1);

        EmotionLog emotion1 = new EmotionLog();
        emotion1.setUserId(parentId);
        emotion1.setEmotion("孤独");
        emotion1.setNote("今天孩子们都在加班，一个人在家看电视。");
        emotion1.setTimestamp(now - 3 * oneDay);
        emotionLogRepository.save(emotion1);

        return ResponseEntity.ok(String.format("测试数据初始化成功！老人ID: %d, 子女ID: %d", parentId, childId));
    }

    @PostMapping("/trigger-report")
    public ResponseEntity<String> triggerWeeklyReport() {
        try {
            weeklyReportScheduler.generateAndPushWeeklyReports();
            return ResponseEntity.ok("周报生成任务已手动触发。");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("周报生成失败: " + e.getMessage());
        }
    }
}
