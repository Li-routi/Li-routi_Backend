package com.lirouti.domain.group.entity;

import com.lirouti.domain.group.enums.GroupMemberRole;
import com.lirouti.domain.group.enums.GroupMemberStatus;
import com.lirouti.domain.group.exception.GroupException;
import com.lirouti.domain.group.exception.code.error.GroupErrorCode;
import com.lirouti.domain.member.entity.Member;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

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

    @Test
    @DisplayName("일반 구성원은 강제 탈퇴 시 상태와 퇴출 일시를 변경한다")
    void kick_Member_ChangesStatusAndLeftAt() {
        // given
        GroupMember groupMember = GroupMember.builder()
                .member(mock(Member.class))
                .group(mock(Group.class))
                .role(GroupMemberRole.MEMBER)
                .build();

        // when
        groupMember.kick();

        // then
        assertThat(groupMember.getStatus()).isEqualTo(GroupMemberStatus.KICKED);
        assertThat(groupMember.getLeftAt()).isNotNull();
    }

    @Test
    @DisplayName("방장은 강제 탈퇴시킬 수 없다")
    void kick_Owner_ThrowsOwnerCannotKick() {
        // given
        GroupMember owner = GroupMember.builder()
                .member(mock(Member.class))
                .group(mock(Group.class))
                .role(GroupMemberRole.OWNER)
                .build();

        // when & then
        assertThatThrownBy(owner::kick)
                .isInstanceOf(GroupException.class)
                .extracting("code")
                .isEqualTo(GroupErrorCode.OWNER_CANNOT_KICK);
        assertThat(owner.getStatus()).isEqualTo(GroupMemberStatus.ACTIVE);
        assertThat(owner.getLeftAt()).isNull();
    }

    @Test
    @DisplayName("LEFT 구성원은 전달받은 기준 시각으로 일반 ACTIVE 구성원으로 재가입한다")
    void rejoin_LeftMember_RestoresActiveStateAtReferenceTime() {
        // given
        GroupMember groupMember = GroupMember.builder()
                .member(mock(Member.class))
                .group(mock(Group.class))
                .role(GroupMemberRole.MEMBER)
                .joinedAt(LocalDateTime.of(2026, 8, 7, 9, 0))
                .build();
        groupMember.leave();
        LocalDateTime rejoinedAt = LocalDateTime.of(2026, 8, 7, 10, 30);

        // when
        groupMember.rejoin(rejoinedAt);

        // then
        assertThat(groupMember.getRole()).isEqualTo(GroupMemberRole.MEMBER);
        assertThat(groupMember.getStatus()).isEqualTo(GroupMemberStatus.ACTIVE);
        assertThat(groupMember.getJoinedAt()).isEqualTo(rejoinedAt);
        assertThat(groupMember.getLeftAt()).isNull();
    }

}
