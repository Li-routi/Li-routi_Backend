package com.lirouti.domain.routine.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.lirouti.domain.routine.entity.MemberRoutine;
import com.lirouti.domain.routine.entity.RoutineCategory;
import com.lirouti.domain.routine.enums.RoutineCategoryColor;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

public final class RoutineReqDTO {
    private RoutineReqDTO() {
    }

    /**
     * 루틴 추가 화면에서 선택·작성한 루틴들을 한 번에 등록하는 요청이다.
     *
     * <p>화면이 "총 N개 선택됨 → 확인" 흐름이라 벌크로 받는다. 활성 루틴 상한도 요청 전체를
     * 기존 루틴 수에 더해서 판단해야 하므로, 항목을 한 건씩 나눠 받으면 상한 검사가
     * 요청 사이에서 흔들린다.
     *
     * @param routines 등록할 루틴 목록
     */
    @Schema(
            name = "PersonalRoutineCreateRequest",
            description = "개인 루틴 벌크 생성 요청",
            // 스웨거의 "Try it out" 입력창에 그대로 채워지는 값이다. 세 항목이 각각 다른
            // 동작을 보여 준다 — 이름을 유지한 기본 루틴, 이름을 바꿔 원본 선택이 해제되는
            // 기본 루틴, 사용자 카테고리에 직접 추가하는 루틴.
            example = """
                    {
                      "routines": [
                        {
                          "categoryId": 2,
                          "templateId": 201,
                          "name": "물 챙겨 마시기"
                        },
                        {
                          "categoryId": 2,
                          "templateId": 203,
                          "name": "아침 한 끼",
                          "endTime": "09:30",
                          "repeatDays": ["MONDAY", "WEDNESDAY", "FRIDAY"],
                          "alarmTime": "08:00"
                        },
                        {
                          "categoryId": 1,
                          "name": "저녁 산책",
                          "endTime": "21:00"
                        }
                      ]
                    }"""
    )
    public record CreateRoutines(
            @NotEmpty(message = "루틴을 하나 이상 선택해야 합니다.")
            @Size(
                    max = MemberRoutine.MAX_ACTIVE_COUNT,
                    message = "한 번에 등록할 수 있는 루틴은 최대 30개입니다."
            )
            List<@NotNull(message = "루틴은 null일 수 없습니다.") @Valid CreateRoutine> routines
    ) {
        /**
         * 같은 기본 제공 루틴을 한 요청에서 두 번 선택했는지 검증한다.
         * 직접 추가한 루틴({@code templateId}가 없는 항목)은 같은 이름을 허용하므로 대상이 아니다.
         *
         * @return 요청에 담긴 기본 루틴 ID가 모두 다르면 {@code true}
         */
        @AssertTrue(message = "같은 기본 제공 루틴을 중복해서 선택할 수 없습니다.")
        @JsonIgnore
        public boolean isTemplateSelectionUnique() {
            if (routines == null) {
                return true;
            }
            return routines.stream()
                    .filter(Objects::nonNull)
                    .map(CreateRoutine::templateId)
                    .filter(Objects::nonNull)
                    .allMatch(new HashSet<>()::add);
        }
    }

