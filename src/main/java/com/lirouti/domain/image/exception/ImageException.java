package com.lirouti.domain.image.exception;

import com.lirouti.domain.image.exception.code.error.ImageErrorCode;
import com.lirouti.global.apiPayload.exception.GeneralException;

public class ImageException extends GeneralException {

    public ImageException(ImageErrorCode errorCode) {
        super(errorCode);
    }
}
