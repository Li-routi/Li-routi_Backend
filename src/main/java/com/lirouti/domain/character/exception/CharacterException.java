package com.lirouti.domain.character.exception;

import com.lirouti.global.apiPayload.code.BaseErrorCode;
import com.lirouti.global.apiPayload.exception.GeneralException;

public class CharacterException extends GeneralException {

    public CharacterException(BaseErrorCode code) {
        super(code);
    }
}
