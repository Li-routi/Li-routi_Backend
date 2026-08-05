package com.lirouti.domain.group.dto.request;

import com.lirouti.domain.group.dto.response.GroupResDTO;
import com.lirouti.domain.routine.enums.RoutineCategoryColor;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("그룹 통합 생성 요청 검증 테스트")
class GroupReqDTOValidationTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    @DisplayName("기본 카테고리와 요청 내 사용자 카테고리를 참조하는 요청을 허용한다")
    void validate_ValidFixedAndCustomCategoryReferences_HasNoViolation() {
        // given
        GroupReqDTO.CreateGroup request = request(
                List.of(category("morning", "아침 관리")),
                List.of(
                        routine(1L, null, "아침 운동", schedules()),
                        routine(null, "morning", "침구 정리", schedules(DayOfWeek.TUESDAY))
                )
        );

        // when
        Set<ConstraintViolation<GroupReqDTO.CreateGroup>> violations = validator.validate(request);

        // then
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("모임명과 카테고리 키·이름 및 루틴 제목은 생성 시 앞뒤 공백을 제거한다")
    void construct_TrimmedValues_NormalizesRequest() {
        // given & when
        GroupReqDTO.CreateGroupCategory category = category("  morning  ", "  아침 관리  ");
        GroupReqDTO.CreateGroupRoutine routine = routine(
                null,
                "  morning  ",
                "  침구 정리  ",
                schedules()
        );
        GroupReqDTO.CreateGroup request = new GroupReqDTO.CreateGroup(
                "  아침 모임  ",
                List.of(category),
                List.of(routine)
        );

        // then
        assertThat(request.name()).isEqualTo("아침 모임");
        assertThat(category.clientKey()).isEqualTo("morning");
        assertThat(category.name()).isEqualTo("아침 관리");
        assertThat(routine.categoryKey()).isEqualTo("morning");
        assertThat(routine.title()).isEqualTo("침구 정리");
        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    @DisplayName("공백 categoryKey는 null로 정규화해 categoryId 참조를 허용한다")
    void construct_BlankCategoryKey_NormalizesToNull() {
        GroupReqDTO.CreateGroupRoutine routine = routine(
                1L,
                "   ",
                "아침 운동",
                schedules()
        );
        GroupReqDTO.CreateGroup request = request(List.of(), List.of(routine));

        assertThat(routine.categoryKey()).isNull();
        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    @DisplayName("같은 clientKey를 두 번 선언하면 거부한다")
    void validate_DuplicateClientKey_HasViolation() {
        // given
        GroupReqDTO.CreateGroup request = request(
                List.of(category("same", "첫 번째"), category("same", "두 번째")),
                List.of(routine(null, "same", "루틴", schedules()))
        );

        // when
        Set<String> messages = messages(request);

        // then
        assertThat(messages).contains("사용자 카테고리 clientKey는 요청 안에서 중복될 수 없습니다.");
    }

    @Test
    @DisplayName("요청에 선언되지 않은 categoryKey 참조를 거부한다")
    void validate_UnknownCategoryKey_HasViolation() {
        // given
        GroupReqDTO.CreateGroup request = request(
                List.of(category("morning", "아침 관리")),
                List.of(routine(null, "missing", "루틴", schedules()))
        );

        // when
        Set<String> messages = messages(request);

        // then
        assertThat(messages).contains("요청에 존재하지 않는 사용자 카테고리 키입니다.");
    }

    @Test
    @DisplayName("categoryId와 categoryKey를 동시에 지정하거나 모두 누락하면 거부한다")
    void validate_InvalidCategoryReferenceChoice_HasViolation() {
        // given
        GroupReqDTO.CreateGroup both = request(
                List.of(category("morning", "아침 관리")),
                List.of(routine(1L, "morning", "동시 지정", schedules()))
        );
        GroupReqDTO.CreateGroup neither = request(
                List.of(),
                List.of(routine(null, null, "모두 누락", schedules()))
        );

        // when
        Set<String> bothMessages = messages(both);
        Set<String> neitherMessages = messages(neither);

        // then
        assertThat(bothMessages).contains("기본 카테고리 ID 또는 사용자 카테고리 키 중 하나만 지정해야 합니다.");
        assertThat(neitherMessages).contains("기본 카테고리 ID 또는 사용자 카테고리 키 중 하나만 지정해야 합니다.");
    }

    @Test
    @DisplayName("양수 categoryId의 실제 기본 카테고리 여부는 저장 단계에 위임한다")
    void validate_PositiveCategoryId_HasNoRequestViolation() {
        // given
        GroupReqDTO.CreateGroup request = request(
                List.of(),
                List.of(routine(7L, null, "루틴", schedules()))
        );

        // when
        Set<ConstraintViolation<GroupReqDTO.CreateGroup>> violations = validator.validate(request);

        // then
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("사용자 카테고리는 5개를 초과할 수 없다")
    void validate_SixCustomCategories_HasViolation() {
        // given
        List<GroupReqDTO.CreateGroupCategory> categories = new ArrayList<>();
        for (int index = 1; index <= 6; index++) {
            categories.add(category("key" + index, "분류" + index));
        }
        GroupReqDTO.CreateGroup request = request(
                categories,
                List.of(routine(1L, null, "루틴", schedules()))
        );

        // when
        Set<String> messages = messages(request);

        // then
        assertThat(messages).contains("사용자 카테고리는 최대 5개까지 등록할 수 있습니다.");
    }

    @Test
    @DisplayName("카테고리 이름은 trim 후 1~10자의 한 줄이어야 한다")
    void validate_InvalidCategoryNames_HasViolation() {
        // given
        GroupReqDTO.CreateGroup blank = request(
                List.of(category("blank", "   ")),
                List.of(routine(1L, null, "루틴", schedules()))
        );
        GroupReqDTO.CreateGroup tooLong = request(
                List.of(category("long", "가나다라마바사아자차카")),
                List.of(routine(1L, null, "루틴", schedules()))
        );
        GroupReqDTO.CreateGroup lineBreak = request(
                List.of(category("line", "아침\n관리")),
                List.of(routine(1L, null, "루틴", schedules()))
        );

        // when
        Set<String> blankMessages = messages(blank);
        Set<String> longMessages = messages(tooLong);
        Set<String> lineBreakMessages = messages(lineBreak);

        // then
        assertThat(blankMessages).contains("카테고리 이름은 필수입니다.");
        assertThat(longMessages).contains("카테고리 이름은 10자 이하여야 합니다.");
        assertThat(lineBreakMessages).contains("카테고리 이름에는 줄바꿈을 포함할 수 없습니다.");
    }

    @Test
    @DisplayName("사용자 카테고리 이름은 같은 요청 안에서 대소문자와 무관하게 중복될 수 없다")
    void validate_DuplicateCategoryNames_HasViolation() {
        // given
        GroupReqDTO.CreateGroup requestDuplicate = request(
                List.of(category("one", "Morning"), category("two", "morning")),
                List.of(routine(1L, null, "루틴", schedules()))
        );

        // when
        Set<String> requestMessages = messages(requestDuplicate);

        // then
        assertThat(requestMessages)
                .contains("카테고리 이름은 요청 내 사용자 카테고리와 중복될 수 없습니다.");
    }

    @Test
    @DisplayName("기본 카테고리와의 이름 중복 여부는 개인 카테고리처럼 저장 단계에 위임한다")
    void validate_FixedCategoryNameCandidate_HasNoRequestViolation() {
        // given
        GroupReqDTO.CreateGroup request = request(
                List.of(category("exercise", "운동")),
                List.of(routine(null, "exercise", "루틴", schedules()))
        );

        // when
        Set<ConstraintViolation<GroupReqDTO.CreateGroup>> violations = validator.validate(request);

        // then
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("초기 그룹 루틴은 1개 이상 30개 이하여야 한다")
    void validate_InvalidRoutineCount_HasViolation() {
        // given
        GroupReqDTO.CreateGroup empty = request(List.of(), List.of());
        List<GroupReqDTO.CreateGroupRoutine> routines = new ArrayList<>();
        for (int index = 1; index <= 31; index++) {
            routines.add(routine(1L, null, "루틴" + index, schedules()));
        }
        GroupReqDTO.CreateGroup excessive = request(List.of(), routines);

        // when
        Set<String> emptyMessages = messages(empty);
        Set<String> excessiveMessages = messages(excessive);

        // then
        assertThat(emptyMessages).contains("초기 그룹 루틴은 하나 이상 필요합니다.");
        assertThat(excessiveMessages).contains("초기 그룹 루틴은 최대 30개까지 등록할 수 있습니다.");
    }

    @Test
    @DisplayName("사용자 카테고리 5개와 루틴 30개 및 일정 7개의 경계값을 허용한다")
    void validate_MaximumAllowedCounts_HasNoViolation() {
        // given
        List<GroupReqDTO.CreateGroupCategory> categories = new ArrayList<>();
        for (int index = 1; index <= 5; index++) {
            categories.add(category("key" + index, "분류" + index));
        }
        List<GroupReqDTO.RoutineSchedule> weeklySchedules = List.of(
                schedule(DayOfWeek.MONDAY, 9, 10),
                schedule(DayOfWeek.TUESDAY, 9, 10),
                schedule(DayOfWeek.WEDNESDAY, 9, 10),
                schedule(DayOfWeek.THURSDAY, 9, 10),
                schedule(DayOfWeek.FRIDAY, 9, 10),
                schedule(DayOfWeek.SATURDAY, 9, 10),
                schedule(DayOfWeek.SUNDAY, 9, 10)
        );
        List<GroupReqDTO.CreateGroupRoutine> routines = new ArrayList<>();
        for (int index = 1; index <= 30; index++) {
            routines.add(routine(1L, null, "루틴" + index, weeklySchedules));
        }
        GroupReqDTO.CreateGroup request = request(categories, routines);

        // when
        Set<ConstraintViolation<GroupReqDTO.CreateGroup>> violations = validator.validate(request);

        // then
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("루틴 제목과 설명 길이를 검증하고 같은 제목을 대소문자와 무관하게 거부한다")
    void validate_InvalidRoutineText_HasViolation() {
        // given
        GroupReqDTO.CreateGroup invalidText = new GroupReqDTO.CreateGroup(
                "모임",
                List.of(),
                List.of(new GroupReqDTO.CreateGroupRoutine(
                        1L,
                        null,
                        "123456789012345678901",
                        "가".repeat(256),
                        schedules()
                ))
        );
        GroupReqDTO.CreateGroup duplicate = request(
                List.of(),
                List.of(
                        routine(1L, null, "Morning", schedules()),
                        routine(1L, null, " morning ", schedules(DayOfWeek.TUESDAY))
                )
        );

        // when
        Set<String> invalidTextMessages = messages(invalidText);
        Set<String> duplicateMessages = messages(duplicate);

        // then
        assertThat(invalidTextMessages)
                .contains("루틴 제목은 20자 이하여야 합니다.", "루틴 설명은 255자 이하여야 합니다.");
        assertThat(duplicateMessages).contains("루틴 제목은 요청 안에서 중복될 수 없습니다.");
    }

    @Test
    @DisplayName("루틴 일정은 1~7개이며 요일 중복과 잘못된 시간 범위를 허용하지 않는다")
    void validate_InvalidSchedules_HasViolation() {
        // given
        GroupReqDTO.CreateGroup empty = request(
                List.of(),
                List.of(routine(1L, null, "빈 일정", List.of()))
        );
        GroupReqDTO.CreateGroup duplicateDay = request(
                List.of(),
                List.of(routine(1L, null, "요일 중복", List.of(
                        schedule(DayOfWeek.MONDAY, 9, 10),
                        schedule(DayOfWeek.MONDAY, 10, 11)
                )))
        );
        GroupReqDTO.CreateGroup invalidTime = request(
                List.of(),
                List.of(routine(1L, null, "시간 오류", List.of(
                        schedule(DayOfWeek.MONDAY, 10, 10)
                )))
        );
        List<GroupReqDTO.RoutineSchedule> eightSchedules = new ArrayList<>();
        for (int index = 0; index < 8; index++) {
            eightSchedules.add(schedule(DayOfWeek.values()[index % 7], 9, 10));
        }
        GroupReqDTO.CreateGroup excessive = request(
                List.of(),
                List.of(routine(1L, null, "일정 초과", eightSchedules))
        );

        // when
        Set<String> emptyMessages = messages(empty);
        Set<String> duplicateDayMessages = messages(duplicateDay);
        Set<String> invalidTimeMessages = messages(invalidTime);
        Set<String> excessiveMessages = messages(excessive);

        // then
        assertThat(emptyMessages).contains("하나 이상의 반복 일정이 필요합니다.");
        assertThat(duplicateDayMessages).contains("같은 요일의 일정을 중복해서 등록할 수 없습니다.");
        assertThat(invalidTimeMessages).contains("시작 시간은 종료 시간보다 빨라야 합니다.");
        assertThat(excessiveMessages).contains("반복 일정은 최대 7개까지 등록할 수 있습니다.");
    }

    @Test
    @DisplayName("통합 생성 응답은 clientKey 매핑과 할당 건수를 포함하고 초대코드는 노출하지 않는다")
    void createResult_ResponseContract_ExcludesInviteCode() {
        // given & when
        GroupResDTO.CreateResult result = GroupResDTO.CreateResult.builder()
                .groupId(10L)
                .name("아침 모임")
                .customCategories(List.of(GroupResDTO.CreatedCategory.builder()
                        .clientKey("morning")
                        .categoryId(20L)
                        .name("아침 관리")
                        .color(RoutineCategoryColor.BLUE)
                        .build()))
                .routines(List.of())
                .assignmentCount(1)
                .build();

        // then
        assertThat(result.customCategories().getFirst().clientKey()).isEqualTo("morning");
        assertThat(result.assignmentCount()).isEqualTo(1);
        assertThat(GroupResDTO.CreateResult.class.getRecordComponents())
                .extracting(component -> component.getName())
                .doesNotContain("inviteCode");
    }

    private GroupReqDTO.CreateGroup request(
            List<GroupReqDTO.CreateGroupCategory> categories,
            List<GroupReqDTO.CreateGroupRoutine> routines
    ) {
        return new GroupReqDTO.CreateGroup("아침 모임", categories, routines);
    }

    private GroupReqDTO.CreateGroupCategory category(String key, String name) {
        return new GroupReqDTO.CreateGroupCategory(key, name, RoutineCategoryColor.BLUE);
    }

    private GroupReqDTO.CreateGroupRoutine routine(
            Long categoryId,
            String categoryKey,
            String title,
            List<GroupReqDTO.RoutineSchedule> schedules
    ) {
        return new GroupReqDTO.CreateGroupRoutine(
                categoryId,
                categoryKey,
                title,
                "함께 실천합니다.",
                schedules
        );
    }

    private List<GroupReqDTO.RoutineSchedule> schedules() {
        return schedules(DayOfWeek.MONDAY);
    }

    private List<GroupReqDTO.RoutineSchedule> schedules(DayOfWeek day) {
        return List.of(schedule(day, 9, 10));
    }

    private GroupReqDTO.RoutineSchedule schedule(DayOfWeek day, int startHour, int endHour) {
        return new GroupReqDTO.RoutineSchedule(
                day,
                LocalTime.of(startHour, 0),
                LocalTime.of(endHour, 0)
        );
    }

    private Set<String> messages(GroupReqDTO.CreateGroup request) {
        return validator.validate(request).stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.toSet());
    }
}
