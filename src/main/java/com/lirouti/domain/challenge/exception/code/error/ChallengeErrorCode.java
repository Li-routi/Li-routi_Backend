package com.lirouti.domain.challenge.exception.code.error;

import org.springframework.http.HttpStatus;

import com.lirouti.global.apiPayload.code.BaseErrorCode;

import lombok.AllArgsConstructor;
import lombok.Getter;

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
    );

    private final HttpStatus httpStatus;
    private final String message;
    private final String code;
}
