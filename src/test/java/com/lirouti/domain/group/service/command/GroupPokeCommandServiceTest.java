package com.lirouti.domain.group.service.command;

import com.lirouti.domain.group.dto.response.GroupResDTO;
import com.lirouti.domain.group.entity.GroupMember;
import com.lirouti.domain.group.enums.GroupMemberStatus;
import com.lirouti.domain.group.exception.GroupException;
import com.lirouti.domain.group.exception.code.error.GroupErrorCode;
import com.lirouti.domain.group.repository.GroupMemberRepository;
import com.lirouti.domain.group.repository.GroupMemberLockCandidate;
import com.lirouti.domain.group.service.GroupValidationService;
import com.lirouti.domain.member.entity.Member;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("그룹 구성원 찌르기 명령 서비스 테스트")
class GroupPokeCommandServiceTest {
    private static final Long GROUP_ID = 10L;
    private static final Long REQUESTER_ID = 1L;
    private static final Long TARGET_ID = 2L;

    @Mock private GroupValidationService groupValidationService;
    @Mock private GroupMemberRepository groupMemberRepository;

    @Test
    @DisplayName("두 ACTIVE 멤버십을 잠근 뒤 대상의 누적 찔림 수를 증가시킨다")
    void poke_ActiveDifferentMembers_IncreasesTargetCount() {
        GroupPokeCommandService service = new GroupPokeCommandService(
                groupValidationService, groupMemberRepository);
        GroupMember requester = membership(REQUESTER_ID);
        GroupMember target = membership(TARGET_ID);
        when(target.getTotalPokeCount()).thenReturn(7L);
        when(groupMemberRepository.findActiveMembershipLockCandidatesByGroupIdAndMemberIds(
                GROUP_ID, List.of(REQUESTER_ID, TARGET_ID)))
                .thenReturn(List.of(candidate(REQUESTER_ID, 10L), candidate(TARGET_ID, 20L)));
        when(groupMemberRepository.findByIdForUpdate(10L)).thenReturn(java.util.Optional.of(requester));
        when(groupMemberRepository.findByIdForUpdate(20L)).thenReturn(java.util.Optional.of(target));

        GroupResDTO.PokeResult result = service.poke(GROUP_ID, REQUESTER_ID, TARGET_ID);

        assertThat(result).isEqualTo(new GroupResDTO.PokeResult(TARGET_ID, 7L));
        verify(target).increaseTotalPokeCount();
        InOrder order = inOrder(groupValidationService, groupMemberRepository);
        order.verify(groupValidationService).validateActiveGroupMember(GROUP_ID, REQUESTER_ID);
        order.verify(groupMemberRepository).findActiveMembershipLockCandidatesByGroupIdAndMemberIds(
                GROUP_ID, List.of(REQUESTER_ID, TARGET_ID));
        order.verify(groupMemberRepository).findByIdForUpdate(10L);
        order.verify(groupMemberRepository).findByIdForUpdate(20L);
    }

    @Test
    @DisplayName("자기 자신을 찌르려 하면 멤버십 잠금 전에 거부한다")
    void poke_Self_ThrowsCannotPokeSelf() {
        GroupPokeCommandService service = new GroupPokeCommandService(
                groupValidationService, groupMemberRepository);

        assertThatThrownBy(() -> service.poke(GROUP_ID, REQUESTER_ID, REQUESTER_ID))
                .isInstanceOf(GroupException.class)
                .extracting("code")
                .isEqualTo(GroupErrorCode.CANNOT_POKE_SELF);
        verifyNoInteractions(groupMemberRepository);
    }

    @Test
    @DisplayName("잠금 시 대상 ACTIVE 멤버십이 없으면 대상 미존재 오류를 반환한다")
    void poke_InactiveTarget_ThrowsActiveGroupMemberNotFound() {
        GroupPokeCommandService service = new GroupPokeCommandService(
                groupValidationService, groupMemberRepository);
        GroupMember requester = membership(REQUESTER_ID);
        when(groupMemberRepository.findActiveMembershipLockCandidatesByGroupIdAndMemberIds(
                GROUP_ID, List.of(REQUESTER_ID, TARGET_ID)))
                .thenReturn(List.of(candidate(REQUESTER_ID, 10L)));
        when(groupMemberRepository.findByIdForUpdate(10L)).thenReturn(java.util.Optional.of(requester));

        assertThatThrownBy(() -> service.poke(GROUP_ID, REQUESTER_ID, TARGET_ID))
                .isInstanceOf(GroupException.class)
                .extracting("code")
                .isEqualTo(GroupErrorCode.ACTIVE_GROUP_MEMBER_NOT_FOUND);
    }

    @Test
    @DisplayName("최초 검증 뒤 요청자 ACTIVE 멤버십이 사라지면 접근 거부한다")
    void poke_RequesterBecameInactive_ThrowsAccessDenied() {
        GroupPokeCommandService service = new GroupPokeCommandService(
                groupValidationService, groupMemberRepository);
        GroupMember target = membership(TARGET_ID);
        when(groupMemberRepository.findActiveMembershipLockCandidatesByGroupIdAndMemberIds(
                GROUP_ID, List.of(REQUESTER_ID, TARGET_ID)))
                .thenReturn(List.of(candidate(TARGET_ID, 20L)));
        when(groupMemberRepository.findByIdForUpdate(20L)).thenReturn(java.util.Optional.of(target));

        assertThatThrownBy(() -> service.poke(GROUP_ID, REQUESTER_ID, TARGET_ID))
                .isInstanceOf(GroupException.class)
                .extracting("code")
                .isEqualTo(GroupErrorCode.GROUP_MEMBER_ACCESS_DENIED);
    }

    private GroupMember membership(Long memberId) {
        GroupMember membership = mock(GroupMember.class);
        Member member = mock(Member.class);
        when(membership.getMember()).thenReturn(member);
        when(member.getId()).thenReturn(memberId);
        when(membership.getStatus()).thenReturn(GroupMemberStatus.ACTIVE);
        return membership;
    }

    private GroupMemberLockCandidate candidate(
            Long memberId,
            Long groupMemberId
    ) {
        return new GroupMemberLockCandidate(memberId, groupMemberId);
    }
}
