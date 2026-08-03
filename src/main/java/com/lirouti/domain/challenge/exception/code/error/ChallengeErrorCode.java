package com.lirouti.domain.challenge.exception.code.error;

import com.lirouti.global.apiPayload.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ChallengeErrorCode implements BaseErrorCode {

    CHALLENGE_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "챌린지를 찾을 수 없습니다.",
            "CHALLENGE404_1"
    ),
    ALREADY_PARTICIPATING(
            HttpStatus.CONFLICT,
            "이미 참여 중인 챌린지입니다.",
            "CHALLENGE409_1"
    ),
    NOT_PARTICIPATING(
            HttpStatus.CONFLICT,
            "참여 중인 챌린지가 아닙니다.",
            "CHALLENGE409_2"
    ),
    // 같은 날 인증 요청이 동시에 들어와 유니크 제약에 걸린 경우.
    // 순차적인 재인증은 덮어쓰기로 성공하므로 이 코드가 나가지 않는다.
    VERIFICATION_CONFLICT(
            HttpStatus.CONFLICT,
            "인증 처리 중 중복 요청이 감지되었습니다. 잠시 후 다시 시도해 주세요.",
            "CHALLENGE409_3"
    ),
    // AI 심사에서 챌린지 의도와 맞지 않다고 판정된 경우. 사진은 저장되지 않고 스트릭도 오르지 않는다.
    // 심사기가 답을 못 준 경우(장애·타임아웃)는 이 코드가 아니라 통과로 처리된다 — 그건 반려가 아니다.
    VERIFICATION_REJECTED_BY_REVIEW(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "챌린지 내용과 맞지 않는 사진입니다.",
            "CHALLENGE422_1"
    ),
    // 선정적·폭력적이거나 타인의 개인정보가 드러난 사진. 위와 코드를 나눈 이유는 사용자에게
    // "왜 막혔는지"를 다르게 알려야 하기 때문이다 — 다시 찍으면 되는 것과 올리면 안 되는 것은 다르다.
    VERIFICATION_REJECTED_AS_UNSAFE(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "공개 피드에 올릴 수 없는 사진입니다.",
            "CHALLENGE422_2"
    ),
    VERIFICATION_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "인증을 찾을 수 없습니다.",
            "CHALLENGE404_2"
    ),
    // 이미 신고한 인증을 다시 신고한 경우. 유니크 제약이 막는다.
    ALREADY_REPORTED(
            HttpStatus.CONFLICT,
            "이미 신고한 인증입니다.",
            "CHALLENGE409_4"
    ),
    /**
     * 나갔다 다시 들어왔는데 그날 이미 인증한 이력이 있는 경우.
     *
     * <p>하루 1회는 참여 회차를 넘어 적용된다. 회차가 올라가도 그날 인증한 사실은 남으므로,
     * 재참여로 인증 횟수를 늘릴 수 없다.
     */
    ALREADY_VERIFIED_TODAY(
            HttpStatus.CONFLICT,
            "오늘은 이미 인증했습니다.",
            "CHALLENGE409_5"
    );

    private final HttpStatus httpStatus;
    private final String message;
    private final String code;
}
