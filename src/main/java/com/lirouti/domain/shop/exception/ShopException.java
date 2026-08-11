package com.lirouti.domain.shop.exception;

import com.lirouti.domain.shop.exception.code.error.ShopErrorCode;
import com.lirouti.global.apiPayload.exception.GeneralException;

public class ShopException extends GeneralException {

    public ShopException(ShopErrorCode errorCode) {
        super(errorCode);
    }
}
