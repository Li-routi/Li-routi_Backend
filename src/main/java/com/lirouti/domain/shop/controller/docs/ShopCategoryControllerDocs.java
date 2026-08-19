package com.lirouti.domain.shop.controller.docs;

import com.lirouti.domain.shop.dto.response.ShopResDTO;
import com.lirouti.global.apiPayload.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Shop", description = "상점 API")
public interface ShopCategoryControllerDocs {

    @Operation(
            summary = "상점 탭 목록 조회",
            description = """
                    상점 상단 탭이다. **받은 순서대로 그리면 된다** — 정렬해서 내린다.

                    **탭 이름으로 분기하지 말 것.** 탭이 늘거나 줄 때 앱을 고치지 않아도
                    되게 하려고 만든 API 인데, 아는 값만 그리면 그 목적이 사라진다.

                    누른 탭에서 무엇을 가져올지는 `source` 가 정한다.

                    | `source` | 부를 것 |
                    | --- | --- |
                    | `ITEM` | `GET /api/shop/items` — `slot` 이 있으면 그 값을 함께 보낸다 |

                    지금은 `source` 가 `ITEM` 하나뿐이다. 그래도 이 값으로 분기해 둘 것 —
                    아이템이 아닌 탭이 생길 때 앱을 고치지 않아도 된다.

                    **`slot` 이 비어 있는 탭은 `전체` 하나다.** 슬롯을 빼고 아이템을 부르면 된다.

                    회원과 무관하게 같은 목록이 나간다. 보유 여부로 탭이 생기거나 사라지지
                    않는다.

                    > **캐릭터 탭은 없다.** 상점은 파는 것을 늘어놓는 자리인데 캐릭터는 돈이
                    > 아니라 업적으로 열린다 — 값이 안 붙는 칸이 섞이면, 잠긴 캐릭터를 눌렀을 때
                    > "얼마" 가 아니라 "무엇을 해야 하는가" 를 상점이 답해야 한다.
                    > 캐릭터는 `GET /api/characters` 가 맡는다.
                    """
    )
    ApiResponse<ShopResDTO.Categories> getCategories();
}
