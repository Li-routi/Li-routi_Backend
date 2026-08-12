package com.lirouti.domain.group.service.query;

import com.lirouti.domain.group.converter.GroupConverter;
import com.lirouti.domain.group.dto.response.GroupResDTO;
import com.lirouti.domain.group.entity.GroupRoutineCategory;
import com.lirouti.domain.group.repository.GroupRoutineCategoryRepository;
import com.lirouti.domain.group.service.GroupValidationService;
import com.lirouti.domain.group.repository.GroupRoutineAssignmentRepository;
import com.lirouti.domain.group.repository.GroupRoutineAssignmentRepositoryCustom.TodayAssignmentProjection;
import com.lirouti.domain.group.repository.GroupDetailQueryRepository;
import com.lirouti.domain.group.repository.GroupListQueryRepository;
import com.lirouti.domain.group.repository.GroupListQueryRepository.AssignmentCountProjection;
import com.lirouti.domain.group.repository.GroupListQueryRepository.GroupCountProjection;
import com.lirouti.domain.group.repository.GroupListQueryRepository.GroupScheduleCountProjection;
import com.lirouti.domain.group.repository.GroupListQueryRepository.MyGroupProjection;
import com.lirouti.domain.group.repository.GroupRoutineQueryRepository;
import com.lirouti.domain.group.repository.GroupRoutineQueryRepository.GroupRoutineProjection;
import com.lirouti.domain.group.repository.GroupRoutineQueryRepository.RoutineScheduleProjection;
import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.service.query.MemberQueryService;
import com.lirouti.domain.shop.converter.ShopConverter;
import com.lirouti.domain.shop.dto.response.ShopResDTO;
import com.lirouti.domain.shop.repository.MemberAvatarEquipmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GroupQueryService {
    private final GroupRoutineAssignmentRepository groupRoutineAssignmentRepository;
    private final GroupDetailQueryRepository groupDetailQueryRepository;
    private final GroupListQueryRepository groupListQueryRepository;
    private final GroupRoutineQueryRepository groupRoutineQueryRepository;
    private final GroupRoutineCategoryRepository groupRoutineCategoryRepository;
    private final MemberAvatarEquipmentRepository memberAvatarEquipmentRepository;
    private final GroupValidationService groupValidationService;
    private final MemberQueryService memberQueryService;
    private final Clock clock;

    /** 로그인 회원의 ACTIVE 참여 그룹과 오늘·월간 활동 요약을 배치 조회한다. */
    @Transactional(readOnly = true)
    public GroupResDTO.MyGroupList getMyGroups(Long memberId) {
        Member member = memberQueryService.getActiveMember(memberId);
        LocalDate today = LocalDate.now(clock);
        List<MyGroupProjection> groups = groupListQueryRepository
                .findActiveGroupsByMemberId(member.getId());

        if (groups.isEmpty()) {
            return GroupConverter.toMyGroupList(
                    List.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
        }

        List<Long> groupIds = groups.stream().map(MyGroupProjection::groupId).toList();
        Map<Long, Long> activeMemberCounts = toCountMap(
                groupListQueryRepository.countActiveMembersByGroupIds(groupIds));
        Map<Long, Long> activeRoutineCounts = toCountMap(
                groupListQueryRepository.countActiveRoutinesByGroupIds(groupIds));
        List<AssignmentCountProjection> todayAssignments = groupListQueryRepository
                .findTodayAssignmentCounts(member.getId(), groupIds, today);
        Map<Long, Long> todayAssignedCounts = toAssignedCountMap(todayAssignments);
        Map<Long, Long> todayCompletedCounts = toCompletedCountMap(todayAssignments);
        Map<Long, Long> todayVerificationCounts = toCountMap(
                groupListQueryRepository.countTodayVerificationsByGroupIds(groupIds, today));

        YearMonth currentMonth = YearMonth.from(today);
        List<AssignmentCountProjection> monthlyAssignments = groupListQueryRepository
                .findMonthlyAssignmentCounts(member.getId(), groupIds, currentMonth.atDay(1), today);
        Map<Long, Integer> monthlyAchievementRates = calculateMonthlyAchievementRates(
                groupIds,
                monthlyAssignments,
                groupListQueryRepository.countActiveSchedulesByGroupIds(groupIds),
                today,
                currentMonth.atEndOfMonth()
        );

        log.debug("참여 그룹 목록을 조회했습니다. memberId={}, groupCount={}, assignedDate={}",
                member.getId(), groups.size(), today);
        return GroupConverter.toMyGroupList(
                groups,
                activeMemberCounts,
                activeRoutineCounts,
                todayAssignedCounts,
                todayCompletedCounts,
                monthlyAchievementRates,
                todayVerificationCounts
        );
    }

    /** ACTIVE 그룹 구성원에게 기본 카테고리와 해당 그룹의 활성 사용자 카테고리를 반환한다. */
    @Transactional(readOnly = true)
    public GroupResDTO.CategoryList getCategories(Long groupId, Long memberId) {
        groupValidationService.validateActiveGroupMember(groupId, memberId);

        List<GroupRoutineCategory> categories = groupRoutineCategoryRepository
                .findUsableByGroupId(groupId);
        long customCategoryCount = groupRoutineCategoryRepository
                .countByGroupIdAndActiveTrue(groupId);
        int addableCount = (int) Math.max(
                0,
                GroupRoutineCategory.MAX_GROUP_CATEGORY_COUNT - customCategoryCount
        );

        log.debug("그룹 루틴 카테고리 목록을 조회했습니다. groupId={}, memberId={}, "
                        + "categoryCount={}, addableCount={}",
                groupId, memberId, categories.size(), addableCount);
        return GroupConverter.toCategoryList(categories, addableCount);
    }

    /** ACTIVE 구성원이 그룹방 진입에 필요한 기본 정보와 구성원별 활동 현황을 조회한다. */
    @Transactional(readOnly = true)
    public GroupResDTO.Detail getGroupDetail(Long groupId, Long memberId) {
        groupValidationService.validateActiveGroupMember(groupId, memberId);

        LocalDate today = LocalDate.now(clock);
        List<GroupDetailQueryRepository.GroupMemberDetailProjection> memberDetails =
                groupDetailQueryRepository.findActiveMemberDetails(groupId);
        List<GroupDetailQueryRepository.TodayMemberProgressProjection> progresses =
                groupDetailQueryRepository.findTodayMemberProgress(groupId, today);
        List<Long> activeMemberIds = memberDetails.stream()
                .map(GroupDetailQueryRepository.GroupMemberDetailProjection::memberId)
                .toList();
        Map<Long, ShopResDTO.Avatar> avatarsByMemberId = ShopConverter.toAvatarsByMemberId(
                activeMemberIds,
                activeMemberIds.isEmpty()
                        ? List.of()
                        : memberAvatarEquipmentRepository
                                .findAllByMemberIdInWithMemberAndAvatarItem(activeMemberIds)
        );

        log.debug("그룹 상세 정보를 조회했습니다. groupId={}, memberId={}, memberCount={}",
                groupId, memberId, memberDetails.size());
        return GroupConverter.toGroupDetail(memberDetails, progresses, avatarsByMemberId);
    }

    /** ACTIVE OWNER가 관리 중인 ACTIVE 그룹의 활성 루틴과 반복 일정을 조회한다. */
    @Transactional(readOnly = true)
    public GroupResDTO.GroupRoutineList getGroupRoutines(Long groupId, Long memberId) {
        groupValidationService.validateGroupOwner(groupId, memberId);

        List<GroupRoutineProjection> routines = groupRoutineQueryRepository
                .findActiveRoutinesByGroupId(groupId);
        if (routines.isEmpty()) {
            return GroupConverter.toGroupRoutineList(List.of(), List.of());
        }

        List<Long> routineIds = routines.stream().map(GroupRoutineProjection::routineId).toList();
        List<RoutineScheduleProjection> schedules = groupRoutineQueryRepository
                .findSchedulesByRoutineIds(routineIds);

        log.debug("그룹 활성 루틴 목록을 조회했습니다. groupId={}, memberId={}, routineCount={}",
                groupId, memberId, routines.size());
        return GroupConverter.toGroupRoutineList(routines, schedules);
    }

    /**
     * 로그인 회원의 오늘 그룹 루틴 할당을 조회한다.
     * 존재하지 않거나 비활성 상태인 회원은 {@link MemberQueryService}의 도메인 예외를 그대로 전파하고,
     * 활성 그룹 구성원 조건을 만족하는 할당이 없으면 빈 목록을 반환한다.
     *
     * @param memberId 인증 객체에서 추출한 회원 ID
     * @return 오늘의 그룹 루틴 할당 목록
     */
    @Transactional(readOnly = true)
    public GroupResDTO.TodayRoutineList getTodayRoutines(Long memberId) {
        Member member = memberQueryService.getActiveMember(memberId);
        LocalDate today = LocalDate.now(clock);

        List<TodayAssignmentProjection> assignments = groupRoutineAssignmentRepository
                .findTodayAssignmentsByMemberId(member.getId(), today);

        log.debug("오늘의 그룹 루틴 조회를 완료했습니다. memberId={}, assignedDate={}, assignmentCount={}",
                member.getId(), today, assignments.size());

        return GroupConverter.toTodayRoutineList(assignments);
    }

    private Map<Long, Integer> calculateMonthlyAchievementRates(
            List<Long> groupIds,
            List<AssignmentCountProjection> monthlyAssignments,
            List<GroupScheduleCountProjection> schedules,
            LocalDate today,
            LocalDate monthEnd
    ) {
        Map<Long, AssignmentCountProjection> monthlyByGroupId = monthlyAssignments.stream()
                .collect(Collectors.toMap(AssignmentCountProjection::groupId, Function.identity()));
        Map<Long, Long> futureScheduledCounts = calculateFutureScheduledCounts(schedules, today, monthEnd);

        return groupIds.stream().collect(Collectors.toMap(
                Function.identity(),
                groupId -> {
                    AssignmentCountProjection monthly = monthlyByGroupId.get(groupId);
                    long completed = monthly == null ? 0L : monthly.completedCount();
                    long actualAssigned = monthly == null ? 0L : monthly.assignedCount();
                    long denominator = actualAssigned + futureScheduledCounts.getOrDefault(groupId, 0L);
                    return denominator == 0L
                            ? 0
                            : (int) Math.round(completed * 100.0 / denominator);
                }
        ));
    }

    private Map<Long, Long> calculateFutureScheduledCounts(
            List<GroupScheduleCountProjection> schedules,
            LocalDate today,
            LocalDate monthEnd
    ) {
        if (today.equals(monthEnd)) {
            return Map.of();
        }

        Map<Long, Map<DayOfWeek, Long>> schedulesByGroupAndDay = schedules.stream()
                .collect(Collectors.groupingBy(
                        GroupScheduleCountProjection::groupId,
                        Collectors.toMap(
                                GroupScheduleCountProjection::repeatDay,
                                GroupScheduleCountProjection::count
                        )
                ));
        Map<Long, Long> futureCounts = new HashMap<>();
        for (LocalDate date = today.plusDays(1); !date.isAfter(monthEnd); date = date.plusDays(1)) {
            for (Map.Entry<Long, Map<DayOfWeek, Long>> entry : schedulesByGroupAndDay.entrySet()) {
                long scheduledCount = entry.getValue().getOrDefault(date.getDayOfWeek(), 0L);
                futureCounts.merge(entry.getKey(), scheduledCount, Long::sum);
            }
        }
        return futureCounts;
    }

    private Map<Long, Long> toCountMap(List<GroupCountProjection> counts) {
        return counts.stream().collect(Collectors.toMap(
                GroupCountProjection::groupId,
                GroupCountProjection::count
        ));
    }

    private Map<Long, Long> toAssignedCountMap(List<AssignmentCountProjection> counts) {
        return counts.stream().collect(Collectors.toMap(
                AssignmentCountProjection::groupId,
                AssignmentCountProjection::assignedCount
        ));
    }

    private Map<Long, Long> toCompletedCountMap(List<AssignmentCountProjection> counts) {
        return counts.stream().collect(Collectors.toMap(
                AssignmentCountProjection::groupId,
                AssignmentCountProjection::completedCount
        ));
    }
}