    /**
     * 등록할 루틴 한 건이다.
     *
     * <p>{@code name}은 컴팩트 생성자에서 앞뒤 공백을 제거한다. 기획의 길이 규칙이 "앞뒤 공백
     * 제거 후 1~20자"라서, 정규화 전 값에 {@code @Size}를 걸면 공백이 붙은 20자 이름이
     * 규칙에 맞는데도 거절된다.
     *
     * @param categoryId 루틴이 소속될 카테고리 ID. 고정 카테고리이거나 본인이 만든 카테고리
     * @param templateId 선택한 기본 제공 루틴 ID. 직접 추가한 루틴이면 {@code null}
     * @param name 루틴 이름. 기본 루틴을 골랐고 이름을 그대로 두면 원본 선택이 유지되고,
     *             이름을 바꾸면 원본 선택이 해제되어 사용자 루틴으로 등록된다
     * @param endTime 마감 시각. 미지정 시 23:59
     * @param repeatDays 반복 요일. 미지정 시 매일
     * @param alarmTime 알람 시각. 선택하지 않았으면 {@code null}
     */
    @Schema(name = "PersonalRoutineCreateItem", description = "등록할 개인 루틴 한 건")
    public record CreateRoutine(
            @Schema(
                    description = "루틴이 소속될 카테고리 ID. 고정 카테고리(운동 1, 건강 2, "
                            + "자기계발 3, 생활정리 4, 마음관리 5, 취미 6)이거나 본인이 만든 카테고리",
                    example = "2"
            )
            @NotNull(message = "카테고리는 필수입니다.")
            @Positive(message = "카테고리 ID는 양수여야 합니다.")
            Long categoryId,

            @Schema(
                    description = "선택한 기본 제공 루틴 ID. 직접 추가한 루틴이면 생략한다",
                    example = "201"
            )
            @Positive(message = "기본 제공 루틴 ID는 양수여야 합니다.")
            Long templateId,

            @Schema(
                    description = "루틴 이름. 앞뒤 공백을 제거한 뒤 1~20자이며 줄바꿈을 포함할 수 없다. "
                            + "기본 루틴을 골랐고 이름을 그대로 두면 원본 선택이 유지되고, "
                            + "이름을 바꾸면 원본 선택이 해제되어 사용자 루틴으로 등록된다",
                    example = "물 챙겨 마시기"
            )
            @NotBlank(message = "루틴 이름은 필수입니다.")
            @Size(
                    max = MemberRoutine.MAX_NAME_LENGTH,
                    message = "루틴 이름은 20자 이하여야 합니다."
            )
            String name,

            @Schema(
                    type = "string",
                    description = "마감 시각(HH:mm). 생략하면 23:59",
                    example = "23:59"
            )
            @JsonFormat(pattern = "HH:mm")
            LocalTime endTime,

            @Schema(
                    description = "반복 요일. 생략하면 매일",
                    example = "[\"MONDAY\", \"WEDNESDAY\", \"FRIDAY\"]"
            )
            @Size(max = 7, message = "반복 요일은 최대 7개까지 지정할 수 있습니다.")
            List<@NotNull(message = "반복 요일은 null일 수 없습니다.") DayOfWeek> repeatDays,

            @Schema(
                    type = "string",
                    description = "알람 시각(HH:mm). 생략하면 알림을 보내지 않는다",
                    example = "08:00"
            )
            @JsonFormat(pattern = "HH:mm")
            LocalTime alarmTime
    ) {
        public CreateRoutine {
            name = name == null ? null : name.trim();
        }

        /**
         * 이름에 줄바꿈이 들어 있는지 검증한다. 목록의 한 줄에 그려지는 값이라 개행을 허용하지 않는다.
         * 비어 있는 경우는 {@code @NotBlank}가 담당한다.
         *
         * @return 이름이 없거나 줄바꿈을 포함하지 않으면 {@code true}
         */
        @AssertTrue(message = "루틴 이름에는 줄바꿈을 포함할 수 없습니다.")
        @JsonIgnore
        public boolean isNameSingleLine() {
            return name == null || !(name.contains("\n") || name.contains("\r"));
        }

        /**
         * 지정한 반복 요일이 서로 다른지 검증한다. 요일을 아예 보내지 않으면 매일이 기본값이므로
         * 빈 값은 여기서 막지 않고, 빈 배열을 명시적으로 보낸 경우만 거절한다.
         *
         * @return 요일을 생략했거나, 하나 이상이며 모두 다르면 {@code true}
         */
        @AssertTrue(message = "반복 요일은 하나 이상이어야 하고 중복될 수 없습니다.")
        @JsonIgnore
        public boolean isRepeatDaysValid() {
            if (repeatDays == null) {
                return true;
            }
            return !repeatDays.isEmpty()
                    && repeatDays.stream().filter(Objects::nonNull).allMatch(new HashSet<>()::add);
        }
    }

