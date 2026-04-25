package com.eldercare.backend.service;

import com.eldercare.backend.dto.DiaryEntryDto;
import com.eldercare.backend.entity.DiaryEntry;
import com.eldercare.backend.entity.EmotionLog;
import com.eldercare.backend.entity.User;
import com.eldercare.backend.repository.DiaryEntryRepository;
import com.eldercare.backend.repository.EmotionLogRepository;
import com.eldercare.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DiaryService {

    private final DiaryEntryRepository diaryRepo;
    private final EmotionLogRepository emotionLogRepo;
    private final UserRepository userRepo;

    /** 父母端上传对话日记（批量同步） */
    @Transactional
    public void syncDiaries(Long userId, List<DiaryEntryDto> entries) {
        for (DiaryEntryDto dto : entries) {
            DiaryEntry entry = new DiaryEntry();
            entry.setUserId(userId);
            entry.setDate(dto.getDate());
            entry.setContent(dto.getContent());
            entry.setEmotion(dto.getEmotion());
            entry.setAiResponse(dto.getAiResponse());
            diaryRepo.save(entry);
        }
    }

    /** 子女端获取父母的日记（需要同家庭） */
    public List<DiaryEntry> getParentDiaries(Long childUserId, Long sinceTimestamp) {
        Long parentId = getParentIdForChild(childUserId);
        long since = sinceTimestamp != null ? sinceTimestamp : 0L;
        long now = System.currentTimeMillis();
        return diaryRepo.findByUserIdAndDateBetweenOrderByDateDesc(parentId, since, now);
    }

    /** 子女端获取父母的情绪日志 */
    public List<EmotionLog> getParentEmotionLogs(Long childUserId, Long sinceTimestamp) {
        Long parentId = getParentIdForChild(childUserId);
        long since = sinceTimestamp != null ? sinceTimestamp : 0L;
        long now = System.currentTimeMillis();
        return emotionLogRepo.findByUserIdAndDayTimestampBetween(parentId, since, now);
    }

    /** 父母端上传情绪日志 */
    @Transactional
    public void syncEmotionLog(Long userId, EmotionLog log) {
        // 按天去重：同一天的日志直接覆盖
        emotionLogRepo.findByUserIdAndDayTimestamp(userId, log.getDayTimestamp())
                .ifPresentOrElse(
                    existing -> {
                        log.setId(existing.getId());
                        log.setUserId(userId);
                        emotionLogRepo.save(log);
                    },
                    () -> {
                        log.setId(null);
                        log.setUserId(userId);
                        emotionLogRepo.save(log);
                    }
                );
    }

    private Long getParentIdForChild(Long childUserId) {
        User child = userRepo.findById(childUserId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        if (child.getFamilyId() == null) {
            throw new IllegalArgumentException("尚未绑定家庭");
        }
        return userRepo.findByFamilyId(child.getFamilyId()).stream()
                .filter(u -> "parent".equals(u.getRole()))
                .map(User::getId)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未找到父母账号"));
    }
}
