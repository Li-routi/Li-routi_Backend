package com.lirouti.domain.media.exception.code.success;

import com.lirouti.global.apiPayload.code.BaseSuccessCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum MediaSuccessCode implements BaseSuccessCode {

    PRESIGNED_URL_ISSUE_SUCCESS(
            HttpStatus.OK,
            "미디어 업로드 URL 발급에 성공했습니다.",
            "MEDIA200_1"
    );

    private final HttpStatus httpStatus;
    private final String message;
    private final String code;
}
