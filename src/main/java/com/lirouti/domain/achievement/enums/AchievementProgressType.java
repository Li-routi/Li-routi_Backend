package com.lirouti.domain.achievement.enums;

import com.lirouti.domain.achievement.entity.MemberAchievement;

/**
 * 진행도 계산 방식.
 *
 * <p>단일 조건 업적은 {@link MemberAchievement#getCurrentProgress()}
 * 하나로 충분하지만, {@code COMPOSITE} 는 조건이 여러 개라 각각 따로 세야 한다
 * ({@link com.lirouti.domain.achievement.entity.MemberAchievementCondition}).
 *
 * <p><b>이 enum의 값은 achievement 마스터 데이터 마이그레이션의 progress_type 컬럼과
 * 반드시 1:1로 일치해야 한다.</b> {@code Achievement.progressType} 이
 * {@code @Enumerated(EnumType.STRING)} 이라, DB에 이 enum에 없는 문자열이 들어있는
 * 행을 하나라도 조회하면 전체 업적 목록 조회(AchievementQueryService)가
 * {@code IllegalArgumentException} 으로 즉시 실패한다. 새 progress_type 값을 마이그레이션에
 * 추가할 때는 이 enum에도 같은 이름으로 상수를 먼저 추가해야 한다.
 *
 * <p>아래 중 {@code DISTINCT_DAY_COUNT}·{@code WEEKLY_DISTINCT_DAY_COUNT}·
 * {@code MONTHLY_DISTINCT_DAY_COUNT}·{@code CATEGORY_COVERAGE_COUNT}·{@code STREAK_DAYS}
 * 는 값 자체는 정의되어 있지만 {@code AchievementProgressService} 의 실제 반영 로직은
 * 아직 구현 전이다(TODO) — 조회 API가 죽는 것만 우선 막아 둔 상태이고, 실제 진행도
 * 반영 로직은 후속 작업으로 연결한다.
 */
public enum AchievementProgressType {
    /** 진행률 없음. 이벤트 발생 즉시 달성 (예: 첫 좋아요, 꽉 찬 방, 첫 루틴의 새싹). */
    NONE,
    /** 단순 누적 횟수. */
    CUMULATIVE_COUNT,
    /** 서로 다른 방 참여 수. */
    DISTINCT_ROOM_COUNT,
    /** 여러 조건을 모두 만족해야 하는 복합 조건. */
    COMPOSITE,
    /**
     * 평생 누적 - 기간 제한 없이 "서로 다른 날짜" 수.
     * 예: 작심삼일 탈출(3일), 배움이 차곡차곡·건강한 땀방울·마음에 쉼표·취미의 물결·정리하면 다미(각 20일).
     * <p>TODO: 같은 날 여러 번 이벤트가 와도 하루로만 세는 dedup 로직 필요(아직 미구현).
     */
    DISTINCT_DAY_COUNT,
    /**
     * 이번 주 안에서 서로 다른 날짜 수. 예: 일주일 루틴러(5일 이상).
     * <p>TODO: 주 경계(월~일 등) 정의 및 주가 바뀌면 카운트 리셋하는 로직 필요(아직 미구현).
     */
    WEEKLY_DISTINCT_DAY_COUNT,
    /**
     * 이번 달 안에서 서로 다른 날짜 수. 예: 한 달의 루틴러(20일 이상).
     * <p>TODO: 월 경계 정의 및 월이 바뀌면 카운트 리셋하는 로직 필요(아직 미구현).
     */
    MONTHLY_DISTINCT_DAY_COUNT,
    /**
     * 서로 다른 카테고리를 모두 커버했는지. 예: 루틴 탐험가(6개 카테고리 각 1회 이상).
     * <p>TODO: 방문한 카테고리 집합을 회원별로 추적하는 로직 필요(아직 미구현) —
     * {@code MemberAchievementCondition} 을 재사용해 conditionKey="CATEGORY_{id}" 로
     * 마킹하는 방식을 검토 중.
     */
    CATEGORY_COVERAGE_COUNT,
    /**
     * 연속 기록(스트릭) 일수 스냅샷 비교. 예: 100일의 태양, 100일 완주(둘 다 100일에서 동시 달성).
     * <p>TODO: {@code ROUTINE_COMPLETE_COUNT} 같은 누적 이벤트가 아니라
     * {@code member_routine_streak.current_streak} 갱신 시점에 반응해야 한다 — 다른 두 타입과
     * 달리 "증가량을 더하는" 게 아니라 "현재 스트릭 값을 그대로 비교"하는 방식이라 별도 설계 필요.
     */
    STREAK_DAYS,
    /**
     * 그룹(루틴방) 단위 누적 카운트. 예: 우리 방 정상영업합니다(구성원 전체 인증 합계 100회).
     * <p>이 값을 쓰는 업적은 {@code condition_key} 를 비워 두므로
     * {@code AchievementRepository.findAllActiveByConditionKey} 로 조회되지 않고, 회원 단위
     * {@code AchievementProgressEvent} 리스너({@code AchievementProgressService})에도 절대
     * 도달하지 않는다. 그룹 레벨 이벤트를 구독하는 별도 파이프라인(TODO, 미구현)에서
     * group_id 단위로 처리해야 한다.
     */
    GROUP_CUMULATIVE_COUNT,
    /**
     * 그룹(루틴방) 단위 "구성원 전체가 함께 인증한 날" 카운트. 예: 루틴 하우스 메이트(10일).
     * {@code GROUP_CUMULATIVE_COUNT} 와 동일한 이유로 이 리스너에는 도달하지 않는다(TODO, 미구현).
     */
    GROUP_DISTINCT_DAY_COUNT
}
