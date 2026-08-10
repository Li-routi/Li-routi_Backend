package com.lirouti.domain.achievement.exception.code.error;

import com.lirouti.global.apiPayload.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum AchievementErrorCode implements BaseErrorCode {

    NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "존재하지 않는 업적입니다.",
            "ACHIEVEMENT404_1"
    ),
    NOT_ACHIEVED(
            HttpStatus.CONFLICT,
            "아직 달성하지 않은 업적입니다.",
            "ACHIEVEMENT409_1"
    ),
    ALREADY_CLAIMED(
            HttpStatus.CONFLICT,
            "이미 보상을 수령한 업적입니다.",
            "ACHIEVEMENT409_2"
    );

    private final HttpStatus httpStatus;
    private final String message;
    private final String code;
}
