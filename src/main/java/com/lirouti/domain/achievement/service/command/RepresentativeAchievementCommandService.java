package com.lirouti.domain.achievement.service.command;

import com.lirouti.domain.achievement.entity.Achievement;
import com.lirouti.domain.achievement.entity.MemberAchievement;
import com.lirouti.domain.achievement.enums.AchievementCategory;
import com.lirouti.domain.achievement.enums.MemberAchievementStatus;
import com.lirouti.domain.achievement.exception.AchievementException;
import com.lirouti.domain.achievement.exception.code.error.AchievementErrorCode;
import com.lirouti.domain.achievement.repository.MemberAchievementRepository;
import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 홈 화면·그룹 프로필에 노출할 "대표 업적"을 선택한다.
 *
 * <p>배지 이미지가 있는(badge_image_key not null) 업적 중, 본인이 실제로 CLAIMED한
 * 것만 고를 수 있다.
 */
@Service
@RequiredArgsConstructor
public class RepresentativeAchievementCommandService {

    private final MemberRepository memberRepository;
    private final MemberAchievementRepository memberAchievementRepository;

    @Transactional
    public void select(Long memberId, Long achievementId) {
        MemberAchievement memberAchievement = memberAchievementRepository
                .findByMemberIdAndAchievementId(memberId, achievementId)
                .orElseThrow(() -> new AchievementException(AchievementErrorCode.NOT_FOUND));

        if (memberAchievement.getStatus() != MemberAchievementStatus.CLAIMED) {
            throw new AchievementException(AchievementErrorCode.NOT_ACHIEVED);
        }

        Achievement achievement = memberAchievement.getAchievement();
        if (achievement.getCategory() == AchievementCategory.EGG) {
            throw new AchievementException(AchievementErrorCode.BADGE_IMAGE_NOT_AVAILABLE);
        }
        if (achievement.getBadgeImageKey() == null) {
            throw new AchievementException(AchievementErrorCode.BADGE_IMAGE_NOT_AVAILABLE);
        }

        Member member = memberRepository.getReferenceById(memberId);
        member.selectRepresentativeAchievement(achievementId);
    }

    @Transactional
    public void clear(Long memberId) {
        Member member = memberRepository.getReferenceById(memberId);
        member.clearRepresentativeAchievement();
    }
}
