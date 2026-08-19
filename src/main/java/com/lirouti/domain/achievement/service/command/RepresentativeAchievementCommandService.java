package com.lirouti.domain.achievement.service.command;

import com.lirouti.domain.achievement.entity.Achievement;
import com.lirouti.domain.achievement.entity.MemberAchievement;
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
 * <p><b>배찌로 등록된({@code badge_yn}) 업적 중 본인이 CLAIMED 한 것만</b> 고를 수 있다.
 * 후보 목록({@code findClaimedWithBadgeByMemberId})과 같은 기준이어야 한다 — 목록에 없는
 * 것을 요청으로 밀어 넣을 수 있으면 막은 의미가 없다.
 *
 * <p>캐릭터알(EGG)은 배찌가 아니라 {@code badge_yn = 0} 이므로 여기서 걸린다. 이미지까지
 * 보는 것은 <b>그릴 수 없는 것을 대표로 세우지 않기 위해서</b>다.
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
        if (!achievement.isBadgeYn() || achievement.getBadgeImageKey() == null) {
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
