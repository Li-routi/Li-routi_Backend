package com.lirouti.domain.shop.controller.docs;

import com.lirouti.domain.shop.dto.response.ShopResDTO;
import com.lirouti.domain.shop.enums.AvatarSlot;
import com.lirouti.global.apiPayload.ApiResponse;
import com.lirouti.global.auth.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Shop", description = "상점 API")
public interface ShopControllerDocs {

    @Operation(
            summary = "아바타 아이템 목록 조회",
            description = """
                    상점 격자에 뿌릴 아이템 목록이다. **보유한 것도 같은 목록에 섞여** 나오고,
                    `owned` 로 가른다. 화면이 격자 하나이기 때문이다.

                    `slot` 을 생략하면 전체다 — 화면의 **"전체" 탭이 곧 필터 없음**이다.

                    **판매가 내려간 아이템도 보유했으면 실린다**(`onSale: false`). 빠지면 가진
                    것을 화면에서 고를 수 없고, 착용 저장이 착장 전체를 받으므로 목록에 없다는
                    이유로 조용히 벗겨진다.
                    """
    )
    ApiResponse<ShopResDTO.Items> getItems(AvatarSlot slot, boolean ownedOnly,
                                           CustomUserDetails userDetails);

    @Operation(
            summary = "아바타 아이템 구매",
            description = """
                    아이템을 사고 **그 자리에 바로 입힌다.** 응답은 구매 후의 착용 상태다.

                    요청 본문은 없다 — **가격과 결제 재화는 서버가 갖고 있다.** 클라이언트가
                    보낸 값을 믿으면 조작된다.

                    차감·보유·착용은 **한 트랜잭션**이다. 잔액이 모자라면 아무것도 일어나지
                    않는다.

                    착용은 **그 아이템의 자리만** 바꾼다. 손에 든 것을 샀다고 모자가 벗겨지지
                    않는다.

                    - `SHOP404_1` 없는 아이템
                    - `SHOP409_1` 판매가 종료된 아이템
                    - `SHOP409_2` 이미 보유한 아이템
                    - `WALLET409_*` 잔액 부족
                    """
    )
    ApiResponse<ShopResDTO.Avatar> purchase(Long itemId, CustomUserDetails userDetails);
}
