package com.lirouti.domain.group.service.command;

import com.lirouti.domain.group.dto.request.GroupReqDTO;
import com.lirouti.domain.group.dto.response.GroupResDTO;
import com.lirouti.domain.group.entity.Group;
import com.lirouti.domain.group.entity.GroupMember;
import com.lirouti.domain.group.entity.GroupRoutine;
import com.lirouti.domain.group.entity.GroupRoutineCategory;
import com.lirouti.domain.group.enums.GroupMemberRole;
import com.lirouti.domain.group.enums.GroupMemberStatus;
import com.lirouti.domain.group.exception.GroupException;
import com.lirouti.domain.group.exception.code.error.GroupErrorCode;
import com.lirouti.domain.group.repository.GroupMemberRepository;
import com.lirouti.domain.group.repository.GroupRepository;
import com.lirouti.domain.group.repository.GroupRoutineCategoryRepository;
import com.lirouti.domain.group.repository.GroupRoutineRepository;
import com.lirouti.domain.group.service.GroupValidationService;
import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.enums.Role;
import com.lirouti.domain.member.enums.SocialProvider;
import com.lirouti.domain.routine.enums.RoutineCategoryColor;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("GroupCommandService 그룹 통합 생성 테스트")
class GroupCreationCommandServiceTest {
    private static final Long MEMBER_ID = 1L;
    private static final Long GROUP_ID = 10L;
    private static final LocalDateTime INVITE_EXPIRES_AT =
            LocalDateTime.of(2026, 8, 1, 10, 10);

    @Mock
    private GroupValidationService groupValidationService;
    @Mock
    private GroupRepository groupRepository;
    @Mock
    private GroupMemberRepository groupMemberRepository;
    @Mock
    private GroupRoutineCategoryRepository groupRoutineCategoryRepository;
    @Mock
    private GroupRoutineRepository groupRoutineRepository;
    @Mock
    private GroupRoutineAssignmentCommandService assignmentCommandService;
    @Mock
    private GroupInviteCodeGenerator inviteCodeGenerator;
    @Mock
    private Validator validator;

    @InjectMocks
    private GroupCommandService groupCommandService;

    @Test
    @DisplayName("그룹과 OWNER 및 categoryKey로 연결된 카테고리·루틴·할당 결과를 생성한다")
    void createGroup_ValidRequest_SavesAggregateAndReturnsResult() {
        // given
        GroupReqDTO.CreateGroup request = request();
        Member owner = member();
        GroupRoutineCategory fixedCategory = fixedCategory();
        givenValidRequest(request, owner);
        givenCategoryPersistence();
        givenRoutinePersistence();
        when(groupRoutineCategoryRepository.findByIdAndActiveTrue(1L))
                .thenReturn(java.util.Optional.of(fixedCategory));
        when(assignmentCommandService.assignRoutineToActiveMembersToday(any(GroupRoutine.class)))
                .thenReturn(1, 0);

        // when
        GroupResDTO.CreateResult result = groupCommandService.createGroup(MEMBER_ID, request);

        // then
        assertThat(result.groupId()).isEqualTo(GROUP_ID);
        assertThat(result.name()).isEqualTo("아침 모임");
        assertThat(result.customCategories()).singleElement().satisfies(category -> {
            assertThat(category.clientKey()).isEqualTo("morning");
            assertThat(category.categoryId()).isEqualTo(20L);
            assertThat(category.color()).isEqualTo(RoutineCategoryColor.BLUE);
        });
        assertThat(result.routines()).hasSize(2);
        assertThat(result.routines().get(0).categoryId()).isEqualTo(1L);
        assertThat(result.routines().get(1).categoryId()).isEqualTo(20L);
        assertThat(result.assignmentCount()).isEqualTo(1);

        ArgumentCaptor<Group> groupCaptor = ArgumentCaptor.forClass(Group.class);
        verify(groupRepository).saveAndFlush(groupCaptor.capture());
        assertThat(groupCaptor.getValue().getInviteCode()).isEqualTo("NEW1234");
        assertThat(groupCaptor.getValue().getInviteCodeExpiresAt()).isEqualTo(INVITE_EXPIRES_AT);

        ArgumentCaptor<GroupMember> ownerCaptor = ArgumentCaptor.forClass(GroupMember.class);
        verify(groupMemberRepository).saveAndFlush(ownerCaptor.capture());
        assertThat(ownerCaptor.getValue().getMember()).isSameAs(owner);
        assertThat(ownerCaptor.getValue().getRole()).isEqualTo(GroupMemberRole.OWNER);
        assertThat(ownerCaptor.getValue().getStatus()).isEqualTo(GroupMemberStatus.ACTIVE);

        ArgumentCaptor<GroupRoutine> routineCaptor = ArgumentCaptor.forClass(GroupRoutine.class);
        verify(groupRoutineRepository, times(2)).saveAndFlush(routineCaptor.capture());
        assertThat(routineCaptor.getAllValues().get(1).getCategory().getId()).isEqualTo(20L);
        verify(groupValidationService).lockActiveMemberAndValidateParticipationLimit(MEMBER_ID);
    }

