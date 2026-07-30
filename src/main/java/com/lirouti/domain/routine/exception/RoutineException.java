package com.lirouti.domain.routine.exception;

import com.lirouti.domain.routine.exception.code.error.RoutineErrorCode;
import com.lirouti.global.apiPayload.exception.GeneralException;

public class RoutineException extends GeneralException {

    public RoutineException(RoutineErrorCode errorCode) {
        super(errorCode);
    }
}
