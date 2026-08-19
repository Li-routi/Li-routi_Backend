package com.lirouti.domain.group.converter;

import com.lirouti.domain.group.dto.request.GroupReqDTO;
import com.lirouti.domain.character.dto.response.CharacterResDTO;
import com.lirouti.domain.group.dto.response.GroupResDTO;
import com.lirouti.domain.group.entity.Group;
import com.lirouti.domain.group.entity.GroupRoutine;
import com.lirouti.domain.group.entity.GroupRoutineCategory;
import com.lirouti.domain.group.entity.GroupRoutineSchedule;
import com.lirouti.domain.group.entity.GroupMember;
import com.lirouti.domain.group.enums.GroupJoinUnavailableReason;
import com.lirouti.domain.group.enums.GroupMemberRole;
import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.group.repository.GroupRoutineAssignmentRepositoryCustom.TodayAssignmentProjection;
import com.lirouti.domain.group.repository.GroupDetailQueryRepository.GroupMemberDetailProjection;
import com.lirouti.domain.group.repository.GroupDetailQueryRepository.TodayMemberProgressProjection;
import com.lirouti.domain.group.repository.GroupListQueryRepository.MyGroupProjection;
import com.lirouti.domain.group.repository.GroupRoutineQueryRepository.GroupRoutineProjection;
import com.lirouti.domain.group.repository.GroupRoutineQueryRepository.RoutineScheduleProjection;
import com.lirouti.domain.routine.enums.RoutineCategoryColor;
import com.lirouti.domain.shop.entity.MemberAvatarEquipment;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

public final class GroupConverter {
    private GroupConverter() {
    }

    /** 목록 기본 projection과 그룹별 배치 집계 결과를 참여 그룹 응답으로 조립한다. */
    public static GroupResDTO.MyGroupList toMyGroupList(
            List<MyGroupProjection> groups,
            Map<Long, Long> activeMemberCounts,
            Map<Long, Long> activeRoutineCounts,
            Map<Long, Long> todayAssignedCounts,
            Map<Long, Long> todayCompletedCounts,
            Map<Long, Integer> monthlyAchievementRates,
            Map<Long, Long> todayVerificationCounts,
            Map<Long, List<String>> profileImageKeysByGroupId
    ) {
        return new GroupResDTO.MyGroupList(groups.stream()
                .map(group -> new GroupResDTO.MyGroup(
                        group.groupId(),
                        group.groupName(),
                        activeMemberCounts.getOrDefault(group.groupId(), 0L),
                        activeRoutineCounts.getOrDefault(group.groupId(), 0L),
                        todayAssignedCounts.getOrDefault(group.groupId(), 0L),
                        todayCompletedCounts.getOrDefault(group.groupId(), 0L),
                        group.currentStreak(),
                        monthlyAchievementRates.getOrDefault(group.groupId(), 0),
                        todayVerificationCounts.getOrDefault(group.groupId(), 0L),
                        group.lastVerificationAt(),
                        profileImageKeysByGroupId.getOrDefault(group.groupId(), List.of())
                ))
                .toList());
    }

    /** 통합 생성 요청과 생성된 영구 초대코드로 신규 ACTIVE 그룹을 변환한다. */
    public static Group toGroup(
            GroupReqDTO.CreateGroup request,
            String inviteCode
    ) {
        return Group.builder()
                .name(request.name())
                .inviteCode(inviteCode)
                .build();
    }

    /** 신규 그룹의 인증 회원 참여 관계를 ACTIVE OWNER로 생성한다. */
    public static GroupMember toOwnerMembership(Member member, Group group) {
        return GroupMember.createActive(member, group, GroupMemberRole.OWNER, null);
    }

    /** 요청의 사용자 카테고리를 그룹 소유의 활성 카테고리로 변환한다. */
    public static GroupRoutineCategory toGroupRoutineCategory(
            GroupReqDTO.CreateGroupCategory request,
            Group group
    ) {
        return newGroupRoutineCategory(group, request.name(), request.color());
    }

    /** 그룹 카테고리 추가 요청을 그룹 소유의 활성 카테고리로 변환한다. */
    public static GroupRoutineCategory toGroupRoutineCategory(
            GroupReqDTO.CreateCategory request,
            Group group
    ) {
        return newGroupRoutineCategory(group, request.name(), request.color());
    }

    private static GroupRoutineCategory newGroupRoutineCategory(
            Group group,
            String name,
            RoutineCategoryColor color
    ) {
        return GroupRoutineCategory.builder()
                .group(group)
                .name(name)
                .color(color)
                .displayOrder(0)
                .active(true)
                .build();
    }

