package com.lirouti.domain.auth.exception;

import com.lirouti.global.apiPayload.code.BaseErrorCode;
import com.lirouti.global.apiPayload.exception.GeneralException;

public class AuthException extends GeneralException {
    public AuthException(BaseErrorCode errorCode) {
        super(errorCode);
    }
}