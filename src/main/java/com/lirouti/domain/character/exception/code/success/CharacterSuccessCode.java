package com.lirouti.domain.character.exception.code.success;

import com.lirouti.global.apiPayload.code.BaseSuccessCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum CharacterSuccessCode implements BaseSuccessCode {

    CHARACTER_LIST_FETCH_SUCCESS(HttpStatus.OK, "캐릭터 목록 조회에 성공했습니다.", "CHARACTER200_1"),
    CHARACTER_SELECT_SUCCESS(HttpStatus.OK, "캐릭터 선택에 성공했습니다.", "CHARACTER200_2");

    private final HttpStatus httpStatus;
    private final String message;
    private final String code;
}
