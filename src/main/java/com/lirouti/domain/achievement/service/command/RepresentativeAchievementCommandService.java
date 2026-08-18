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
 * 것만 고를 수 있다. EGG 업적은 배지 이미지가 없는 게 정상 데이터라 이 조건만으로도
 * 걸러지지만, 데이터 오류로 EGG에 badge_image_key가 잘못 채워지는 경우까지 대비해
 * 카테고리로 한 번 더 명시적으로 막는다.
 */
@Service
@RequiredArgsConstructor
public class RepresentativeAchievementCommandService {

    private final MemberRepository memberRepository;
    private final MemberAchievementRepository memberAchievementRepository;

    @Transactional
    public void select(Long memberId, Long achievementId) {
        validateId(memberId, "memberId");
        validateId(achievementId, "achievementId");

        MemberAchievement memberAchievement = memberAchievementRepository
                .findByMemberIdAndAchievementId(memberId, achievementId)
                .orElseThrow(() -> new AchievementException(AchievementErrorCode.NOT_FOUND));

        if (memberAchievement.getStatus() != MemberAchievementStatus.CLAIMED) {
            throw new AchievementException(AchievementErrorCode.NOT_ACHIEVED);
        }

        Achievement achievement = memberAchievement.getAchievement();
        if (achievement.getCategory() == AchievementCategory.EGG
                || achievement.getBadgeImageKey() == null) {
            throw new AchievementException(AchievementErrorCode.BADGE_IMAGE_NOT_AVAILABLE);
        }

        Member member = memberRepository.getReferenceById(memberId);
        member.selectRepresentativeAchievement(achievementId);
    }

    @Transactional
    public void clear(Long memberId) {
        validateId(memberId, "memberId");

        Member member = memberRepository.getReferenceById(memberId);
        member.clearRepresentativeAchievement();
    }

    /**
     * 컨트롤러의 Bean Validation(@Positive 등)을 우회해 이 서비스가 직접 호출되는
     * 경로(배치, 테스트, 다른 서비스 조합 등)에서도 잘못된 id가 조용히 repository까지
     * 흘러가지 않도록 진입점에서 한 번 더 막는다.
     */
    private void validateId(Long id, String fieldName) {
        if (id == null || id <= 0) {
            throw new AchievementException(AchievementErrorCode.ACHIEVEMENT_ID_REQUIRED);
        }
    }
}
