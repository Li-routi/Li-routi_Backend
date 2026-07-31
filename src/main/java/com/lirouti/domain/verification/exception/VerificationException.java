package com.lirouti.domain.verification.exception;

import com.lirouti.global.apiPayload.code.BaseErrorCode;
import com.lirouti.global.apiPayload.exception.GeneralException;

public class VerificationException extends GeneralException {
    public VerificationException(BaseErrorCode errorCode) {
        super(errorCode);
    }
}
