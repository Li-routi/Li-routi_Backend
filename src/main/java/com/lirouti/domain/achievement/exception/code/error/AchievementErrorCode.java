package com.lirouti.domain.achievement.exception.code.error;

import com.lirouti.global.apiPayload.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum AchievementErrorCode implements BaseErrorCode {

    ACHIEVEMENT_ID_REQUIRED(
            HttpStatus.BAD_REQUEST,
            "업적 ID는 필수입니다.",
            "ACHIEVEMENT400_1"
    ),
    BADGE_IMAGE_KEY_REQUIRED(
            HttpStatus.BAD_REQUEST,
            "업적 뱃지 이미지 key는 필수입니다.",
            "ACHIEVEMENT400_2"
    ),

    NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "존재하지 않는 업적입니다.",
            "ACHIEVEMENT404_1"
    ),
    WAVE_ROUTINE_NOT_SELECTED(
            HttpStatus.NOT_FOUND,
            "아직 파도 업적 추적 루틴을 선택하지 않았습니다.",
            "ACHIEVEMENT404_2"
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
