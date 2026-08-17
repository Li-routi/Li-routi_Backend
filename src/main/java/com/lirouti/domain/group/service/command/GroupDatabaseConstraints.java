package com.lirouti.domain.group.service.command;

/** 그룹 도메인이 저장 오류를 분류할 때 사용하는 실제 DB unique 제약 이름이다. */
final class GroupDatabaseConstraints {
    static final String INVITE_CODE = "UKmgt3kl7whp0n031hlo8x6jupi";
    static final String GROUP_MEMBER = "uk_group_member_member_group";
    static final String ROUTINE_TITLE = "uk_group_routine_group_title";
    static final String ROUTINE_CATEGORY_NAME = "uk_group_routine_category_group_name";

    private GroupDatabaseConstraints() {
    }
}
