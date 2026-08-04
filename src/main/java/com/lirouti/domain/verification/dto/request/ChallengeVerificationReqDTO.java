package com.lirouti.domain.verification.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class ChallengeVerificationReqDTO {
    private ChallengeVerificationReqDTO() {
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

    /**
     * 인증 메모 수정하기.
     *
     * <p>메모만 바꾼다. 사진을 바꾸려면 그날 다시 인증한다(Verify) — 사진은 수행의 증거라
     * 교체 시 심사를 다시 거쳐야 하고, 그래서 경로가 다르다.
     *
     * <p>{@code content} 를 비우거나 보내지 않으면 <b>메모를 지운다.</b> 처음 인증할 때도
     * 선택 값이라 나중에 지우지 못할 이유가 없다. 필드가 하나뿐이라 "안 보냄"과 "비움"을
     * 구분하지 않는다 — 구분하려면 {@code Optional} 래핑이 필요한데 얻는 것이 없다.
     */
    public record UpdateMemo(
            @Size(max = 255, message = "인증 코멘트는 255자를 넘을 수 없습니다.")
            String content
    ) {
    }

    /**
     * 인증 신고하기.
     *
     * reason은 선택이다. 화면의 더보기 메뉴에서 사유 선택 없이 바로 신고할 수 있어야 하므로
     * 필수로 두지 않는다(database-schema.md의 reason NULL 허용과 짝을 이룬다).
     */
    public record Report(
            @Size(max = 255, message = "신고 사유는 255자를 넘을 수 없습니다.")
            String reason
    ) {
    }
}
