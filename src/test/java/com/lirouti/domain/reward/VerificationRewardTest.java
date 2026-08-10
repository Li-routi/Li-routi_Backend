package com.lirouti.domain.reward;

import com.lirouti.domain.challenge.entity.Challenge;
import com.lirouti.domain.challenge.entity.MemberChallenge;
import com.lirouti.domain.challenge.enums.ChallengeCategory;
import com.lirouti.domain.media.service.MediaImageLoad;
import com.lirouti.domain.media.service.MediaService;
import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.enums.Role;
import com.lirouti.domain.member.enums.SocialProvider;
import com.lirouti.domain.reward.entity.RewardGrant;
import com.lirouti.domain.reward.enums.RewardReason;
import com.lirouti.domain.reward.exception.RewardClawbackException;
import com.lirouti.domain.reward.repository.RewardGrantRepository;
import com.lirouti.domain.reward.service.command.RewardCommandService;
import com.lirouti.domain.verification.dto.request.ChallengeVerificationReqDTO;
import com.lirouti.domain.verification.entity.ChallengeVerification;
import com.lirouti.domain.verification.enums.ReviewStatus;
import com.lirouti.domain.verification.service.ChallengeVerificationService;
import com.lirouti.domain.verification.service.command.ChallengeVerificationCommandService;
import com.lirouti.domain.wallet.enums.Currency;
import com.lirouti.domain.wallet.enums.WalletTransactionType;
import com.lirouti.domain.wallet.repository.MemberWalletRepository;
import com.lirouti.domain.wallet.repository.WalletTransactionRepository;
import com.lirouti.domain.wallet.service.WalletService;
import com.lirouti.domain.wallet.service.command.WalletCommandService.WalletCommand;
import com.lirouti.global.util.TimeUtil;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

/**
 * 인증 통과 리워드의 지급과 회수.
 *
 * <p><b>"올려서 이득만 챙기고 지우기" 를 막는 것은 이 회수뿐이다.</b> 인증 삭제는 스트릭을
 * 건드리지 않기로 했으므로, 회수가 새면 남용을 막을 장치가 하나도 남지 않는다.
 *
 * <p>지급 행과 원장을 함께 본다. 잔액만 맞고 둘 중 하나가 어긋나면, 나중에 재지급이 막히거나
 * (지급 행이 남음) 무슨 일이 있었는지 되짚을 수 없다(원장이 빔).
 */
@SpringBootTest
@Transactional
@DisplayName("인증 통과 리워드")
class VerificationRewardTest {

    private static final String STAGING_KEY =
            "challenge-verifications-staging/2026/08/10/bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb.jpg";
    private static final String PUBLIC_KEY =
            "challenge-verifications/2026/08/10/aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa.jpg";
    private static final int REWARD = 10;

    @Autowired
    private ChallengeVerificationService challengeVerificationService;
    @Autowired
    private RewardCommandService rewardCommandService;
    @Autowired
    private RewardGrantRepository rewardGrantRepository;
    @Autowired
    private MemberWalletRepository memberWalletRepository;
    @Autowired
    private WalletTransactionRepository walletTransactionRepository;
    @Autowired
    private WalletService walletService;
    @Autowired
    private ChallengeVerificationCommandService challengeVerificationCommandService;

    @MockitoBean
    private MediaService mediaService;

    @PersistenceContext
    private EntityManager em;

    private final AtomicInteger seq = new AtomicInteger();

    private Member me;
    private Challenge challenge;
    private MemberChallenge participation;

