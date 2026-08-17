package com.lirouti.domain.achievement.exception;

import com.lirouti.domain.achievement.exception.code.error.AchievementErrorCode;
import com.lirouti.global.apiPayload.exception.GeneralException;

public class AchievementException extends GeneralException {

    public AchievementException(AchievementErrorCode errorCode) {
        super(errorCode);
    }
}
