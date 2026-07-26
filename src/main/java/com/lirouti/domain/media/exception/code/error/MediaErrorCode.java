package com.lirouti.domain.media.exception.code.error;

import org.springframework.http.HttpStatus;

import com.lirouti.global.apiPayload.code.BaseErrorCode;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum MediaErrorCode implements BaseErrorCode {

    UNSUPPORTED_CONTENT_TYPE(
            HttpStatus.BAD_REQUEST,
            "지원하지 않는 미디어 형식입니다.",
            "MEDIA400_1"
    ),
    CONTENT_TYPE_NOT_ALLOWED_FOR_PURPOSE(
            HttpStatus.BAD_REQUEST,
            "해당 용도에서 허용하지 않는 미디어 형식입니다.",
            "MEDIA400_2"
    ),
    INVALID_MEDIA_KEY(
            HttpStatus.BAD_REQUEST,
            "올바르지 않은 미디어 key입니다.",
            "MEDIA400_3"
    ),
    FILE_TOO_LARGE(
            HttpStatus.CONTENT_TOO_LARGE,
            "업로드 가능한 최대 용량을 초과했습니다.",
            "MEDIA413_1"
    ),
    PRESIGNED_URL_ISSUE_FAILED(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "미디어 업로드 URL 발급에 실패했습니다.",
            "MEDIA500_1"
    );

    private final HttpStatus httpStatus;
    private final String message;
    private final String code;
}
