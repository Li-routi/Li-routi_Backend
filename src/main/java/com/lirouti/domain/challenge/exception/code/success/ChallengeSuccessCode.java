package com.lirouti.domain.challenge.exception.code.success;

import org.springframework.http.HttpStatus;

import com.lirouti.global.apiPayload.code.BaseSuccessCode;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ChallengeSuccessCode implements BaseSuccessCode {

    CHALLENGE_LIST_FETCH_SUCCESS(
            HttpStatus.OK,
            "챌린지 목록 조회에 성공했습니다.",
            "CHALLENGE200_1"
    ),
    CHALLENGE_DETAIL_FETCH_SUCCESS(
            HttpStatus.OK,
            "챌린지 상세 조회에 성공했습니다.",
            "CHALLENGE200_2"
    );

    private final HttpStatus httpStatus;
    private final String message;
    private final String code;
}
