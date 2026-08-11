package com.lirouti.domain.shop.controller.docs;

import com.lirouti.domain.shop.dto.request.ShopReqDTO;
import com.lirouti.domain.shop.dto.response.ShopResDTO;
import com.lirouti.global.apiPayload.ApiResponse;
import com.lirouti.global.auth.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "MyAvatar", description = "내 아바타 API")
public interface MyAvatarControllerDocs {

    @Operation(
            summary = "내 아바타 착용 상태 조회",
            description = """
                    지금 입고 있는 아이템을 자리별로 내려준다.

                    **안 입은 자리는 실리지 않는다.** 기본 제공 아이템이 없으므로 가입 직후에는
                    **빈 목록이 정상**이다.
                    """
    )
    ApiResponse<ShopResDTO.Avatar> getMyAvatar(CustomUserDetails userDetails);

    @Operation(
            summary = "내 아바타 착용 저장",
            description = """
                    **보낸 것이 곧 전체 착장이다.** 목록에 없는 자리는 벗는다.

                    ```
                    지금       HEAD=12  FACE=47  BODY=88
                    요청       [12, 47]
                    저장 후     HEAD=12  FACE=47            ← BODY 는 벗겨진다
                    ```

                    그래서 **클라이언트는 항상 전체 착장을 보내야 한다.** 바뀐 것만 보내면
                    나머지가 벗겨진다. 대신 벗기기를 위한 별도 표현(`null`·빈 값)이 필요 없다.

                    **자리는 보내지 않는다** — 아이템이 갖고 있으므로 서버가 알아낸다.

                    **하나라도 미보유면 전체를 거절한다.** 부분 성공은 없다.

                    **판매가 종료된 아이템도 보유했다면 계속 입을 수 있다** — 판매를 내리는 것과
                    보유를 뺏는 것은 다르다.

                    - `SHOP404_1` 없는 아이템
                    - `SHOP403_1` 보유하지 않은 아이템
                    - `SHOP400_1` 같은 자리에 두 개
                    """
    )
    ApiResponse<ShopResDTO.Avatar> equip(ShopReqDTO.EquipAvatar request,
                                         CustomUserDetails userDetails);
}
