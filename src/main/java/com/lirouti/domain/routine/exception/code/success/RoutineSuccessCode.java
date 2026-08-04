package com.lirouti.domain.routine.exception.code.success;

import com.lirouti.global.apiPayload.code.BaseSuccessCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum RoutineSuccessCode implements BaseSuccessCode {
    ROUTINE_CATEGORY_LIST_FETCH_SUCCESS(
            HttpStatus.OK,
            "루틴 카테고리 목록 조회에 성공했습니다.",
            "ROUTINE200_1"
    ),
    ROUTINE_TEMPLATE_LIST_FETCH_SUCCESS(
            HttpStatus.OK,
            "기본 제공 루틴 목록 조회에 성공했습니다.",
            "ROUTINE200_2"
    ),
    ROUTINE_LIST_FETCH_SUCCESS(
            HttpStatus.OK,
            "개인 루틴 목록 조회에 성공했습니다.",
            "ROUTINE200_3"
    ),
    ROUTINE_UPDATE_SUCCESS(
            HttpStatus.OK,
            "개인 루틴 수정에 성공했습니다.",
            "ROUTINE200_4"
    ),
    ROUTINE_DELETE_SUCCESS(
            HttpStatus.OK,
            "개인 루틴 삭제에 성공했습니다.",
            "ROUTINE200_5"
    ),
    ROUTINE_CATEGORY_UPDATE_SUCCESS(
            HttpStatus.OK,
            "개인 루틴 카테고리 수정에 성공했습니다.",
            "ROUTINE200_6"
    ),
    ROUTINE_CATEGORY_DELETE_SUCCESS(
            HttpStatus.OK,
            "개인 루틴 카테고리 삭제에 성공했습니다.",
            "ROUTINE200_7"
    ),
    ROUTINE_CREATE_SUCCESS(
            HttpStatus.CREATED,
            "루틴 생성에 성공했습니다.",
            "ROUTINE201_1"
    ),
    ROUTINE_CATEGORY_CREATE_SUCCESS(
            HttpStatus.CREATED,
            "루틴 카테고리 생성에 성공했습니다.",
            "ROUTINE201_2"
    );

    private final HttpStatus httpStatus;
    private final String message;
    private final String code;
}
