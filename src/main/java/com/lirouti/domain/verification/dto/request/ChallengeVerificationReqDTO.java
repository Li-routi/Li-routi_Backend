package com.lirouti.domain.verification.dto.request;

import com.lirouti.domain.verification.enums.ReportType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

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
     * <p><b>사유 선택({@code reportType})은 필수다.</b> 예전에는 자유 입력 하나뿐이라 신고를
     * 모아 봐도 무엇 때문인지 알 수 없었다 — 대부분 비어 있었고, 채워져 있어도 자유 문장이라
     * 셀 수가 없었다.
     *
     * <p>{@code reason}(직접 입력)은 <b>{@link ReportType#ETC} 일 때만 필수다.</b> 나머지 넷은
     * 화면에 입력란이 없다.
     */
    @Schema(name = "ChallengeVerificationReportRequest", description = "챌린지 인증 신고 요청")
    public record Report(
            @Schema(description = "신고 사유 종류", example = "IRRELEVANT",
                    allowableValues = {"IRRELEVANT", "REUSED", "STOLEN", "SPAM", "ETC"})
            @NotNull(message = "신고 사유를 선택해 주세요.")
            ReportType reportType,

            @Schema(description = "직접 입력한 사유. reportType 이 ETC 일 때만 보냅니다", example = "광고 링크가 적혀 있어요")
            @Size(max = 100, message = "직접 입력한 사유는 100자를 넘을 수 없습니다.")
            String reason
    ) {
        /**
         * {@code ETC} 면 직접 입력이 있어야 한다.
         *
         * <p>입력값만으로 판단되는 규칙이라 DTO 에 둔다(dto_convention: null 여부·길이·형식은
         * 요청 DTO 에서 검증한다). 서비스로 내리면 같은 판단이 두 계층에 흩어진다.
         *
         * <p>{@code reportType} 이 null 인 경우는 {@code true} 를 돌려준다 — 그건
         * {@code @NotNull} 이 이미 잡는다. 여기서 또 잡으면 오류 메시지가 두 개 나간다.
         */
        @AssertTrue(message = "기타를 선택하면 사유를 직접 입력해 주세요.")
        public boolean isReasonPresentWhenEtc() {
            if (reportType != ReportType.ETC) {
                return true;
            }
            return reason != null && !reason.isBlank();
        }
    }
}
