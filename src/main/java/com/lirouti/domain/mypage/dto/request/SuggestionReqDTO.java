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

            @NotBlank(message = "건의 내용은 필수입니다.")
            @Size(max = 2000, message = "건의 내용은 2000자를 넘을 수 없습니다.")
            @Schema(description = "본문. 제목은 받지 않는다 — 분류가 그 자리를 대신한다")
            String content
    ) {
    }
}
