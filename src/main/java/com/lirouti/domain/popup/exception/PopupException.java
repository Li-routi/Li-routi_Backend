package com.lirouti.domain.popup.exception;

import com.lirouti.global.apiPayload.code.BaseErrorCode;
import com.lirouti.global.apiPayload.exception.GeneralException;

public class PopupException extends GeneralException {

    public PopupException(BaseErrorCode code) {
        super(code);
    }
}
