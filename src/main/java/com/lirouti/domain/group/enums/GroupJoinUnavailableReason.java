package com.lirouti.domain.group.enums;

/** 초대코드 Preview에서 그룹 정보는 표시할 수 있지만 가입할 수 없는 사유다. */
public enum GroupJoinUnavailableReason {
    ALREADY_ACTIVE_MEMBER,
    KICKED_MEMBER,
    MEMBER_GROUP_LIMIT_REACHED,
    GROUP_MEMBER_LIMIT_REACHED
}
