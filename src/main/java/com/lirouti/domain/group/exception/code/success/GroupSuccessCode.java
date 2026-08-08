package com.lirouti.domain.group.exception.code.success;

import org.springframework.http.HttpStatus;

import com.lirouti.global.apiPayload.code.BaseSuccessCode;

import lombok.AllArgsConstructor;
import lombok.Getter;

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
    GROUP_CREATE_SUCCESS(
            HttpStatus.CREATED,
            "그룹 생성에 성공했습니다.",
            "GROUP201_3"
    ),
    GROUP_ROUTINE_CATEGORY_CREATE_SUCCESS(
            HttpStatus.CREATED,
            "그룹 루틴 카테고리 생성에 성공했습니다.",
            "GROUP201_4"
    ),
    GROUP_DELETE_SUCCESS(
            HttpStatus.OK,
            "그룹 삭제에 성공했습니다.",
            "GROUP200_5"
    ),
    GROUP_LEAVE_SUCCESS(
            HttpStatus.OK,
            "그룹 탈퇴에 성공했습니다.",
            "GROUP200_6"
    ),
    GROUP_LOCK_SUCCESS(
            HttpStatus.OK,
            "그룹을 잠갔습니다.",
            "GROUP200_7"
    ),
    GROUP_UNLOCK_SUCCESS(
            HttpStatus.OK,
            "그룹 잠금을 해제했습니다.",
            "GROUP200_8"
    ),
    GROUP_MEMBER_KICK_SUCCESS(
            HttpStatus.OK,
            "그룹 구성원 강제 퇴장에 성공했습니다.",
            "GROUP200_9"
    ),
    GROUP_JOIN_PREVIEW_FETCH_SUCCESS(
            HttpStatus.OK,
            "초대코드로 참여할 그룹 조회에 성공했습니다.",
            "GROUP200_10"
    ),
    GROUP_JOIN_SUCCESS(
            HttpStatus.CREATED,
            "그룹 참여에 성공했습니다.",
            "GROUP201_5"
    ),
    GROUP_DETAIL_FETCH_SUCCESS(
            HttpStatus.OK,
            "그룹 상세 조회에 성공했습니다.",
            "GROUP200_11"
    );

    private final HttpStatus httpStatus;
    private final String message;
    private final String code;
}
