package com.lirouti.domain.verification.dto.response;

import com.lirouti.domain.verification.enums.ReviewStatus;
import com.lirouti.domain.verification.enums.VerificationSourceType;
import lombok.Builder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class MyVerificationResDTO {
    private MyVerificationResDTO() {}

    @Builder
    public record DailyFeed(
            LocalDate date,
            List<Item> verifications
    ) {}

    @Builder
    public record Item(
            Long verificationId,
            VerificationSourceType sourceType,
            String categoryName,
            String title,
            String content,
            String imageUrl,
            LocalDateTime verifiedAt,
            /**
             * 심사 상태. <b>챌린지 인증에만 값이 있다</b> — 심사가 붙는 유일한 용도이기 때문이다.
             * 루틴 인증은 {@code null} 이다.
             *
             * <p>{@code PENDING} 이면 아직 공개되지 않았고, {@code imageUrl} 은 공개 주소가
             * 아니라 한시적 서명 주소다.
             */
            ReviewStatus reviewStatus
    ) {}
}
