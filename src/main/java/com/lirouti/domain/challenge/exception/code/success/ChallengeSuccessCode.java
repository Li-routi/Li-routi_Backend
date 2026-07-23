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
    ),
    MY_CHALLENGE_LIST_FETCH_SUCCESS(
            HttpStatus.OK,
            "참여 중인 챌린지 목록 조회에 성공했습니다.",
            "CHALLENGE200_3"
    ),
    CHALLENGE_PARTICIPATE_SUCCESS(
            HttpStatus.OK,
            "챌린지 참여에 성공했습니다.",
            "CHALLENGE200_4"
    ),
    CHALLENGE_LEAVE_SUCCESS(
            HttpStatus.OK,
            "챌린지 이탈에 성공했습니다.",
            "CHALLENGE200_5"
    ),
    CHALLENGE_VERIFY_SUCCESS(
            HttpStatus.OK,
            "챌린지 인증에 성공했습니다.",
            "CHALLENGE200_6"
    ),
    VERIFICATION_FEED_FETCH_SUCCESS(
            HttpStatus.OK,
            "인증 피드 조회에 성공했습니다.",
            "CHALLENGE200_7"
    );

    private final HttpStatus httpStatus;
    private final String message;
    private final String code;
}
