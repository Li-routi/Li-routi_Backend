package com.lirouti.domain.challenge.client;

/**
 * 반려 사유의 종류.
 *
 * 둘을 나누는 이유는 <b>판단 방향이 반대</b>이기 때문이다. 미션 불일치는 애매하면 통과시키는
 * 쪽이 맞다 — 정상 인증을 반려하는 손해가 더 크다. 반면 유해성은 애매하면 막는 쪽이 맞다.
 * 한 가지로 뭉뚱그리면 어느 한쪽 방침을 잘못 적용하게 된다.
 *
 * 로그에서 구분되는 것도 이유다. "심사가 너무 깐깐한가"를 볼 때 불일치 반려와 유해성 반려를
 * 섞어 세면 판단이 안 된다.
 */
public enum ReviewRejection {
    /** 사진이 그 챌린지가 요구하는 행동·대상과 맞지 않는다. */
    MISMATCH,

    /** 선정적·폭력적이거나 타인의 개인정보가 드러나는 등, 공개 피드에 올릴 수 없는 사진이다. */
    UNSAFE;

    /** 모델이 모르는 값을 주면 안전한 쪽으로 해석한다. */
    public static ReviewRejection from(Object raw) {
        if (raw instanceof String text) {
            for (ReviewRejection value : values()) {
                if (value.name().equalsIgnoreCase(text.trim())) {
                    return value;
                }
            }
        }
        return MISMATCH;
    }
}
