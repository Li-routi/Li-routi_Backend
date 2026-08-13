package com.lirouti.domain.popup.exception.code.error;

import com.lirouti.global.apiPayload.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum PopupErrorCode implements BaseErrorCode {

    /**
     * 없는 팝업이거나 남의 팝업이다. <b>둘을 가르지 않는다</b> — 가르면 id 를 넣어 보는 것만으로
     * 남의 팝업이 존재하는지 알 수 있다.
     */
    POPUP_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "팝업을 찾을 수 없습니다.",
            "POPUP404_1"
    );

    private final HttpStatus httpStatus;
    private final String message;
    private final String code;
}
