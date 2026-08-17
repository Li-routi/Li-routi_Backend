package com.lirouti.domain.verification.entity;

import com.lirouti.domain.verification.enums.ReviewStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 심사 상태와 보류 시각이 <b>서로 어긋난 채로 만들어지지 않는지</b> 본다.
 *
 * <p>어긋나면 조용히 깨진다 — 보류인데 시작 시각이 없으면 상한 조회
 * {@code (review_status, pending_since)} 에 안 잡혀 무기한 보류가 된다. 사진은 대기 prefix 에
 * 있다가 수명 주기가 가져가고, 사용자 화면에는 "심사 중" 만 영영 남는다.
 */
@DisplayName("인증 심사 상태 불변식")
class ChallengeVerificationReviewStatusTest {

    private static ChallengeVerification.ChallengeVerificationBuilder base() {
        return ChallengeVerification.builder()
                .participationRound(1)
                .verifiedDate(LocalDate.of(2026, 8, 8))
                .periodStartDate(LocalDate.of(2026, 8, 8))
                .verifiedAt(LocalDateTime.of(2026, 8, 8, 10, 0))
                .imageUrl("challenge-verifications/2026/08/08/x.jpg");
    }

    @Test
    @DisplayName("상태를 안 주면 정상 인증이다 — 기본이 보류면 빠뜨렸을 때 사진이 조용히 안 보인다")
    void build_WithoutStatus_IsApproved() {
        // when
        ChallengeVerification v = base().build();

        // then
        assertThat(v.getReviewStatus()).isEqualTo(ReviewStatus.APPROVED);
        assertThat(v.getPendingSince()).isNull();
        assertThat(v.getReviewAttempts()).isZero();
    }

    @Test
    @DisplayName("보류인데 시작 시각이 없으면 만들 수 없다 — 상한 조회에 안 잡혀 무기한 보류가 된다")
    void build_PendingWithoutSince_Throws() {
        // when & then
        assertThatThrownBy(() -> base().reviewStatus(ReviewStatus.PENDING).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pendingSince");
    }

    @Test
    @DisplayName("보류면 시작 시각이 그대로 남는다")
    void build_Pending_KeepsPendingSince() {
        // given
        LocalDateTime since = LocalDateTime.of(2026, 8, 8, 10, 0, 5);

        // when
        ChallengeVerification v = base()
                .reviewStatus(ReviewStatus.PENDING)
                .pendingSince(since)
                .build();

        // then
        assertThat(v.getPendingSince()).isEqualTo(since);
    }

    @Test
    @DisplayName("보류가 아니면 시작 시각을 남기지 않는다 — 지난 흔적과 현재 보류가 섞이면 안 된다")
    void build_Approved_DropsPendingSince() {
        // when
        ChallengeVerification v = base()
                .reviewStatus(ReviewStatus.APPROVED)
                .pendingSince(LocalDateTime.of(2026, 8, 8, 10, 0, 5))
                .build();

        // then
        assertThat(v.getPendingSince()).isNull();
    }
}
