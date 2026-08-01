package com.lirouti.domain.group.service;

import com.lirouti.domain.group.entity.Group;
import com.lirouti.domain.group.entity.GroupMember;
import com.lirouti.domain.group.enums.GroupMemberRole;
import com.lirouti.domain.group.enums.GroupMemberStatus;
import com.lirouti.domain.group.enums.GroupStatus;
import com.lirouti.domain.group.exception.GroupException;
import com.lirouti.domain.group.exception.code.error.GroupErrorCode;
import com.lirouti.domain.group.repository.GroupMemberRepository;
import com.lirouti.domain.group.repository.GroupRepository;
import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.exception.MemberException;
import com.lirouti.domain.member.exception.code.error.MemberErrorCode;
import com.lirouti.domain.member.repository.MemberRepository;
import com.lirouti.domain.member.service.query.MemberQueryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("GroupValidationService 테스트")
class GroupValidationServiceTest {
    private static final Long MEMBER_ID = 1L;
    private static final Long GROUP_ID = 10L;
    private static final Long OTHER_GROUP_ID = 20L;

    @Mock
    private GroupRepository groupRepository;
    @Mock
    private GroupMemberRepository groupMemberRepository;
    @Mock
    private MemberRepository memberRepository;
    @Mock
    private MemberQueryService memberQueryService;
    @Mock
    private Member member;
    @Mock
    private Group group;
    @Mock
    private Group otherGroup;
    @Mock
    private GroupMember groupMember;
    @Mock
    private GroupMember otherGroupMember;

    @InjectMocks
    private GroupValidationService groupValidationService;

    @Test
    @DisplayName("회원 행을 잠근 뒤 ACTIVE 그룹 참여 수가 6개 미만이면 참여할 수 있다")
    void lockActiveMemberAndValidateParticipationLimit_UnderLimit_ReturnsLockedMember() {
        // given
        when(memberRepository.findByIdForUpdate(MEMBER_ID)).thenReturn(Optional.of(member));
        when(member.getIsActive()).thenReturn(true);
        when(groupMemberRepository.countByMemberIdAndStatusAndGroupStatus(
                MEMBER_ID, GroupMemberStatus.ACTIVE, GroupStatus.ACTIVE
        )).thenReturn(5L);

        // when
        Member result = groupValidationService
                .lockActiveMemberAndValidateParticipationLimit(MEMBER_ID);

        // then
        assertThat(result).isSameAs(member);
        verify(memberRepository).findByIdForUpdate(MEMBER_ID);
    }

    @Test
    @DisplayName("ACTIVE 그룹에 이미 6개 참여 중이면 추가 참여를 거부한다")
    void lockActiveMemberAndValidateParticipationLimit_AtLimit_ThrowsLimitExceeded() {
        // given
        when(memberRepository.findByIdForUpdate(MEMBER_ID)).thenReturn(Optional.of(member));
        when(member.getIsActive()).thenReturn(true);
        when(groupMemberRepository.countByMemberIdAndStatusAndGroupStatus(
                MEMBER_ID, GroupMemberStatus.ACTIVE, GroupStatus.ACTIVE
        )).thenReturn(6L);

        // when & then
        assertThatThrownBy(() -> groupValidationService
                .lockActiveMemberAndValidateParticipationLimit(MEMBER_ID))
                .isInstanceOf(GroupException.class)
                .extracting("code")
                .isEqualTo(GroupErrorCode.GROUP_PARTICIPATION_LIMIT_EXCEEDED);
    }

    @Test
    @DisplayName("활성 그룹 행을 비관적 쓰기 잠금으로 조회한다")
    void lockActiveGroupForUpdate_ActiveGroup_ReturnsLockedGroup() {
        // given
        when(groupRepository.findByIdForUpdate(GROUP_ID)).thenReturn(Optional.of(group));
        when(group.getStatus()).thenReturn(GroupStatus.ACTIVE);

        // when
        Group result = groupValidationService.lockActiveGroupForUpdate(GROUP_ID);

        // then
        assertThat(result).isSameAs(group);
        verify(groupRepository).findByIdForUpdate(GROUP_ID);
    }

