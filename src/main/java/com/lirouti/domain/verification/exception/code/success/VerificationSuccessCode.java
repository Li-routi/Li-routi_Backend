package com.lirouti.domain.verification.exception.code.success;

import org.springframework.http.HttpStatus;

import com.lirouti.global.apiPayload.code.BaseSuccessCode;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum VerificationSuccessCode implements BaseSuccessCode {

    GROUP_ROUTINE_VERIFY_SUCCESS(
            HttpStatus.OK,
            "그룹 루틴 인증이 완료되었습니다.",
            "VERIFICATION200_1"
    ),
    MEMBER_ROUTINE_VERIFY_SUCCESS(
            HttpStatus.OK,
            "루틴 인증이 완료되었습니다.",
            "VERIFICATION200_2"
    ),
    GROUP_ROUTINE_VERIFICATION_LIST_SUCCESS(
            HttpStatus.OK,
            "그룹 루틴 인증 목록 조회에 성공했습니다.",
            "VERIFICATION200_3"
    ),
    MY_VERIFICATION_DAILY_FETCH_SUCCESS(
            HttpStatus.OK,
            "내 인증 목록 조회에 성공했습니다.",
                    "VERIFICATION200_4"
    ),
    GROUP_ROUTINE_VERIFICATION_LIKE_SUCCESS(
            HttpStatus.OK,
            "그룹 루틴 인증 게시물 좋아요가 반영되었습니다.",
            "VERIFICATION200_5"
    ),
    GROUP_ROUTINE_VERIFICATION_UNLIKE_SUCCESS(
            HttpStatus.OK,
            "그룹 루틴 인증 게시물 좋아요가 취소되었습니다.",
            "VERIFICATION200_6"
    );

    private final HttpStatus httpStatus;
    private final String message;
    private final String code;
}