    @BeforeEach
    void setUp() {
        int n = seq.incrementAndGet();
        me = Member.builder()
                .email("rw" + n + "@ex.com").nickname("rw" + n)
                .socialProvider(SocialProvider.GOOGLE).role(Role.ROLE_USER)
                .socialId("rw-sid-" + n).build();
        em.persist(me);

        challenge = Challenge.builder()
                .name("리워드챌린지").category(ChallengeCategory.HEALTH)
                .reward(REWARD).active(true).build();
        em.persist(challenge);

        participation = MemberChallenge.builder()
                .member(me).challenge(challenge)
                .participationRound(1).currentStreak(0)
                .joinedAt(LocalDateTime.now()).active(true).build();
        em.persist(participation);
        em.flush();

        when(mediaService.resolvePublicUrl(any())).thenReturn("https://cdn.example.com/" + PUBLIC_KEY);
        doNothing().when(mediaService).validateMediaKey(any(), any());
        doNothing().when(mediaService).validateUploadedBytes(any(), any());
        when(mediaService.loadForReview(any(), anyInt())).thenReturn(MediaImageLoad.readFailed());
        when(mediaService.promote(any(), any(), any())).thenReturn(PUBLIC_KEY);
        when(mediaService.presignedViewUrl(any())).thenReturn("https://signed.example.com/staging?sig=x");
    }

    // ── 픽스처 ──

    private ChallengeVerification verificationToday() {
        LocalDate today = LocalDate.now(TimeUtil.KST);
        ChallengeVerification v = ChallengeVerification.builder()
                .memberChallenge(participation).participationRound(1)
                .verifiedDate(today).periodStartDate(today)
                .verifiedAt(LocalDateTime.now())
                .imageUrl(PUBLIC_KEY).content("인증").build();
        em.persist(v);
        em.flush();
        return v;
    }

    private int balance() {
        return memberWalletRepository.findByMemberIdAndCurrency(me.getId(), Currency.TOPAZ)
                .map(w -> w.totalBalance())
                .orElse(0);
    }

    private long ledgerCount(WalletTransactionType type) {
        return walletTransactionRepository.findAll().stream()
                .filter(t -> t.getMember().getId().equals(me.getId()) && t.getTransactionType() == type)
                .count();
    }

    private Optional<RewardGrant> grantOf(ChallengeVerification v) {
        return rewardGrantRepository.findByMemberIdAndReasonAndReferenceId(
                me.getId(), RewardReason.VERIFICATION, v.getId());
    }

    // ── 지급 ──

    @Test
    @DisplayName("지급하면 잔액·지급 행·원장이 함께 남는다")
    void grant_LeavesBalanceGrantAndLedger() {
        ChallengeVerification v = verificationToday();

        boolean granted = rewardCommandService.grantForVerification(me, v.getId(), REWARD);
        em.flush();

        assertAll(
                () -> assertThat(granted).isTrue(),
                () -> assertThat(balance()).isEqualTo(REWARD),
                () -> assertThat(grantOf(v)).isPresent(),
                () -> assertThat(ledgerCount(WalletTransactionType.CHALLENGE_REWARD)).isEqualTo(1)
        );
    }

    @Test
    @DisplayName("리워드는 무상 잔액으로 들어간다 — 산 재화보다 먼저 쓰인다")
    void grant_GoesToFreeBalance() {
        ChallengeVerification v = verificationToday();

        rewardCommandService.grantForVerification(me, v.getId(), REWARD);
        em.flush();

        assertAll(
                () -> assertThat(memberWalletRepository
                        .findByMemberIdAndCurrency(me.getId(), Currency.TOPAZ).orElseThrow().getFreeBalance())
                        .isEqualTo(REWARD),
                () -> assertThat(memberWalletRepository
                        .findByMemberIdAndCurrency(me.getId(), Currency.TOPAZ).orElseThrow().getPaidBalance())
                        .as("현금으로 산 것이 아니므로 환불 대상이 아니다").isZero()
        );
    }

