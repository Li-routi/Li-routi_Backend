package com.lirouti.domain.achievement.service.command;

import com.lirouti.domain.achievement.entity.Achievement;
import com.lirouti.domain.achievement.exception.AchievementException;
import com.lirouti.domain.achievement.exception.code.error.AchievementErrorCode;
import com.lirouti.domain.achievement.repository.AchievementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AchievementCommandService {

    private final AchievementRepository achievementRepository;

    /**
     * 업적이 가리키는 뱃지 key를 교체한다.
     * 외부 S3 작업은 호출자가 끝낸 뒤 이 짧은 transaction만 실행해야 한다.
     */
    @Transactional
    public BadgeImageUpdate replaceBadgeImageKey(Long achievementId, String newBadgeImageKey) {
        if (achievementId == null) {
            throw new AchievementException(AchievementErrorCode.ACHIEVEMENT_ID_REQUIRED);
        }
        if (newBadgeImageKey == null) {
            throw new AchievementException(AchievementErrorCode.BADGE_IMAGE_KEY_REQUIRED);
        }

        Achievement achievement = achievementRepository.findById(achievementId)
                .orElseThrow(() -> new AchievementException(AchievementErrorCode.NOT_FOUND));
        String previousBadgeImageKey = achievement.getBadgeImageKey();
        achievement.replaceBadgeImageKey(newBadgeImageKey);

        return new BadgeImageUpdate(
                achievement.getId(),
                achievement.getCode(),
                previousBadgeImageKey
        );
    }

    public record BadgeImageUpdate(
            Long achievementId,
            String achievementCode,
            String previousBadgeImageKey
    ) {
    }
}
