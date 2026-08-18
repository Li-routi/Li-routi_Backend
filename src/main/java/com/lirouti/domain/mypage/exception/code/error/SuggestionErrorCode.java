package com.lirouti.domain.mypage.exception.code.error;

import com.lirouti.global.apiPayload.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum SuggestionErrorCode implements BaseErrorCode {

    /** 없는 분류를 보낸 경우. */
    CATEGORY_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "존재하지 않는 건의 분류입니다.",
            "SUGGESTION404_1"
    ),

    /**
     * 내려간 분류로 등록하려는 경우.
     *
     * <p><b>목록에서 감추는 것과 등록을 막는 것은 다르다.</b> 분류 id 를 아는 클라이언트는 목록을
     * 거치지 않고 바로 등록을 부를 수 있다 — 아이템의 판매 종료 확인과 같은 자리다.
     */
    CATEGORY_NOT_ACTIVE(
            HttpStatus.CONFLICT,
            "더 이상 사용할 수 없는 분류입니다.",
            "SUGGESTION409_1"
    );

    private final HttpStatus httpStatus;
    private final String message;
    private final String code;
}
