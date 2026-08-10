package com.lirouti.domain.achievement.exception;

import com.lirouti.domain.achievement.exception.code.error.AchievementErrorCode;

public class AchievementException extends RuntimeException {
    private final AchievementErrorCode errorCode;

    public AchievementException(AchievementErrorCode errorCode) {
        super(errorCode.name());
        this.errorCode = errorCode;
    }

    public AchievementErrorCode getErrorCode() {
        return errorCode;
    }
}
