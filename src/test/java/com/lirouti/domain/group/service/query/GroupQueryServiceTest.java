package com.lirouti.domain.group.service.query;

import com.lirouti.domain.group.dto.response.GroupResDTO;
import com.lirouti.domain.group.enums.GroupRoutineAssignmentStatus;
import com.lirouti.domain.group.exception.GroupException;
import com.lirouti.domain.group.exception.code.error.GroupErrorCode;
import com.lirouti.domain.group.repository.GroupRoutineAssignmentRepository;
import com.lirouti.domain.group.repository.GroupRoutineAssignmentRepositoryCustom.TodayAssignmentProjection;
import com.lirouti.domain.group.repository.GroupRoutineCategoryRepository;
import com.lirouti.domain.group.repository.GroupDetailQueryRepository;
import com.lirouti.domain.group.service.GroupValidationService;
import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.exception.MemberException;
import com.lirouti.domain.member.exception.code.error.MemberErrorCode;
import com.lirouti.domain.member.service.query.MemberQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.*;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("GroupQueryService 테스트")
class GroupQueryServiceTest {
    private static final Long MEMBER_ID = 1L;
    private static final LocalDate TODAY = LocalDate.of(2026, 7, 25);

    @Mock
    private GroupRoutineAssignmentRepository assignmentRepository;
    @Mock
    private GroupDetailQueryRepository groupDetailQueryRepository;
    @Mock
    private GroupRoutineCategoryRepository categoryRepository;
    @Mock
    private GroupValidationService groupValidationService;
    @Mock
    private MemberQueryService memberQueryService;
    @Mock
    private Member member;

