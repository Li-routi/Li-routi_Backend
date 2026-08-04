package com.lirouti.domain.routine.converter;

import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.routine.dto.request.RoutineReqDTO;
import com.lirouti.domain.routine.dto.response.RoutineResDTO;
import com.lirouti.domain.routine.entity.MemberRoutine;
import com.lirouti.domain.routine.entity.MemberRoutineSchedule;
import com.lirouti.domain.routine.entity.RoutineCategory;
import com.lirouti.domain.routine.entity.RoutineTemplate;
import java.time.DayOfWeek;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

public final class RoutineConverter {
    private RoutineConverter() {
    }

    /** 반복 요일을 지정하지 않았을 때 쓰는 기본값. 기획의 기본값은 매일이다. */
    private static final List<DayOfWeek> EVERY_DAY = List.of(DayOfWeek.values());

    /**
     * 생성 요청과 검증된 회원·카테고리·기본 루틴을 반복 요일이 연결된 개인 루틴으로 변환한다.
     *
     * <p>이름을 바꾼 기본 루틴에서 원본 참조를 떼는 판단은 {@link MemberRoutine} 생성자가 한다.
     * 여기서는 전달받은 값을 그대로 넘긴다.
     *
     * @param request 루틴 한 건의 생성 요청
     * @param member 소유 회원
     * @param category 검증된 소속 카테고리
     * @param template 선택한 기본 제공 루틴. 직접 추가한 루틴이면 {@code null}
     * @param name 앞뒤 공백을 제거하고 길이를 검증한 루틴 이름
     * @return 반복 요일이 연결된 개인 루틴
     */
    public static MemberRoutine toMemberRoutine(
            RoutineReqDTO.CreateRoutine request,
            Member member,
            RoutineCategory category,
            RoutineTemplate template,
            String name
    ) {
        MemberRoutine routine = MemberRoutine.builder()
                .member(member)
                .category(category)
                .template(template)
                .name(name)
                .endTime(request.endTime())
                .alarmTime(request.alarmTime())
                .active(true)
                .build();

        repeatDaysOf(request).forEach(routine::addSchedule);
        return routine;
    }

    /**
     * 카테고리 목록을 응답으로 변환한다.
     *
     * @param categories 노출 순서대로 정렬된 카테고리 목록
     * @param addableCount 더 추가할 수 있는 사용자 카테고리 수
     * @return 카테고리 목록 응답
     */
    public static RoutineResDTO.CategoryList toCategoryList(
            List<RoutineCategory> categories,
            int addableCount
    ) {
        return RoutineResDTO.CategoryList.builder()
                .categories(categories.stream().map(RoutineConverter::toCategory).toList())
                .addableCount(addableCount)
                .build();
    }

    /**
     * 카테고리를 응답으로 변환한다.
     *
     * @param category 변환할 카테고리
     * @return 카테고리 응답
     */
    public static RoutineResDTO.Category toCategory(RoutineCategory category) {
        return RoutineResDTO.Category.builder()
                .categoryId(category.getId())
                .name(category.getName())
                .color(category.getColor())
                .fixed(category.isFixed())
                .build();
    }

    /**
     * 기본 제공 루틴 목록을 응답으로 변환한다.
     *
     * @param templates 노출 순서대로 정렬된 기본 제공 루틴 목록
     * @param addedTemplateIds 회원이 이미 등록한 기본 루틴 ID 집합
     * @return 기본 제공 루틴 목록 응답
     */
    public static RoutineResDTO.TemplateList toTemplateList(
            List<RoutineTemplate> templates,
            Set<Long> addedTemplateIds
    ) {
        return RoutineResDTO.TemplateList.builder()
                .templates(templates.stream()
                        .map(template -> toTemplate(template, addedTemplateIds.contains(template.getId())))
                        .toList())
                .build();
    }

