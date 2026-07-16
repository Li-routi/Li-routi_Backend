package com.lirouti.domain.challenge.enums;

/**
 * 챌린지 분류. 화면 필터 칩의 "전체"(필터 없음)와 "인기"(참여자 수 정렬)는 분류가 아니므로
 * 여기에 넣지 않는다. 실제 분류는 아래 5개뿐이다.
 */
public enum ChallengeCategory {
    HEALTH,
    EXERCISE,
    STUDY,
    LIFE,
    HOBBY
}
