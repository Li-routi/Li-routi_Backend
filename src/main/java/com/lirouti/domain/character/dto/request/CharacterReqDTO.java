package com.lirouti.domain.character.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public final class CharacterReqDTO {

    private CharacterReqDTO() {
    }

    @Schema(name = "SelectCharacter", description = "쓸 캐릭터 선택")
    public record Select(
            @NotNull(message = "캐릭터 id 는 필수입니다.")
            @Schema(description = """
                    **보유한 캐릭터만 고를 수 있다.** 알 상태를 고르면 거절된다 — 그 규칙은
                    복합 외래 키가 지킨다.""")
            Long characterId
    ) {
    }
}
