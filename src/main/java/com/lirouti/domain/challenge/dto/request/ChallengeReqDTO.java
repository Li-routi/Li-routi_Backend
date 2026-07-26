package com.lirouti.domain.challenge.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class ChallengeReqDTO {
    private ChallengeReqDTO() {
    }

    /**
     * 챌린지 인증하기.
     *
     * mediaKey는 미디어 presigned URL 발급(POST /api/media/presigned-url) 응답의 mediaKey를 그대로 보낸다.
     * 전체 URL이 아니라 key다 — 읽기 URL은 서버가 조회 시점에 조립한다.
     * 발급 규칙에 맞는 key인지(용도 경로·확장자)는 형식만으로 판단할 수 없어 Service에서 검증한다.
     */
    public record Verify(
            @NotBlank(message = "인증 사진은 필수입니다.")
            @Size(max = 2048, message = "미디어 key는 2048자를 넘을 수 없습니다.")
            String mediaKey,

            @Size(max = 255, message = "인증 코멘트는 255자를 넘을 수 없습니다.")
            String content
    ) {
    }
}
