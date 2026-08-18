package com.lirouti.domain.character.enums;

import com.lirouti.domain.shop.enums.AvatarSlot;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 아바타를 그릴 때 겹치는 순서.
 *
 * <p>캐릭터는 <b>둥지 사이에 끼어</b> 그려진다. 아래가 먼저, 위가 나중이다.
 *
 * <p><b>간격을 10 으로 띄운다.</b> 사이에 레이어가 하나 생겨도 기존 값을 다시 매기지 않는다 —
 * 자산·앱·서버가 같은 숫자를 보고 있어 전부 옮기는 것이 제일 비싸다.
 *
 * <p><b>z 는 아이템이 아니라 자리에 붙는다.</b> 아이템마다 깊이를 정하게 두면 같은 옷끼리도
 * 앞뒤가 갈려 조합이 깨진다.
 */
@Getter
@RequiredArgsConstructor
public enum AvatarLayer {

    NEST_BACK(10, null),
    CHARACTER(20, null),
    BODY(30, AvatarSlot.BODY),
    NEST_FRONT(40, null),
    HEAD(50, AvatarSlot.HEAD),
    HAND(60, AvatarSlot.HAND);

    private final int z;

    /** 아바타 아이템이 차지하는 자리. 캐릭터·둥지는 아이템이 아니라 비어 있다. */
    private final AvatarSlot slot;

    public static AvatarLayer of(AvatarSlot slot) {
        return switch (slot) {
            case HEAD -> HEAD;
            case BODY -> BODY;
            case HAND -> HAND;
        };
    }
}
