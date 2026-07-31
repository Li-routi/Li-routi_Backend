package com.lirouti.domain.group.converter;

import com.lirouti.domain.group.dto.request.GroupReqDTO;
import com.lirouti.domain.group.dto.response.GroupResDTO;
import com.lirouti.domain.group.entity.Group;
import com.lirouti.domain.group.entity.GroupRoutine;
import com.lirouti.domain.group.entity.GroupRoutineCategory;
import com.lirouti.domain.group.entity.GroupRoutineSchedule;
import com.lirouti.domain.group.entity.GroupMember;
import com.lirouti.domain.group.enums.GroupMemberRole;
import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.group.repository.GroupRoutineAssignmentRepositoryCustom.TodayAssignmentProjection;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

public final class GroupConverter {
    private GroupConverter() {
    }

    /** 통합 생성 요청과 생성된 초대코드 정보를 신규 ACTIVE 그룹으로 변환한다. */
    public static Group toGroup(
            GroupReqDTO.CreateGroup request,
            String inviteCode,
            LocalDateTime inviteCodeExpiresAt
    ) {
        return Group.builder()
                .name(request.name())
                .inviteCode(inviteCode)
                .inviteCodeExpiresAt(inviteCodeExpiresAt)
                .build();
    }

    /** 신규 그룹의 인증 회원 참여 관계를 ACTIVE OWNER로 생성한다. */
    public static GroupMember toOwnerMembership(Member member, Group group) {
        return GroupMember.builder()
                .member(member)
                .group(group)
                .role(GroupMemberRole.OWNER)
                .build();
    }

    /** 요청의 사용자 카테고리를 그룹 소유의 활성 카테고리로 변환한다. */
    public static GroupRoutineCategory toGroupRoutineCategory(
            GroupReqDTO.CreateGroupCategory request,
            Group group
    ) {
        return GroupRoutineCategory.builder()
                .group(group)
                .name(request.name())
                .color(request.color())
                .displayOrder(0)
                .active(true)
                .build();
    }

    /** 통합 생성 요청의 초기 루틴을 일정이 연결된 그룹 루틴으로 변환한다. */
    public static GroupRoutine toGroupRoutine(
            GroupReqDTO.CreateGroupRoutine request,
            Group group,
            GroupRoutineCategory category
    ) {
        GroupRoutine groupRoutine = GroupRoutine.builder()
                .group(group)
                .category(category)
                .title(request.title())
                .description(request.description())
                .build();

        request.schedules().forEach(schedule -> groupRoutine.addSchedule(
                schedule.repeatDay(),
                schedule.startTime(),
                schedule.endTime()
        ));
        return groupRoutine;
    }

    /** 저장된 그룹·사용자 카테고리·초기 루틴 및 할당 수를 통합 생성 응답으로 조립한다. */
    public static GroupResDTO.CreateResult toCreateResult(
            Group group,
            List<CreatedCategory> createdCategories,
            List<CreatedRoutine> createdRoutines
    ) {
        List<GroupResDTO.CreatedCategory> categories = createdCategories.stream()
                .map(created -> GroupResDTO.CreatedCategory.builder()
                        .clientKey(created.clientKey())
                        .categoryId(created.category().getId())
                        .name(created.category().getName())
                        .color(created.category().getColor())
                        .build())
                .toList();
        List<GroupResDTO.CreatedRoutine> routines = createdRoutines.stream()
                .map(created -> GroupResDTO.CreatedRoutine.builder()
                        .routineId(created.routine().getId())
                        .categoryId(created.routine().getCategory().getId())
                        .categoryName(created.routine().getCategory().getName())
                        .title(created.routine().getTitle())
                        .description(created.routine().getDescription())
                        .schedules(toRoutineSchedules(created.routine()))
                        .assignmentCount(created.assignmentCount())
                        .build())
                .toList();

        return GroupResDTO.CreateResult.builder()
                .groupId(group.getId())
                .name(group.getName())
                .customCategories(categories)
                .routines(routines)
                .assignmentCount(createdRoutines.stream()
                        .mapToInt(CreatedRoutine::assignmentCount)
                        .sum())
                .build();
    }

    /** clientKey와 실제 저장된 사용자 카테고리를 연결하는 변환 입력이다. */
    public record CreatedCategory(String clientKey, GroupRoutineCategory category) {
    }

    /** 실제 저장된 초기 루틴과 생성 당일 할당 건수를 연결하는 변환 입력이다. */
    public record CreatedRoutine(GroupRoutine routine, int assignmentCount) {
    }

