package com.lirouti.domain.mypage.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class SuggestionReqDTO {

    private SuggestionReqDTO() {
    }

    @Schema(name = "CreateSuggestion", description = "건의 등록")
    public record Create(
            @NotNull(message = "분류는 필수입니다.")
            @Schema(description = """
                    분류 id. **목록 조회로 받은 것만 보낼 수 있다** — 내려간 분류를 보내면 거절된다.""")
            Long categoryId,

            @NotBlank(message = "제목은 필수입니다.")
            @Size(max = 100, message = "제목은 100자를 넘을 수 없습니다.")
            @Schema(description = "제목. 목록과 검색이 이 값을 쓴다", example = "루틴 알림 시간을 고르게 해주세요")
            String title,

            @NotBlank(message = "건의 내용은 필수입니다.")
            @Size(max = 2000, message = "건의 내용은 2000자를 넘을 수 없습니다.")
            @Schema(description = "본문")
            String content
    ) {
    }
}
