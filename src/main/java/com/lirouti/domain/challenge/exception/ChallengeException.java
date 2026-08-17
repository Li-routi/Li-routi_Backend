package com.lirouti.domain.challenge.exception;

import com.lirouti.domain.challenge.exception.code.error.ChallengeErrorCode;
import com.lirouti.global.apiPayload.exception.GeneralException;

public class ChallengeException extends GeneralException {

    public ChallengeException(ChallengeErrorCode errorCode) {
        super(errorCode);
    }
}
