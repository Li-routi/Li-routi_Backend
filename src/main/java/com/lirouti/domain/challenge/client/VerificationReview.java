package com.lirouti.domain.challenge.client;

/**
 * 인증 사진 AI 심사 결과.
 *
 * @param decided   심사가 실제로 이뤄졌는지. false 면 아래 값들은 의미가 없다 —
 *                  심사기가 답을 못 준 것이지 "부적절하다"고 판단한 것이 아니다.
 *                  이 둘을 한 boolean 으로 합치면 장애와 반려가 구분되지 않는다.
 * @param approved  심사가 이뤄졌을 때의 통과 여부
 * @param rejection 반려 사유의 종류. 통과면 null
 * @param reason    반려 사유. 로그에 남길 한 문장
 */
public record VerificationReview(
        boolean decided,
        boolean approved,
        ReviewRejection rejection,
        String reason
) {
    private static final String NOT_DECIDED = "심사하지 않음";

    public static VerificationReview pass() {
        return new VerificationReview(true, true, null, null);
    }

    public static VerificationReview reject(ReviewRejection rejection, String reason) {
        return new VerificationReview(true, false, rejection, reason);
    }

    /**
     * 심사기가 답을 주지 못했다. 장애·타임아웃·설정 꺼짐이 여기에 해당한다.
     *
     * <p>호출부는 이 경우 <b>통과시킨다</b>(fail-open). 외부 API 하나가 인증 기능 전체를
     * 멈추게 두지 않는다는 결정이다.
     *
     * <p><b>모델이 안전상 응답을 거부한 것은 여기 해당하지 않는다.</b> 그건 "답을 못 준 것"이
     * 아니라 "위험해서 답하지 않기로 한 것"이라, 통과시키면 가장 걸러야 할 사진이 가장 확실하게
     * 통과한다. 그 경우는 {@link #reject}({@link ReviewRejection#UNSAFE}) 로 다룬다.
     */
    public static VerificationReview undecided() {
        return new VerificationReview(false, false, null, NOT_DECIDED);
    }
}
