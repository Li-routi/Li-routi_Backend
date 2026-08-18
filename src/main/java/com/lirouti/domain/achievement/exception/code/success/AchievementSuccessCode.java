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
    ),
    WAVE_ROUTINE_SELECT_SUCCESS(
            HttpStatus.OK,
            "파도 업적 추적 루틴 선택에 성공했습니다.",
            "ACHIEVEMENT200_4"
    ),
    WAVE_ROUTINE_FETCH_SUCCESS(
            HttpStatus.OK,
            "파도 업적 진행 상황 조회에 성공했습니다.",
            "ACHIEVEMENT200_5"
    ),
    REPRESENTATIVE_SELECT_SUCCESS(
            HttpStatus.OK,
            "대표 업적 선택에 성공했습니다.",
            "ACHIEVEMENT200_6"
    ),
    REPRESENTATIVE_CLEAR_SUCCESS(
            HttpStatus.OK,
            "대표 업적 해제에 성공했습니다.",
            "ACHIEVEMENT200_7"
    ),
    REPRESENTATIVE_LIST_FETCH_SUCCESS(
            HttpStatus.OK,
            "선택 가능한 대표 업적 목록 조회에 성공했습니다.",
            "ACHIEVEMENT200_8"
    );

    private final HttpStatus httpStatus;
    private final String message;
    private final String code;
}