    @Test
    @DisplayName("참여 상한 검증이 실패하면 초대코드와 그룹을 생성하지 않는다")
    void createGroup_ParticipationLimitExceeded_DoesNotPersist() {
        // given
        GroupReqDTO.CreateGroup request = request();
        when(validator.validate(request)).thenReturn(Set.of());
        when(groupValidationService.lockActiveMemberAndValidateParticipationLimit(MEMBER_ID))
                .thenThrow(new GroupException(GroupErrorCode.GROUP_PARTICIPATION_LIMIT_EXCEEDED));

        // when & then
        assertThatThrownBy(() -> groupCommandService.createGroup(MEMBER_ID, request))
                .isInstanceOf(GroupException.class)
                .extracting("code")
                .isEqualTo(GroupErrorCode.GROUP_PARTICIPATION_LIMIT_EXCEEDED);
        verify(inviteCodeGenerator, never()).generate();
        verify(groupRepository, never()).saveAndFlush(any(Group.class));
    }

    @Test
    @DisplayName("categoryId가 기본 카테고리가 아니면 초기 루틴 저장을 거부한다")
    void createGroup_CategoryIdReferencesCustomCategory_ThrowsAccessDenied() {
        // given
        GroupReqDTO.CreateGroup request = new GroupReqDTO.CreateGroup(
                "아침 모임",
                List.of(),
                List.of(routine(1L, null, "아침 운동", DayOfWeek.MONDAY))
        );
        Member owner = member();
        givenValidRequest(request, owner);
        Group otherGroup = Group.builder()
                .name("다른 그룹")
                .inviteCode("OTHER12")
                .inviteCodeExpiresAt(INVITE_EXPIRES_AT)
                .build();
        ReflectionTestUtils.setField(otherGroup, "id", 99L);
        GroupRoutineCategory otherCategory = GroupRoutineCategory.builder()
                .group(otherGroup)
                .name("다른 카테고리")
                .active(true)
                .build();
        ReflectionTestUtils.setField(otherCategory, "id", 1L);
        when(groupRoutineCategoryRepository.findByIdAndActiveTrue(1L))
                .thenReturn(java.util.Optional.of(otherCategory));

        // when & then
        assertThatThrownBy(() -> groupCommandService.createGroup(MEMBER_ID, request))
                .isInstanceOf(GroupException.class)
                .extracting("code")
                .isEqualTo(GroupErrorCode.GROUP_ROUTINE_CATEGORY_ACCESS_DENIED);
        verify(groupRoutineRepository, never()).saveAndFlush(any(GroupRoutine.class));
    }

    @Test
    @DisplayName("사용자 카테고리 이름이 기본 카테고리와 중복되면 전체 생성을 중단한다")
    void createGroup_CustomCategoryDuplicatesFixedName_ThrowsDuplicateName() {
        // given
        GroupReqDTO.CreateGroup request = new GroupReqDTO.CreateGroup(
                "아침 모임",
                List.of(new GroupReqDTO.CreateGroupCategory(
                        "exercise", "운동", RoutineCategoryColor.BLUE
                )),
                List.of(routine(null, "exercise", "아침 운동", DayOfWeek.MONDAY))
        );
        givenValidRequest(request, member());
        when(groupRoutineCategoryRepository.existsUsableName(GROUP_ID, "운동"))
                .thenReturn(true);

        // when & then
        assertThatThrownBy(() -> groupCommandService.createGroup(MEMBER_ID, request))
                .isInstanceOf(GroupException.class)
                .extracting("code")
                .isEqualTo(GroupErrorCode.DUPLICATE_GROUP_ROUTINE_CATEGORY_NAME);
        verify(groupRoutineCategoryRepository, never())
                .saveAndFlush(any(GroupRoutineCategory.class));
        verify(groupRoutineRepository, never()).saveAndFlush(any(GroupRoutine.class));
    }

