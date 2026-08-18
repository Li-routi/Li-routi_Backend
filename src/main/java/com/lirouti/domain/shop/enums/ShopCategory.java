package com.lirouti.domain.shop.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 상점 화면의 탭.
 *
 * <p><b>슬롯 목록이 아니다.</b> 탭 넷 중 셋은 아바타 슬롯과 짝이지만 "전체" 는 필터가 없는
 * 것이고 "캐릭터" 는 아이템이 아니다. 그래서 {@link AvatarSlot} 을 그대로 내려주지 않고
 * 화면의 단위를 따로 둔다.
 *
 * <p><b>{@code AvatarSlot.values()} 로 만들지 않는다.</b> 만들면 슬롯을 더하거나 뺄 때
 * 탭이 조용히 따라 움직인다. 무엇을 파는가와 무엇을 보여주는가는 같은 속도로 바뀌지 않고,
 * 실제로 얼굴 슬롯을 걷어낼 때 탭이 먼저 사라져 앱과 배포 순서를 맞출 필요가 없었다.
 *
 * <p><b>이름을 서버가 내려준다.</b> 이 프로젝트는 화면 문구를 클라이언트가 붙이는 것을
 * 기본으로 하지만(재화 이름·스트릭 문구), 탭은 성격이 다르다 — 이 API 의 존재 이유가
 * "탭이 늘거나 줄 때 앱 배포 없이 따라오게" 하는 것인데 문구를 앱이 들고 있으면 새 탭에
 * 이름이 안 붙어 목적이 반만 이뤄진다. 재화는 값이 둘로 고정이라 그 문제가 없다.
 */
@Getter
@RequiredArgsConstructor
public enum ShopCategory {

    /** 필터 없음. 슬롯을 생략한 아이템 목록이 그대로 이 탭이다. */
    ALL(null, ShopCategorySource.ITEM, "전체", 1),

    HEAD(AvatarSlot.HEAD, ShopCategorySource.ITEM, "머리", 2),
    BODY(AvatarSlot.BODY, ShopCategorySource.ITEM, "옷", 3),
    HAND(AvatarSlot.HAND, ShopCategorySource.ITEM, "소품", 4),

    /**
     * 캐릭터. <b>파는 것이 아니라 조건으로 열리는 것</b>이라 아이템 목록에 섞이지 않는다.
     * 슬롯도 없다 — 캐릭터는 아이템이 앉는 자리가 아니라 아이템을 입는 몸이다.
     */
    CHARACTER(null, ShopCategorySource.CHARACTER, "캐릭터", 5);

    /**
     * 아이템 목록을 요청할 때 넣을 슬롯. {@code null} 이면 슬롯을 넣지 않는다.
     *
     * <p>"전체" 와 "캐릭터" 가 둘 다 {@code null} 이라 이 값만으로는 갈리지 않는다.
     * 무엇을 가져올지는 {@link #source} 가 정한다.
     */
    private final AvatarSlot slot;

    private final ShopCategorySource source;

    private final String name;

    private final int sortOrder;
}
