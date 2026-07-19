package com.lirouti.domain.group.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.lirouti.domain.group.enums.GroupMemberRole;
import com.lirouti.domain.group.enums.GroupMemberStatus;
import com.lirouti.domain.group.exception.GroupException;
import com.lirouti.domain.group.exception.code.error.GroupErrorCode;
import com.lirouti.domain.member.entity.Member;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("GroupMember 테스트")
class GroupMemberTest {

    @Test
    @DisplayName("방장은 권한 위임 또는 그룹 삭제 전까지 일반 탈퇴할 수 없다")
    void leave_Owner_ThrowsOwnerCannotLeave() {
        // given
        GroupMember owner = GroupMember.builder()
                .member(mock(Member.class))
                .group(mock(Group.class))
                .role(GroupMemberRole.OWNER)
                .build();

        // when & then
        assertThatThrownBy(owner::leave)
                .isInstanceOf(GroupException.class)
                .extracting("code")
                .isEqualTo(GroupErrorCode.OWNER_CANNOT_LEAVE);
        assertThat(owner.getStatus()).isEqualTo(GroupMemberStatus.ACTIVE);
        assertThat(owner.getLeftAt()).isNull();
    }

    @Test
    @DisplayName("일반 구성원은 탈퇴 시 행을 삭제하지 않고 상태와 탈퇴 일시를 변경한다")
    void leave_Member_ChangesStatusAndLeftAt() {
        // given
        GroupMember groupMember = GroupMember.builder()
                .member(mock(Member.class))
                .group(mock(Group.class))
                .role(GroupMemberRole.MEMBER)
                .build();

        // when
        groupMember.leave();

        // then
        assertThat(groupMember.getStatus()).isEqualTo(GroupMemberStatus.LEFT);
        assertThat(groupMember.getLeftAt()).isNotNull();
    }

}
