package com.lirouti.global.apiPayload;

import com.lirouti.global.apiPayload.code.BaseErrorCode;
import com.lirouti.global.apiPayload.code.BaseSuccessCode;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ApiResponse<T> {
    private final Boolean isSuccess;
    private final String code;
    private final String message;
    private T result;

    // 실패한 경우
    public static <T> ApiResponse<T> onFailure(BaseErrorCode code, T result) {
        return new ApiResponse<>(
                false,
                code.getCode(),
                code.getMessage(),
                result);
    }

    public static <T> ApiResponse<T> onFailure(BaseErrorCode code) {
        return new ApiResponse<>(
                false,
                code.getCode(),
                code.getMessage(),
                null);
    }

    // 성공한 경우
    public static <T> ApiResponse<T> onSuccess(BaseSuccessCode code, T result) {
        return new ApiResponse<>(
                true,
                code.getCode(),
                code.getMessage(),
                result);
    }
}
