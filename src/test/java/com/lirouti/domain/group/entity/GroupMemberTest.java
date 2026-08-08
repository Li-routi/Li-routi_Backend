package com.lirouti.domain.group.entity;

import com.lirouti.domain.group.enums.GroupMemberRole;
import com.lirouti.domain.group.enums.GroupMemberStatus;
import com.lirouti.domain.group.exception.GroupException;
import com.lirouti.domain.group.exception.code.error.GroupErrorCode;
import com.lirouti.domain.member.entity.Member;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
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
        GroupMember groupMember = GroupMember.builder()
                .member(mock(Member.class)).group(mock(Group.class))
                .role(GroupMemberRole.MEMBER).joinedAt(LocalDateTime.of(2026, 8, 7, 9, 0)).build();
        groupMember.leave();
        LocalDateTime rejoinedAt = LocalDateTime.of(2026, 8, 7, 10, 30);

        groupMember.rejoin(rejoinedAt);

        assertThat(groupMember.getRole()).isEqualTo(GroupMemberRole.MEMBER);
        assertThat(groupMember.getStatus()).isEqualTo(GroupMemberStatus.ACTIVE);
        assertThat(groupMember.getJoinedAt()).isEqualTo(rejoinedAt);
        assertThat(groupMember.getLeftAt()).isNull();
    }

    @Test
    @DisplayName("ACTIVE 또는 KICKED 구성원은 재가입할 수 없고 기존 상태를 유지한다")
    void rejoin_NonLeftMember_ThrowsWithoutChangingFields() {
        LocalDateTime joinedAt = LocalDateTime.of(2026, 8, 7, 9, 0);
        LocalDateTime rejoinedAt = LocalDateTime.of(2026, 8, 7, 10, 30);
        GroupMember active = GroupMember.builder().member(mock(Member.class)).group(mock(Group.class))
                .role(GroupMemberRole.MEMBER).joinedAt(joinedAt).build();
        GroupMember kicked = GroupMember.builder().member(mock(Member.class)).group(mock(Group.class))
                .role(GroupMemberRole.MEMBER).joinedAt(joinedAt).build();
        kicked.kick();
        LocalDateTime kickedLeftAt = kicked.getLeftAt();

        assertThatThrownBy(() -> active.rejoin(rejoinedAt)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> kicked.rejoin(rejoinedAt)).isInstanceOf(IllegalStateException.class);

        assertThat(active.getStatus()).isEqualTo(GroupMemberStatus.ACTIVE);
        assertThat(active.getRole()).isEqualTo(GroupMemberRole.MEMBER);
        assertThat(active.getJoinedAt()).isEqualTo(joinedAt);
        assertThat(active.getLeftAt()).isNull();
        assertThat(kicked.getStatus()).isEqualTo(GroupMemberStatus.KICKED);
        assertThat(kicked.getRole()).isEqualTo(GroupMemberRole.MEMBER);
        assertThat(kicked.getJoinedAt()).isEqualTo(joinedAt);
        assertThat(kicked.getLeftAt()).isEqualTo(kickedLeftAt);
    }

    @Test
    @DisplayName("재가입 기준 시각이 없으면 LEFT 관계도 거부하고 상태를 유지한다")
    void rejoin_NullJoinedAt_ThrowsWithoutChangingFields() {
        GroupMember groupMember = GroupMember.builder().member(mock(Member.class)).group(mock(Group.class))
                .role(GroupMemberRole.MEMBER).build();
        groupMember.leave();
        LocalDateTime leftAt = groupMember.getLeftAt();

        assertThatThrownBy(() -> groupMember.rejoin(null)).isInstanceOf(IllegalArgumentException.class);

        assertThat(groupMember.getStatus()).isEqualTo(GroupMemberStatus.LEFT);
        assertThat(groupMember.getRole()).isEqualTo(GroupMemberRole.MEMBER);
        assertThat(groupMember.getLeftAt()).isEqualTo(leftAt);
    }

    @Test
    @DisplayName("같은 날짜의 전체 완료는 현재 스트릭을 한 번만 증가시킨다")
    void recordStreakCompletion_SameDate_IncreasesOnlyOnce() {
        GroupMember groupMember = GroupMember.builder()
                .member(mock(Member.class)).group(mock(Group.class)).role(GroupMemberRole.MEMBER).build();
        LocalDate completedDate = LocalDate.of(2026, 8, 7);

        groupMember.recordStreakCompletion(completedDate);
        groupMember.recordStreakCompletion(completedDate);

        assertThat(groupMember.getCurrentStreak()).isEqualTo(1);
        assertThat(groupMember.getLongestStreak()).isEqualTo(1);
        assertThat(groupMember.getLastStreakCompletedDate()).isEqualTo(completedDate);
    }

    @Test
    @DisplayName("MISSED 초기화는 최장 스트릭을 보존한다")
    void resetCurrentStreak_PreservesLongestStreak() {
        GroupMember groupMember = GroupMember.builder()
                .member(mock(Member.class)).group(mock(Group.class)).role(GroupMemberRole.MEMBER).build();
        groupMember.recordStreakCompletion(LocalDate.of(2026, 8, 6));
        groupMember.recordStreakCompletion(LocalDate.of(2026, 8, 7));

        groupMember.resetCurrentStreak();

        assertThat(groupMember.getCurrentStreak()).isZero();
        assertThat(groupMember.getLastStreakCompletedDate()).isNull();
        assertThat(groupMember.getLongestStreak()).isEqualTo(2);
    }

    @Test
    @DisplayName("새 가입 회차 초기화는 모든 활동 상태를 초기값으로 되돌린다")
    void resetActivityForNewMembership_ResetsAllActivity() {
        GroupMember groupMember = GroupMember.builder()
                .member(mock(Member.class)).group(mock(Group.class)).role(GroupMemberRole.MEMBER).build();
        groupMember.recordStreakCompletion(LocalDate.of(2026, 8, 6));
        groupMember.increaseTotalLikeCount();

        groupMember.resetActivityForNewMembership();

        assertThat(groupMember.getCurrentStreak()).isZero();
        assertThat(groupMember.getLongestStreak()).isZero();
        assertThat(groupMember.getLastStreakCompletedDate()).isNull();
        assertThat(groupMember.getTotalLikeCount()).isZero();
    }

}