    @Test
    @DisplayName("그룹 행을 잠근 뒤 활성 그룹원이 6명 미만이면 가입할 수 있다")
    void lockActiveGroupAndValidateMemberLimit_UnderLimit_ReturnsLockedGroup() {
        // given
        when(groupRepository.findByIdForUpdate(GROUP_ID)).thenReturn(Optional.of(group));
        when(group.getStatus()).thenReturn(GroupStatus.ACTIVE);
        when(groupMemberRepository.countActiveMembersByGroupId(
                GROUP_ID, GroupMemberStatus.ACTIVE
        )).thenReturn(5L);

        // when
        Group result = groupValidationService.lockActiveGroupAndValidateMemberLimit(GROUP_ID);

        // then
        assertThat(result).isSameAs(group);
    }

    @Test
    @DisplayName("활성 그룹원이 이미 6명이면 추가 가입을 거부한다")
    void lockActiveGroupAndValidateMemberLimit_AtLimit_ThrowsLimitExceeded() {
        // given
        when(groupRepository.findByIdForUpdate(GROUP_ID)).thenReturn(Optional.of(group));
        when(group.getStatus()).thenReturn(GroupStatus.ACTIVE);
        when(groupMemberRepository.countActiveMembersByGroupId(
                GROUP_ID, GroupMemberStatus.ACTIVE
        )).thenReturn(6L);

        // when & then
        assertThatThrownBy(() -> groupValidationService
                .lockActiveGroupAndValidateMemberLimit(GROUP_ID))
                .isInstanceOf(GroupException.class)
                .extracting("code")
                .isEqualTo(GroupErrorCode.GROUP_MEMBER_LIMIT_EXCEEDED);
    }

    @Test
    @DisplayName("가입 제한은 그룹과 회원을 모두 잠근 뒤 두 상한을 검사한다")
    void lockAndValidateJoinLimits_ValidRequest_LocksBeforeCounting() {
        // given
        when(groupRepository.findByIdForUpdate(GROUP_ID)).thenReturn(Optional.of(group));
        when(group.getStatus()).thenReturn(GroupStatus.ACTIVE);
        when(memberRepository.findByIdForUpdate(MEMBER_ID)).thenReturn(Optional.of(member));
        when(member.getIsActive()).thenReturn(true);
        when(groupMemberRepository.countActiveMembersByGroupId(
                GROUP_ID, GroupMemberStatus.ACTIVE
        )).thenReturn(5L);
        when(groupMemberRepository.countByMemberIdAndStatusAndGroupStatus(
                MEMBER_ID, GroupMemberStatus.ACTIVE, GroupStatus.ACTIVE
        )).thenReturn(5L);

        // when
        GroupValidationService.JoinLimitContext result = groupValidationService
                .lockAndValidateJoinLimits(GROUP_ID, MEMBER_ID);

        // then
        assertThat(result.group()).isSameAs(group);
        assertThat(result.member()).isSameAs(member);
        InOrder inOrder = inOrder(groupRepository, memberRepository, groupMemberRepository);
        inOrder.verify(groupRepository).findByIdForUpdate(GROUP_ID);
        inOrder.verify(memberRepository).findByIdForUpdate(MEMBER_ID);
        inOrder.verify(groupMemberRepository).countActiveMembersByGroupId(
                GROUP_ID, GroupMemberStatus.ACTIVE
        );
        inOrder.verify(groupMemberRepository).countByMemberIdAndStatusAndGroupStatus(
                MEMBER_ID, GroupMemberStatus.ACTIVE, GroupStatus.ACTIVE
        );
    }

    @Test
    @DisplayName("현재 회원이 대상 그룹의 ACTIVE OWNER이면 방장 검증에 성공한다")
    void validateGroupOwner_ActiveOwner_ReturnsGroupMember() {
        // given
        givenActiveMember();
        givenActiveGroup(GROUP_ID, group);
        when(groupMemberRepository.findByGroupIdAndMemberId(GROUP_ID, MEMBER_ID))
                .thenReturn(Optional.of(groupMember));
        when(groupMember.getStatus()).thenReturn(GroupMemberStatus.ACTIVE);
        when(groupMember.getRole()).thenReturn(GroupMemberRole.OWNER);

        // when
        GroupMember result = groupValidationService.validateGroupOwner(GROUP_ID, MEMBER_ID);

        // then
        assertThat(result).isSameAs(groupMember);
    }

