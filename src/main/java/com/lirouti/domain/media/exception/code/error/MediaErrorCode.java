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
    // 발급받은 key로 실제 업로드를 하지 않고 그 key를 그대로 보낸 경우.
    // 클라이언트가 업로드 단계를 건너뛰었거나 실패했는데 진행한 것이므로 404다.
    MEDIA_NOT_UPLOADED(
            HttpStatus.NOT_FOUND,
            "업로드된 파일을 찾을 수 없습니다. 업로드를 먼저 완료해 주세요.",
            "MEDIA404_1"
    ),
    // 업로드는 됐지만 실제 바이트가 선언한 형식이 아닌 경우(#22).
    // 형식·용량은 presigned URL 서명이 강제하지만 바이트 내용은 강제하지 못한다.
    MEDIA_CONTENT_MISMATCH(
            HttpStatus.UNPROCESSABLE_CONTENT,
            "업로드된 파일이 이미지 형식이 아닙니다.",
            "MEDIA422_1"
    ),
    PRESIGNED_URL_ISSUE_FAILED(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "미디어 업로드 URL 발급에 실패했습니다.",
            "MEDIA500_1"
    ),
    // S3 조회 자체가 실패한 경우(권한·네트워크·타임아웃). 사용자 잘못이 아니므로 5xx.
    MEDIA_VALIDATION_FAILED(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "업로드된 파일을 확인하지 못했습니다. 잠시 후 다시 시도해 주세요.",
            "MEDIA500_2"
    );

    private final HttpStatus httpStatus;
    private final String message;
    private final String code;
}
