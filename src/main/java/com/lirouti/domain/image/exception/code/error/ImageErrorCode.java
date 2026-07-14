package com.lirouti.domain.image.exception.code.error;

import org.springframework.http.HttpStatus;

import com.lirouti.global.apiPayload.code.BaseErrorCode;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ImageErrorCode implements BaseErrorCode {

    UNSUPPORTED_CONTENT_TYPE(
            HttpStatus.BAD_REQUEST,
            "지원하지 않는 이미지 형식입니다. jpeg, png, webp만 업로드할 수 있습니다.",
            "IMAGE400_1"
    ),
    FILE_TOO_LARGE(
            HttpStatus.CONTENT_TOO_LARGE,
            "업로드 가능한 최대 용량을 초과했습니다.",
            "IMAGE413_1"
    ),
    PRESIGNED_URL_ISSUE_FAILED(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "이미지 업로드 URL 발급에 실패했습니다.",
            "IMAGE500_1"
    );

    private final HttpStatus httpStatus;
    private final String message;
    private final String code;
}
