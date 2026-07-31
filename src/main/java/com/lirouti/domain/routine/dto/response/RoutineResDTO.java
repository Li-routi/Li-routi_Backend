package com.lirouti.domain.routine.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.lirouti.domain.routine.enums.RoutineCategoryColor;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

public final class RoutineResDTO {
    private RoutineResDTO() {
    }

    /**
     * 루틴 추가 화면의 카테고리 칩 목록이다. 고정 카테고리가 먼저 오고 그다음이 사용자 카테고리다.
     *
     * @param categories 회원이 사용할 수 있는 카테고리 목록
     * @param addableCount 더 추가할 수 있는 사용자 카테고리 수
     */
    @Builder
    @Schema(name = "CategoryList", description = "루틴 카테고리 목록")
    public record CategoryList(
            @Schema(description = "노출 순서대로 정렬된 카테고리. 고정 카테고리가 먼저 오고 "
                    + "사용자 카테고리는 생성 순서로 이어진다")
            List<Category> categories,

            @Schema(
                    description = "카테고리를 몇 개 더 추가할 수 있는지. "
                            + "`5 - 내 사용자 카테고리 수`이며 0이면 추가 버튼을 비활성화한다. "
                            + "상한이 서버 규칙이므로 클라이언트가 5를 직접 갖고 있지 않도록 내려 준다",
                    example = "4"
            )
            int addableCount
    ) {
    }

    /**
     * 카테고리 한 건이다.
     *
     * @param categoryId 카테고리 ID
     * @param name 카테고리 이름
     * @param color 색상 칩. 고정 카테고리와 "색 없음"은 {@code null}
     * @param fixed 앱이 제공하는 고정 카테고리인지 여부. 사용자 카테고리면 {@code false}
     */
    @Builder
    @Schema(name = "Category", description = "루틴 카테고리")
    public record Category(
            @Schema(description = "카테고리 ID", example = "2")
            Long categoryId,

            @Schema(description = "카테고리 이름", example = "건강")
            String name,

            @Schema(description = "색상 칩. 고정 카테고리와 \"색 없음\"은 null", example = "MAGENTA")
            RoutineCategoryColor color,

            @Schema(
                    description = "앱이 제공하는 고정 카테고리인지 여부. "
                            + "true면 앱이 모든 회원에게 같은 id로 제공하는 카테고리라 수정·삭제할 수 없고, "
                            + "false면 이 회원이 만든 카테고리라 수정·삭제 대상이다. "
                            + "color가 null인 사용자 카테고리(색 없음)도 있으므로 color로는 구분할 수 없다",
                    example = "true"
            )
            boolean fixed
    ) {
    }

    /**
     * 카테고리별 기본 제공 루틴 목록이다.
     *
     * @param templates 기본 제공 루틴 목록
     */
    @Builder
    @Schema(name = "TemplateList", description = "기본 제공 루틴 목록")
    public record TemplateList(
            List<Template> templates
    ) {
    }

    /**
     * 기본 제공 루틴 한 건이다.
     *
     * @param templateId 기본 제공 루틴 ID. 루틴 생성 요청에 그대로 넣는다
     * @param categoryId 노출 카테고리 ID
     * @param categoryName 노출 카테고리 이름
     * @param name 기본 제공 루틴 이름
     * @param alreadyAdded 회원이 이미 등록한 기본 루틴인지 여부. 목록의 체크 상태에 대응한다
     */
    @Builder
    @Schema(name = "Template", description = "기본 제공 루틴")
    public record Template(
            @Schema(description = "기본 제공 루틴 ID. 생성 요청의 templateId에 그대로 넣는다",
                    example = "201")
            Long templateId,

            @Schema(description = "노출 카테고리 ID", example = "2")
            Long categoryId,

            @Schema(description = "노출 카테고리 이름", example = "건강")
            String categoryName,

            @Schema(description = "기본 제공 루틴 이름", example = "물 챙겨 마시기")
            String name,

            @Schema(
                    description = "이 회원이 이미 등록한 기본 루틴인지 여부. "
                            + "true면 루틴 추가 목록에서 체크된 상태로 그려야 하고, "
                            + "같은 templateId로 다시 생성하면 ROUTINE409_2가 응답된다",
                    example = "false"
            )
            boolean alreadyAdded
    ) {
    }

    /**
     * 벌크 생성 결과다.
     *
     * @param routines 생성된 루틴 목록. 요청 순서를 유지한다
     * @param activeRoutineCount 생성 후 회원의 활성 루틴 총 개수
     */
    @Builder
    @Schema(name = "RoutineCreateResult", description = "개인 루틴 생성 결과")
    public record RoutineCreateResult(
            @Schema(description = "이번 요청으로 생성된 루틴. 요청에 담은 순서를 그대로 유지한다")
            List<Routine> routines,

            @Schema(
                    description = "생성이 끝난 뒤 이 회원이 가진 활성 루틴의 총 개수. "
                            + "이번에 만든 것뿐 아니라 기존 루틴까지 합한 값이다(상한 30개). "
                            + "다음 요청에서 몇 개를 더 넣을 수 있는지 알려면 목록을 다시 조회하지 않고 "
                            + "이 값을 쓰면 된다",
                    example = "3"
            )
            long activeRoutineCount
    ) {
    }

    /**
     * 생성된 개인 루틴 한 건이다.
     *
     * @param routineId 생성된 루틴 ID
     * @param categoryId 소속 카테고리 ID
     * @param categoryName 소속 카테고리 이름
     * @param templateId 원본 기본 제공 루틴 ID. 직접 추가했거나 이름을 바꾼 루틴은 {@code null}
     * @param name 루틴 이름
     * @param endTime 마감 시각
     * @param repeatDays 반복 요일. 월요일부터의 요일 순서로 정렬된다
     * @param alarmTime 알람 시각. 설정하지 않았으면 {@code null}
     */
    @Builder
    @Schema(name = "Routine", description = "생성된 개인 루틴")
    public record Routine(
            @Schema(description = "생성된 루틴 ID", example = "12")
            Long routineId,

            @Schema(description = "소속 카테고리 ID", example = "2")
            Long categoryId,

            @Schema(description = "소속 카테고리 이름", example = "건강")
            String categoryName,

            @Schema(description = "원본 기본 제공 루틴 ID. 직접 추가했거나 이름을 바꾼 루틴은 null",
                    example = "201")
            Long templateId,

            @Schema(description = "루틴 이름", example = "물 챙겨 마시기")
            String name,

            @Schema(type = "string", description = "마감 시각(HH:mm)", example = "23:59")
            @JsonFormat(pattern = "HH:mm")
            LocalTime endTime,

            @Schema(description = "반복 요일. 월요일부터의 요일 순서로 정렬된다",
                    example = "[\"MONDAY\", \"WEDNESDAY\", \"FRIDAY\"]")
            List<DayOfWeek> repeatDays,

            @Schema(type = "string", description = "알람 시각(HH:mm). 설정하지 않았으면 null",
                    example = "08:00")
            @JsonFormat(pattern = "HH:mm")
            LocalTime alarmTime,

            @Schema(
                    description = "오늘 이 루틴을 인증했는지. 루틴 생성 응답에서는 항상 false다",
                    example = "false"
            )
            boolean completedToday
    ) {
    }
}
