package com.lirouti.domain.chat.exception;

import com.lirouti.domain.chat.exception.code.error.ChatErrorCode;
import com.lirouti.global.apiPayload.exception.GeneralException;

public class ChatException extends GeneralException {

    public ChatException(ChatErrorCode errorCode) {
        super(errorCode);
    }
}