    /**
     * 생성 요청과 검증된 그룹·카테고리를 일정이 연결된 그룹 루틴으로 변환한다.
     *
     * @param request 그룹 루틴 생성 요청
     * @param group 루틴이 속할 그룹
     * @param category 앱 또는 소속 그룹이 관리하는 활성 그룹 카테고리
     * @return 요일별 일정이 연결된 그룹 루틴
     */
    public static GroupRoutine toGroupRoutine(
            GroupReqDTO.CreateRoutine request,
            Group group,
            GroupRoutineCategory category
    ) {
        GroupRoutine groupRoutine = GroupRoutine.builder()
                .group(group)
                .category(category)
                .title(request.title())
                .description(request.description())
                .build();

        request.schedules().forEach(schedule -> groupRoutine.addSchedule(
                schedule.repeatDay(),
                schedule.startTime(),
                schedule.endTime()
        ));
        return groupRoutine;
    }

    /**
     * 저장된 그룹 루틴을 생성 응답으로 변환하고 일정을 요일 순서로 정렬한다.
     *
     * @param groupRoutine 저장된 그룹 루틴
     * @param assignmentCount 생성 당일 할당 대상 수
     * @return 그룹 루틴 생성 응답
     */
    public static GroupResDTO.RoutineCreateResult toRoutineCreateResult(
            GroupRoutine groupRoutine,
            int assignmentCount
    ) {
        return GroupResDTO.RoutineCreateResult.builder()
                .routineId(groupRoutine.getId())
                .groupId(groupRoutine.getGroup().getId())
                .categoryId(groupRoutine.getCategory().getId())
                .categoryName(groupRoutine.getCategory().getName())
                .title(groupRoutine.getTitle())
                .description(groupRoutine.getDescription())
                .schedules(toRoutineSchedules(groupRoutine))
                .assignmentCount(assignmentCount)
                .build();
    }

    /** 저장된 그룹 루틴을 수정 결과 응답으로 변환한다. */
    public static GroupResDTO.RoutineUpdateResult toRoutineUpdateResult(
            GroupRoutine groupRoutine,
            int assignmentCount
    ) {
        return GroupResDTO.RoutineUpdateResult.builder()
                .routineId(groupRoutine.getId())
                .groupId(groupRoutine.getGroup().getId())
                .categoryId(groupRoutine.getCategory().getId())
                .categoryName(groupRoutine.getCategory().getName())
                .title(groupRoutine.getTitle())
                .description(groupRoutine.getDescription())
                .schedules(toRoutineSchedules(groupRoutine))
                .assignmentCount(assignmentCount)
                .build();
    }

    private static List<GroupResDTO.RoutineSchedule> toRoutineSchedules(
            GroupRoutine groupRoutine
    ) {
        return groupRoutine.getSchedules().stream()
                .sorted(Comparator.comparingInt(schedule -> schedule.getRepeatDay().getValue()))
                .map(GroupConverter::toRoutineSchedule)
                .toList();
    }

    /**
     * 그룹 루틴 일정을 응답용 일정으로 변환한다.
     *
     * @param schedule 그룹 루틴 일정
     * @return 요일과 시간 범위를 담은 응답 일정
     */
    private static GroupResDTO.RoutineSchedule toRoutineSchedule(GroupRoutineSchedule schedule) {
        return GroupResDTO.RoutineSchedule.builder()
                .repeatDay(schedule.getRepeatDay())
                .startTime(schedule.getStartTime())
                .endTime(schedule.getEndTime())
                .build();
    }

    /**
     * 오늘의 그룹 루틴 조회 Projection 목록을 API 응답으로 변환한다.
     *
     * @param projections Repository가 조회한 오늘의 할당 목록
     * @return 오늘의 그룹 루틴 목록 응답
     */
    public static GroupResDTO.TodayRoutineList toTodayRoutineList(
            List<TodayAssignmentProjection> projections
    ) {
        List<GroupResDTO.TodayRoutine> routines = projections.stream()
                .map(GroupConverter::toTodayRoutine)
                .toList();

        return GroupResDTO.TodayRoutineList.builder()
                .routines(routines)
                .build();
    }

    // 저장된 그룹 초대코드를 설정 화면 응답으로 변환
    public static GroupResDTO.InviteCode toInviteCodeResult(Group group) {
        return GroupResDTO.InviteCode.builder()
                .inviteCode(group.getInviteCode())
                .expiresAt(group.getInviteCodeExpiresAt())
                .build();
    }

    /** Repository 조회 Projection 한 건을 오늘의 그룹 루틴 응답으로 변환한다. */
    private static GroupResDTO.TodayRoutine toTodayRoutine(TodayAssignmentProjection projection) {
        return GroupResDTO.TodayRoutine.builder()
                .assignmentId(projection.assignmentId())
                .routineId(projection.routineId())
                .groupId(projection.groupId())
                .groupName(projection.groupName())
                .categoryId(projection.categoryId())
                .categoryName(projection.categoryName())
                .title(projection.title())
                .description(projection.description())
                .assignedDate(projection.assignedDate())
                .scheduledStartTime(projection.scheduledStartTime())
                .scheduledEndTime(projection.scheduledEndTime())
                .status(projection.status())
                .build();
    }
}
