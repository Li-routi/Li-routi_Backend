package com.lirouti.domain.group.exception.code.success;

import com.lirouti.global.apiPayload.code.BaseSuccessCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum GroupSuccessCode implements BaseSuccessCode {
    GROUP_ROUTINE_TODAY_FETCH_SUCCESS(
            HttpStatus.OK,
            "오늘의 그룹 루틴 조회에 성공했습니다.",
            "GROUP200_1"
    ),
    GROUP_ROUTINE_UPDATE_SUCCESS(
            HttpStatus.OK,
            "그룹 루틴 수정에 성공했습니다.",
            "GROUP200_3"
    ),
    GROUP_ROUTINE_CATEGORY_LIST_FETCH_SUCCESS(
            HttpStatus.OK,
            "그룹 루틴 카테고리 조회에 성공했습니다.",
            "GROUP200_4"
    ),
    GROUP_ROUTINE_DELETE_SUCCESS(
            HttpStatus.OK,
            "그룹 루틴 삭제에 성공했습니다.",
            "GROUP200_5"
    ),
    GROUP_ROUTINE_CREATE_SUCCESS(
            HttpStatus.CREATED,
            "그룹 루틴 생성에 성공했습니다.",
            "GROUP201_1"
    ),
    GROUP_INVITE_CODE_FETCH_SUCCESS(
            HttpStatus.OK,
            "그룹 초대코드 조회에 성공했습니다.",
            "GROUP200_2"
    ),
    GROUP_INVITE_CODE_ISSUE_SUCCESS(
            HttpStatus.CREATED,
            "그룹 초대코드 발급에 성공했습니다.",
            "GROUP201_2"
    ),
    GROUP_CREATE_SUCCESS(
            HttpStatus.CREATED,
            "그룹 생성에 성공했습니다.",
            "GROUP201_3"
    ),
    GROUP_ROUTINE_CATEGORY_CREATE_SUCCESS(
            HttpStatus.CREATED,
            "그룹 루틴 카테고리 생성에 성공했습니다.",
            "GROUP201_4"
    );

    private final HttpStatus httpStatus;
    private final String message;
    private final String code;
}
