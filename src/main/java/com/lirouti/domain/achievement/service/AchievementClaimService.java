package com.lirouti.domain.achievement.service;

import com.lirouti.domain.achievement.entity.Achievement;
import com.lirouti.domain.achievement.entity.MemberAchievement;
import com.lirouti.domain.achievement.enums.MemberAchievementStatus;
import com.lirouti.domain.achievement.exception.AchievementException;
import com.lirouti.domain.achievement.exception.code.error.AchievementErrorCode;
import com.lirouti.domain.achievement.repository.MemberAchievementRepository;
import com.lirouti.domain.wallet.enums.Currency;
import com.lirouti.domain.wallet.enums.WalletTransactionType;
import com.lirouti.domain.wallet.service.WalletResult;
import com.lirouti.domain.wallet.service.WalletService;
import com.lirouti.domain.wallet.service.command.WalletCommandService.WalletCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 업적 보상 수령.
 *
 * <p>지갑 지급이 먼저다. {@code WalletService.grant} 는 멱등이라 여러 번 불러도 안전하지만,
 * {@code member_achievement.status} 를 먼저 CLAIMED 로 바꾸면 그 뒤 지갑 호출이 실패했을 때
 * 재시도가 "이미 CLAIMED"로 보고 지갑 호출을 건너뛰어 보상 없이 상태만 CLAIMED 가 되는 사고가
 * 난다. 순서를 뒤집으면 안 된다.
 */
@Service
@RequiredArgsConstructor
public class AchievementClaimService {

    private final MemberAchievementRepository memberAchievementRepository;
    private final WalletService walletService;

    public ClaimResult claim(Long memberId, Long achievementId) {
        MemberAchievement memberAchievement = loadAndValidate(memberId, achievementId);
        Achievement achievement = memberAchievement.getAchievement();

        // 이미 CLAIMED 라도 여기서 막지 않고 지갑을 한 번 더 부른다.
        // WalletService.grant 는 같은 idempotencyKey 면 잔액을 다시 움직이지 않고
        // 처음 거래를 그대로 돌려주므로, 상태 갱신이 이전에 실패했던 경우를 여기서 복구한다.
        String idempotencyKey = "ACHIEVEMENT_CLAIM:" + achievement.getCode();
        WalletCommand command = new WalletCommand(
                memberId,
                Currency.TOPAZ,
                WalletTransactionType.ACHIEVEMENT_REWARD,
                idempotencyKey,
                "ACHIEVEMENT",
                achievement.getId()
        );

        WalletResult walletResult = walletService.grant(command, 0, achievement.getTopazReward());

        markClaimedIfNeeded(memberAchievement);

        return new ClaimResult(achievement.getId(), walletResult.freeBalance(), walletResult.applied());
    }

    @Transactional(readOnly = true)
    protected MemberAchievement loadAndValidate(Long memberId, Long achievementId) {
        MemberAchievement memberAchievement = memberAchievementRepository
                .findByMemberIdAndAchievementId(memberId, achievementId)
                .orElseThrow(() -> new AchievementException(AchievementErrorCode.NOT_FOUND));

        if (memberAchievement.getStatus() == MemberAchievementStatus.IN_PROGRESS) {
            throw new AchievementException(AchievementErrorCode.NOT_ACHIEVED);
        }
        return memberAchievement;
    }

    /**
     * 상태 갱신은 지갑 지급과 별도 트랜잭션이다. 조건부 UPDATE 로 두 번째 호출이 상태를
     * 다시 CLAIMED 로 덮어써도 무해하게 한다 — claimed_at 은 최초 시각을 유지한다.
     */
    @Transactional
    protected void markClaimedIfNeeded(MemberAchievement memberAchievement) {
        if (memberAchievement.getStatus() == MemberAchievementStatus.ACHIEVED) {
            memberAchievement.claim();
        }
    }

    public record ClaimResult(Long achievementId, int freeBalanceAfter, boolean rewardApplied) {
    }
}
