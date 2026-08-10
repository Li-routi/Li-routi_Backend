package com.lirouti.domain.wallet.exception;

import com.lirouti.domain.wallet.exception.code.error.WalletErrorCode;
import com.lirouti.global.apiPayload.exception.GeneralException;

public class WalletException extends GeneralException {

    public WalletException(WalletErrorCode errorCode) {
        super(errorCode);
    }
}
