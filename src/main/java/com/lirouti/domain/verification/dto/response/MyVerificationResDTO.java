package com.lirouti.domain.verification.dto.response;

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
            LocalDateTime verifiedAt
    ) {}
}
