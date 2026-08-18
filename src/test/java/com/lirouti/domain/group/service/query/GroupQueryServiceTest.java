package com.lirouti.domain.group.service.query;

import com.lirouti.domain.achievement.repository.AchievementRepository;
import com.lirouti.domain.group.dto.response.GroupResDTO;
import com.lirouti.domain.group.entity.GroupMember;
import com.lirouti.domain.group.enums.GroupMemberRole;
import com.lirouti.domain.group.enums.GroupRoutineAssignmentStatus;
import com.lirouti.domain.group.exception.GroupException;
import com.lirouti.domain.group.exception.code.error.GroupErrorCode;
import com.lirouti.domain.group.repository.GroupRoutineAssignmentRepository;
import com.lirouti.domain.group.repository.GroupRoutineAssignmentRepositoryCustom.TodayAssignmentProjection;
import com.lirouti.domain.group.repository.GroupRoutineCategoryRepository;
import com.lirouti.domain.group.repository.GroupDetailQueryRepository;
import com.lirouti.domain.group.repository.GroupListQueryRepository;
import com.lirouti.domain.group.repository.GroupListQueryRepository.AssignmentCountProjection;
import com.lirouti.domain.group.repository.GroupListQueryRepository.GroupCountProjection;
import com.lirouti.domain.group.repository.GroupListQueryRepository.GroupScheduleCountProjection;
import com.lirouti.domain.group.repository.GroupListQueryRepository.MyGroupProjection;
import com.lirouti.domain.group.repository.GroupRoutineQueryRepository;
import com.lirouti.domain.group.repository.GroupRoutineQueryRepository.GroupRoutineProjection;
import com.lirouti.domain.group.repository.GroupRoutineQueryRepository.RoutineScheduleProjection;
import com.lirouti.domain.group.service.GroupValidationService;
import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.exception.MemberException;
import com.lirouti.domain.member.exception.code.error.MemberErrorCode;
import com.lirouti.domain.member.repository.MemberRepository;
import com.lirouti.domain.member.service.query.MemberQueryService;
import com.lirouti.domain.shop.entity.AvatarItem;
import com.lirouti.domain.shop.entity.MemberAvatarEquipment;
import com.lirouti.domain.shop.enums.AvatarSlot;
import com.lirouti.domain.shop.repository.MemberAvatarEquipmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import com.lirouti.domain.character.service.query.AvatarLayerAssembler;
import com.lirouti.domain.media.service.MediaService;
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
    private GroupListQueryRepository groupListQueryRepository;
    @Mock
    private GroupRoutineQueryRepository groupRoutineQueryRepository;
    @Mock
    private GroupRoutineCategoryRepository categoryRepository;
    @Mock
    private MemberAvatarEquipmentRepository memberAvatarEquipmentRepository;
    @Mock
    private MediaService mediaService;
    @Mock
    private AvatarLayerAssembler avatarLayerAssembler;
    @Mock
    private GroupValidationService groupValidationService;
    @Mock
    private MemberQueryService memberQueryService;
    @Mock
    private Member member;
    @Mock
    private MemberRepository memberRepository;
    @Mock
    private AchievementRepository achievementRepository;

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
                groupListQueryRepository,
                groupRoutineQueryRepository,
                categoryRepository,
                memberAvatarEquipmentRepository,
                mediaService,
                avatarLayerAssembler,
                groupValidationService,
                memberQueryService,
                clock,
                memberRepository,
                achievementRepository
        );
    }

    @Test
    @DisplayName("ACTIVE 구성원의 활동 상태와 오늘 진행도를 그룹 상세 응답으로 조립한다")
    void getGroupDetail_ActiveMember_ReturnsDetailAndDailyProgress() {
        // given
        Long groupId = 301L;
        GroupMember currentMembership = mock(GroupMember.class);
        when(currentMembership.getRole()).thenReturn(GroupMemberRole.MEMBER);
        when(groupValidationService.validateActiveGroupMember(groupId, MEMBER_ID))
                .thenReturn(currentMembership);
        when(groupDetailQueryRepository.findActiveMemberDetails(groupId)).thenReturn(List.of(
                new GroupDetailQueryRepository.GroupMemberDetailProjection(
                        groupId, "우리 집", "DETAIL1", MEMBER_ID, "리루티",
                        "오늘도 완료", 4, 12L, 7L, 2L),
                new GroupDetailQueryRepository.GroupMemberDetailProjection(
                        groupId, "우리 집", "DETAIL1", 2L, "동료",
                        null, 1, 3L, 0L, 1L)
        ));
        when(groupDetailQueryRepository.findTodayMemberProgress(groupId, TODAY)).thenReturn(List.of(
                new GroupDetailQueryRepository.TodayMemberProgressProjection(MEMBER_ID, 3L, 2L)
        ));
        Member equipmentOwner = mock(Member.class);
        AvatarItem avatarItem = mock(AvatarItem.class);
        MemberAvatarEquipment equipment = mock(MemberAvatarEquipment.class);
        when(equipmentOwner.getId()).thenReturn(MEMBER_ID);
        when(avatarItem.getImageKey()).thenReturn("avatar/item/head/hat-v1.png");
        when(mediaService.resolveAvatarAssetUrl("avatar/item/head/hat-v1.png"))
                .thenReturn("https://cdn/hat.png");
        when(equipment.getMember()).thenReturn(equipmentOwner);
        when(equipment.getAvatarItem()).thenReturn(avatarItem);
        when(equipment.getSlot()).thenReturn(AvatarSlot.HEAD);
        when(memberAvatarEquipmentRepository.findAllByMemberIdInWithMemberAndAvatarItem(
                List.of(MEMBER_ID, 2L))).thenReturn(List.of(equipment));
        when(memberRepository.findAllById(anyList())).thenReturn(List.of());

        // when
        GroupResDTO.Detail result = groupQueryService.getGroupDetail(groupId, MEMBER_ID);

        // then
        verify(groupValidationService).validateActiveGroupMember(groupId, MEMBER_ID);
        verify(groupDetailQueryRepository).findActiveMemberDetails(groupId);
        verify(groupDetailQueryRepository).findTodayMemberProgress(groupId, TODAY);
        verify(memberAvatarEquipmentRepository)
                .findAllByMemberIdInWithMemberAndAvatarItem(List.of(MEMBER_ID, 2L));
        assertThat(result.groupId()).isEqualTo(groupId);
        assertThat(result.groupName()).isEqualTo("우리 집");
        assertThat(result.inviteCode()).isEqualTo("DETAIL1");
        assertThat(result.myRole()).isEqualTo(GroupMemberRole.MEMBER);
        assertThat(result.members()).containsExactly(
                new GroupResDTO.MemberActivity(
                        MEMBER_ID, "리루티", new GroupResDTO.Avatar(List.of(
                        new GroupResDTO.Equipped(
                                AvatarSlot.HEAD, "https://cdn/hat.png")), List.of()), "오늘도 완료",
                        4, 12L, 7L, 2L, new GroupResDTO.DailyProgress(2L, 3L), null),
                new GroupResDTO.MemberActivity(
                        2L, "동료", new GroupResDTO.Avatar(List.of(), List.of()), null,
                        1, 3L, 0L, 1L, new GroupResDTO.DailyProgress(0L, 0L), null)
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
    @DisplayName("ACTIVE OWNER의 활성 루틴 projection과 일정을 월요일부터 조립한다")
    void getGroupRoutines_ActiveOwner_ReturnsRoutinesWithOrderedSchedules() {
        // given
        Long groupId = 301L;
        List<GroupRoutineProjection> routines = List.of(
                new GroupRoutineProjection(202L, 402L, "건강", "물 마시기", "하루 2L"),
                new GroupRoutineProjection(201L, 401L, "청소", "거실 정리", "거실을 정리합니다.")
        );
        when(groupRoutineQueryRepository.findActiveRoutinesByGroupId(groupId)).thenReturn(routines);
        when(groupRoutineQueryRepository.findSchedulesByRoutineIds(List.of(202L, 201L)))
                .thenReturn(List.of(
                        new RoutineScheduleProjection(
                                202L, DayOfWeek.SUNDAY, LocalTime.of(18, 0), LocalTime.of(19, 0)),
                        new RoutineScheduleProjection(
                                202L, DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(10, 0)),
                        new RoutineScheduleProjection(
                                201L, DayOfWeek.WEDNESDAY, LocalTime.of(12, 0), LocalTime.of(13, 0))
                ));

        // when
        GroupResDTO.GroupRoutineList result = groupQueryService.getGroupRoutines(groupId, MEMBER_ID);

        // then
        verify(groupValidationService).validateGroupOwner(groupId, MEMBER_ID);
        verify(groupRoutineQueryRepository).findActiveRoutinesByGroupId(groupId);
        verify(groupRoutineQueryRepository).findSchedulesByRoutineIds(List.of(202L, 201L));
        assertThat(result.routines()).containsExactly(
                new GroupResDTO.GroupRoutineItem(
                        202L, 402L, "건강", "물 마시기", "하루 2L",
                        List.of(
                                new GroupResDTO.RoutineSchedule(
                                        DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(10, 0)),
                                new GroupResDTO.RoutineSchedule(
                                        DayOfWeek.SUNDAY, LocalTime.of(18, 0), LocalTime.of(19, 0))
                        )),
                new GroupResDTO.GroupRoutineItem(
                        201L, 401L, "청소", "거실 정리", "거실을 정리합니다.",
                        List.of(new GroupResDTO.RoutineSchedule(
                                DayOfWeek.WEDNESDAY, LocalTime.of(12, 0), LocalTime.of(13, 0))))
        );
    }

    @Test
    @DisplayName("ACTIVE OWNER에게 활성 루틴이 없으면 일정 조회 없이 빈 목록을 반환한다")
    void getGroupRoutines_NoActiveRoutines_ReturnsEmptyList() {
        // given
        Long groupId = 301L;
        when(groupRoutineQueryRepository.findActiveRoutinesByGroupId(groupId)).thenReturn(List.of());

        // when
        GroupResDTO.GroupRoutineList result = groupQueryService.getGroupRoutines(groupId, MEMBER_ID);

        // then
        assertThat(result.routines()).isEmpty();
        verify(groupValidationService).validateGroupOwner(groupId, MEMBER_ID);
        verify(groupRoutineQueryRepository).findActiveRoutinesByGroupId(groupId);
        verify(groupRoutineQueryRepository, never()).findSchedulesByRoutineIds(anyList());
    }

    @Test
    @DisplayName("OWNER 검증에 실패하면 루틴 조회를 수행하지 않는다")
    void getGroupRoutines_AccessDenied_DoesNotQueryRoutines() {
        // given
        Long groupId = 301L;
        GroupException exception = new GroupException(GroupErrorCode.GROUP_OWNER_ACCESS_DENIED);
        doThrow(exception).when(groupValidationService).validateGroupOwner(groupId, MEMBER_ID);

        // when & then
        assertThatThrownBy(() -> groupQueryService.getGroupRoutines(groupId, MEMBER_ID))
                .isSameAs(exception);
        verifyNoInteractions(groupRoutineQueryRepository);
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

    @Test
    @DisplayName("참여 그룹을 고정 횟수 배치 집계로 조립하고 미래 일정까지 월간 달성률에 반영한다")
    void getMyGroups_ActiveGroups_ReturnsBatchedSummary() {
        // given
        Long firstGroupId = 301L;
        Long secondGroupId = 302L;
        List<Long> groupIds = List.of(firstGroupId, secondGroupId);
        when(memberQueryService.getActiveMember(MEMBER_ID)).thenReturn(member);
        when(member.getId()).thenReturn(MEMBER_ID);
        when(groupListQueryRepository.findActiveGroupsByMemberId(MEMBER_ID)).thenReturn(List.of(
                new MyGroupProjection(firstGroupId, "아침 모임", 4, LocalDateTime.of(2026, 7, 24, 9, 30)),
                new MyGroupProjection(secondGroupId, "저녁 모임", 1, null)
        ));
        when(groupListQueryRepository.countActiveMembersByGroupIds(groupIds)).thenReturn(List.of(
                new GroupCountProjection(firstGroupId, 4L),
                new GroupCountProjection(secondGroupId, 2L)
        ));
        when(groupListQueryRepository.countActiveRoutinesByGroupIds(groupIds)).thenReturn(List.of(
                new GroupCountProjection(firstGroupId, 6L)
        ));
        when(groupListQueryRepository.findTodayAssignmentCounts(MEMBER_ID, groupIds, TODAY)).thenReturn(List.of(
                new AssignmentCountProjection(firstGroupId, 3L, 2L)
        ));
        when(groupListQueryRepository.countTodayVerificationsByGroupIds(groupIds, TODAY)).thenReturn(List.of(
                new GroupCountProjection(firstGroupId, 8L)
        ));
        when(groupListQueryRepository.findMonthlyAssignmentCounts(
                MEMBER_ID, groupIds, LocalDate.of(2026, 7, 1), TODAY)).thenReturn(List.of(
                new AssignmentCountProjection(firstGroupId, 3L, 1L)
        ));
        when(groupListQueryRepository.countActiveSchedulesByGroupIds(groupIds)).thenReturn(List.of(
                new GroupScheduleCountProjection(firstGroupId, DayOfWeek.SUNDAY, 1L),
                new GroupScheduleCountProjection(firstGroupId, DayOfWeek.FRIDAY, 1L),
                new GroupScheduleCountProjection(secondGroupId, DayOfWeek.MONDAY, 1L)
        ));

        // when
        GroupResDTO.MyGroupList result = groupQueryService.getMyGroups(MEMBER_ID);

        // then
        assertThat(result.groups()).containsExactly(
                new GroupResDTO.MyGroup(firstGroupId, "아침 모임", 4L, 6L, 3L, 2L, 4, 20, 8L,
                        LocalDateTime.of(2026, 7, 24, 9, 30)),
                new GroupResDTO.MyGroup(secondGroupId, "저녁 모임", 2L, 0L, 0L, 0L, 1, 0, 0L, null)
        );
        verify(groupListQueryRepository).findActiveGroupsByMemberId(MEMBER_ID);
        verify(groupListQueryRepository).countActiveMembersByGroupIds(groupIds);
        verify(groupListQueryRepository).countActiveRoutinesByGroupIds(groupIds);
        verify(groupListQueryRepository).findTodayAssignmentCounts(MEMBER_ID, groupIds, TODAY);
        verify(groupListQueryRepository).countTodayVerificationsByGroupIds(groupIds, TODAY);
        verify(groupListQueryRepository).findMonthlyAssignmentCounts(
                MEMBER_ID, groupIds, LocalDate.of(2026, 7, 1), TODAY);
        verify(groupListQueryRepository).countActiveSchedulesByGroupIds(groupIds);
    }

    @Test
    @DisplayName("참여 그룹이 없으면 추가 집계 없이 빈 목록을 반환한다")
    void getMyGroups_NoActiveGroups_ReturnsEmptyList() {
        when(memberQueryService.getActiveMember(MEMBER_ID)).thenReturn(member);
        when(member.getId()).thenReturn(MEMBER_ID);
        when(groupListQueryRepository.findActiveGroupsByMemberId(MEMBER_ID)).thenReturn(List.of());

        GroupResDTO.MyGroupList result = groupQueryService.getMyGroups(MEMBER_ID);

        assertThat(result.groups()).isEmpty();
        verify(groupListQueryRepository).findActiveGroupsByMemberId(MEMBER_ID);
        verifyNoMoreInteractions(groupListQueryRepository);
    }

    @Test
    @DisplayName("월 마지막 날에는 미래 일정을 더하지 않고 실제 할당만으로 달성률을 계산한다")
    void getMyGroups_LastDayOfMonth_UsesOnlyActualAssignments() {
        Clock monthEndClock = Clock.fixed(
                Instant.parse("2026-07-31T00:00:00Z"), ZoneId.of("Asia/Seoul"));
        GroupQueryService monthEndService = new GroupQueryService(
                assignmentRepository,
                groupDetailQueryRepository,
                groupListQueryRepository,
                groupRoutineQueryRepository,
                categoryRepository,
                memberAvatarEquipmentRepository,
                mediaService,
                avatarLayerAssembler,
                groupValidationService,
                memberQueryService,
                monthEndClock,
                memberRepository,
                achievementRepository
        );
        LocalDate monthEnd = LocalDate.of(2026, 7, 31);
        List<Long> groupIds = List.of(301L);
        when(memberQueryService.getActiveMember(MEMBER_ID)).thenReturn(member);
        when(member.getId()).thenReturn(MEMBER_ID);
        when(groupListQueryRepository.findActiveGroupsByMemberId(MEMBER_ID)).thenReturn(List.of(
                new MyGroupProjection(301L, "월말 그룹", 2, null)
        ));
        when(groupListQueryRepository.countActiveMembersByGroupIds(groupIds)).thenReturn(List.of());
        when(groupListQueryRepository.countActiveRoutinesByGroupIds(groupIds)).thenReturn(List.of());
        when(groupListQueryRepository.findTodayAssignmentCounts(MEMBER_ID, groupIds, monthEnd))
                .thenReturn(List.of(new AssignmentCountProjection(301L, 1L, 1L)));
        when(groupListQueryRepository.countTodayVerificationsByGroupIds(groupIds, monthEnd))
                .thenReturn(List.of());
        when(groupListQueryRepository.findMonthlyAssignmentCounts(
                MEMBER_ID, groupIds, LocalDate.of(2026, 7, 1), monthEnd))
                .thenReturn(List.of(new AssignmentCountProjection(301L, 1L, 1L)));
        when(groupListQueryRepository.countActiveSchedulesByGroupIds(groupIds)).thenReturn(List.of(
                new GroupScheduleCountProjection(301L, DayOfWeek.MONDAY, 10L)
        ));

        GroupResDTO.MyGroupList result = monthEndService.getMyGroups(MEMBER_ID);

        assertThat(result.groups()).singleElement()
                .extracting(GroupResDTO.MyGroup::monthlyAchievementRate)
                .isEqualTo(100);
    }

    @Test
    @DisplayName("이번 달 실제 할당과 미래 예정 일정이 모두 없으면 달성률은 0이다")
    void getMyGroups_MonthlyDenominatorZero_ReturnsZero() {
        List<Long> groupIds = List.of(301L);
        when(memberQueryService.getActiveMember(MEMBER_ID)).thenReturn(member);
        when(member.getId()).thenReturn(MEMBER_ID);
        when(groupListQueryRepository.findActiveGroupsByMemberId(MEMBER_ID)).thenReturn(List.of(
                new MyGroupProjection(301L, "분모 없는 그룹", 0, null)
        ));
        when(groupListQueryRepository.countActiveMembersByGroupIds(groupIds)).thenReturn(List.of());
        when(groupListQueryRepository.countActiveRoutinesByGroupIds(groupIds)).thenReturn(List.of());
        when(groupListQueryRepository.findTodayAssignmentCounts(MEMBER_ID, groupIds, TODAY))
                .thenReturn(List.of());
        when(groupListQueryRepository.countTodayVerificationsByGroupIds(groupIds, TODAY))
                .thenReturn(List.of());
        when(groupListQueryRepository.findMonthlyAssignmentCounts(
                MEMBER_ID, groupIds, LocalDate.of(2026, 7, 1), TODAY)).thenReturn(List.of());
        when(groupListQueryRepository.countActiveSchedulesByGroupIds(groupIds)).thenReturn(List.of());

        GroupResDTO.MyGroupList result = groupQueryService.getMyGroups(MEMBER_ID);

        assertThat(result.groups()).singleElement()
                .extracting(GroupResDTO.MyGroup::monthlyAchievementRate)
                .isEqualTo(0);
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
