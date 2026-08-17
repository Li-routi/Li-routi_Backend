package com.lirouti.domain.achievement.exception.code.success;

import com.lirouti.global.apiPayload.code.BaseSuccessCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum AchievementSuccessCode implements BaseSuccessCode {

    ACHIEVEMENT_LIST_FETCH_SUCCESS(
            HttpStatus.OK,
            "업적 목록 조회에 성공했습니다.",
            "ACHIEVEMENT200_1"
    ),
    ACHIEVEMENT_CLAIM_SUCCESS(
            HttpStatus.OK,
            "업적 보상 수령에 성공했습니다.",
            "ACHIEVEMENT200_2"
    ),
    ACHIEVEMENT_BADGE_IMAGE_UPLOAD_SUCCESS(
            HttpStatus.OK,
            "업적 뱃지 이미지 업로드에 성공했습니다.",
            "ACHIEVEMENT200_3"
    );

    private final HttpStatus httpStatus;
    private final String message;
    private final String code;
}