    @Test
    @DisplayName("리워드는 무료 재화로 준다 — 유료 재화 지갑은 만들어지지도 않는다")
    void grant_UsesFreeCurrencyOnly() {
        // 여기가 실제로 틀렸던 자리다. 리워드를 GEM 으로 주는 코드가 들어가 배포까지 됐다 —
        // 인증만 하면 현금으로만 얻어야 할 재화가 공짜로 생기는 상태였다. 재화 성격은 문서에서
        // 두 번 뒤집혔으므로, 다음에 또 뒤집히면 이 테스트가 먼저 빨간불을 내야 한다.
        ChallengeVerification v = verificationToday();

        rewardCommandService.grantForVerification(me, v.getId(), REWARD);
        em.flush();

        assertAll(
                () -> assertThat(memberWalletRepository
                        .findByMemberIdAndCurrency(me.getId(), Currency.TOPAZ))
                        .as("무료 재화로 들어간다").isPresent(),
                () -> assertThat(memberWalletRepository
                        .findByMemberIdAndCurrency(me.getId(), Currency.GEM))
                        .as("유료 재화는 현금으로만 얻는다 — 지갑이 생길 이유가 없다").isEmpty(),
                // 회수는 지급 행에 적힌 재화로 차감한다. 지갑만 보면 지급 행이 다른 재화로
                // 남아도 통과하는데, 그러면 삭제할 때 엉뚱한 지갑에서 빠진다.
                () -> assertThat(grantOf(v).orElseThrow().getCurrency())
                        .as("지급 행에 적힌 재화가 회수 대상을 정한다").isEqualTo(Currency.TOPAZ)
        );
    }

    @Test
    @DisplayName("같은 인증에 두 번 지급되지 않는다 — 사진만 갈아끼우고 또 받는 길이 없다")
    void grant_NeverTwiceForSameVerification() {
        ChallengeVerification v = verificationToday();

        rewardCommandService.grantForVerification(me, v.getId(), REWARD);
        boolean second = rewardCommandService.grantForVerification(me, v.getId(), REWARD);
        em.flush();

        assertAll(
                () -> assertThat(second).isFalse(),
                () -> assertThat(balance()).isEqualTo(REWARD),
                () -> assertThat(ledgerCount(WalletTransactionType.CHALLENGE_REWARD)).isEqualTo(1)
        );
    }

    @Test
    @DisplayName("리워드가 0인 챌린지는 지급 행도 원장도 남기지 않는다")
    void grant_SkipsWhenRewardIsZero() {
        ChallengeVerification v = verificationToday();

        boolean granted = rewardCommandService.grantForVerification(me, v.getId(), 0);
        em.flush();

        assertAll(
                () -> assertThat(granted).isFalse(),
                () -> assertThat(grantOf(v)).isEmpty(),
                () -> assertThat(ledgerCount(WalletTransactionType.CHALLENGE_REWARD)).isZero(),
                () -> assertThat(balance()).isZero()
        );
    }

    // ── 실제 경로 ──

    @Test
    @DisplayName("인증하면 그 경로에서 실제로 지급된다 — 서비스를 직접 부른 것과 별개다")
    void verify_GrantsThroughTheRealPath() {
        // 지급을 저장에 붙여 놓고 서비스만 따로 테스트하면, 배선이 빠져도 통과한다.
        challengeVerificationService.verify(me.getId(), challenge.getId(),
                new ChallengeVerificationReqDTO.Verify(STAGING_KEY, "오늘도 했어요"));
        em.flush();

        assertAll(
                () -> assertThat(balance()).isEqualTo(REWARD),
                () -> assertThat(ledgerCount(WalletTransactionType.CHALLENGE_REWARD)).isEqualTo(1)
        );
    }

    @Test
    @DisplayName("보류로 저장된 인증에는 지급하지 않는다 — 아직 통과한 것이 아니다")
    void pendingVerification_IsNotGrantedYet() {
        challengeVerificationCommandService.save(
                me.getId(), challenge.getId(),
                new ChallengeVerificationReqDTO.Verify(STAGING_KEY, "심사 중"),
                STAGING_KEY, ReviewStatus.PENDING);
        ChallengeVerification pending = em.createQuery(
                        "select v from ChallengeVerification v where v.memberChallenge.id = :id",
                        ChallengeVerification.class)
                .setParameter("id", participation.getId()).getSingleResult();
        em.flush();

        assertAll(
                () -> assertThat(pending.isPending()).isTrue(),
                () -> assertThat(balance()).as("남의 서비스 장애로 보류된 것이라 나중에 승인되면 준다").isZero(),
                () -> assertThat(rewardGrantRepository.findByMemberIdAndReasonAndReferenceId(
                        me.getId(), RewardReason.VERIFICATION, pending.getId())).isEmpty()
        );
    }

    // ── 회수 ──

