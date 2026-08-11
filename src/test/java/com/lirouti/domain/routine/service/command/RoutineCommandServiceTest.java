package com.lirouti.domain.routine.service.command;

import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.enums.Role;
import com.lirouti.domain.member.enums.SocialProvider;
import com.lirouti.domain.member.repository.MemberRepository;
import com.lirouti.domain.member.service.query.MemberQueryService;
import com.lirouti.domain.routine.dto.request.RoutineReqDTO;
import com.lirouti.domain.routine.dto.response.RoutineResDTO;
import com.lirouti.domain.routine.entity.MemberRoutine;
import com.lirouti.domain.routine.entity.RoutineCategory;
import com.lirouti.domain.routine.entity.RoutineTemplate;
import com.lirouti.domain.routine.enums.RoutineCategoryColor;
import com.lirouti.domain.routine.exception.RoutineException;
import com.lirouti.domain.routine.exception.code.error.RoutineErrorCode;
import com.lirouti.domain.routine.repository.MemberRoutineRepository;
import com.lirouti.domain.routine.repository.MemberRoutineScheduleRepository;
import com.lirouti.domain.routine.repository.RoutineCategoryRepository;
import com.lirouti.domain.routine.repository.RoutineTemplateRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RoutineCommandService 개인 루틴 생성 테스트")
class RoutineCommandServiceTest {
    private static final Long MEMBER_ID = 1L;
    private static final Long OTHER_MEMBER_ID = 2L;
    private static final Long HEALTH_CATEGORY_ID = 2L;
    private static final Long EXERCISE_CATEGORY_ID = 1L;
    private static final Long WATER_TEMPLATE_ID = 201L;
    private static final String WATER_TEMPLATE_NAME = "물 챙겨 마시기";

    @Mock
    private MemberQueryService memberQueryService;
    @Mock
    private MemberRepository memberRepository;
    @Mock
    private RoutineCategoryRepository routineCategoryRepository;
    @Mock
    private RoutineTemplateRepository routineTemplateRepository;
    @Mock
    private MemberRoutineRepository memberRoutineRepository;
    @Mock
    private MemberRoutineScheduleRepository memberRoutineScheduleRepository;

    @InjectMocks
    private RoutineCommandService routineCommandService;

    private Member member;
    private RoutineCategory health;
    private RoutineTemplate water;

    @BeforeEach
    void setUp() {
        member = member(MEMBER_ID);
        health = fixedCategory(HEALTH_CATEGORY_ID, "건강");
        water = template(WATER_TEMPLATE_ID, health, WATER_TEMPLATE_NAME);
    }

    @Test
    @DisplayName("기본 루틴과 직접 추가 루틴을 한 번에 등록한다")
    void createRoutines_TemplateAndCustom_CreatesBoth() {
        // given
        givenActiveMember();
        givenExistingRoutines(0, List.of());
        givenCategories(health);
        givenTemplates(water);
        RoutineReqDTO.CreateRoutines request = new RoutineReqDTO.CreateRoutines(List.of(
                item(HEALTH_CATEGORY_ID, WATER_TEMPLATE_ID, WATER_TEMPLATE_NAME, null, null),
                item(HEALTH_CATEGORY_ID, null, "  영양제 먹기  ", LocalTime.of(21, 0),
                        List.of(DayOfWeek.FRIDAY, DayOfWeek.MONDAY))
        ));

        // when
        RoutineResDTO.RoutineCreateResult result =
                routineCommandService.createRoutines(MEMBER_ID, request);

        // then
        assertAll(
                () -> assertThat(result.activeRoutineCount()).isEqualTo(2),
                () -> assertThat(result.routines()).hasSize(2),
                () -> assertThat(result.routines().getFirst().templateId())
                        .isEqualTo(WATER_TEMPLATE_ID),
                () -> assertThat(result.routines().getFirst().endTime())
                        .isEqualTo(MemberRoutine.DEFAULT_END_TIME),
                () -> assertThat(result.routines().getFirst().repeatDays())
                        .containsExactly(DayOfWeek.values()),
                () -> assertThat(result.routines().get(1).templateId()).isNull(),
                () -> assertThat(result.routines().get(1).name()).isEqualTo("영양제 먹기"),
                () -> assertThat(result.routines().get(1).endTime())
                        .isEqualTo(LocalTime.of(21, 0)),
                () -> assertThat(result.routines().get(1).repeatDays())
                        .containsExactly(DayOfWeek.MONDAY, DayOfWeek.FRIDAY)
        );
    }

