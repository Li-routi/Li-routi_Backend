package com.lirouti.domain.group.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** 그룹의 가벼운 상호작용 API 응답 모음이다. */
public final class GroupInteractionResDTO {
    private GroupInteractionResDTO() {}

    @Schema(name = "DisappointmentResult", description = "아쉬워요 등록·취소 결과")
    public record Disappointment(
            @Schema(description = "인증 게시물 ID", example = "10")
            Long verificationId,
            @Schema(description = "현재 이 게시물의 아쉬워요 총 개수", example = "2")
            long count,
            @Schema(description = "요청 회원이 현재 아쉬워요를 남긴 상태인지", example = "true")
            boolean disappointed
    ) {}

    @Schema(name = "PokeResult", description = "찌르기 결과")
    public record Poke(
            @Schema(description = "그룹 ID", example = "1")
            Long groupId,
            @Schema(description = "찌른 그룹원의 회원 ID", example = "9002")
            Long recipientId,
            @Schema(description = "찌르기 성공 여부", example = "true")
            boolean poked
    ) {}
}
