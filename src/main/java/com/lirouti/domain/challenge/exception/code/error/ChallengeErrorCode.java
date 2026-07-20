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
    );

    private final HttpStatus httpStatus;
    private final String message;
    private final String code;
}
