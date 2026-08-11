package com.lirouti.domain.achievement.event;

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
        Long sourceId
) {
}
