package com.lirouti.domain.challenge.service.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import com.lirouti.domain.challenge.client.AnthropicVerificationReviewClient;
import com.lirouti.domain.challenge.client.VerificationReview;
import com.lirouti.domain.challenge.dto.request.ChallengeReqDTO;
import com.lirouti.domain.challenge.entity.Challenge;
import com.lirouti.domain.challenge.entity.MemberChallenge;
import com.lirouti.domain.challenge.enums.ChallengeCategory;
import com.lirouti.domain.challenge.exception.ChallengeException;
import com.lirouti.domain.challenge.exception.code.error.ChallengeErrorCode;
import com.lirouti.domain.challenge.repository.ChallengeVerificationRepository;
import com.lirouti.domain.media.service.MediaImage;
import com.lirouti.domain.media.service.MediaService;
import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.enums.Role;
import com.lirouti.domain.member.enums.SocialProvider;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * 인증 사진 AI 심사.
 *
 * <p>가장 중요한 것은 <b>심사기가 죽었을 때 인증이 막히지 않는가</b>이다(fail-open).
 * 반려 동작만 검증하면 장애 시 서비스가 멈추는 구현도 통과한다.
 *
 * <p>실제 Anthropic 호출은 하지 않는다. 외부 API 응답에 테스트가 매달리면 결정적이지 않고,
 * 여기서 보려는 것은 우리 쪽 분기이지 모델의 판단력이 아니다.
 */
@SpringBootTest
@Transactional
@DisplayName("인증 사진 AI 심사 테스트")
class ChallengeAiReviewTest {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final String KEY =
            "challenge-verifications/2026/07/31/eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee.jpg";

    @Autowired
    private ChallengeCommandService challengeCommandService;
    @Autowired
    private ChallengeVerificationRepository verificationRepository;

    @MockitoBean
    private AnthropicVerificationReviewClient reviewClient;
    @MockitoBean
    private MediaService mediaService;

    @PersistenceContext
    private EntityManager em;

    private final AtomicInteger seq = new AtomicInteger();
    private Long memberId;
    private Long challengeId;

    @BeforeEach
    void setUp() {
        int n = seq.incrementAndGet();
        Member m = Member.builder()
                .email("ai" + n + "@ex.com").nickname("ai" + n)
                .socialProvider(SocialProvider.GOOGLE).role(Role.ROLE_USER)
                .socialId("ai-sid-" + n).build();
        em.persist(m);
        Challenge c = Challenge.builder()
                .name("물 1L 마시기").description("하루에 물 1L 이상")
                .category(ChallengeCategory.HEALTH).active(true).build();
        em.persist(c);
        em.persist(MemberChallenge.builder()
                .member(m).challenge(c)
                .participationRound(1).currentStreak(0)
                .joinedAt(LocalDateTime.now()).active(true).build());
        em.flush();

        memberId = m.getId();
        challengeId = c.getId();

        // 심사 앞단(형식·바이트 검증)은 이 테스트의 관심사가 아니라 통과시킨다.
        when(mediaService.loadForReview(eq(KEY), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(Optional.of(new MediaImage(new byte[] {1, 2, 3}, "image/jpeg")));
    }

    private ChallengeReqDTO.Verify request() {
        return new ChallengeReqDTO.Verify(KEY, "오늘도 마셨어요");
    }

    private long savedCount() {
        em.flush();
        em.clear();
        return verificationRepository.count();
    }

    @Test
    @DisplayName("심사를 통과하면 인증이 저장된다")
    void verify_Approved_IsSaved() {
        // given
        when(reviewClient.review(any(), any(), any())).thenReturn(VerificationReview.pass());
        long before = savedCount();

        // when
        challengeCommandService.verify(memberId, challengeId, request());

        // then
        assertThat(savedCount()).isEqualTo(before + 1);
    }

    @Test
    @DisplayName("심사에서 반려되면 422로 막고 저장하지 않는다")
    void verify_Rejected_IsBlocked() {
        // given
        when(reviewClient.review(any(), any(), any()))
                .thenReturn(VerificationReview.reject("물이 아니라 커피로 보입니다."));
        long before = savedCount();

        // when & then
        assertThatThrownBy(() -> challengeCommandService.verify(memberId, challengeId, request()))
                .isInstanceOf(ChallengeException.class)
                .hasFieldOrPropertyWithValue("code", ChallengeErrorCode.VERIFICATION_REJECTED_BY_REVIEW);
        assertThat(savedCount()).isEqualTo(before);
    }

    @Test
    @DisplayName("심사기가 답을 못 주면 통과시킨다 — 외부 API 장애로 인증이 막히면 안 된다")
    void verify_Undecided_PassesThrough() {
        // given: 장애·타임아웃·응답 이상은 전부 undecided 로 돌아온다
        when(reviewClient.review(any(), any(), any())).thenReturn(VerificationReview.undecided());
        long before = savedCount();

        // when
        challengeCommandService.verify(memberId, challengeId, request());

        // then
        assertThat(savedCount()).isEqualTo(before + 1);
    }

    @Test
    @DisplayName("사진을 읽지 못해도 통과시킨다 — S3 장애가 인증을 막으면 안 된다")
    void verify_ImageUnavailable_PassesThrough() {
        // given
        when(mediaService.loadForReview(eq(KEY), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(Optional.empty());
        long before = savedCount();

        // when
        challengeCommandService.verify(memberId, challengeId, request());

        // then
        assertThat(savedCount()).isEqualTo(before + 1);
        verify(reviewClient, never()).review(any(), any(), any());
    }

    @Test
    @DisplayName("판정 기준으로 챌린지 이름과 설명을 넘긴다 — 카테고리로는 물과 우유를 못 가른다")
    void verify_PassesChallengeIntent() {
        // given
        when(reviewClient.review(any(), any(), any())).thenReturn(VerificationReview.pass());

        // when
        challengeCommandService.verify(memberId, challengeId, request());

        // then
        verify(reviewClient).review(eq("물 1L 마시기"), eq("하루에 물 1L 이상"), any());
    }
}
