package com.lirouti.domain.mypage.exception;

import com.lirouti.domain.mypage.exception.code.error.SuggestionErrorCode;
import com.lirouti.global.apiPayload.exception.GeneralException;

public class SuggestionException extends GeneralException {

    public SuggestionException(SuggestionErrorCode errorCode) {
        super(errorCode);
    }
}
