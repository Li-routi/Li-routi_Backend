package com.lirouti.domain.achievement.service.query;

import com.lirouti.domain.achievement.dto.response.AchievementResDTO;
import com.lirouti.domain.achievement.entity.Achievement;
import com.lirouti.domain.achievement.entity.MemberAchievement;
import com.lirouti.domain.achievement.exception.AchievementException;
import com.lirouti.domain.achievement.exception.code.error.AchievementErrorCode;
import com.lirouti.domain.achievement.repository.MemberAchievementRepository;
import com.lirouti.domain.media.enums.MediaPurpose;
import com.lirouti.domain.media.service.MediaService;
import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RepresentativeAchievementQueryService {

    private final MemberAchievementRepository memberAchievementRepository;
    private final MemberRepository memberRepository;
    private final MediaService mediaService;

    @Transactional(readOnly = true)
    public AchievementResDTO.ClaimedBadgeAchievements getSelectableBadges(Long memberId) {
        validateId(memberId);

        Long currentRepresentativeId = memberRepository.findById(memberId)
                .map(Member::getRepresentativeAchievementId)
                .orElse(null);

        List<MemberAchievement> claimed = memberAchievementRepository
                .findClaimedWithBadgeByMemberId(memberId);

        List<AchievementResDTO.ClaimedBadgeAchievement> items = claimed.stream()
                .map(ma -> toItem(ma, currentRepresentativeId))
                .toList();

        return AchievementResDTO.ClaimedBadgeAchievements.builder()
                .totalCount(items.size())
                .achievements(items)
                .build();
    }

    private AchievementResDTO.ClaimedBadgeAchievement toItem(
            MemberAchievement memberAchievement, Long currentRepresentativeId
    ) {
        Achievement achievement = memberAchievement.getAchievement();
        String badgeImageUrl = mediaService.resolveViewUrl(
                achievement.getBadgeImageKey(), MediaPurpose.ACHIEVEMENT_BADGE);

        return AchievementResDTO.ClaimedBadgeAchievement.builder()
                .achievementId(achievement.getId())
                .name(achievement.getName())
                .badgeImageUrl(badgeImageUrl)
                .representative(achievement.getId().equals(currentRepresentativeId))
                .build();
    }

    private void validateId(Long memberId) {
        if (memberId == null || memberId <= 0) {
            throw new AchievementException(AchievementErrorCode.ACHIEVEMENT_ID_REQUIRED);
        }
    }
}
