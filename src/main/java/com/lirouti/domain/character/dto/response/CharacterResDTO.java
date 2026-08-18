package com.lirouti.domain.character.dto.response;

import com.lirouti.domain.character.enums.AvatarLayer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.LocalDate;
import java.util.List;

public final class CharacterResDTO {

    private CharacterResDTO() {
    }

    @Schema(name = "Character", description = "캐릭터 한 마리")
    @Builder
    public record Character(
            @Schema(description = "캐릭터 id") Long id,
            @Schema(description = "논리 키", example = "ROUTI") String code,
            @Schema(description = "이름") String name,
            @Schema(description = """
                    해금했는가. **`false` 면 알 그림이 내려간다** — 조건을 채우면 성체로 바뀐다.""")
            boolean unlocked,
            @Schema(description = "지금 쓰고 있는 캐릭터인가") boolean selected,
            @Schema(description = """
                    보여줄 그림. 해금 전에는 알, 해금 뒤에는 성체다.
                    **앱이 둘을 고르지 않는다** — 서버가 이미 골라서 내린다.""")
            String imageUrl,
            @Schema(description = "해금한 날. 아직이면 비어 있다") LocalDate unlockedDate
    ) {
    }

    @Schema(name = "Characters", description = "캐릭터 목록")
    @Builder
    public record Characters(
            @Schema(description = "노출 순서대로") List<Character> characters
    ) {
    }

    @Schema(name = "AvatarLayer", description = "겹쳐 그릴 레이어 하나")
    @Builder
    public record Layer(
            @Schema(description = "자리", example = "CHARACTER") AvatarLayer layer,
            @Schema(description = "그리는 순서. 작을수록 아래다") int z,
            @Schema(description = "이미지 주소") String imageUrl
    ) {
    }
}
