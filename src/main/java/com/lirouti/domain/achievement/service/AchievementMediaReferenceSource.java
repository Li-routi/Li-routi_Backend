package com.lirouti.domain.achievement.service;

import com.lirouti.domain.achievement.repository.AchievementRepository;
import com.lirouti.domain.media.enums.MediaPurpose;
import com.lirouti.domain.media.service.MediaReferenceSource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * 업적이 현재 참조하는 뱃지 이미지 key를 미디어 정리 배치에 제공한다.
 * 비활성 업적도 DB에 key가 남아 있으면 참조 중인 자산으로 보존한다.
 */
@Component
@RequiredArgsConstructor
public class AchievementMediaReferenceSource implements MediaReferenceSource {

    private final AchievementRepository achievementRepository;

    @Override
    public Set<MediaPurpose> coveredPurposes() {
        return Set.of(MediaPurpose.ACHIEVEMENT_BADGE);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<String> findReferencedKeys(Collection<String> candidateKeys) {
        if (candidateKeys.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(achievementRepository.findBadgeImageKeysIn(candidateKeys));
    }
}
