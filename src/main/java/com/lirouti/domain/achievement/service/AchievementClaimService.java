package com.lirouti.domain.achievement.service;

import com.lirouti.domain.achievement.entity.Achievement;
import com.lirouti.domain.achievement.entity.MemberAchievement;
import com.lirouti.domain.achievement.enums.MemberAchievementStatus;
import com.lirouti.domain.achievement.exception.AchievementException;
import com.lirouti.domain.achievement.exception.code.error.AchievementErrorCode;
import com.lirouti.domain.achievement.repository.MemberAchievementRepository;
import com.lirouti.domain.character.service.CharacterUnlockService;
import com.lirouti.domain.wallet.entity.MemberWallet;
import com.lirouti.domain.wallet.enums.Currency;
import com.lirouti.domain.wallet.enums.WalletTransactionType;
import com.lirouti.domain.wallet.repository.MemberWalletRepository;
import com.lirouti.domain.wallet.service.WalletResult;
import com.lirouti.domain.wallet.service.WalletService;
import com.lirouti.domain.wallet.service.command.WalletCommandService.WalletCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 업적 보상 수령.
 *
 * <p>지갑 지급이 먼저다. WalletService.grant 는 멱등이라 여러 번 불러도 안전하지만,
 * member_achievement.status 를 먼저 CLAIMED 로 바꾸면 그 뒤 지갑 호출이 실패했을 때
 * 재시도가 "이미 CLAIMED"로 보고 지갑 호출을 건너뛰어 보상 없이 상태만 CLAIMED 가 되는
 * 사고가 난다. 순서를 뒤집으면 안 된다.
 *
 * <p>topazReward가 0인 업적(캐릭터알 시리즈)은 지갑을 호출하지 않는다 -
 * WalletCommandService.grant()가 0원 이하 지급을 INVALID_AMOUNT로 막고 있어서,
 * 0원을 그대로 넘기면 claim 전체가 예외로 실패한다.
 *
 * <p>배지·캐릭터알 지급은 지갑 지급과 별개 관심사로 분리한다 — 둘 중 하나가 실패해도
 * 이미 성공한 지갑 지급/claim 상태를 되돌리지 않는다. 로그만 남기고 넘어간다.
 *
 * <p>한정 의상 지급은 이번 스코프에서 제외 — 추후 별도 작업으로 연결한다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AchievementClaimService {

    private final MemberAchievementRepository memberAchievementRepository;
    private final WalletService walletService;
    private final MemberWalletRepository memberWalletRepository;
    private final CharacterUnlockService characterUnlockService;

    public ClaimResult claim(Long memberId, Long achievementId) {
        MemberAchievement memberAchievement = loadAndValidate(memberId, achievementId);
        Achievement achievement = memberAchievement.getAchievement();

        WalletResult walletResult = grantTopazIfAny(memberId, achievement);

        markClaimedIfNeeded(memberAchievement);

        grantCharacterIfNeeded(memberId, achievement);

        return new ClaimResult(achievement.getId(), walletResult.freeBalance(), walletResult.applied());
    }

    /**
     * 지급할 토파즈가 있을 때만 지갑을 호출한다. 0원인 업적(알 시리즈)은 지갑 호출 자체를
     * 건너뛰고, 응답에는 프론트 오해를 막기 위해 회원의 현재 토파즈 잔액을 그대로 싣는다 —
     * freeBalanceAfter가 뜬금없이 0으로 나가면 "잔액이 초기화됐다"로 보일 수 있다.
     */
    private WalletResult grantTopazIfAny(Long memberId, Achievement achievement) {
        if (achievement.getTopazReward() <= 0) {
            int currentBalance = memberWalletRepository
                    .findByMemberIdAndCurrency(memberId, Currency.TOPAZ)
                    .map(MemberWallet::getFreeBalance)
                    .orElse(0);
            return new WalletResult(null, Currency.TOPAZ, 0, currentBalance, true);
        }

        String idempotencyKey = "ACHIEVEMENT_CLAIM:" + achievement.getCode();
        WalletCommand command = new WalletCommand(
                memberId,
                Currency.TOPAZ,
                WalletTransactionType.ACHIEVEMENT_REWARD,
                idempotencyKey,
                "ACHIEVEMENT",
                achievement.getId()
        );
        return walletService.grant(command, 0, achievement.getTopazReward());
    }

    /**
     * badgeYn/limitedOutfitYn 과 달리 achievement 쪽에 "이 업적이 캐릭터알을 주는지"를
     * 나타내는 플래그가 없다 - CharacterUnlockCondition 쪽에 이 achievement.code 를
     * 가리키는 행이 있는지 자체가 판단 기준이다. 그래서 매번 조회를 시도하고, 없으면
     * CharacterUnlockService 내부에서 빈 리스트로 조용히 끝난다.
     */
    private void grantCharacterIfNeeded(Long memberId, Achievement achievement) {
        try {
            characterUnlockService.unlockByAchievementClaim(memberId, achievement.getCode());
        } catch (RuntimeException e) {
            log.error("캐릭터알 지급 실패 - memberId={}, achievementCode={}",
                    memberId, achievement.getCode(), e);
        }
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

    @Transactional
    protected void markClaimedIfNeeded(MemberAchievement memberAchievement) {
        if (memberAchievement.getStatus() == MemberAchievementStatus.ACHIEVED) {
            memberAchievement.claim();
        }
    }

    public record ClaimResult(Long achievementId, int freeBalanceAfter, boolean rewardApplied) {
    }
}
