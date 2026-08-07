package com.lirouti.domain.group.service.command;

import com.lirouti.domain.group.dto.request.GroupReqDTO;
import com.lirouti.domain.group.dto.response.GroupResDTO;
import com.lirouti.domain.group.entity.Group;
import com.lirouti.domain.group.entity.GroupMember;
import com.lirouti.domain.group.enums.GroupMemberRole;
import com.lirouti.domain.group.enums.GroupMemberStatus;
import com.lirouti.domain.group.exception.GroupException;
import com.lirouti.domain.group.exception.code.error.GroupErrorCode;
import com.lirouti.domain.group.repository.GroupMemberRepository;
import com.lirouti.domain.group.repository.GroupRepository;
import com.lirouti.domain.group.service.GroupValidationService;
import com.lirouti.domain.member.entity.Member;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("GroupJoinCommandService 테스트")
class GroupJoinCommandServiceTest {
    private static final Long MEMBER_ID = 1L;
    private static final Long GROUP_ID = 10L;
    private static final String INVITE_CODE = "AB12CD3";
    private static final LocalDateTime JOINED_AT = LocalDateTime.of(2026, 8, 7, 9, 30);

    @Mock private GroupRepository groupRepository;
    @Mock private GroupMemberRepository groupMemberRepository;
    @Mock private GroupValidationService groupValidationService;
    @Mock private GroupRoutineAssignmentCommandService assignmentCommandService;
    @Mock private Group lookupGroup;
    @Mock private Group lockedGroup;
    @Mock private Member lockedMember;

