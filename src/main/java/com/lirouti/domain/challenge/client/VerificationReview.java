package com.lirouti.domain.challenge.client;

/**
 * 인증 사진 AI 심사 결과.
 *
 * @param decided 심사가 실제로 이뤄졌는지. false 면 아래 approved 는 의미가 없다 —
 *                심사기가 답을 못 준 것이지 "부적절하다"고 판단한 것이 아니다.
 *                이 둘을 한 boolean 으로 합치면 장애와 반려가 구분되지 않는다.
 * @param approved 심사가 이뤄졌을 때의 통과 여부
 * @param reason   반려 사유. 사용자에게 보여줄 수 있는 한 문장
 */
public record VerificationReview(
        boolean decided,
        boolean approved,
        String reason
) {
    private static final String NOT_DECIDED = "심사하지 않음";

    public static VerificationReview pass() {
        return new VerificationReview(true, true, null);
    }

    public static VerificationReview reject(String reason) {
        return new VerificationReview(true, false, reason);
    }

    /**
     * 심사기가 답을 주지 못했다. 장애·타임아웃·설정 꺼짐이 여기에 해당한다.
     *
     * 호출부는 이 경우 <b>통과시킨다</b>(fail-open). 외부 API 하나가 인증 기능 전체를
     * 멈추게 두지 않는다는 결정이다. 대신 그 사실이 로그에 남아야 한다.
     */
    public static VerificationReview undecided() {
        return new VerificationReview(false, false, NOT_DECIDED);
    }
}