    /** 조회된 카테고리와 남은 추가 가능 개수를 목록 응답으로 변환한다. */
    public static GroupResDTO.CategoryList toCategoryList(
            List<GroupRoutineCategory> categories,
            int addableCount
    ) {
        return GroupResDTO.CategoryList.builder()
                .categories(categories.stream().map(GroupConverter::toCategory).toList())
                .addableCount(addableCount)
                .build();
    }

    /** 그룹 루틴 카테고리 한 건을 응답으로 변환한다. */
    public static GroupResDTO.Category toCategory(GroupRoutineCategory category) {
        return GroupResDTO.Category.builder()
                .categoryId(category.getId())
                .name(category.getName())
                .color(category.getColor())
                .fixed(category.isFixed())
                .build();
    }

    /** 그룹 상세 멤버 projection과 오늘 진행도 집계를 진입 화면 응답으로 조립한다. */
    public static GroupResDTO.Detail toGroupDetail(
            List<GroupMemberDetailProjection> memberDetails,
            List<TodayMemberProgressProjection> progresses,
            Map<Long, GroupResDTO.Avatar> avatarsByMemberId,
            Map<Long, GroupResDTO.RepresentativeAchievement> representativeAchievementsByMemberId,
            GroupMemberRole myRole
    ) {
        GroupMemberDetailProjection group = memberDetails.getFirst();
        Map<Long, TodayMemberProgressProjection> progressByMemberId = progresses.stream()
                .collect(Collectors.toMap(
                        TodayMemberProgressProjection::memberId,
                        Function.identity()
                ));

        return GroupResDTO.Detail.builder()
                .groupId(group.groupId())
                .groupName(group.groupName())
                .inviteCode(group.inviteCode())
                .myRole(myRole)
                .members(memberDetails.stream()
                        .map(member -> toMemberActivity(
                                member,
                                progressByMemberId.get(member.memberId()),
                                avatarsByMemberId.getOrDefault(
                                        member.memberId(), new GroupResDTO.Avatar(List.of(), List.of())),
                                representativeAchievementsByMemberId.get(member.memberId())))
                        .toList())
                .build();
    }

    private static GroupResDTO.MemberActivity toMemberActivity(
            GroupMemberDetailProjection member,
            TodayMemberProgressProjection progress,
            GroupResDTO.Avatar avatar,
            GroupResDTO.RepresentativeAchievement representativeAchievement
    ) {
        long completedCount = progress == null ? 0L : progress.completedCount();
        long totalCount = progress == null ? 0L : progress.totalCount();
        return GroupResDTO.MemberActivity.builder()
                .memberId(member.memberId())
                .name(member.name())
                .avatar(avatar)
                .statusMessage(member.statusMessage())
                .currentStreak(member.currentStreak())
                .totalLikeCount(member.totalLikeCount())
                .totalPokeCount(member.totalPokeCount())
                .totalDisappointmentCount(member.totalDisappointmentCount())
                .dailyProgress(new GroupResDTO.DailyProgress(completedCount, totalCount))
                .representativeAchievement(representativeAchievement)
                .build();
    }

    /** 장착하지 않은 회원도 빈 목록으로 포함해 그룹 조회용 아바타를 조립한다. */
    public static Map<Long, GroupResDTO.Avatar> toAvatarsByMemberId(
            List<Long> memberIds,
            List<MemberAvatarEquipment> equipments,
            Map<Long, List<CharacterResDTO.Layer>> layersByMemberId,
            UnaryOperator<String> toViewUrl
    ) {
        Map<Long, List<MemberAvatarEquipment>> equipmentsByMemberId = equipments.stream()
                .collect(Collectors.groupingBy(equipment -> equipment.getMember().getId()));

        return memberIds.stream().distinct().collect(Collectors.toMap(
                Function.identity(),
                memberId -> toAvatar(
                        equipmentsByMemberId.getOrDefault(memberId, List.of()),
                        layersByMemberId.getOrDefault(memberId, List.of()),
                        toViewUrl),
                (left, right) -> left,
                LinkedHashMap::new
        ));
    }

    private static GroupResDTO.Avatar toAvatar(List<MemberAvatarEquipment> equipments,
                                               List<CharacterResDTO.Layer> layers,
                                               UnaryOperator<String> toViewUrl) {
        return GroupResDTO.Avatar.builder()
                .layers(layers)
                .equipped(equipments.stream()
                        .map(equipment -> new GroupResDTO.Equipped(
                                equipment.getSlot(),
                                toViewUrl.apply(equipment.getAvatarItem().getImageKey())))
                        .toList())
                .build();
    }

    /** 수정된 로그인 회원의 그룹별 상태 메시지를 응답으로 변환한다. */
    public static GroupResDTO.StatusMessageUpdate toStatusMessageUpdate(GroupMember groupMember) {
        return new GroupResDTO.StatusMessageUpdate(
                groupMember.getGroup().getId(),
                groupMember.getStatusMessage()
        );
    }

