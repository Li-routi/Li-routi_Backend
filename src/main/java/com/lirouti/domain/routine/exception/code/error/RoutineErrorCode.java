package com.lirouti.domain.routine.exception.code.error;

import com.lirouti.global.apiPayload.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum RoutineErrorCode implements BaseErrorCode {
    INVALID_ROUTINE_NAME(
            HttpStatus.BAD_REQUEST,
            "루틴 이름은 앞뒤 공백을 제외하고 1자 이상 20자 이하여야 하며 줄바꿈을 포함할 수 없습니다.",
            "ROUTINE400_1"
    ),
    INVALID_ROUTINE_CATEGORY_NAME(
            HttpStatus.BAD_REQUEST,
            "카테고리 이름은 앞뒤 공백을 제외하고 1자 이상 10자 이하여야 하며 줄바꿈을 포함할 수 없습니다.",
            "ROUTINE400_2"
    ),
    ROUTINE_TEMPLATE_CATEGORY_MISMATCH(
            HttpStatus.BAD_REQUEST,
            "선택한 기본 루틴이 요청한 카테고리에 속하지 않습니다.",
            "ROUTINE400_3"
    ),
    INVALID_ROUTINE_UPDATE(
            HttpStatus.BAD_REQUEST,
            "마감 시각과 하나 이상의 중복되지 않은 반복 요일이 필요합니다.",
            "ROUTINE400_4"
    ),
    ROUTINE_CATEGORY_ACCESS_DENIED(
            HttpStatus.FORBIDDEN,
            "다른 회원이 만든 카테고리는 사용할 수 없습니다.",
            "ROUTINE403_1"
    ),
    // 회원 조회·탈퇴 검증은 MemberQueryService가 담당하므로 여기에 회원 관련 404를 두지 않는다.
    ROUTINE_CATEGORY_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "사용 가능한 루틴 카테고리를 찾을 수 없습니다.",
            "ROUTINE404_1"
    ),
    ROUTINE_TEMPLATE_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "사용 가능한 기본 제공 루틴을 찾을 수 없습니다.",
            "ROUTINE404_2"
    ),
    ROUTINE_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "수정하거나 삭제할 수 있는 개인 루틴을 찾을 수 없습니다.",
            "ROUTINE404_3"
    ),
    ACTIVE_ROUTINE_LIMIT_EXCEEDED(
            HttpStatus.CONFLICT,
            "활성 루틴은 최대 30개까지 등록할 수 있습니다.",
            "ROUTINE409_1"
    ),
    DUPLICATE_ROUTINE_TEMPLATE(
            HttpStatus.CONFLICT,
            "이미 등록한 기본 제공 루틴입니다.",
            "ROUTINE409_2"
    ),
    ROUTINE_CATEGORY_LIMIT_EXCEEDED(
            HttpStatus.CONFLICT,
            "카테고리는 최대 5개까지 추가할 수 있습니다.",
            "ROUTINE409_3"
    ),
    DUPLICATE_ROUTINE_CATEGORY_NAME(
            HttpStatus.CONFLICT,
            "같은 이름의 카테고리가 이미 존재합니다.",
            "ROUTINE409_4"
    );

    private final HttpStatus httpStatus;
    private final String message;
    private final String code;
}
