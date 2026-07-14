package com.lirouti.domain.image.exception.code.success;

import org.springframework.http.HttpStatus;

import com.lirouti.global.apiPayload.code.BaseSuccessCode;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ImageSuccessCode implements BaseSuccessCode {

    PRESIGNED_URL_ISSUE_SUCCESS(
            HttpStatus.OK,
            "이미지 업로드 URL 발급에 성공했습니다.",
            "IMAGE200_1"
    );

    private final HttpStatus httpStatus;
    private final String message;
    private final String code;
}
