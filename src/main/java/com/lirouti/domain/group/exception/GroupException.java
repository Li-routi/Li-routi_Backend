package com.lirouti.domain.group.exception;

import com.lirouti.domain.group.exception.code.error.GroupErrorCode;
import com.lirouti.global.apiPayload.exception.GeneralException;

public class GroupException extends GeneralException {

    public GroupException(GroupErrorCode errorCode) {
        super(errorCode);
    }
}
