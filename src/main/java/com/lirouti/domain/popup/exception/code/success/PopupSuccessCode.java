package com.lirouti.domain.popup.exception.code.success;

import com.lirouti.global.apiPayload.code.BaseSuccessCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum PopupSuccessCode implements BaseSuccessCode {

    POPUP_PENDING_FETCH_SUCCESS(
            HttpStatus.OK,
            "보여줄 팝업 조회에 성공했습니다.",
            "POPUP200_1"
    ),
    POPUP_ACK_SUCCESS(
            HttpStatus.OK,
            "팝업 확인 처리에 성공했습니다.",
            "POPUP200_2"
    );

    private final HttpStatus httpStatus;
    private final String message;
    private final String code;
}
