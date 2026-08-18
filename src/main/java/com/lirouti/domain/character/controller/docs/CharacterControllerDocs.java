package com.lirouti.domain.character.controller.docs;

import com.lirouti.domain.character.dto.request.CharacterReqDTO;
import com.lirouti.domain.character.dto.response.CharacterResDTO;
import com.lirouti.global.apiPayload.ApiResponse;
import com.lirouti.global.auth.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Character", description = "캐릭터 API")
public interface CharacterControllerDocs {

    @Operation(
            summary = "캐릭터 목록 조회",
            description = """
                    도감 화면이다. 노출 순서대로 내려간다.

                    **알과 성체 중 무엇을 그릴지 앱이 고르지 않는다.** `imageUrl` 이 이미
                    골라진 그림이다 — 안 연 캐릭터는 알, 연 캐릭터는 성체가 들어 있다.
                    `unlocked` 는 잠금 표시(자물쇠·흐리게)에만 쓴다.

                    **가입 직후에도 하나는 열려 있다.** 조건이 없는 기본 캐릭터가 가입 시점에
                    들어오기 때문이다.
                    """
    )
    ApiResponse<CharacterResDTO.Characters> getCharacters(CustomUserDetails userDetails);

    @Operation(
            summary = "쓸 캐릭터 선택",
            description = """
                    아바타에 쓸 캐릭터를 바꾼다. **보유한 것만 고를 수 있다** — 알 상태를
                    고르면 거절된다.

                    같은 것을 다시 골라도 성공이다. 화면에서 두 번 누르는 것이 오류일 이유가
                    없다.

                    - `CHARACTER404_1` 없는 캐릭터이거나 아직 안 연 캐릭터
                    """
    )
    ApiResponse<Void> select(CharacterReqDTO.Select request, CustomUserDetails userDetails);
}
