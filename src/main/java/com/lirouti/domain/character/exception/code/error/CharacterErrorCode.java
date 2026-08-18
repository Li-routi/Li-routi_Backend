package com.lirouti.domain.character.exception.code.error;

import com.lirouti.global.apiPayload.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum CharacterErrorCode implements BaseErrorCode {

    /**
     * 없는 캐릭터이거나 아직 해금하지 않은 캐릭터다. <b>둘을 가르지 않는다</b> — 가르면 id 를
     * 넣어 보는 것만으로 어떤 캐릭터가 존재하는지 알 수 있다.
     */
    CHARACTER_NOT_OWNED(
            HttpStatus.NOT_FOUND,
            "보유하지 않은 캐릭터입니다.",
            "CHARACTER404_1"
    );

    private final HttpStatus httpStatus;
    private final String message;
    private final String code;
}
