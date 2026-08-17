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
                    | `CHARACTER` | 캐릭터 목록 |

                    **`slot` 이 비어 있는 탭이 둘이다.** `전체` 와 `캐릭터` 인데, `전체` 는
                    슬롯을 빼고 아이템을 부르는 것이고 `캐릭터` 는 아예 다른 것을 부른다.
                    그래서 `slot` 만 보면 갈리지 않는다 — `source` 를 봐야 한다.

                    회원과 무관하게 같은 목록이 나간다. 보유 여부로 탭이 생기거나 사라지지
                    않는다.

                    > **캐릭터 목록 API 는 아직 없다.** 캐릭터 도메인과 함께 붙는다. 그때까지
                    > `CHARACTER` 탭은 목록을 채울 수 없다.
                    """
    )
    ApiResponse<ShopResDTO.Categories> getCategories();
}