    @Test
    @DisplayName("일반 구성원은 방장 전용 기능을 수행할 수 없다")
    void validateGroupOwner_RegularMember_ThrowsOwnerAccessDenied() {
        // given
        givenActiveMember();
        givenActiveGroup(GROUP_ID, group);
        when(groupMemberRepository.findByGroupIdAndMemberId(GROUP_ID, MEMBER_ID))
                .thenReturn(Optional.of(groupMember));
        when(groupMember.getStatus()).thenReturn(GroupMemberStatus.ACTIVE);
        when(groupMember.getRole()).thenReturn(GroupMemberRole.MEMBER);

        // when & then
        assertThatThrownBy(() -> groupValidationService.validateGroupOwner(GROUP_ID, MEMBER_ID))
                .isInstanceOf(GroupException.class)
                .extracting("code")
                .isEqualTo(GroupErrorCode.GROUP_OWNER_ACCESS_DENIED);
    }

    @Test
    @DisplayName("대상 그룹에 가입하지 않은 회원은 접근할 수 없다")
    void validateActiveGroupMember_NonMember_ThrowsMemberAccessDenied() {
        // given
        givenActiveMember();
        givenActiveGroup(GROUP_ID, group);
        when(groupMemberRepository.findByGroupIdAndMemberId(GROUP_ID, MEMBER_ID))
                .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() ->
                groupValidationService.validateActiveGroupMember(GROUP_ID, MEMBER_ID))
                .isInstanceOf(GroupException.class)
                .extracting("code")
                .isEqualTo(GroupErrorCode.GROUP_MEMBER_ACCESS_DENIED);
    }

    @Test
    @DisplayName("탈퇴하거나 강제 탈퇴된 구성원은 그룹 기능을 수행할 수 없다")
    void validateActiveGroupMember_InactiveMembership_ThrowsMemberAccessDenied() {
        // given
        givenActiveMember();
        givenActiveGroup(GROUP_ID, group);
        when(groupMemberRepository.findByGroupIdAndMemberId(GROUP_ID, MEMBER_ID))
                .thenReturn(Optional.of(groupMember));
        when(groupMember.getStatus()).thenReturn(GroupMemberStatus.LEFT);

        // when & then
        assertThatThrownBy(() ->
                groupValidationService.validateActiveGroupMember(GROUP_ID, MEMBER_ID))
                .isInstanceOf(GroupException.class)
                .extracting("code")
                .isEqualTo(GroupErrorCode.GROUP_MEMBER_ACCESS_DENIED);
    }

    @Test
    @DisplayName("여러 그룹에 속한 회원의 권한은 요청 대상 그룹별로 검증한다")
    void validateActiveGroupMember_MultipleGroups_ValidatesEachGroupMembership() {
        // given
        givenActiveMember();
        givenActiveGroup(GROUP_ID, group);
        givenActiveGroup(OTHER_GROUP_ID, otherGroup);
        when(groupMemberRepository.findByGroupIdAndMemberId(GROUP_ID, MEMBER_ID))
                .thenReturn(Optional.of(groupMember));
        when(groupMemberRepository.findByGroupIdAndMemberId(OTHER_GROUP_ID, MEMBER_ID))
                .thenReturn(Optional.of(otherGroupMember));
        when(groupMember.getStatus()).thenReturn(GroupMemberStatus.ACTIVE);
        when(otherGroupMember.getStatus()).thenReturn(GroupMemberStatus.ACTIVE);

        // when
        GroupMember first = groupValidationService
                .validateActiveGroupMember(GROUP_ID, MEMBER_ID);
        GroupMember second = groupValidationService
                .validateActiveGroupMember(OTHER_GROUP_ID, MEMBER_ID);

        // then
        assertThat(first).isSameAs(groupMember);
        assertThat(second).isSameAs(otherGroupMember);
        verify(groupMemberRepository).findByGroupIdAndMemberId(GROUP_ID, MEMBER_ID);
        verify(groupMemberRepository).findByGroupIdAndMemberId(OTHER_GROUP_ID, MEMBER_ID);
    }

    @Test
    @DisplayName("한 그룹의 방장이어도 다른 그룹에서 MEMBER이면 방장 권한이 없다")
    void validateGroupOwner_OwnerOfAnotherGroup_ThrowsOwnerAccessDenied() {
        // given
        givenActiveMember();
        givenActiveGroup(OTHER_GROUP_ID, otherGroup);
        when(groupMemberRepository.findByGroupIdAndMemberId(OTHER_GROUP_ID, MEMBER_ID))
                .thenReturn(Optional.of(otherGroupMember));
        when(otherGroupMember.getStatus()).thenReturn(GroupMemberStatus.ACTIVE);
        when(otherGroupMember.getRole()).thenReturn(GroupMemberRole.MEMBER);

        // when & then
        assertThatThrownBy(() ->
                groupValidationService.validateGroupOwner(OTHER_GROUP_ID, MEMBER_ID))
                .isInstanceOf(GroupException.class)
                .extracting("code")
                .isEqualTo(GroupErrorCode.GROUP_OWNER_ACCESS_DENIED);
    }

    @Test
    @DisplayName("존재하지 않는 그룹이면 GROUP_NOT_FOUND를 던진다")
    void validateActiveGroupMember_GroupNotFound_ThrowsGroupNotFound() {
        // given
        givenActiveMemberWithoutId();
        when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() ->
                groupValidationService.validateActiveGroupMember(GROUP_ID, MEMBER_ID))
                .isInstanceOf(GroupException.class)
                .extracting("code")
                .isEqualTo(GroupErrorCode.GROUP_NOT_FOUND);
        verify(groupMemberRepository, never()).findByGroupIdAndMemberId(GROUP_ID, MEMBER_ID);
    }

    @Test
    @DisplayName("존재하지 않는 로그인 회원이면 MEMBER_NOT_FOUND를 던진다")
    void validateActiveGroupMember_MemberNotFound_ThrowsMemberNotFound() {
        // given
        when(memberQueryService.getActiveMember(MEMBER_ID))
                .thenThrow(new MemberException(MemberErrorCode.MEMBER_NOT_FOUND));

        // when & then
        assertThatThrownBy(() ->
                groupValidationService.validateActiveGroupMember(GROUP_ID, MEMBER_ID))
                .isInstanceOf(MemberException.class)
                .extracting("code")
                .isEqualTo(MemberErrorCode.MEMBER_NOT_FOUND);
        verify(groupRepository, never()).findById(GROUP_ID);
    }

    @Test
    @DisplayName("비활성 로그인 회원이면 WITHDRAWN_MEMBER를 던진다")
    void validateActiveGroupMember_InactiveMember_ThrowsWithdrawnMember() {
        // given
        when(memberQueryService.getActiveMember(MEMBER_ID))
                .thenThrow(new MemberException(MemberErrorCode.WITHDRAWN_MEMBER));

        // when & then
        assertThatThrownBy(() ->
                groupValidationService.validateActiveGroupMember(GROUP_ID, MEMBER_ID))
                .isInstanceOf(MemberException.class)
                .extracting("code")
                .isEqualTo(MemberErrorCode.WITHDRAWN_MEMBER);
        verify(groupRepository, never()).findById(GROUP_ID);
    }

    private void givenActiveMember() {
        givenActiveMemberWithoutId();
        when(member.getId()).thenReturn(MEMBER_ID);
    }

    private void givenActiveMemberWithoutId() {
        when(memberQueryService.getActiveMember(MEMBER_ID)).thenReturn(member);
    }

    private void givenActiveGroup(Long groupId, Group targetGroup) {
        when(groupRepository.findById(groupId)).thenReturn(Optional.of(targetGroup));
        when(targetGroup.getStatus()).thenReturn(GroupStatus.ACTIVE);
        when(targetGroup.getId()).thenReturn(groupId);
    }
}
