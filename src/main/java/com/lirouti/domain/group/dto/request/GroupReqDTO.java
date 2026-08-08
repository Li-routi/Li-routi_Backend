package com.lirouti.domain.group.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.lirouti.domain.group.entity.GroupRoutine;
import com.lirouti.domain.group.entity.GroupRoutineCategory;
import com.lirouti.domain.routine.enums.RoutineCategoryColor;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public final class GroupReqDTO {
    private GroupReqDTO() {
    }

    /** 영구 초대코드로 그룹 가입을 요청한다. */
    @Schema(name = "JoinGroup", description = "초대코드 기반 그룹 가입 요청")
    public record JoinGroup(
            @Schema(description = "그룹에 영구 귀속된 7자리 초대코드", example = "AB12CD3")
            @NotBlank(message = "초대코드는 필수입니다.")
            @Size(min = 7, max = 7, message = "초대코드는 7자여야 합니다.")
            String inviteCode
    ) {
        public JoinGroup {
            inviteCode = inviteCode == null ? null : inviteCode.trim().toUpperCase(Locale.ROOT);
        }
    }

    /** 그룹 설정에서 사용자 카테고리를 추가하는 요청이다. */
    @Schema(name = "CreateGroupRoutineCategory", description = "그룹 사용자 카테고리 생성 요청")
    public record CreateCategory(
            @Schema(
                    description = "카테고리 이름. 앞뒤 공백을 제거한 뒤 1~10자이며 줄바꿈을 포함할 수 없다",
                    example = "아침 관리"
            )
            @NotBlank(message = "카테고리 이름은 필수입니다.")
            @Size(
                    max = GroupRoutineCategory.MAX_GROUP_CATEGORY_NAME_LENGTH,
                    message = "카테고리 이름은 10자 이하여야 합니다."
            )
            String name,

            @Schema(description = "색상 칩. 생략하면 색 없음으로 저장된다", example = "BLUE")
            RoutineCategoryColor color
    ) {
        public CreateCategory {
            name = name == null ? null : name.trim();
        }

        @AssertTrue(message = "카테고리 이름에는 줄바꿈을 포함할 수 없습니다.")
        @JsonIgnore
        public boolean isNameSingleLine() {
            return name == null || !(name.contains("\n") || name.contains("\r"));
        }
    }

    /**
     * 그룹과 초기 카테고리·루틴을 한 번에 생성하는 요청이다.
     * 요청 전체만으로 판단 가능한 키 참조와 중복 규칙은 record의 검증 메서드가 담당한다.
     */
    @Schema(name = "CreateGroup", description = "모임방과 초기 그룹 루틴 통합 생성 요청")
    public record CreateGroup(
            @Schema(description = "모임 이름. 앞뒤 공백을 제거한 뒤 1~20자", example = "아침 루틴 모임")
            @NotBlank(message = "모임 이름은 필수입니다.")
            @Size(max = 20, message = "모임 이름은 20자 이하여야 합니다.")
            String name,

            @Schema(description = "이번 요청에서 함께 생성할 그룹 사용자 카테고리. 최대 5개")
            @NotNull(message = "사용자 카테고리 목록은 필수입니다.")
            @Size(
                    max = GroupRoutineCategory.MAX_GROUP_CATEGORY_COUNT,
                    message = "사용자 카테고리는 최대 5개까지 등록할 수 있습니다."
            )
            List<@NotNull(message = "사용자 카테고리는 null일 수 없습니다.") @Valid CreateGroupCategory>
                    customCategories,

            @Schema(description = "그룹 생성과 함께 등록할 초기 그룹 루틴. 1~30개")
            @NotEmpty(message = "초기 그룹 루틴은 하나 이상 필요합니다.")
            @Size(
                    max = GroupRoutine.MAX_GROUP_ROUTINE_COUNT,
                    message = "초기 그룹 루틴은 최대 30개까지 등록할 수 있습니다."
            )
            List<@NotNull(message = "초기 그룹 루틴은 null일 수 없습니다.") @Valid CreateGroupRoutine>
                    routines
    ) {
        public CreateGroup {
            name = name == null ? null : name.trim();
        }

        @AssertTrue(message = "사용자 카테고리 clientKey는 요청 안에서 중복될 수 없습니다.")
        @JsonIgnore
        public boolean isClientKeyUnique() {
            if (customCategories == null) {
                return true;
            }
            Set<String> keys = new HashSet<>();
            return customCategories.stream()
                    .filter(Objects::nonNull)
                    .map(CreateGroupCategory::clientKey)
                    .filter(CreateGroup::hasText)
                    .allMatch(keys::add);
        }

        @AssertTrue(message = "카테고리 이름은 요청 내 사용자 카테고리와 중복될 수 없습니다.")
        @JsonIgnore
        public boolean isCategoryNameUnique() {
            if (customCategories == null) {
                return true;
            }
            Set<String> names = new HashSet<>();
            return customCategories.stream()
                    .filter(Objects::nonNull)
                    .map(CreateGroupCategory::name)
                    .filter(CreateGroup::hasText)
                    .map(CreateGroup::normalizeName)
                    .allMatch(names::add);
        }

        @AssertTrue(message = "기본 카테고리 ID 또는 사용자 카테고리 키 중 하나만 지정해야 합니다.")
        @JsonIgnore
        public boolean isCategoryReferenceExclusive() {
            if (routines == null) {
                return true;
            }
            return routines.stream()
                    .filter(Objects::nonNull)
                    .allMatch(routine -> (routine.categoryId() != null)
                            != hasText(routine.categoryKey()));
        }

        @AssertTrue(message = "요청에 존재하지 않는 사용자 카테고리 키입니다.")
        @JsonIgnore
        public boolean isCategoryKeyResolvable() {
            if (customCategories == null || routines == null) {
                return true;
            }
            Set<String> keys = customCategories.stream()
                    .filter(Objects::nonNull)
                    .map(CreateGroupCategory::clientKey)
                    .filter(CreateGroup::hasText)
                    .collect(Collectors.toSet());

            return routines.stream()
                    .filter(Objects::nonNull)
                    .map(CreateGroupRoutine::categoryKey)
                    .filter(CreateGroup::hasText)
                    .allMatch(keys::contains);
        }

        @AssertTrue(message = "루틴 제목은 요청 안에서 중복될 수 없습니다.")
        @JsonIgnore
        public boolean isRoutineTitleUnique() {
            if (routines == null) {
                return true;
            }
            Set<String> titles = new HashSet<>();
            return routines.stream()
                    .filter(Objects::nonNull)
                    .map(CreateGroupRoutine::title)
                    .filter(CreateGroup::hasText)
                    .map(CreateGroup::normalizeName)
                    .allMatch(titles::add);
        }

        private static boolean hasText(String value) {
            return value != null && !value.isBlank();
        }

        private static String normalizeName(String value) {
            return value.toLowerCase(Locale.ROOT);
        }
    }

    /** 같은 생성 요청 안에서만 사용되는 임시 키를 가진 그룹 사용자 카테고리다. */
    @Schema(name = "CreateGroupCategory", description = "그룹 생성과 함께 추가할 사용자 카테고리")
    public record CreateGroupCategory(
            @Schema(description = "요청 안에서 루틴과 카테고리를 연결하는 임시 키", example = "morning")
            @NotBlank(message = "사용자 카테고리 clientKey는 필수입니다.")
            String clientKey,

            @Schema(description = "앞뒤 공백을 제거한 1~10자의 한 줄 카테고리 이름", example = "아침 관리")
            @NotBlank(message = "카테고리 이름은 필수입니다.")
            @Size(
                    max = GroupRoutineCategory.MAX_GROUP_CATEGORY_NAME_LENGTH,
                    message = "카테고리 이름은 10자 이하여야 합니다."
            )
            String name,

            @Schema(description = "색상 칩. 생략하면 색 없음으로 저장된다", example = "BLUE")
            RoutineCategoryColor color
    ) {
        public CreateGroupCategory {
            clientKey = clientKey == null ? null : clientKey.trim();
            name = name == null ? null : name.trim();
        }

        @AssertTrue(message = "카테고리 이름에는 줄바꿈을 포함할 수 없습니다.")
        @JsonIgnore
        public boolean isNameSingleLine() {
            return name == null || !(name.contains("\n") || name.contains("\r"));
        }
    }

    /** 기본 카테고리 ID 또는 요청 내 사용자 카테고리 키를 참조하는 초기 그룹 루틴이다. */
    @Schema(name = "CreateGroupRoutine", description = "그룹 생성과 함께 등록할 초기 그룹 루틴")
    public record CreateGroupRoutine(
            @Schema(description = "기본 제공 그룹 카테고리 ID. categoryKey와 함께 사용할 수 없다", example = "1")
            @Positive(message = "기본 카테고리 ID는 양수여야 합니다.")
            Long categoryId,

            @Schema(description = "같은 요청의 사용자 카테고리 clientKey. categoryId와 함께 사용할 수 없다",
                    example = "morning")
            String categoryKey,

            @NotBlank(message = "루틴 제목은 필수입니다.")
            @Size(max = 20, message = "루틴 제목은 20자 이하여야 합니다.")
            String title,

            @NotBlank(message = "루틴 설명은 필수입니다.")
            @Size(max = 255, message = "루틴 설명은 255자 이하여야 합니다.")
            String description,

            @NotEmpty(message = "하나 이상의 반복 일정이 필요합니다.")
            @Size(max = 7, message = "반복 일정은 최대 7개까지 등록할 수 있습니다.")
            List<@NotNull(message = "반복 일정은 null일 수 없습니다.") @Valid RoutineSchedule> schedules
    ) {
        public CreateGroupRoutine {
            categoryKey = categoryKey == null || categoryKey.isBlank()
                    ? null
                    : categoryKey.trim();
            title = title == null ? null : title.trim();
        }

        @AssertTrue(message = "같은 요일의 일정을 중복해서 등록할 수 없습니다.")
        @JsonIgnore
        public boolean isScheduleDayUnique() {
            if (schedules == null) {
                return true;
            }
            return schedules.stream()
                    .filter(Objects::nonNull)
                    .map(RoutineSchedule::repeatDay)
                    .filter(Objects::nonNull)
                    .allMatch(new HashSet<>()::add);
        }
    }

    /**
     * 카테고리와 요일별 수행 일정을 포함한 그룹 루틴 생성 요청이다.
     *
     * @param categoryId 앱에서 관리하는 루틴 카테고리 ID
     * @param title 그룹 내 루틴 제목
     * @param description 루틴 설명
     * @param schedules 중복되지 않는 요일별 수행 일정
     */
    @Schema(name = "GroupRoutineCreateRequest", description = "그룹 루틴 생성 요청")
    public record GroupRoutineCreateRequest(
            @NotNull(message = "카테고리는 필수입니다.")
            @Positive(message = "카테고리 ID는 양수여야 합니다.")
            Long categoryId,

            @NotBlank(message = "루틴 제목은 필수입니다.")
            @Size(max = 20, message = "루틴 제목은 20자 이하여야 합니다.")
            String title,

            @NotBlank(message = "루틴 설명은 필수입니다.")
            @Size(max = 255, message = "루틴 설명은 255자 이하여야 합니다.")
            String description,

            @NotEmpty(message = "하나 이상의 반복 일정이 필요합니다.")
            @Size(max = 7, message = "반복 일정은 최대 7개까지 등록할 수 있습니다.")
            List<@NotNull(message = "반복 일정은 null일 수 없습니다.") @Valid RoutineSchedule> schedules
    ) {
        public GroupRoutineCreateRequest {
            title = title == null ? null : title.trim();
        }

        /**
         * null 요소와 null 요일은 각 필드 제약에 맡기고, 입력된 요일의 중복만 검증한다.
         *
         * @return null이 아닌 일정의 반복 요일이 모두 다르면 {@code true}
         */
        @AssertTrue(message = "같은 요일의 일정을 중복해서 등록할 수 없습니다.")
        @JsonIgnore
        public boolean isScheduleDayUnique() {
            if (schedules == null) {
                return true;
            }
            return schedules.stream()
                    .filter(Objects::nonNull)
                    .map(RoutineSchedule::repeatDay)
                    .filter(Objects::nonNull)
                    .allMatch(new HashSet<>()::add);
        }
    }

    /**
     * 카테고리와 요일별 수행 일정을 전체 교체하는 그룹 루틴 수정 요청이다.
     *
     * @param categoryId 앱에서 관리하는 루틴 카테고리 ID
     * @param title 그룹 내 루틴 제목
     * @param description 루틴 설명
     * @param schedules 중복되지 않는 요일별 수행 일정
     */
    public record UpdateRoutine(
            @NotNull(message = "카테고리는 필수입니다.")
            @Positive(message = "카테고리 ID는 양수여야 합니다.")
            Long categoryId,

            @NotBlank(message = "루틴 제목은 필수입니다.")
            @Size(max = 20, message = "루틴 제목은 20자 이하여야 합니다.")
            String title,

            @NotBlank(message = "루틴 설명은 필수입니다.")
            @Size(max = 255, message = "루틴 설명은 255자 이하여야 합니다.")
            String description,

            @NotEmpty(message = "하나 이상의 반복 일정이 필요합니다.")
            @Size(max = 7, message = "반복 일정은 최대 7개까지 등록할 수 있습니다.")
            List<@NotNull(message = "반복 일정은 null일 수 없습니다.") @Valid RoutineSchedule> schedules
    ) {
        public UpdateRoutine {
            title = title == null ? null : title.trim();
        }

        /**
         * null 요소와 null 요일은 각 필드 제약에 맡기고, 입력된 요일의 중복만 검증한다.
         *
         * @return null이 아닌 일정의 반복 요일이 모두 다르면 {@code true}
         */
        @AssertTrue(message = "같은 요일의 일정을 중복해서 등록할 수 없습니다.")
        @JsonIgnore
        public boolean isScheduleDayUnique() {
            if (schedules == null) {
                return true;
            }
            return schedules.stream()
                    .filter(Objects::nonNull)
                    .map(RoutineSchedule::repeatDay)
                    .filter(Objects::nonNull)
                    .allMatch(new HashSet<>()::add);
        }
    }

    /**
     * 한 반복 요일에 적용할 수행 시간 범위다.
     *
     * @param repeatDay 반복 요일
     * @param startTime 수행 시작 시각
     * @param endTime 수행 마감 시각
     */
    @Schema(name = "GroupRoutineScheduleRequest", description = "그룹 루틴 수행 일정 요청")
    public record RoutineSchedule(
            @NotNull(message = "반복 요일은 필수입니다.")
            DayOfWeek repeatDay,

            @NotNull(message = "시작 시간은 필수입니다.")
            @JsonFormat(pattern = "HH:mm")
            LocalTime startTime,

            @NotNull(message = "종료 시간은 필수입니다.")
            @JsonFormat(pattern = "HH:mm")
            LocalTime endTime
    ) {
        /**
         * 시작·종료 값의 필수 검증과 분리해 시간의 선후 관계를 검증한다.
         *
         * @return 값이 누락됐거나 시작 시간이 종료 시간보다 빠르면 {@code true}
         */
        @AssertTrue(message = "시작 시간은 종료 시간보다 빨라야 합니다.")
        @JsonIgnore
        public boolean isTimeRangeValid() {
            if (startTime == null || endTime == null) {
                return true;
            }
            return startTime.isBefore(endTime);
        }
    }
}