    @Test
    @DisplayName("글을 내리면 준 것을 되돌린다 — 지급 행은 지우고 원장에는 두 줄이 남는다")
    void delete_ClawsBackReward() {
        ChallengeVerification v = verificationToday();
        rewardCommandService.grantForVerification(me, v.getId(), REWARD);
        em.flush();

        challengeVerificationService.deleteVerification(me.getId(), challenge.getId(), v.getId());
        em.flush();

        assertAll(
                () -> assertThat(balance()).as("준 만큼 되돌아간다").isZero(),
                () -> assertThat(grantOf(v))
                        .as("남겨 두면 다시 인증해도 유니크 제약에 막혀 못 받는다").isEmpty(),
                () -> assertThat(ledgerCount(WalletTransactionType.CHALLENGE_REWARD)).isEqualTo(1),
                () -> assertThat(ledgerCount(WalletTransactionType.CHALLENGE_REWARD_CLAWBACK))
                        .as("원장은 지우지 않는다 — 반대 방향 거래로 상쇄한다").isEqualTo(1),
                () -> assertThat(v.isDeleted()).isTrue()
        );
    }

    @Test
    @DisplayName("회수 뒤 다시 인증하면 리워드를 또 받는다")
    void regrantAfterClawback() {
        ChallengeVerification v = verificationToday();
        rewardCommandService.grantForVerification(me, v.getId(), REWARD);
        em.flush();
        challengeVerificationService.deleteVerification(me.getId(), challenge.getId(), v.getId());
        em.flush();

        // 되살리기는 같은 행을 쓰므로 인증 id 가 그대로다. 멱등 키를 인증 id 로 만들었다면
        // 여기서 조용히 건너뛰어져 사용자는 영영 못 받는다.
        boolean regranted = rewardCommandService.grantForVerification(me, v.getId(), REWARD);
        em.flush();

        assertAll(
                () -> assertThat(regranted).isTrue(),
                () -> assertThat(balance()).isEqualTo(REWARD),
                () -> assertThat(ledgerCount(WalletTransactionType.CHALLENGE_REWARD)).isEqualTo(2)
        );
    }

    @Test
    @DisplayName("잔액이 모자라면 삭제를 거절한다 — 글도 재화도 그대로다")
    void delete_RejectedWhenBalanceSpent() {
        ChallengeVerification v = verificationToday();
        rewardCommandService.grantForVerification(me, v.getId(), REWARD);
        em.flush();
        // 받은 것을 아이템 사는 데 다 써 버렸다.
        walletService.deduct(new WalletCommand(me.getId(), Currency.TOPAZ,
                WalletTransactionType.PURCHASE, "spend-all", null, null), REWARD);
        em.flush();

        assertThatThrownBy(() -> challengeVerificationService
                .deleteVerification(me.getId(), challenge.getId(), v.getId()))
                .isInstanceOf(RewardClawbackException.class)
                .satisfies(e -> assertAll(
                        () -> assertThat(((RewardClawbackException) e).getRequired()).isEqualTo(REWARD),
                        () -> assertThat(((RewardClawbackException) e).getBalance()).isZero(),
                        () -> assertThat(((RewardClawbackException) e).shortfall())
                                .as("몇 개를 더 모으면 되는지 알아야 사용자가 행동할 수 있다")
                                .isEqualTo(REWARD)));

        // 아래 isDeleted 는 "롤백됐다" 가 아니라 "회수가 먼저 막혀 삭제까지 가지 못했다" 를
        // 본다. 이 테스트는 @Transactional 이라 서비스가 바깥 트랜잭션에 참여하므로 실제
        // 커밋·롤백 경계가 생기지 않는다. 회수를 삭제 앞에 두었다는 사실이 회귀로 깨지는 것을
        // 잡는 값은 있어 남긴다.
        assertAll(
                () -> assertThat(v.isDeleted())
                        .as("회수가 먼저 막히므로 삭제 코드에 닿지 않는다").isFalse(),
                () -> assertThat(grantOf(v))
                        .as("거절됐으므로 지급 행이 남아 있어야 한다").isPresent(),
                () -> assertThat(balance()).isZero()
        );
    }
}
