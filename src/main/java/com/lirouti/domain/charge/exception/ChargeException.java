package com.lirouti.domain.charge.exception;

import com.lirouti.domain.charge.exception.code.error.ChargeErrorCode;
import com.lirouti.global.apiPayload.exception.GeneralException;

public class ChargeException extends GeneralException {

    public ChargeException(ChargeErrorCode errorCode) {
        super(errorCode);
    }
}