    @Test
    @DisplayName("초기 루틴 할당 실패를 호출자에게 전파한다")
    void createGroup_AssignmentFails_PropagatesException() {
        // given
        GroupReqDTO.CreateGroup request = new GroupReqDTO.CreateGroup(
                "아침 모임",
                List.of(),
                List.of(routine(1L, null, "아침 운동", DayOfWeek.MONDAY))
        );
        givenValidRequest(request, member());
        givenRoutinePersistence();
        when(groupRoutineCategoryRepository.findByIdAndActiveTrue(1L))
                .thenReturn(java.util.Optional.of(fixedCategory()));
        when(assignmentCommandService.assignRoutineToActiveMembersToday(any(GroupRoutine.class)))
                .thenThrow(new IllegalStateException("assignment failure"));

        // when & then
        assertThatThrownBy(() -> groupCommandService.createGroup(MEMBER_ID, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("assignment failure");
    }

    private void givenValidRequest(GroupReqDTO.CreateGroup request, Member owner) {
        when(validator.validate(request)).thenReturn(Set.of());
        when(groupValidationService.lockActiveMemberAndValidateParticipationLimit(MEMBER_ID))
                .thenReturn(owner);
        when(inviteCodeGenerator.generate()).thenReturn(
                new GroupInviteCodeGenerator.GeneratedInviteCode("NEW1234", INVITE_EXPIRES_AT)
        );
        when(groupRepository.saveAndFlush(any(Group.class))).thenAnswer(invocation -> {
            Group group = invocation.getArgument(0);
            ReflectionTestUtils.setField(group, "id", GROUP_ID);
            return group;
        });
        when(groupMemberRepository.saveAndFlush(any(GroupMember.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(groupRoutineCategoryRepository.countByGroupIdAndActiveTrue(GROUP_ID)).thenReturn(0L);
    }

    private void givenCategoryPersistence() {
        when(groupRoutineCategoryRepository.existsUsableName(eq(GROUP_ID), anyString()))
                .thenReturn(false);
        when(groupRoutineCategoryRepository.saveAndFlush(any(GroupRoutineCategory.class)))
                .thenAnswer(invocation -> {
                    GroupRoutineCategory category = invocation.getArgument(0);
                    ReflectionTestUtils.setField(category, "id", 20L);
                    return category;
                });
    }

    private void givenRoutinePersistence() {
        AtomicLong routineId = new AtomicLong(30L);
        when(groupRoutineRepository.saveAndFlush(any(GroupRoutine.class)))
                .thenAnswer(invocation -> {
                    GroupRoutine routine = invocation.getArgument(0);
                    ReflectionTestUtils.setField(routine, "id", routineId.getAndIncrement());
                    return routine;
                });
    }

    private GroupReqDTO.CreateGroup request() {
        return new GroupReqDTO.CreateGroup(
                "아침 모임",
                List.of(new GroupReqDTO.CreateGroupCategory(
                        "morning", "아침 관리", RoutineCategoryColor.BLUE
                )),
                List.of(
                        routine(1L, null, "아침 운동", DayOfWeek.MONDAY),
                        routine(null, "morning", "침구 정리", DayOfWeek.TUESDAY)
                )
        );
    }

    private GroupReqDTO.CreateGroupRoutine routine(
            Long categoryId,
            String categoryKey,
            String title,
            DayOfWeek day
    ) {
        return new GroupReqDTO.CreateGroupRoutine(
                categoryId,
                categoryKey,
                title,
                "함께 실천합니다.",
                List.of(new GroupReqDTO.RoutineSchedule(
                        day,
                        LocalTime.of(9, 0),
                        LocalTime.of(10, 0)
                ))
        );
    }

    private GroupRoutineCategory fixedCategory() {
        GroupRoutineCategory category = GroupRoutineCategory.builder()
                .name("운동")
                .active(true)
                .build();
        ReflectionTestUtils.setField(category, "id", 1L);
        return category;
    }

    private Member member() {
        Member member = Member.builder()
                .email("owner@example.com")
                .nickname("방장")
                .socialProvider(SocialProvider.GOOGLE)
                .role(Role.ROLE_USER)
                .socialId("owner-social-id")
                .build();
        ReflectionTestUtils.setField(member, "id", MEMBER_ID);
        return member;
    }
}
