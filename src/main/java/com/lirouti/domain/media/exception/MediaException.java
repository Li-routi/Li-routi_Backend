package com.lirouti.domain.media.exception;

import com.lirouti.domain.media.exception.code.error.MediaErrorCode;
import com.lirouti.global.apiPayload.exception.GeneralException;

public class MediaException extends GeneralException {

    public MediaException(MediaErrorCode errorCode) {
        super(errorCode);
    }
}
