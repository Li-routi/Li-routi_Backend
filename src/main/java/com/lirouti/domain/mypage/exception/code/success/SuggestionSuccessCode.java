package com.lirouti.domain.mypage.exception.code.success;

import com.lirouti.global.apiPayload.code.BaseSuccessCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum SuggestionSuccessCode implements BaseSuccessCode {

    SUGGESTION_CATEGORY_LIST_FETCH_SUCCESS(
            HttpStatus.OK, "건의 분류 조회에 성공했습니다.", "SUGGESTION200_1"),
    SUGGESTION_LIST_FETCH_SUCCESS(
            HttpStatus.OK, "건의 목록 조회에 성공했습니다.", "SUGGESTION200_2"),
    SUGGESTION_CREATE_SUCCESS(
            HttpStatus.CREATED, "건의 등록에 성공했습니다.", "SUGGESTION201_1");

    private final HttpStatus httpStatus;
    private final String message;
    private final String code;
}
