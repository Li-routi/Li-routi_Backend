package com.lirouti.domain.group.exception.code.error;

import com.lirouti.global.apiPayload.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum GroupErrorCode implements BaseErrorCode {
    GROUP_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "그룹을 찾을 수 없습니다.",
            "GROUP404_1"
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
    OWNER_CANNOT_LEAVE(
            HttpStatus.CONFLICT,
            "방장은 권한을 위임하거나 그룹을 삭제하기 전까지 탈퇴할 수 없습니다.",
            "GROUP409_1"
    ),
    OWNER_CANNOT_KICK(
            HttpStatus.CONFLICT,
            "방장 권한 유저는 강제 퇴장 시킬 수 없습니다.",
            "GROUP409_2"
    );

    private final HttpStatus httpStatus;
    private final String message;
    private final String code;
}
