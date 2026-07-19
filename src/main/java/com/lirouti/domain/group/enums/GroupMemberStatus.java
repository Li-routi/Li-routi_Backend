package com.lirouti.domain.group.enums;

/**
 * 그룹 참여 관계의 현재 상태다. LEFT와 KICKED도 이력 보존을 위해 물리 삭제하지 않는다.
 */
public enum GroupMemberStatus {
    ACTIVE,
    LEFT,
    KICKED
}
