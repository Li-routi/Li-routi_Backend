package com.lirouti.domain.shop.controller.docs;

import com.lirouti.domain.shop.dto.request.ShopReqDTO;
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
                    고른 아이템을 **한 번에 산다.** 아이템 id 를 여러 개 보내면 된다.

                    **재화가 섞여도 된다.** 파란 보석 아이템과 주황 보석 아이템을 함께 담으면
                    재화별로 합계를 내어 각각 차감하고, 응답의 `payments` 에 재화마다 한 줄로
                    내려간다. 한 재화로 통일해야만 살 수 있는 제약은 없다.

                    **전부 되거나 전부 안 되거나.** 하나라도 막히면 아무것도 사지 않는다.

                    가격과 결제 재화는 받지 않는다 — **서버가 갖고 있다.** 클라이언트가 보낸
                    값을 믿으면 조작된다.

                    `idempotencyKey` 는 **클라이언트가 만든다.** 재시도는 같은 키로 보내면
                    두 번 결제되지 않고, 새 구매는 새 키로 보낸다. 아이템 id 로 만들면 같은
                    묶음을 다시 사는 정상 요청이 조용히 건너뛰어진다.

                    **같은 키로 같은 장바구니를 다시 보내면 처음 결과가 그대로 나간다**
                    (200). 응답을 못 받아 다시 보내는 경우라, 이미 가졌다는 오류가 아니라
                    성공이 나가야 한다.

                    **장바구니를 바꿨으면 반드시 새 키를 만들어야 한다.** 같은 키에 다른
                    아이템을 담아 보내면 `SHOP409_4` 로 거절한다 — 그대로 진행하면 보유만
                    생기고 값이 빠지지 않아 아이템이 공짜가 된다.

                    **입히지 않는다.** 같은 자리 아이템을 둘 이상 함께 사면 어느 쪽을 입힐지
                    정할 수 없기 때문이다. 착용은 `PUT /api/members/me/avatar` 가 맡는다.

                    실패 응답은 **`result` 에 본문을 함께 싣는다** — 화면이 무엇을 고쳐야
                    하는지 알 수 있어야 하기 때문이다.

                    - `SHOP400_2` 같은 아이템을 두 번 담음 → `result.itemIds`
                    - `SHOP404_1` 없는 아이템 → `result.itemIds`
                    - `SHOP409_1` 판매가 종료된 아이템 → `result.itemIds`
                    - `SHOP409_2` 이미 보유한 아이템 → `result.itemIds`
                    - `SHOP409_3` 재화 부족 → `result.shortages` 에 **모자란 재화를 전부**
                      담는다(`required`·`balance`·`shortfall`). 하나씩 알려주면 충전하고
                      돌아왔을 때 다른 재화로 또 막히기 때문이다

                    아래 둘은 **`result` 가 비어 있다.** 고칠 대상이 특정 아이템이 아니라
                    요청 자체이기 때문이다.

                    - `SHOP400_3` 빈 장바구니
                    - `SHOP409_4` **이미 쓴 키를 다른 장바구니로 보냄** → 새 키를 만들어
                      다시 보낸다. 같은 키로 다시 보내도 계속 거절된다
                    """
    )
    ApiResponse<ShopResDTO.PurchaseResult> purchase(ShopReqDTO.Purchase request,
                                                    CustomUserDetails userDetails);
}
