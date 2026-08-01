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
    );

    private final HttpStatus httpStatus;
    private final String message;
    private final String code;
}