    /**
     * 벌크 생성 결과를 응답으로 변환한다.
     *
     * @param routines 저장된 개인 루틴 목록. 요청 순서를 유지한다
     * @param activeRoutineCount 생성 후 회원의 활성 루틴 총 개수
     * @return 루틴 생성 응답
     */
    public static RoutineResDTO.RoutineCreateResult toRoutineCreateResult(
            List<MemberRoutine> routines,
            long activeRoutineCount
    ) {
        return RoutineResDTO.RoutineCreateResult.builder()
                .routines(routines.stream().map(RoutineConverter::toRoutine).toList())
                .activeRoutineCount(activeRoutineCount)
                .build();
    }

    public static RoutineResDTO.RoutineList toRoutineListResponse(List<MemberRoutine> routines) {
        return RoutineResDTO.RoutineList.builder()
                .routines(routines.stream().map(RoutineConverter::toRoutine).toList())
                .build();
    }

    /** 기본 제공 루틴 한 건을 응답으로 변환한다. */
    private static RoutineResDTO.Template toTemplate(RoutineTemplate template, boolean alreadyAdded) {
        return RoutineResDTO.Template.builder()
                .templateId(template.getId())
                .categoryId(template.getCategory().getId())
                .categoryName(template.getCategory().getName())
                .name(template.getName())
                .alreadyAdded(alreadyAdded)
                .build();
    }

    /** 저장된 개인 루틴 한 건을 응답으로 변환하고 반복 요일을 월요일부터의 순서로 정렬한다. */
    public static RoutineResDTO.Routine toRoutine(MemberRoutine routine) {
        return toRoutine(routine, false);
    }

    /**
     * 완료 여부까지 담아 변환한다.
     *
     * 완료 집합을 인자로 받는 이유는 루틴마다 따로 묻지 않기 위해서다. 목록 크기만큼
     * 쿼리가 나가면 홈 화면 한 번에 수십 건이 된다.
     */
    private static RoutineResDTO.Routine toRoutine(MemberRoutine routine, boolean completedToday) {
        List<DayOfWeek> repeatDays = routine.getSchedules().stream()
                .map(MemberRoutineSchedule::getRepeatDay)
                .sorted(Comparator.comparingInt(DayOfWeek::getValue))
                .toList();

        return RoutineResDTO.Routine.builder()
                .routineId(routine.getId())
                .categoryId(routine.getCategory().getId())
                .categoryName(routine.getCategory().getName())
                .templateId(routine.isFromTemplate() ? routine.getTemplate().getId() : null)
                .name(routine.getName())
                .endTime(routine.getEndTime())
                .repeatDays(repeatDays)
                .alarmTime(routine.getAlarmTime())
                .completedToday(completedToday)
                .build();
    }

    /** 요청의 반복 요일을 돌려주고, 지정하지 않았으면 기본값인 매일을 돌려준다. */
    private static Collection<DayOfWeek> repeatDaysOf(RoutineReqDTO.CreateRoutine request) {
        return request.repeatDays() == null || request.repeatDays().isEmpty()
                ? EVERY_DAY
                : request.repeatDays();
    }

    /**
     * 개인 루틴 목록을 응답으로 변환한다.
     * 홈 화면의 '오늘의 루틴' 탭처럼 생성 결과가 아닌 단순 조회 목록을 응답으로 할 때 사용한다.
     */
    public static List<RoutineResDTO.Routine> toRoutineList(List<MemberRoutine> routines) {
        return toRoutineList(routines, Set.of());
    }

    /**
     * 완료 여부까지 담아 목록을 변환한다.
     *
     * @param completedRoutineIds 오늘 인증이 있는 루틴 id. 한 번에 조회해 넘긴다
     */
    public static List<RoutineResDTO.Routine> toRoutineList(
            List<MemberRoutine> routines,
            Set<Long> completedRoutineIds
    ) {
        return routines.stream()
                .map(routine -> toRoutine(routine, completedRoutineIds.contains(routine.getId())))
                .toList();
    }
}
