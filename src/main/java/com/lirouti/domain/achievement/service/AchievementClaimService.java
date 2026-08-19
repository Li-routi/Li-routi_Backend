package com.lirouti.domain.achievement.service;

import com.lirouti.domain.achievement.entity.Achievement;
import com.lirouti.domain.achievement.entity.MemberAchievement;
import com.lirouti.domain.achievement.enums.AchievementCategory;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 업적 보상 수령.
 *
 * <p><b>self-injection을 쓰는 이유</b>: claim()이 내부에서 loadAndValidate()·
 * markClaimedIfNeeded()를 {@code this.}로 직접 호출하면, Spring @Transactional은
 * 프록시를 거쳐야만 동작하는데 같은 인스턴스 안에서의 호출은 프록시를 우회해
 * 트랜잭션이 조용히 무시된다. markClaimedIfNeeded()의 상태 변경이 DB에 반영되지
 * 않고 사라지는 사고가 실제로 발생했었다 - self가 아니라 self proxy(自身의 스프링
 * 빈)를 통해 호출해야 @Transactional이 실제로 걸린다.
 *
 * <p>캐릭터알 지급을 가장 먼저 시도한다. 캐릭터알을 주는 업적(EGG)은 topazReward가
 * 항상 0이라 지갑을 아예 건드리지 않으므로, 여기서 실패해 claim 전체를 중단시켜도
 * "이미 성공한 다른 지급"을 되돌리는 문제가 생기지 않는다.
 *
 * <p>지갑 지급은 캐릭터알 지급 다음이다. 상태 전이는 반드시 지갑 지급 뒤에 온다.
 */
@Service
@Slf4j
public class AchievementClaimService {

    private final MemberAchievementRepository memberAchievementRepository;
    private final WalletService walletService;
    private final MemberWalletRepository memberWalletRepository;
    private final CharacterUnlockService characterUnlockService;

    /** self-injection: @Transactional이 실제로 걸리도록 프록시를 통해 자기 자신을 호출하기 위함. */
    private final AchievementClaimService self;

    public AchievementClaimService(
            MemberAchievementRepository memberAchievementRepository,
            WalletService walletService,
            MemberWalletRepository memberWalletRepository,
            CharacterUnlockService characterUnlockService,
            @Lazy AchievementClaimService self
    ) {
        this.memberAchievementRepository = memberAchievementRepository;
        this.walletService = walletService;
        this.memberWalletRepository = memberWalletRepository;
        this.characterUnlockService = characterUnlockService;
        this.self = self;
    }

    public ClaimResult claim(Long memberId, Long achievementId) {
        MemberAchievement memberAchievement = self.loadAndValidate(memberId, achievementId);
        Achievement achievement = memberAchievement.getAchievement();

        self.grantCharacterIfNeeded(memberId, achievement);

        WalletResult walletResult = grantTopazIfAny(memberId, achievement);

        self.markClaimedIfNeeded(memberAchievement.getId());

        return new ClaimResult(achievement.getId(), walletResult.freeBalance(), walletResult.applied());
    }

    /**
     * 캐릭터알 지급 실패 시 예외를 그대로 던져 claim 전체를 중단시킨다 - EGG 업적은
     * topazReward가 항상 0이라 여기서 멈춰도 되돌릴 지갑 지급이 없다.
     */
    @Transactional
    protected void grantCharacterIfNeeded(Long memberId, Achievement achievement) {
        characterUnlockService.unlockByAchievementClaim(memberId, achievement.getCode());
    }

    private WalletResult grantTopazIfAny(Long memberId, Achievement achievement) {
        int topazReward = achievement.getTopazReward();
        if (topazReward < 0) {
            log.error("업적 topazReward가 음수입니다 - 데이터 설정 오류. achievementCode={}, topazReward={}",
                    achievement.getCode(), topazReward);
            throw new IllegalStateException("업적 " + achievement.getCode() + "의 topazReward가 음수입니다.");
        }
        if (topazReward == 0) {
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
     * memberAchievement를 다시 조회해서 갱신한다 - claim()에서 넘겨받은 엔티티는
     * loadAndValidate()의 readOnly 트랜잭션이 끝나며 detach된 상태라, 그대로 쓰면
     * 이번에도 flush가 안 될 수 있다. id로 새로 조회해 이 트랜잭션에 정식으로 편입시킨다.
     */
    @Transactional
    protected void markClaimedIfNeeded(Long memberAchievementId) {
        MemberAchievement memberAchievement = memberAchievementRepository
                .findById(memberAchievementId)
                .orElseThrow(() -> new AchievementException(AchievementErrorCode.NOT_FOUND));
        if (memberAchievement.getStatus() == MemberAchievementStatus.ACHIEVED) {
            memberAchievement.claim();
        }
    }

    public record ClaimResult(Long achievementId, int freeBalanceAfter, boolean rewardApplied) {
    }
}
