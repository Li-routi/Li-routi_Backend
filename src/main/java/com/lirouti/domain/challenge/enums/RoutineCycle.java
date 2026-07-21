package com.lirouti.domain.challenge.enums;

/**
 * 챌린지 인증 주기. 프론트가 매일/매주/매월로 변환해 카드 배지로 표시한다.
 * 현재 데이터는 전부 DAILY이며, 스트릭 셈법도 DAILY 기준으로만 구현한다.
 * WEEKLY·MONTHLY 스트릭 규칙은 해당 주기의 챌린지가 도입될 때 별도로 정의한다.
 */
public enum RoutineCycle {
    DAILY,
    WEEKLY,
    MONTHLY
}