    private GroupJoinCommandService groupJoinCommandService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-07T00:30:00Z"), ZoneId.of("Asia/Seoul"));
        groupJoinCommandService = new GroupJoinCommandService(
                groupRepository,
                groupMemberRepository,
                groupValidationService,
                assignmentCommandService,
                clock
        );
        lenient().when(groupRepository.findByInviteCode(INVITE_CODE)).thenReturn(Optional.of(lookupGroup));
        lenient().when(lookupGroup.getId()).thenReturn(GROUP_ID);
        lenient().when(groupValidationService.lockActiveGroupAndMemberForJoin(GROUP_ID, MEMBER_ID))
                .thenReturn(new GroupValidationService.JoinLimitContext(lockedGroup, lockedMember));
        lenient().when(lockedGroup.getId()).thenReturn(GROUP_ID);
        lenient().when(lockedGroup.getName()).thenReturn("아침 루틴 모임");
        lenient().when(lockedGroup.isLocked()).thenReturn(false);
        lenient().when(lockedMember.getId()).thenReturn(MEMBER_ID);
        lenient().when(groupMemberRepository.findByGroupIdAndMemberId(GROUP_ID, MEMBER_ID))
                .thenReturn(Optional.empty());
        lenient().when(groupMemberRepository.saveAndFlush(any(GroupMember.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("관계가 없는 회원은 잠긴 그룹·회원 기준으로 가입하고 동일 기준 시각으로 할당을 요청한다")
    void join_NewMember_CreatesMembershipAndAssignments() {
        GroupResDTO.JoinResult result = groupJoinCommandService.join(
                MEMBER_ID, new GroupReqDTO.JoinGroup(INVITE_CODE));

        ArgumentCaptor<GroupMember> membershipCaptor = ArgumentCaptor.forClass(GroupMember.class);
        verify(groupMemberRepository).saveAndFlush(membershipCaptor.capture());
        assertThat(membershipCaptor.getValue().getGroup()).isSameAs(lockedGroup);
        assertThat(membershipCaptor.getValue().getMember()).isSameAs(lockedMember);
        assertThat(membershipCaptor.getValue().getRole()).isEqualTo(GroupMemberRole.MEMBER);
        assertThat(membershipCaptor.getValue().getJoinedAt()).isEqualTo(JOINED_AT);
        verify(groupValidationService).validateJoinLimits(GROUP_ID, MEMBER_ID);
        verify(assignmentCommandService).assignTodayRoutinesToMember(GROUP_ID, MEMBER_ID, JOINED_AT);
        assertThat(result).isEqualTo(new GroupResDTO.JoinResult(
                GROUP_ID, "아침 루틴 모임", GroupMemberStatus.ACTIVE));
    }

    @Test
    @DisplayName("LEFT 관계는 같은 행을 기준 시각으로 재활성화하고 당일 할당을 요청한다")
    void join_LeftMember_RejoinsAndAssignsTodayRoutines() {
        GroupMember leftMembership = GroupMember.builder()
                .group(lockedGroup).member(lockedMember).role(GroupMemberRole.MEMBER)
                .joinedAt(JOINED_AT.minusDays(1)).build();
        leftMembership.leave();
        ReflectionTestUtils.setField(leftMembership, "id", 500L);
        when(groupMemberRepository.findByGroupIdAndMemberId(GROUP_ID, MEMBER_ID))
                .thenReturn(Optional.of(leftMembership));

        groupJoinCommandService.join(MEMBER_ID, new GroupReqDTO.JoinGroup(INVITE_CODE));

        assertThat(leftMembership.getStatus()).isEqualTo(GroupMemberStatus.ACTIVE);
        assertThat(leftMembership.getJoinedAt()).isEqualTo(JOINED_AT);
        assertThat(leftMembership.getLeftAt()).isNull();
        verify(groupMemberRepository).saveAndFlush(leftMembership);
        verify(lockedGroup, never()).addMember(any(GroupMember.class));
        verify(assignmentCommandService).assignTodayRoutinesToMember(GROUP_ID, MEMBER_ID, JOINED_AT);
    }

    @Test
    @DisplayName("LEFT 관계는 상한 검증이 실패하면 재활성화하지 않는다")
    void join_LeftMember_LimitFailure_DoesNotRejoinBeforeValidation() {
        GroupMember leftMembership = GroupMember.builder()
                .group(lockedGroup).member(lockedMember).role(GroupMemberRole.MEMBER)
                .joinedAt(JOINED_AT.minusDays(1)).build();
        leftMembership.leave();
        when(groupMemberRepository.findByGroupIdAndMemberId(GROUP_ID, MEMBER_ID))
                .thenReturn(Optional.of(leftMembership));
        doThrow(new GroupException(GroupErrorCode.GROUP_MEMBER_LIMIT_EXCEEDED))
                .when(groupValidationService).validateJoinLimits(GROUP_ID, MEMBER_ID);

        assertThatThrownBy(() -> groupJoinCommandService.join(
                MEMBER_ID, new GroupReqDTO.JoinGroup(INVITE_CODE)))
                .isInstanceOf(GroupException.class).extracting("code")
                .isEqualTo(GroupErrorCode.GROUP_MEMBER_LIMIT_EXCEEDED);

        assertThat(leftMembership.getStatus()).isEqualTo(GroupMemberStatus.LEFT);
        verifyNoInteractions(assignmentCommandService);
    }

    @Test
    @DisplayName("ACTIVE와 KICKED 관계는 가입을 거부하고 상한·할당 처리를 수행하지 않는다")
    void join_ActiveOrKickedMember_RejectsBeforeLimitsAndAssignment() {
        GroupMember active = mock(GroupMember.class);
        when(active.getStatus()).thenReturn(GroupMemberStatus.ACTIVE);
        when(groupMemberRepository.findByGroupIdAndMemberId(GROUP_ID, MEMBER_ID))
                .thenReturn(Optional.of(active));

        assertThatThrownBy(() -> groupJoinCommandService.join(
                MEMBER_ID, new GroupReqDTO.JoinGroup(INVITE_CODE)))
                .isInstanceOf(GroupException.class).extracting("code")
                .isEqualTo(GroupErrorCode.ALREADY_ACTIVE_GROUP_MEMBER);

        GroupMember kicked = mock(GroupMember.class);
        when(kicked.getStatus()).thenReturn(GroupMemberStatus.KICKED);
        when(groupMemberRepository.findByGroupIdAndMemberId(GROUP_ID, MEMBER_ID))
                .thenReturn(Optional.of(kicked));

        assertThatThrownBy(() -> groupJoinCommandService.join(
                MEMBER_ID, new GroupReqDTO.JoinGroup(INVITE_CODE)))
                .isInstanceOf(GroupException.class).extracting("code")
                .isEqualTo(GroupErrorCode.KICKED_MEMBER_CANNOT_REJOIN);
        verify(groupValidationService, never()).validateJoinLimits(GROUP_ID, MEMBER_ID);
        verifyNoInteractions(assignmentCommandService);
    }

    @Test
    @DisplayName("잠긴 그룹과 비활성 그룹은 가입을 거부한다")
    void join_LockedOrInactiveGroup_Rejects() {
        when(lockedGroup.isLocked()).thenReturn(true);

        assertThatThrownBy(() -> groupJoinCommandService.join(
                MEMBER_ID, new GroupReqDTO.JoinGroup(INVITE_CODE)))
                .isInstanceOf(GroupException.class).extracting("code")
                .isEqualTo(GroupErrorCode.GROUP_LOCKED);

        GroupException inactive = new GroupException(GroupErrorCode.GROUP_INACTIVE);
        when(groupValidationService.lockActiveGroupAndMemberForJoin(GROUP_ID, MEMBER_ID))
                .thenThrow(inactive);
        assertThatThrownBy(() -> groupJoinCommandService.join(
                MEMBER_ID, new GroupReqDTO.JoinGroup(INVITE_CODE))).isSameAs(inactive);
    }

    @Test
    @DisplayName("참여 상한과 Assignment 실패는 가입 Command에서 그대로 전파된다")
    void join_LimitOrAssignmentFailure_PropagatesAndDoesNotComplete() {
        GroupException limitExceeded = new GroupException(GroupErrorCode.GROUP_MEMBER_LIMIT_EXCEEDED);
        doThrow(limitExceeded).when(groupValidationService).validateJoinLimits(GROUP_ID, MEMBER_ID);

        assertThatThrownBy(() -> groupJoinCommandService.join(
                MEMBER_ID, new GroupReqDTO.JoinGroup(INVITE_CODE))).isSameAs(limitExceeded);
        verifyNoInteractions(assignmentCommandService);

        reset(groupValidationService, assignmentCommandService);
        when(groupValidationService.lockActiveGroupAndMemberForJoin(GROUP_ID, MEMBER_ID))
                .thenReturn(new GroupValidationService.JoinLimitContext(lockedGroup, lockedMember));
        IllegalStateException assignmentFailure = new IllegalStateException("assignment failure");
        doThrow(assignmentFailure).when(assignmentCommandService)
                .assignTodayRoutinesToMember(GROUP_ID, MEMBER_ID, JOINED_AT);

        assertThatThrownBy(() -> groupJoinCommandService.join(
                MEMBER_ID, new GroupReqDTO.JoinGroup(INVITE_CODE))).isSameAs(assignmentFailure);
    }

    @Test
    @DisplayName("동시 신규 가입의 group_member unique 충돌은 중복 가입 오류로 변환한다")
    void join_GroupMemberUniqueViolation_ConvertsToAlreadyActiveMember() {
        when(groupMemberRepository.saveAndFlush(any(GroupMember.class))).thenThrow(
                uniqueViolation(GroupDatabaseConstraints.GROUP_MEMBER));

        assertThatThrownBy(() -> groupJoinCommandService.join(
                MEMBER_ID, new GroupReqDTO.JoinGroup(INVITE_CODE)))
                .isInstanceOf(GroupException.class).extracting("code")
                .isEqualTo(GroupErrorCode.ALREADY_ACTIVE_GROUP_MEMBER);
        verifyNoInteractions(assignmentCommandService);
    }

    private DataIntegrityViolationException uniqueViolation(String constraintName) {
        SQLException sqlException = new SQLException("duplicate value", "23000", 1062);
        ConstraintViolationException violation = new ConstraintViolationException(
                "duplicate value", sqlException, ConstraintViolationException.ConstraintKind.UNIQUE, constraintName);
        return new DataIntegrityViolationException("duplicate value", violation);
    }
}
