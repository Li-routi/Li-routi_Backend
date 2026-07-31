package com.lirouti.domain.verification.dto.response;

import java.time.LocalDate;
import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

public class VerificationResDTO {

    @Schema(description = "그룹 루틴 인증 결과")
    public record GroupRoutine(
            Long verificationId,
            Long assignmentId,
            @Schema(description = "인증 사진 key. 비공개 prefix라 아직 조회 URL을 내려주지 않는다")
            String imageKey,
            String content,
            LocalDateTime verifiedAt
    ) {
    }

    @Schema(description = "개인 루틴 인증 결과")
    public record MemberRoutine(
            Long verificationId,
            Long routineId,
            @Schema(description = "인증 사진 key. 비공개 prefix라 아직 조회 URL을 내려주지 않는다")
            String imageKey,
            String content,
            LocalDate verifiedDate,
            LocalDateTime verifiedAt
    ) {
    }
}
