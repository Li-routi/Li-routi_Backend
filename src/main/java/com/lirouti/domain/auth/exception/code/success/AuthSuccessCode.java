package com.lirouti.domain.auth.exception.code.success;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

import com.lirouti.global.apiPayload.code.BaseSuccessCode;

@Getter
@AllArgsConstructor
public enum AuthSuccessCode implements BaseSuccessCode {
    SIGNUP_SUCCESS(
        HttpStatus.CREATED,
        "회원가입에 성공했습니다.",
        "AUTH201_1"
    ),
    LOGIN_SUCCESS(
        HttpStatus.OK,
        "로그인에 성공했습니다.",
        "AUTH200_1"
    ),
    LOGOUT_SUCCESS(
        HttpStatus.OK,
        "로그아웃에 성공했습니다.",
        "AUTH200_2"
    ),
    TOKEN_REFRESH_SUCCESS(
        HttpStatus.OK,
        "토큰 재발급에 성공했습니다.",
        "AUTH200_3"
    ),
    PASSWORD_RESET_REQUEST_SUCCESS(
        HttpStatus.OK,
        "비밀번호 재설정 메일이 발송되었습니다.",
        "AUTH200_4"
    ),
    PASSWORD_RESET_SUCCESS(
        HttpStatus.OK,
        "비밀번호가 성공적으로 재설정되었습니다.",
        "AUTH200_5"
    ),
    GOOGLE_NONCE_ISSUE_SUCCESS(
        HttpStatus.OK,
        "Google nonce 발급에 성공했습니다.",
        "AUTH200_6"
    ),
    ;

    private final HttpStatus httpStatus;
    private final String message;
    private final String code;
}
