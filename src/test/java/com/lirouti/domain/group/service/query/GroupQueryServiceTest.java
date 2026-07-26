package com.lirouti.domain.group.service.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.lirouti.domain.group.dto.response.GroupResDTO;
import com.lirouti.domain.group.enums.GroupRoutineAssignmentStatus;
import com.lirouti.domain.group.repository.GroupRoutineAssignmentRepository;
import com.lirouti.domain.group.repository.GroupRoutineAssignmentRepositoryCustom.TodayAssignmentProjection;
import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.exception.MemberException;
import com.lirouti.domain.member.exception.code.error.MemberErrorCode;
import com.lirouti.domain.member.service.query.MemberQueryService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("GroupQueryService 테스트")
class GroupQueryServiceTest {
    private static final Long MEMBER_ID = 1L;
    private static final LocalDate TODAY = LocalDate.of(2026, 7, 25);

    @Mock
    private GroupRoutineAssignmentRepository assignmentRepository;
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
                memberQueryService,
                clock
        );
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
