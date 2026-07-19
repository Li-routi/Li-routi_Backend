package com.lirouti.domain.group.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.empty());

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
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));
        when(member.getIsActive()).thenReturn(false);

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
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));
        when(member.getIsActive()).thenReturn(true);
        when(member.getDeletedAt()).thenReturn(null);
    }

    private void givenActiveGroup(Long groupId, Group targetGroup) {
        when(groupRepository.findById(groupId)).thenReturn(Optional.of(targetGroup));
        when(targetGroup.getStatus()).thenReturn(GroupStatus.ACTIVE);
        when(targetGroup.getId()).thenReturn(groupId);
    }
}