    private GroupQueryService groupQueryService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(
                Instant.parse("2026-07-25T00:00:00Z"),
                ZoneId.of("Asia/Seoul")
        );
        groupQueryService = new GroupQueryService(
                assignmentRepository,
                groupDetailQueryRepository,
                categoryRepository,
                groupValidationService,
                memberQueryService,
                clock
        );
    }

    @Test
    @DisplayName("ACTIVE 구성원의 활동 상태와 오늘 진행도를 그룹 상세 응답으로 조립한다")
    void getGroupDetail_ActiveMember_ReturnsDetailAndDailyProgress() {
        // given
        Long groupId = 301L;
        when(groupDetailQueryRepository.findActiveMemberDetails(groupId)).thenReturn(List.of(
                new GroupDetailQueryRepository.GroupMemberDetailProjection(
                        groupId, "우리 집", "DETAIL1", MEMBER_ID, "리루티",
                        "profiles/member-1.png", "오늘도 완료", 4, 12L),
                new GroupDetailQueryRepository.GroupMemberDetailProjection(
                        groupId, "우리 집", "DETAIL1", 2L, "동료",
                        null, null, 1, 3L)
        ));
        when(groupDetailQueryRepository.findTodayMemberProgress(groupId, TODAY)).thenReturn(List.of(
                new GroupDetailQueryRepository.TodayMemberProgressProjection(MEMBER_ID, 3L, 2L)
        ));

        // when
        GroupResDTO.Detail result = groupQueryService.getGroupDetail(groupId, MEMBER_ID);

        // then
        verify(groupValidationService).validateActiveGroupMember(groupId, MEMBER_ID);
        verify(groupDetailQueryRepository).findActiveMemberDetails(groupId);
        verify(groupDetailQueryRepository).findTodayMemberProgress(groupId, TODAY);
        assertThat(result.groupId()).isEqualTo(groupId);
        assertThat(result.groupName()).isEqualTo("우리 집");
        assertThat(result.inviteCode()).isEqualTo("DETAIL1");
        assertThat(result.members()).containsExactly(
                new GroupResDTO.MemberActivity(
                        MEMBER_ID, "리루티", "profiles/member-1.png", "오늘도 완료",
                        4, 12L, new GroupResDTO.DailyProgress(2L, 3L)),
                new GroupResDTO.MemberActivity(
                        2L, "동료", null, null,
                        1, 3L, new GroupResDTO.DailyProgress(0L, 0L))
        );
    }

    @Test
    @DisplayName("비구성원은 그룹 상세 집계 조회 전에 거부한다")
    void getGroupDetail_NonMember_ThrowsAccessDenied() {
        // given
        Long groupId = 301L;
        GroupException exception = new GroupException(GroupErrorCode.GROUP_MEMBER_ACCESS_DENIED);
        when(groupValidationService.validateActiveGroupMember(groupId, MEMBER_ID)).thenThrow(exception);

        // when & then
        assertThatThrownBy(() -> groupQueryService.getGroupDetail(groupId, MEMBER_ID))
                .isSameAs(exception);
        verifyNoInteractions(groupDetailQueryRepository);
    }

    @Test
    @DisplayName("활성 회원의 오늘 할당 Projection을 응답 DTO로 변환한다")
    void getTodayRoutines_ActiveMember_ReturnsConvertedResponse() {
        // given
        when(memberQueryService.getActiveMember(MEMBER_ID)).thenReturn(member);
        when(member.getId()).thenReturn(MEMBER_ID);
        when(assignmentRepository.findTodayAssignmentsByMemberId(MEMBER_ID, TODAY))
                .thenReturn(List.of(projection()));

        // when
        GroupResDTO.TodayRoutineList result = groupQueryService.getTodayRoutines(MEMBER_ID);

        // then
        verify(memberQueryService).getActiveMember(MEMBER_ID);
        verify(assignmentRepository).findTodayAssignmentsByMemberId(MEMBER_ID, TODAY);
        assertThat(result.routines()).singleElement().satisfies(routine -> {
            assertThat(routine.assignmentId()).isEqualTo(101L);
            assertThat(routine.routineId()).isEqualTo(201L);
            assertThat(routine.groupId()).isEqualTo(301L);
            assertThat(routine.groupName()).isEqualTo("우리 집");
            assertThat(routine.categoryId()).isEqualTo(401L);
            assertThat(routine.categoryName()).isEqualTo("청소");
            assertThat(routine.title()).isEqualTo("거실 정리");
            assertThat(routine.description()).isEqualTo("거실을 함께 정리합니다.");
            assertThat(routine.assignedDate()).isEqualTo(TODAY);
            assertThat(routine.scheduledStartTime()).isEqualTo(LocalTime.of(9, 0));
            assertThat(routine.scheduledEndTime()).isEqualTo(LocalTime.of(10, 0));
            assertThat(routine.status()).isEqualTo(GroupRoutineAssignmentStatus.IN_PROGRESS);
        });
    }

    @Test
    @DisplayName("활성 회원에게 오늘 할당이 없으면 빈 목록을 반환한다")
    void getTodayRoutines_NoAssignments_ReturnsEmptyList() {
        // given
        when(memberQueryService.getActiveMember(MEMBER_ID)).thenReturn(member);
        when(member.getId()).thenReturn(MEMBER_ID);
        when(assignmentRepository.findTodayAssignmentsByMemberId(MEMBER_ID, TODAY))
                .thenReturn(List.of());

        // when
        GroupResDTO.TodayRoutineList result = groupQueryService.getTodayRoutines(MEMBER_ID);

        // then
        assertThat(result.routines()).isEmpty();
    }

    @Test
    @DisplayName("존재하지 않는 회원이면 예외를 전파하고 할당을 조회하지 않는다")
    void getTodayRoutines_MemberNotFound_PropagatesException() {
        // given
        MemberException exception = new MemberException(MemberErrorCode.MEMBER_NOT_FOUND);
        when(memberQueryService.getActiveMember(MEMBER_ID)).thenThrow(exception);

        // when & then
        assertThatThrownBy(() -> groupQueryService.getTodayRoutines(MEMBER_ID))
                .isSameAs(exception)
                .hasFieldOrPropertyWithValue("code", MemberErrorCode.MEMBER_NOT_FOUND);
        verifyNoInteractions(assignmentRepository);
    }

    @Test
    @DisplayName("탈퇴하거나 비활성인 회원이면 예외를 전파하고 할당을 조회하지 않는다")
    void getTodayRoutines_WithdrawnMember_PropagatesException() {
        // given
        MemberException exception = new MemberException(MemberErrorCode.WITHDRAWN_MEMBER);
        when(memberQueryService.getActiveMember(MEMBER_ID)).thenThrow(exception);

        // when & then
        assertThatThrownBy(() -> groupQueryService.getTodayRoutines(MEMBER_ID))
                .isSameAs(exception)
                .hasFieldOrPropertyWithValue("code", MemberErrorCode.WITHDRAWN_MEMBER);
        verifyNoInteractions(assignmentRepository);
    }

    private TodayAssignmentProjection projection() {
        return new TodayAssignmentProjection(
                101L,
                201L,
                301L,
                "우리 집",
                401L,
                "청소",
                "거실 정리",
                "거실을 함께 정리합니다.",
                TODAY,
                LocalTime.of(9, 0),
                LocalTime.of(10, 0),
                GroupRoutineAssignmentStatus.IN_PROGRESS
        );
    }
}
