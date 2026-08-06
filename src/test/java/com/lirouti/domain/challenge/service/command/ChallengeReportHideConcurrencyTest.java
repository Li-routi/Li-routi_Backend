package com.lirouti.domain.challenge.service.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.lirouti.domain.challenge.entity.Challenge;
import com.lirouti.domain.verification.dto.request.ChallengeVerificationReqDTO;
import com.lirouti.domain.verification.entity.ChallengeVerification;
import com.lirouti.domain.challenge.entity.MemberChallenge;
import com.lirouti.domain.challenge.enums.ChallengeCategory;
import com.lirouti.domain.challenge.repository.ChallengeRepository;
import com.lirouti.domain.verification.repository.ChallengeVerificationReportRepository;
import com.lirouti.domain.verification.repository.ChallengeVerificationRepository;
import com.lirouti.domain.challenge.repository.MemberChallengeRepository;
import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.enums.Role;
import com.lirouti.domain.member.enums.SocialProvider;
import com.lirouti.domain.member.repository.MemberRepository;
import com.lirouti.global.properties.ChallengeReportProperties;

/**
 * 신고 누적 자동 숨김의 동시성. {@code @Transactional} 을 쓰지 않는다 — 스레드마다 각자
 * 트랜잭션으로 커밋해야 경합이 재현되므로 셋업 데이터를 실제로 커밋하고 뒤에서 정리한다.
 *
 * <p>지키려는 불변식은 <b>"임계값만큼 신고가 쌓이면 반드시 가려진다"</b>이다.
 *
 * <p>처음 구현은 잠금 없이 "저장 후 세기"만 했는데, 그러면 동시 신고가 서로의 미커밋 INSERT 를
 * 보지 못해 전부 임계값 미만으로 판단한다. <b>정확히 임계값만큼만 동시에 들어오면 그 뒤로 신고가
 * 없는 한 영원히 가려지지 않는다.</b> "다음 신고에서 걸린다"고 봤던 것이 틀렸다 — 담합 신고는
 * 오히려 동시에 몰린다. 이 테스트가 그 회귀를 막는다.
 */
@SpringBootTest
@DisplayName("신고 누적 자동 숨김 동시성 테스트")
class ChallengeReportHideConcurrencyTest {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final String KEY =
            "challenge-verifications/2026/07/31/dddddddd-dddd-4ddd-8ddd-dddddddddddd.jpg";

    @Autowired
    private ChallengeCommandService challengeCommandService;
    @Autowired
    private ChallengeReportProperties reportProperties;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private ChallengeRepository challengeRepository;
    @Autowired
    private MemberChallengeRepository memberChallengeRepository;
    @Autowired
    private ChallengeVerificationRepository challengeVerificationRepository;
    @Autowired
    private ChallengeVerificationReportRepository reportRepository;

    private Long authorId;
    private Long challengeId;
    private Long memberChallengeId;
    private Long verificationId;
    private final List<Long> reporterIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        Member author = memberRepository.save(Member.builder()
                .email("rconc-author@ex.com").nickname("rconc-author")
                .socialProvider(SocialProvider.GOOGLE).role(Role.ROLE_USER)
                .socialId("rconc-author-sid").build());
        Challenge c = challengeRepository.save(Challenge.builder()
                .name("rconc챌린지").category(ChallengeCategory.HEALTH).active(true).build());
        MemberChallenge mc = memberChallengeRepository.save(MemberChallenge.builder()
                .member(author).challenge(c)
                .participationRound(1).currentStreak(1)
                .joinedAt(LocalDateTime.now()).active(true).build());
        ChallengeVerification v = challengeVerificationRepository.save(ChallengeVerification.builder()
                .memberChallenge(mc).participationRound(1)
                .verifiedDate(LocalDate.now(KST)).verifiedAt(LocalDateTime.now())
                .imageUrl(KEY).content("동시 신고 대상").build());

        authorId = author.getId();
        challengeId = c.getId();
        memberChallengeId = mc.getId();
        verificationId = v.getId();

        // 신고자는 임계값만큼 필요하다. 서로 다른 회원이어야 UNIQUE(인증, 신고자)에 걸리지 않는다.
        for (int i = 0; i < reportProperties.getHideThreshold(); i++) {
            Member r = memberRepository.save(Member.builder()
                    .email("rconc-r" + i + "@ex.com").nickname("rconc-r" + i)
                    .socialProvider(SocialProvider.GOOGLE).role(Role.ROLE_USER)
                    .socialId("rconc-r-sid-" + i).build());
            reporterIds.add(r.getId());
        }
    }

    @AfterEach
    void tearDown() {
        reportRepository.deleteAll(reportRepository.findAll().stream()
                .filter(r -> r.getChallengeVerification().getId().equals(verificationId))
                .toList());
        challengeVerificationRepository.deleteById(verificationId);
        memberChallengeRepository.deleteById(memberChallengeId);
        challengeRepository.deleteById(challengeId);
        reporterIds.forEach(memberRepository::deleteById);
        reporterIds.clear();
        memberRepository.deleteById(authorId);
    }

    @Test
    @DisplayName("임계값만큼이 동시에 신고해도 가려진다 — 서로의 미커밋 신고를 못 봐 누락되면 안 된다")
    void report_ExactlyThresholdConcurrently_IsHidden() throws InterruptedException {
        // given
        int threshold = reportProperties.getHideThreshold();
        ExecutorService pool = Executors.newFixedThreadPool(threshold);
        CountDownLatch ready = new CountDownLatch(threshold);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threshold);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        // when: 전부 같은 게이트에 세웠다 동시에 출발시킨다(직렬 실행으로 새는 것 방지)
        for (Long reporterId : reporterIds) {
            pool.submit(() -> {
                try {
                    ready.countDown();
                    start.await();
                    challengeCommandService.report(reporterId, challengeId, verificationId,
                            new ChallengeVerificationReqDTO.Report("부적절한 사진"));
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                } catch (RuntimeException e) {
                    failure.compareAndSet(null, e);
                } finally {
                    done.countDown();
                }
            });
        }
        boolean workersReady = ready.await(5, TimeUnit.SECONDS);
        start.countDown();
        boolean finished = done.await(20, TimeUnit.SECONDS);
        pool.shutdownNow();

        // then
        assertThat(workersReady).as("모든 스레드가 게이트에 도착").isTrue();
        assertThat(finished).as("20초 안에 완료").isTrue();
        assertThat(failure.get()).as("신고가 예외 없이 처리되어야 한다").isNull();

        assertThat(reportRepository.countByChallengeVerificationId(verificationId))
                .as("신고가 임계값만큼 쌓였다")
                .isEqualTo(threshold);
        assertThat(challengeVerificationRepository.findById(verificationId).orElseThrow().getHiddenAt())
                .as("추가 신고 없이도 가려져야 한다")
                .isNotNull();
    }
}
