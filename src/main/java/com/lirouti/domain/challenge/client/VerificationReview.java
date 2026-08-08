package com.lirouti.domain.challenge.client;

/**
 * 인증 사진 AI 심사 결과.
 *
 * @param outcome   심사가 어떻게 끝났는지. 이 값 하나로 후속 처리가 갈린다
 * @param rejection 반려 사유의 종류. {@link ReviewOutcome#REJECTED} 가 아니면 null
 * @param reason    사유 한 문장. 로그에만 남는다 — 응답 메시지는 에러 코드가 만든다
 */
public record VerificationReview(
        ReviewOutcome outcome,
        ReviewRejection rejection,
        String reason
) {

    public static VerificationReview pass() {
        return new VerificationReview(ReviewOutcome.APPROVED, null, null);
    }

    public static VerificationReview reject(ReviewRejection rejection, String reason) {
        return new VerificationReview(ReviewOutcome.REJECTED, rejection, reason);
    }

    /**
     * 심사기가 답을 주지 못했다. 장애·타임아웃·응답 형식 이상이 여기에 해당한다.
     *
     * <p><b>모델이 안전상 응답을 거부한 것은 여기 해당하지 않는다.</b> 그건 "답을 못 준 것"이
     * 아니라 "위험해서 답하지 않기로 한 것"이라, 통과시키면 가장 걸러야 할 사진이 가장 확실하게
     * 통과한다. 그 경우는 {@link #reject}({@link ReviewRejection#UNSAFE}) 로 다룬다.
     */
    public static VerificationReview transientFailure(String reason) {
        return new VerificationReview(ReviewOutcome.TRANSIENT_FAILURE, null, reason);
    }

    /** 심사를 일부러 꺼 둔 상태. 장애가 아니므로 통과시킨다. */
    public static VerificationReview disabled() {
        return new VerificationReview(ReviewOutcome.DISABLED, null, "심사가 꺼져 있음");
    }

    /** 심사할 수가 없다. 다시 해도 같으므로 통과시킨다. */
    public static VerificationReview notApplicable(String reason) {
        return new VerificationReview(ReviewOutcome.NOT_APPLICABLE, null, reason);
    }

    public boolean approved() {
        return outcome == ReviewOutcome.APPROVED;
    }

    public boolean rejected() {
        return outcome == ReviewOutcome.REJECTED;
    }

    /** 보류해야 하는가. 판정이 달라질 수 있는 실패에만 붙는다. */
    public boolean shouldHold() {
        return outcome.shouldHold();
    }
}
