package com.lirouti.domain.challenge.enums;

/**
 * 챌린지 분류. 화면 필터 칩의 "전체"(필터 없음)는 분류가 아니므로 여기에 넣지 않는다.
 * 목록은 최신순 고정이라 정렬 칩은 없다.
 *
 * <p><b>개인·그룹 루틴의 전역 프리셋 카테고리 6종과 같은 목록이다</b>(건강·마음관리·생활정리·
 * 운동·자기계발·취미). 셋이 서로 다른 표에 살아 id 는 다르지만, 뜻은 같은 하나로 다룬다 —
 * 다르면 "운동 카테고리를 며칠 했나" 같은 집계가 어디를 세느냐에 따라 달라진다.
 *
 * <p>DB 컬럼이 실제 {@code ENUM} 이라 값을 늘리면 마이그레이션이 함께 필요하다.
 */
public enum ChallengeCategory {
    HEALTH,
    EXERCISE,
    STUDY,
    LIFE,
    HOBBY,
    MIND
}
