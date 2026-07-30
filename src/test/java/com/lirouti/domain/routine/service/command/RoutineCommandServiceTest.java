package com.lirouti.domain.routine.service.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import com.lirouti.domain.routine.repository.RoutineCategoryRepository;
import com.lirouti.domain.routine.repository.RoutineTemplateRepository;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

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
}