    /** 그룹의 현재 신규 참여 잠금 상태를 응답으로 변환한다. */
    public static GroupResDTO.LockState toLockState(Group group) {
        return GroupResDTO.LockState.builder()
                .groupId(group.getId())
                .isLocked(group.isLocked())
                .build();
    }

    /** 통합 생성 요청의 초기 루틴을 일정이 연결된 그룹 루틴으로 변환한다. */
    public static GroupRoutine toGroupRoutine(
            GroupReqDTO.CreateGroupRoutine request,
            Group group,
            GroupRoutineCategory category
    ) {
        return newGroupRoutine(
                group,
                category,
                request.title(),
                request.description(),
                request.schedules()
        );
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
            GroupReqDTO.GroupRoutineCreateRequest request,
            Group group,
            GroupRoutineCategory category
    ) {
        return newGroupRoutine(
                group,
                category,
                request.title(),
                request.description(),
                request.schedules()
        );
    }

    private static GroupRoutine newGroupRoutine(
            Group group,
            GroupRoutineCategory category,
            String title,
            String description,
            List<GroupReqDTO.RoutineSchedule> schedules
    ) {
        GroupRoutine groupRoutine = GroupRoutine.builder()
                .group(group)
                .category(category)
                .title(title)
                .description(description)
                .build();

        schedules.forEach(schedule -> groupRoutine.addSchedule(
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
    public static GroupResDTO.GroupRoutineCreateResult toRoutineCreateResult(
            GroupRoutine groupRoutine,
            int assignmentCount
    ) {
        return GroupResDTO.GroupRoutineCreateResult.builder()
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

    /** 루틴과 일정의 분리 projection을 목록 응답으로 조립한다. */
    public static GroupResDTO.GroupRoutineList toGroupRoutineList(
            List<GroupRoutineProjection> routines,
            List<RoutineScheduleProjection> schedules
    ) {
        Map<Long, List<RoutineScheduleProjection>> schedulesByRoutineId = schedules.stream()
                .collect(Collectors.groupingBy(RoutineScheduleProjection::routineId));

        return GroupResDTO.GroupRoutineList.builder()
                .routines(routines.stream()
                        .map(routine -> GroupResDTO.GroupRoutineItem.builder()
                                .routineId(routine.routineId())
                                .categoryId(routine.categoryId())
                                .categoryName(routine.categoryName())
                                .title(routine.title())
                                .description(routine.description())
                                .schedules(toRoutineSchedules(
                                        schedulesByRoutineId.getOrDefault(routine.routineId(), List.of())))
                                .build())
                        .toList())
                .build();
    }

    /** DB 반환 순서와 무관하게 월요일부터 일요일까지 반복 일정을 정렬한다. */
    private static List<GroupResDTO.RoutineSchedule> toRoutineSchedules(
            List<RoutineScheduleProjection> schedules
    ) {
        return schedules.stream()
                .sorted(Comparator.comparingInt(schedule -> schedule.repeatDay().getValue()))
                .map(schedule -> GroupResDTO.RoutineSchedule.builder()
                        .repeatDay(schedule.repeatDay())
                        .startTime(schedule.startTime())
                        .endTime(schedule.endTime())
                        .build())
                .toList();
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

    // 저장된 그룹 영구 초대코드를 설정 화면 응답으로 변환
    public static GroupResDTO.InviteCode toInviteCodeResult(Group group) {
        return GroupResDTO.InviteCode.builder()
                .inviteCode(group.getInviteCode())
                .build();
    }

    /** 초대코드 Preview에 필요한 그룹 정보와 참여 가능 상태를 응답으로 변환한다. */
    public static GroupResDTO.JoinPreview toJoinPreview(
            Group group,
            long activeMemberCount,
            long totalRoutineCount,
            List<Long> activeMemberIds,
            Map<Long, GroupResDTO.Avatar> avatarsByMemberId,
            boolean joinable,
            GroupJoinUnavailableReason unavailableReason
    ) {
        return GroupResDTO.JoinPreview.builder()
                .groupId(group.getId())
                .name(group.getName())
                .activeMemberCount((int) activeMemberCount)
                .maxMemberCount(GroupMember.MAX_ACTIVE_MEMBER_COUNT_PER_GROUP)
                .totalRoutineCount((int) totalRoutineCount)
                .members(activeMemberIds.stream()
                        .map(memberId -> new GroupResDTO.JoinPreviewMember(
                                avatarsByMemberId.getOrDefault(memberId, new GroupResDTO.Avatar(List.of(), List.of()))))
                        .toList())
                .joinable(joinable)
                .unavailableReason(unavailableReason)
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
