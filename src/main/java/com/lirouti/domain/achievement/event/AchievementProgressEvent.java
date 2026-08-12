package com.lirouti.domain.achievement.event;

import java.time.LocalDateTime;

/**
 * 회원의 행동(루틴 완료, 좋아요, 쿡쿡 등)이 일어났을 때 발행되는 이벤트.
 *
 * <p>루틴/좋아요/쿡쿡 등 각 행동 도메인이 이 이벤트를 발행하고, achievement 도메인은
 * 이를 구독해 진행도를 올린다. 행동 도메인은 achievement 내부 구현을 몰라도 되고,
 * achievement 도메인도 행동 도메인 내부를 몰라도 된다 — 서로 이 이벤트 타입만 안다.
 *
 * <p>{@code sourceType}·{@code sourceId} 는 멱등성 판별의 근거다. 같은 행동(예: 같은
 * 루틴 체크 로그 1건)에 대해 이벤트가 재발행되더라도, achievement 쪽에서
 * {@code (sourceType, sourceId, conditionKey)} 조합으로 이미 반영했는지 확인해 중복
 * 카운트를 막는다. API 재시도, 메시지 재전송 등으로 이벤트가 중복 발행될 수 있다는
 * 전제 하에 반드시 채워서 보내야 한다.
 */
public record AchievementProgressEvent(
        Long memberId,

        /** 예: ROUTINE_COMPLETE_COUNT, LIKE_COUNT, POKE_COUNT, ROOM_JOIN_COUNT. */
        String conditionKey,

        /** 이번 이벤트로 올릴 양. 대부분 1이지만, 배치 처리 등을 고려해 값을 받는다. */
        int amount,

        /** 이 이벤트를 발생시킨 원본 도메인 식별자. 예: "ROUTINE_CHECK", "LIKE". */
        String sourceType,

        /** 원본 레코드의 PK. 예: 루틴 체크 로그 ID. 중복 반영 방지의 유일 키로 쓰인다. */
        Long sourceId,

        /**
         * 루틴 완료 이벤트일 때만 채운다 — 완료된 루틴이 속한
         * {@link com.lirouti.domain.routine.entity.RoutineCategory} id (고정 카테고리
         * 기준, 1=운동 ~ 6=취미). 루틴과 무관한 이벤트(좋아요, 쿡쿡 등)는 null.
         *
         * <p>{@code Achievement.routineCategoryId} 가 채워진 업적(카테고리 시작 업적)은
         * 이 값이 일치할 때만 반영된다 — null 이면 매칭 자체가 안 돼 아무 업적도
         * 잘못 달성 처리되지 않는다.
         */
        Long routineCategoryId,

        /**
         * 원본 행동이 실제로 일어난 시각(KST 기준). {@code DISTINCT_DAY_COUNT} 계열
         * progressType(작심삼일 탈출, 배움이 차곡차곡 등)이 "서로 다른 날짜"를 판별하는
         * 근거로 쓴다.
         *
         * <p>이 필드를 받지 않는 기존 생성자를 호출하면 이벤트 처리 시점의 {@code now()}로
         * 채워진다 — 대부분의 이벤트가 발행과 거의 동시에 처리되므로 실무상 문제는 없지만,
         * 자정 부근에 발행-처리 사이 시차가 있으면 날짜가 하루 어긋날 수 있다. 날짜 경계에
         * 민감한 이벤트(루틴 완료 등)를 발행하는 쪽은 가능하면 7개 인자 생성자로 실제 발생
         * 시각을 명시적으로 넘기는 걸 권장한다.
         */
        LocalDateTime occurredAt
) {
    /** 루틴 카테고리와 발생 시각까지 명시하는 생성자. 날짜 경계에 민감한 이벤트는 이걸 쓴다. */
    public AchievementProgressEvent(Long memberId, String conditionKey, int amount,
                                    String sourceType, Long sourceId, Long routineCategoryId,
                                    LocalDateTime occurredAt) {
        this.memberId = memberId;
        this.conditionKey = conditionKey;
        this.amount = amount;
        this.sourceType = sourceType;
        this.sourceId = sourceId;
        this.routineCategoryId = routineCategoryId;
        this.occurredAt = occurredAt;
    }

    /** 기존 호출부 호환용. 발생 시각은 처리 시점(now)으로 채워진다. */
    public AchievementProgressEvent(Long memberId, String conditionKey, int amount,
                                    String sourceType, Long sourceId, Long routineCategoryId) {
        this(memberId, conditionKey, amount, sourceType, sourceId, routineCategoryId, LocalDateTime.now());
    }

    /** 루틴 카테고리와 무관한 이벤트(좋아요, 쿡쿡, 방 생성 등)용 편의 생성자. */
    public AchievementProgressEvent(Long memberId, String conditionKey, int amount,
                                    String sourceType, Long sourceId) {
        this(memberId, conditionKey, amount, sourceType, sourceId, null, LocalDateTime.now());
    }
}
