package com.lirouti.domain.verification.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class VerificationReqDTO {

    @Schema(name = "RoutineVerifyRequest", description = "루틴 인증 요청")
    public record Verify(
            @Schema(
                    description = "presigned URL 발급 응답으로 받은 mediaKey. 전체 URL이 아니라 key다",
                    example = "member-routine-verifications/2026/07/31/aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa.jpg"
            )
            @NotBlank(message = "인증 사진은 필수입니다.")
            @Size(max = 2048, message = "미디어 key는 2048자를 넘을 수 없습니다.")
            String mediaKey,

            @Schema(description = "인증 코멘트. 선택", example = "오늘도 완료")
            @Size(max = 255, message = "인증 코멘트는 255자를 넘을 수 없습니다.")
            String content
    ) {
    }

    @Schema(name = "GroupRoutineVerificationReadRequest", description = "그룹 루틴 인증 읽음 처리 요청")
    public record MarkRead(
            @NotNull(message = "마지막으로 확인한 인증 ID는 필수입니다.")
            Long lastReadVerificationId
    ) {
    }
}