    @Test
    @DisplayName("개인 루틴 수정은 설정을 교체하고 변경된 기본 루틴 이름의 원본 참조를 해제한다")
    void updateRoutine_ChangedTemplateName_UpdatesAndDetachesTemplate() {
        givenActiveMember();
        MemberRoutine routine = MemberRoutine.builder()
                .member(member)
                .category(health)
                .template(water)
                .name(WATER_TEMPLATE_NAME)
                .build();
        ReflectionTestUtils.setField(routine, "id", 10L);
        routine.addSchedule(DayOfWeek.MONDAY);
        when(memberRoutineRepository.findByIdAndMemberIdAndActiveTrue(10L, MEMBER_ID))
                .thenReturn(Optional.of(routine));
        RoutineReqDTO.UpdateRoutine request = new RoutineReqDTO.UpdateRoutine(
                "  물 2L 마시기  ",
                LocalTime.of(21, 0),
                List.of(DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
                LocalTime.of(20, 30)
        );

        RoutineResDTO.Routine result = routineCommandService
                .updateRoutine(MEMBER_ID, 10L, request);

        assertAll(
                () -> assertThat(result.templateId()).isNull(),
                () -> assertThat(result.name()).isEqualTo("물 2L 마시기"),
                () -> assertThat(result.endTime()).isEqualTo(LocalTime.of(21, 0)),
                () -> assertThat(result.repeatDays())
                        .containsExactly(DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)
        );
        verify(memberRoutineScheduleRepository).deleteAllByMemberRoutineId(10L);
        verify(memberRoutineRepository, times(2))
                .findByIdAndMemberIdAndActiveTrue(10L, MEMBER_ID);
        verify(memberRoutineRepository).flush();
    }

    @Test
    @DisplayName("개인 루틴 수정의 반복 요일 오류는 일정 삭제 전에 거부한다")
    void updateRoutine_DuplicateRepeatDays_RejectsBeforeDeletingSchedules() {
        givenActiveMember();
        MemberRoutine routine = MemberRoutine.builder()
                .member(member)
                .category(health)
                .name("물 마시기")
                .build();
        routine.addSchedule(DayOfWeek.MONDAY);
        when(memberRoutineRepository.findByIdAndMemberIdAndActiveTrue(10L, MEMBER_ID))
                .thenReturn(Optional.of(routine));
        RoutineReqDTO.UpdateRoutine request = new RoutineReqDTO.UpdateRoutine(
                "물 마시기",
                LocalTime.of(21, 0),
                List.of(DayOfWeek.MONDAY, DayOfWeek.MONDAY),
                null
        );

        assertThatThrownBy(() -> routineCommandService.updateRoutine(MEMBER_ID, 10L, request))
                .isInstanceOf(RoutineException.class)
                .extracting("code")
                .isEqualTo(RoutineErrorCode.INVALID_ROUTINE_UPDATE);
        assertThat(routine.getSchedules())
                .extracting("repeatDay")
                .containsExactly(DayOfWeek.MONDAY);
        verify(memberRoutineScheduleRepository, never()).deleteAllByMemberRoutineId(anyLong());
    }

    @Test
    @DisplayName("개인 루틴 삭제는 비활성화하고 기본 루틴 참조를 해제한다")
    void deleteRoutine_OwnedRoutine_DeactivatesAndDetachesTemplate() {
        givenActiveMember();
        MemberRoutine routine = MemberRoutine.builder()
                .member(member)
                .category(health)
                .template(water)
                .name(WATER_TEMPLATE_NAME)
                .build();
        when(memberRoutineRepository.findByIdAndMemberIdAndActiveTrue(10L, MEMBER_ID))
                .thenReturn(Optional.of(routine));

        routineCommandService.deleteRoutine(MEMBER_ID, 10L);

        assertAll(
                () -> assertThat(routine.getActive()).isFalse(),
                () -> assertThat(routine.getTemplate()).isNull()
        );
        verify(memberRoutineRepository).flush();
    }

    @Test
    @DisplayName("본인 소유의 활성 개인 루틴이 아니면 수정할 수 없다")
    void updateRoutine_NotOwnedOrInactive_ThrowsNotFound() {
        givenActiveMember();
        when(memberRoutineRepository.findByIdAndMemberIdAndActiveTrue(10L, MEMBER_ID))
                .thenReturn(Optional.empty());
        RoutineReqDTO.UpdateRoutine request = new RoutineReqDTO.UpdateRoutine(
                "물 챙겨 마시기",
                LocalTime.of(21, 0),
                List.of(DayOfWeek.MONDAY),
                null
        );

        assertThatThrownBy(() -> routineCommandService.updateRoutine(MEMBER_ID, 10L, request))
                .isInstanceOf(RoutineException.class)
                .extracting("code")
                .isEqualTo(RoutineErrorCode.ROUTINE_NOT_FOUND);
        verify(memberRoutineRepository, never()).flush();
    }

    @Test
    @DisplayName("없는 개인 루틴은 삭제할 수 없다")
    void deleteRoutine_MissingRoutine_ThrowsNotFound() {
        givenActiveMember();
        when(memberRoutineRepository.findByIdAndMemberIdAndActiveTrue(999L, MEMBER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> routineCommandService.deleteRoutine(MEMBER_ID, 999L))
                .isInstanceOf(RoutineException.class)
                .extracting("code")
                .isEqualTo(RoutineErrorCode.ROUTINE_NOT_FOUND);
        verify(memberRoutineRepository, never()).flush();
    }

    @Test
    @DisplayName("기본 루틴 이름을 바꾸면 원본이 이미 등록돼 있어도 사용자 루틴으로 등록한다")
    void createRoutines_RenamedTemplateAlreadyTaken_CreatesCustomRoutine() {
        // given
        givenActiveMember();
        givenExistingRoutines(1, List.of(WATER_TEMPLATE_ID));
        givenCategories(health);
        givenTemplates(water);
        RoutineReqDTO.CreateRoutines request = new RoutineReqDTO.CreateRoutines(List.of(
                item(HEALTH_CATEGORY_ID, WATER_TEMPLATE_ID, "물 2L 마시기", null, null)
        ));

        // when
        RoutineResDTO.RoutineCreateResult result =
                routineCommandService.createRoutines(MEMBER_ID, request);

        // then
        assertAll(
                () -> assertThat(result.routines()).hasSize(1),
                () -> assertThat(result.routines().getFirst().templateId()).isNull(),
                () -> assertThat(result.routines().getFirst().name()).isEqualTo("물 2L 마시기")
        );
    }

    @Test
    @DisplayName("이미 등록한 기본 루틴을 같은 이름으로 다시 등록할 수 없다")
    void createRoutines_TemplateAlreadyTaken_ThrowsDuplicate() {
        // given
        givenActiveMember();
        givenExistingRoutines(1, List.of(WATER_TEMPLATE_ID));
        givenCategories(health);
        givenTemplates(water);
        RoutineReqDTO.CreateRoutines request = new RoutineReqDTO.CreateRoutines(List.of(
                item(HEALTH_CATEGORY_ID, WATER_TEMPLATE_ID, WATER_TEMPLATE_NAME, null, null)
        ));

        // when & then
        assertThatThrownBy(() -> routineCommandService.createRoutines(MEMBER_ID, request))
                .isInstanceOf(RoutineException.class)
                .extracting("code")
                .isEqualTo(RoutineErrorCode.DUPLICATE_ROUTINE_TEMPLATE);
        verify(memberRoutineRepository, never()).saveAll(anyCollection());
    }

    @Test
    @DisplayName("JPA가 감싼 기본 루틴 UNIQUE 위반을 중복 도메인 예외로 변환한다")
    void createRoutines_DataIntegrityViolation_MapsTemplateConstraint() {
        givenActiveMember();
        givenExistingRoutines(0, List.of());
        givenCategories(health);
        givenTemplates(water);
        doThrow(uniqueViolation("uk_member_routine_member_template"))
                .when(memberRoutineRepository).flush();
        RoutineReqDTO.CreateRoutines request = new RoutineReqDTO.CreateRoutines(List.of(
                item(HEALTH_CATEGORY_ID, WATER_TEMPLATE_ID, WATER_TEMPLATE_NAME, null, null)
        ));

        assertThatThrownBy(() -> routineCommandService.createRoutines(MEMBER_ID, request))
                .isInstanceOf(RoutineException.class)
                .extracting("code")
                .isEqualTo(RoutineErrorCode.DUPLICATE_ROUTINE_TEMPLATE);
    }

    @Test
    @DisplayName("기존 루틴과 요청을 합해 30개를 넘으면 등록할 수 없다")
    void createRoutines_OverActiveLimit_ThrowsLimitExceeded() {
        // given
        givenActiveMember();
        when(memberRoutineRepository.countByMemberIdAndActiveTrue(MEMBER_ID))
                .thenReturn((long) MemberRoutine.MAX_ACTIVE_COUNT);
        RoutineReqDTO.CreateRoutines request = new RoutineReqDTO.CreateRoutines(List.of(
                item(HEALTH_CATEGORY_ID, null, "한 개 더", null, null)
        ));

        // when & then
        assertThatThrownBy(() -> routineCommandService.createRoutines(MEMBER_ID, request))
                .isInstanceOf(RoutineException.class)
                .extracting("code")
                .isEqualTo(RoutineErrorCode.ACTIVE_ROUTINE_LIMIT_EXCEEDED);
        verify(routineCategoryRepository, never()).findAllById(anyCollection());
    }

    @Test
    @DisplayName("다른 회원의 카테고리에는 루틴을 만들 수 없다")
    void createRoutines_OtherMemberCategory_ThrowsAccessDenied() {
        // given
        givenActiveMember();
        givenExistingRoutines(0, null);
        givenCategories(memberCategory(50L, "남의 카테고리", member(OTHER_MEMBER_ID)));
        RoutineReqDTO.CreateRoutines request = new RoutineReqDTO.CreateRoutines(List.of(
                item(50L, null, "몰래 추가", null, null)
        ));

        // when & then
        assertThatThrownBy(() -> routineCommandService.createRoutines(MEMBER_ID, request))
                .isInstanceOf(RoutineException.class)
                .extracting("code")
                .isEqualTo(RoutineErrorCode.ROUTINE_CATEGORY_ACCESS_DENIED);
    }

    @Test
    @DisplayName("기본 루틴이 요청한 카테고리에 속하지 않으면 등록할 수 없다")
    void createRoutines_TemplateCategoryMismatch_ThrowsMismatch() {
        // given
        givenActiveMember();
        givenExistingRoutines(0, List.of());
        givenCategories(fixedCategory(EXERCISE_CATEGORY_ID, "운동"));
        givenTemplates(water);
        RoutineReqDTO.CreateRoutines request = new RoutineReqDTO.CreateRoutines(List.of(
                item(EXERCISE_CATEGORY_ID, WATER_TEMPLATE_ID, WATER_TEMPLATE_NAME, null, null)
        ));

        // when & then
        assertThatThrownBy(() -> routineCommandService.createRoutines(MEMBER_ID, request))
                .isInstanceOf(RoutineException.class)
                .extracting("code")
                .isEqualTo(RoutineErrorCode.ROUTINE_TEMPLATE_CATEGORY_MISMATCH);
    }

    @Test
    @DisplayName("비활성 카테고리를 지정하면 등록할 수 없다")
    void createRoutines_InactiveCategory_ThrowsNotFound() {
        // given
        givenActiveMember();
        givenExistingRoutines(0, null);
        RoutineCategory inactive = RoutineCategory.builder().name("비활성").active(false).build();
        ReflectionTestUtils.setField(inactive, "id", 77L);
        givenCategories(inactive);
        RoutineReqDTO.CreateRoutines request = new RoutineReqDTO.CreateRoutines(List.of(
                item(77L, null, "비활성 카테고리", null, null)
        ));

        // when & then
        assertThatThrownBy(() -> routineCommandService.createRoutines(MEMBER_ID, request))
                .isInstanceOf(RoutineException.class)
                .extracting("code")
                .isEqualTo(RoutineErrorCode.ROUTINE_CATEGORY_NOT_FOUND);
    }

    @Test
    @DisplayName("Controller를 거치지 않아도 줄바꿈이 든 루틴 이름을 거절한다")
    void createRoutines_NameWithLineBreak_ThrowsInvalidName() {
        // given — DTO를 직접 만들어 @AssertTrue 검증을 우회한 호출이다
        givenActiveMember();
        givenExistingRoutines(0, null);
        givenCategories(health);
        RoutineReqDTO.CreateRoutines request = new RoutineReqDTO.CreateRoutines(List.of(
                item(HEALTH_CATEGORY_ID, null, "두\n줄 이름", null, null)
        ));

        // when & then
        assertThatThrownBy(() -> routineCommandService.createRoutines(MEMBER_ID, request))
                .isInstanceOf(RoutineException.class)
                .extracting("code")
                .isEqualTo(RoutineErrorCode.INVALID_ROUTINE_NAME);
        verify(memberRoutineRepository, never()).saveAll(anyCollection());
    }

    @Test
    @DisplayName("Controller를 거치지 않아도 줄바꿈이 든 카테고리 이름을 거절한다")
    void createCategory_NameWithLineBreak_ThrowsInvalidName() {
        // given
        givenActiveMember();
        RoutineReqDTO.CreateCategory request =
                new RoutineReqDTO.CreateCategory("두\n줄", null);

        // when & then
        assertThatThrownBy(() -> routineCommandService.createCategory(MEMBER_ID, request))
                .isInstanceOf(RoutineException.class)
                .extracting("code")
                .isEqualTo(RoutineErrorCode.INVALID_ROUTINE_CATEGORY_NAME);
        verify(routineCategoryRepository, never()).saveAndFlush(any(RoutineCategory.class));
    }

    @Test
    @DisplayName("카테고리를 5개 가진 회원은 더 추가할 수 없다")
    void createCategory_AtLimit_ThrowsLimitExceeded() {
        // given
        givenActiveMember();
        when(routineCategoryRepository.countByOwnerIdAndActiveTrue(MEMBER_ID))
                .thenReturn((long) RoutineCategory.MAX_MEMBER_CATEGORY_COUNT);
        RoutineReqDTO.CreateCategory request =
                new RoutineReqDTO.CreateCategory("여섯번째", RoutineCategoryColor.BLUE);

        // when & then
        assertThatThrownBy(() -> routineCommandService.createCategory(MEMBER_ID, request))
                .isInstanceOf(RoutineException.class)
                .extracting("code")
                .isEqualTo(RoutineErrorCode.ROUTINE_CATEGORY_LIMIT_EXCEEDED);
        verify(routineCategoryRepository, never()).saveAndFlush(any(RoutineCategory.class));
    }

    @Test
    @DisplayName("고정 카테고리와 같은 이름은 추가할 수 없다")
    void createCategory_DuplicateName_ThrowsDuplicate() {
        // given
        givenActiveMember();
        when(routineCategoryRepository.countByOwnerIdAndActiveTrue(MEMBER_ID)).thenReturn(1L);
        when(routineCategoryRepository.existsUsableName(MEMBER_ID, "운동")).thenReturn(true);
        RoutineReqDTO.CreateCategory request = new RoutineReqDTO.CreateCategory("운동", null);

        // when & then
        assertThatThrownBy(() -> routineCommandService.createCategory(MEMBER_ID, request))
                .isInstanceOf(RoutineException.class)
                .extracting("code")
                .isEqualTo(RoutineErrorCode.DUPLICATE_ROUTINE_CATEGORY_NAME);
        verify(routineCategoryRepository, never()).saveAndFlush(any(RoutineCategory.class));
    }

    @Test
    @DisplayName("JPA가 감싼 카테고리 이름 UNIQUE 위반을 중복 도메인 예외로 변환한다")
    void createCategory_DataIntegrityViolation_MapsNameConstraint() {
        givenActiveMember();
        when(routineCategoryRepository.countByOwnerIdAndActiveTrue(MEMBER_ID)).thenReturn(0L);
        when(routineCategoryRepository.existsUsableName(MEMBER_ID, "아침")).thenReturn(false);
        doThrow(uniqueViolation("uk_routine_category_member_name"))
                .when(routineCategoryRepository).saveAndFlush(any(RoutineCategory.class));

        assertThatThrownBy(() -> routineCommandService.createCategory(
                MEMBER_ID,
                new RoutineReqDTO.CreateCategory("아침", null)
        )).isInstanceOf(RoutineException.class)
                .extracting("code")
                .isEqualTo(RoutineErrorCode.DUPLICATE_ROUTINE_CATEGORY_NAME);
    }

    @Test
    @DisplayName("색 없이 추가한 카테고리는 색상이 비어 있고 사용자 카테고리로 표시된다")
    void createCategory_NoColor_CreatesMemberCategory() {
        // given
        givenActiveMember();
        when(routineCategoryRepository.countByOwnerIdAndActiveTrue(MEMBER_ID)).thenReturn(0L);
        when(routineCategoryRepository.existsUsableName(MEMBER_ID, "사이드")).thenReturn(false);
        RoutineReqDTO.CreateCategory request = new RoutineReqDTO.CreateCategory("  사이드  ", null);

        // when
        RoutineResDTO.Category result = routineCommandService.createCategory(MEMBER_ID, request);

        // then
        assertAll(
                () -> assertThat(result.name()).isEqualTo("사이드"),
                () -> assertThat(result.color()).isNull(),
                () -> assertThat(result.fixed()).isFalse()
        );
    }

    @Test
    @DisplayName("본인 사용자 카테고리의 이름과 색상을 수정한다")
    void updateCategory_OwnedCategory_UpdatesNameAndColor() {
        givenActiveMember();
        RoutineCategory category = memberCategory(7L, "기존", member);
        when(routineCategoryRepository.findByIdAndActiveTrue(7L))
                .thenReturn(Optional.of(category));
        when(routineCategoryRepository.existsUsableName(MEMBER_ID, "아침 관리"))
                .thenReturn(false);
        RoutineReqDTO.UpdateCategory request = new RoutineReqDTO.UpdateCategory(
                "  아침 관리  ", RoutineCategoryColor.BLUE);

        RoutineResDTO.Category result = routineCommandService
                .updateCategory(MEMBER_ID, 7L, request);

        assertAll(
                () -> assertThat(result.name()).isEqualTo("아침 관리"),
                () -> assertThat(result.color()).isEqualTo(RoutineCategoryColor.BLUE),
                () -> assertThat(result.fixed()).isFalse()
        );
        verify(routineCategoryRepository).saveAndFlush(category);
    }

    @Test
    @DisplayName("이름을 유지한 색상 수정은 자기 자신을 중복으로 판단하지 않는다")
    void updateCategory_SameName_UpdatesWithoutDuplicateQuery() {
        givenActiveMember();
        RoutineCategory category = memberCategory(7L, "아침 관리", member);
        when(routineCategoryRepository.findByIdAndActiveTrue(7L))
                .thenReturn(Optional.of(category));

        routineCommandService.updateCategory(
                MEMBER_ID,
                7L,
                new RoutineReqDTO.UpdateCategory("아침 관리", RoutineCategoryColor.RED)
        );

        assertThat(category.getColor()).isEqualTo(RoutineCategoryColor.RED);
        verify(routineCategoryRepository, never()).existsUsableName(anyLong(), anyString());
    }

    @Test
    @DisplayName("고정 카테고리는 수정할 수 없다")
    void updateCategory_FixedCategory_ThrowsForbidden() {
        givenActiveMember();
        when(routineCategoryRepository.findByIdAndActiveTrue(1L))
                .thenReturn(Optional.of(fixedCategory(1L, "운동")));

        assertThatThrownBy(() -> routineCommandService.updateCategory(
                MEMBER_ID,
                1L,
                new RoutineReqDTO.UpdateCategory("새 이름", null)
        )).isInstanceOf(RoutineException.class)
                .extracting("code")
                .isEqualTo(RoutineErrorCode.FIXED_ROUTINE_CATEGORY_MODIFICATION_NOT_ALLOWED);
    }

    @Test
    @DisplayName("다른 회원의 카테고리는 삭제할 수 없다")
    void deleteCategory_OtherMembersCategory_ThrowsAccessDenied() {
        givenActiveMember();
        RoutineCategory category = memberCategory(7L, "남의 카테고리", member(2L));
        when(routineCategoryRepository.findByIdAndActiveTrue(7L))
                .thenReturn(Optional.of(category));

        assertThatThrownBy(() -> routineCommandService.deleteCategory(MEMBER_ID, 7L))
                .isInstanceOf(RoutineException.class)
                .extracting("code")
                .isEqualTo(RoutineErrorCode.ROUTINE_CATEGORY_ACCESS_DENIED);
        verify(routineCategoryRepository, never()).delete(any());
    }

    @Test
    @DisplayName("비활성 루틴이라도 포함된 카테고리는 삭제할 수 없다")
    void deleteCategory_CategoryWithAnyRoutine_ThrowsNotEmpty() {
        givenActiveMember();
        RoutineCategory category = memberCategory(7L, "기록 보존", member);
        when(routineCategoryRepository.findByIdAndActiveTrue(7L))
                .thenReturn(Optional.of(category));
        when(memberRoutineRepository.existsByCategoryId(7L)).thenReturn(true);

        assertThatThrownBy(() -> routineCommandService.deleteCategory(MEMBER_ID, 7L))
                .isInstanceOf(RoutineException.class)
                .extracting("code")
                .isEqualTo(RoutineErrorCode.ROUTINE_CATEGORY_NOT_EMPTY);
        verify(routineCategoryRepository, never()).delete(any());
    }

    @Test
    @DisplayName("루틴이 없는 본인 사용자 카테고리는 물리 삭제한다")
    void deleteCategory_EmptyOwnedCategory_Deletes() {
        givenActiveMember();
        RoutineCategory category = memberCategory(7L, "삭제 대상", member);
        when(routineCategoryRepository.findByIdAndActiveTrue(7L))
                .thenReturn(Optional.of(category));
        when(memberRoutineRepository.existsByCategoryId(7L)).thenReturn(false);

        routineCommandService.deleteCategory(MEMBER_ID, 7L);

        verify(routineCategoryRepository).delete(category);
        verify(routineCategoryRepository).flush();
    }

    private void givenActiveMember() {
        when(memberQueryService.getActiveMember(MEMBER_ID)).thenReturn(member);
    }

    /**
     * 기존 활성 루틴 수와 이미 등록된 기본 루틴 ID를 준비한다.
     * {@code takenTemplateIds}가 {@code null}이면 그 조회에 닿기 전에 실패하는 시나리오다.
     */
    private void givenExistingRoutines(int activeCount, List<Long> takenTemplateIds) {
        when(memberRoutineRepository.countByMemberIdAndActiveTrue(MEMBER_ID))
                .thenReturn((long) activeCount);
        if (takenTemplateIds != null) {
            when(memberRoutineRepository.findTemplateIdsByMemberId(MEMBER_ID))
                    .thenReturn(takenTemplateIds);
        }
    }

    private void givenCategories(RoutineCategory... categories) {
        when(routineCategoryRepository.findAllById(anyCollection())).thenReturn(List.of(categories));
    }

    private void givenTemplates(RoutineTemplate... templates) {
        when(routineTemplateRepository.findAllActiveByIdIn(anyCollection()))
                .thenReturn(List.of(templates));
    }

    private RoutineReqDTO.CreateRoutine item(
            Long categoryId,
            Long templateId,
            String name,
            LocalTime endTime,
            List<DayOfWeek> repeatDays
    ) {
        return new RoutineReqDTO.CreateRoutine(
                categoryId, templateId, name, endTime, repeatDays, null);
    }

    private Member member(Long id) {
        Member created = Member.builder()
                .email("routine-" + id + "@example.com")
                .nickname("루틴회원" + id)
                .socialProvider(SocialProvider.GOOGLE)
                .role(Role.ROLE_USER)
                .socialId("routine-social-" + id)
                .build();
        ReflectionTestUtils.setField(created, "id", id);
        return created;
    }

    private RoutineCategory fixedCategory(Long id, String name) {
        RoutineCategory category = RoutineCategory.builder().name(name).active(true).build();
        ReflectionTestUtils.setField(category, "id", id);
        return category;
    }

    private RoutineCategory memberCategory(Long id, String name, Member owner) {
        RoutineCategory category = RoutineCategory.builder()
                .owner(owner).name(name).active(true).build();
        ReflectionTestUtils.setField(category, "id", id);
        return category;
    }

    private RoutineTemplate template(Long id, RoutineCategory category, String name) {
        RoutineTemplate created = RoutineTemplate.builder()
                .category(category).name(name).displayOrder(1).active(true).build();
        ReflectionTestUtils.setField(created, "id", id);
        return created;
    }

    private DataIntegrityViolationException uniqueViolation(String constraintName) {
        SQLException sqlException = new SQLException("duplicate", "23000", 1062);
        ConstraintViolationException constraintViolation = new ConstraintViolationException(
                "duplicate",
                sqlException,
                ConstraintViolationException.ConstraintKind.UNIQUE,
                constraintName
        );
        return new DataIntegrityViolationException("duplicate", constraintViolation);
    }
}
