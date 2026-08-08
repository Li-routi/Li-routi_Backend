package com.lirouti.domain.group.exception.code.error;

import com.lirouti.global.apiPayload.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum GroupErrorCode implements BaseErrorCode {
    INVALID_GROUP_ROUTINE_CATEGORY_NAME(
            HttpStatus.BAD_REQUEST,
            "카테고리 이름은 앞뒤 공백 제거 후 1~10자의 한 줄이어야 합니다.",
            "GROUP400_1"
    ),
    GROUP_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "그룹을 찾을 수 없습니다.",
            "GROUP404_1"
    ),
    ROUTINE_CATEGORY_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "사용 가능한 루틴 카테고리를 찾을 수 없습니다.",
            "GROUP404_2"
    ),
    GROUP_ROUTINE_ASSIGNMENT_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "그룹 루틴 할당 내역을 찾을 수 없습니다.",
            "GROUP404_3"
    ),
    GROUP_ROUTINE_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "그룹 루틴을 찾을 수 없습니다.",
            "GROUP404_4"
    ),
    GROUP_INACTIVE(
            HttpStatus.FORBIDDEN,
            "사용할 수 없는 그룹입니다.",
            "GROUP403_1"
    ),
    GROUP_MEMBER_ACCESS_DENIED(
            HttpStatus.FORBIDDEN,
            "해당 그룹의 활성 구성원이 아닙니다.",
            "GROUP403_2"
    ),
    GROUP_OWNER_ACCESS_DENIED(
            HttpStatus.FORBIDDEN,
            "그룹 방장 권한이 필요합니다.",
            "GROUP403_3"
    ),
    GROUP_ROUTINE_CATEGORY_ACCESS_DENIED(
            HttpStatus.FORBIDDEN,
            "해당 그룹에서 사용할 수 없는 루틴 카테고리입니다.",
            "GROUP403_4"
    ),
    GROUP_LOCKED(
            HttpStatus.FORBIDDEN,
            "잠긴 그룹에는 참여할 수 없습니다.",
            "GROUP403_5"
    ),
    OWNER_CANNOT_LEAVE(
            HttpStatus.CONFLICT,
            "방장은 권한을 위임하거나 그룹을 삭제하기 전까지 탈퇴할 수 없습니다.",
            "GROUP409_1"
    ),
    OWNER_CANNOT_KICK(
            HttpStatus.CONFLICT,
            "방장 권한 유저는 강제 퇴장 시킬 수 없습니다.",
            "GROUP409_2"
    ),
    DUPLICATE_GROUP_ROUTINE_TITLE(
            HttpStatus.CONFLICT,
            "같은 그룹에 동일한 제목의 루틴이 이미 존재합니다.",
            "GROUP409_3"
    ),
    GROUP_ROUTINE_ASSIGNMENT_NOT_IN_PROGRESS(
            HttpStatus.CONFLICT,
            "현재 인증할 수 있는 그룹 루틴 할당이 아닙니다.",
            "GROUP409_4"
    ),
    GROUP_ROUTINE_ASSIGNMENT_ALREADY_COMPLETED(
            HttpStatus.CONFLICT,
            "이미 이행한 그룹 루틴 할당입니다.",
            "GROUP409_5"
    ),
    GROUP_PARTICIPATION_LIMIT_EXCEEDED(
            HttpStatus.CONFLICT,
            "참여할 수 있는 활성 그룹 수를 초과했습니다.",
            "GROUP409_6"
    ),
    GROUP_ROUTINE_LIMIT_EXCEEDED(
            HttpStatus.CONFLICT,
            "그룹에 등록할 수 있는 루틴 수를 초과했습니다.",
            "GROUP409_7"
    ),
    GROUP_MEMBER_LIMIT_EXCEEDED(
            HttpStatus.CONFLICT,
            "그룹에 참여할 수 있는 활성 회원 수를 초과했습니다.",
            "GROUP409_8"
    ),
    GROUP_ROUTINE_CATEGORY_LIMIT_EXCEEDED(
            HttpStatus.CONFLICT,
            "그룹에 추가할 수 있는 사용자 카테고리 수를 초과했습니다.",
            "GROUP409_9"
    ),
    DUPLICATE_GROUP_ROUTINE_CATEGORY_NAME(
            HttpStatus.CONFLICT,
            "기본 또는 같은 그룹에 동일한 카테고리 이름이 이미 존재합니다.",
            "GROUP409_10"
    ),
    ALREADY_ACTIVE_GROUP_MEMBER(
            HttpStatus.CONFLICT,
            "이미 해당 그룹의 활성 구성원입니다.",
            "GROUP409_11"
    ),
    KICKED_MEMBER_CANNOT_REJOIN(
            HttpStatus.CONFLICT,
            "강제 퇴장된 그룹에는 다시 참여할 수 없습니다.",
            "GROUP409_12"
    ),
    OWNER_CANNOT_TRANSFER_TO_SELF(
            HttpStatus.CONFLICT,
            "방장 권한은 본인에게 위임할 수 없습니다.",
            "GROUP409_13"
    ),
    INVITE_CODE_ISSUE_FAILED(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "초대코드 생성에 실패했습니다.",
            "GROUP500_1"
    );

    private final HttpStatus httpStatus;
    private final String message;
    private final String code;
}