    /**
     * 저장된 개인 루틴의 설정을 전체 교체하는 요청이다.
     *
     * <p>설정 바텀시트가 전체 폼을 제출하므로 알람을 제외한 필드는 모두 필수다.
     * {@code alarmTime}은 {@code null}이면 알람을 사용하지 않는다는 뜻이다.
     * 카테고리 이동은 기본 루틴 원본과의 관계가 정해지지 않아 이 요청에서 다루지 않는다.
     */
    @Schema(name = "PersonalRoutineUpdateRequest", description = "개인 루틴 설정 수정 요청")
    public record UpdateRoutine(
            @Schema(description = "루틴 이름. 앞뒤 공백을 제거한 뒤 1~20자", example = "물 2L 마시기")
            @NotBlank(message = "루틴 이름은 필수입니다.")
            @Size(
                    max = MemberRoutine.MAX_NAME_LENGTH,
                    message = "루틴 이름은 20자 이하여야 합니다."
            )
            String name,

            @Schema(type = "string", description = "마감 시각(HH:mm)", example = "21:00")
            @NotNull(message = "마감 시각은 필수입니다.")
            @JsonFormat(pattern = "HH:mm")
            LocalTime endTime,

            @Schema(
                    description = "반복 요일. 하나 이상이며 중복될 수 없습니다.",
                    example = "[\"MONDAY\", \"WEDNESDAY\", \"FRIDAY\"]"
            )
            @NotEmpty(message = "반복 요일은 하나 이상이어야 합니다.")
            @Size(max = 7, message = "반복 요일은 최대 7개까지 지정할 수 있습니다.")
            List<@NotNull(message = "반복 요일은 null일 수 없습니다.") DayOfWeek> repeatDays,

            @Schema(
                    type = "string",
                    description = "알람 시각(HH:mm). null이면 알람 없음",
                    example = "20:30"
            )
            @JsonFormat(pattern = "HH:mm")
            LocalTime alarmTime
    ) {
        public UpdateRoutine {
            name = name == null ? null : name.trim();
        }

        @AssertTrue(message = "루틴 이름에는 줄바꿈을 포함할 수 없습니다.")
        @JsonIgnore
        public boolean isNameSingleLine() {
            return name == null || !(name.contains("\n") || name.contains("\r"));
        }

        @AssertTrue(message = "같은 반복 요일을 중복해서 지정할 수 없습니다.")
        @JsonIgnore
        public boolean isRepeatDayUnique() {
            if (repeatDays == null) {
                return true;
            }
            return repeatDays.stream()
                    .filter(Objects::nonNull)
                    .allMatch(new HashSet<>()::add);
        }
    }

    /**
     * 사용자 카테고리 추가 요청이다.
     *
     * <p>{@code name}은 {@link CreateRoutine}과 같은 이유로 컴팩트 생성자에서 정규화한다.
     *
     * @param name 카테고리 이름. 고정 카테고리와 본인의 기존 카테고리와 중복될 수 없다
     * @param color 색상 칩. "없음"을 고르면 {@code null}
     */
    @Schema(
            name = "PersonalRoutineCategoryCreateRequest",
            description = "사용자 카테고리 추가 요청",
            example = """
                    {
                      "name": "운동일지",
                      "color": "MAGENTA"
                    }"""
    )
    public record CreateCategory(
            @Schema(
                    description = "카테고리 이름. 앞뒤 공백을 제거한 뒤 1~10자이며 줄바꿈을 포함할 수 없다. "
                            + "고정 카테고리 및 본인의 기존 카테고리와 같은 이름은 사용할 수 없다",
                    example = "운동일지"
            )
            @NotBlank(message = "카테고리 이름은 필수입니다.")
            @Size(
                    max = RoutineCategory.MAX_MEMBER_CATEGORY_NAME_LENGTH,
                    message = "카테고리 이름은 10자 이하여야 합니다."
            )
            String name,

            @Schema(
                    description = "색상 칩. 생략하면 \"색 없음\"으로 저장된다",
                    example = "MAGENTA"
            )
            RoutineCategoryColor color
    ) {
        public CreateCategory {
            name = name == null ? null : name.trim();
        }

        /**
         * 이름에 줄바꿈이 들어 있는지 검증한다. 카테고리 칩도 한 줄로 그려진다.
         *
         * @return 이름이 없거나 줄바꿈을 포함하지 않으면 {@code true}
         */
        @AssertTrue(message = "카테고리 이름에는 줄바꿈을 포함할 수 없습니다.")
        @JsonIgnore
        public boolean isNameSingleLine() {
            return name == null || !(name.contains("\n") || name.contains("\r"));
        }
    }

    @Schema(
            name = "PersonalRoutineCategoryUpdateRequest",
            description = "사용자 개인 루틴 카테고리 수정 요청",
            example = """
                    {
                      "name": "아침 관리",
                      "color": "BLUE"
                    }"""
    )
    public record UpdateCategory(
            @Schema(description = "카테고리 이름. 앞뒤 공백 제거 후 1~10자, 줄바꿈 불가",
                    example = "아침 관리")
            @NotBlank(message = "카테고리 이름은 필수입니다.")
            @Size(
                    max = RoutineCategory.MAX_MEMBER_CATEGORY_NAME_LENGTH,
                    message = "카테고리 이름은 10자 이하여야 합니다."
            )
            String name,

            @Schema(description = "색상 칩. null이면 색상 없음", example = "BLUE")
            RoutineCategoryColor color
    ) {
        public UpdateCategory {
            name = name == null ? null : name.trim();
        }

        @AssertTrue(message = "카테고리 이름에는 줄바꿈을 포함할 수 없습니다.")
        @JsonIgnore
        public boolean isNameSingleLine() {
            return name == null || !(name.contains("\n") || name.contains("\r"));
        }
    }
}
