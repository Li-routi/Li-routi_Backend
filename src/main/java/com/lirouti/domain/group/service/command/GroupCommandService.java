package com.lirouti.domain.group.service.command;

import com.lirouti.domain.group.converter.GroupConverter;
import com.lirouti.domain.group.dto.request.GroupReqDTO;
import com.lirouti.domain.group.dto.response.GroupResDTO;
import com.lirouti.domain.group.entity.Group;
import com.lirouti.domain.group.entity.GroupMember;
import com.lirouti.domain.group.entity.GroupRoutine;
import com.lirouti.domain.group.entity.GroupRoutineAssignment;
import com.lirouti.domain.group.entity.RoutineCategory;
import com.lirouti.domain.group.enums.GroupMemberStatus;
import com.lirouti.domain.group.exception.GroupException;
import com.lirouti.domain.group.exception.code.error.GroupErrorCode;
import com.lirouti.domain.group.repository.GroupMemberRepository;
import com.lirouti.domain.group.repository.GroupRoutineAssignmentRepository;
import com.lirouti.domain.group.repository.GroupRoutineRepository;
import com.lirouti.domain.group.repository.RoutineCategoryRepository;
import com.lirouti.domain.group.service.GroupValidationService;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GroupCommandService {
    private final GroupValidationService groupValidationService;
    private final RoutineCategoryRepository routineCategoryRepository;
    private final GroupRoutineRepository groupRoutineRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final GroupRoutineAssignmentRepository groupRoutineAssignmentRepository;

    @Transactional
    public GroupResDTO.RoutineCreateResult createRoutine(
            Long groupId,
            Long memberId,
            GroupReqDTO.CreateRoutine request
    ) {
        validateRequest(request);

        GroupMember ownerMembership = groupValidationService.validateGroupOwner(groupId, memberId);
        Group group = ownerMembership.getGroup();

        RoutineCategory category = routineCategoryRepository
                .findByIdAndActiveTrue(request.categoryId())
                .orElseThrow(() -> new GroupException(GroupErrorCode.ROUTINE_CATEGORY_NOT_FOUND));

        validateRoutineTitleNotDuplicated(groupId, request.title());

        List<GroupMember> activeMembers = groupMemberRepository
                .findAllByGroupIdAndStatus(groupId, GroupMemberStatus.ACTIVE);

        GroupRoutine groupRoutine = GroupConverter.toGroupRoutine(request, group, category);
        saveGroupRoutine(groupRoutine);

        List<GroupRoutineAssignment> assignments = activeMembers.stream()
                .map(groupMember -> GroupRoutineAssignment.builder()
                        .groupRoutine(groupRoutine)
                        .member(groupMember.getMember())
                        .build())
                .toList();
        groupRoutineAssignmentRepository.saveAllAndFlush(assignments);

        return GroupConverter.toRoutineCreateResult(groupRoutine, assignments.size());
    }

    private void validateRequest(GroupReqDTO.CreateRoutine request) {
        if (request == null
                || request.categoryId() == null
                || request.title() == null
                || request.title().isBlank()
                || request.title().length() > 20
                || request.description() == null
                || request.description().isBlank()
                || request.description().length() > 255
                || request.schedules() == null
                || request.schedules().isEmpty()
                || request.schedules().size() > 7) {
            throw new IllegalArgumentException("유효하지 않은 그룹 루틴 생성 요청입니다.");
        }

        Set<java.time.DayOfWeek> repeatDays = new HashSet<>();
        boolean invalidSchedule = request.schedules().stream().anyMatch(schedule ->
                schedule == null
                        || schedule.repeatDay() == null
                        || schedule.startTime() == null
                        || schedule.endTime() == null
                        || !schedule.startTime().isBefore(schedule.endTime())
                        || !repeatDays.add(schedule.repeatDay())
        );
        if (invalidSchedule) {
            throw new IllegalArgumentException("유효하지 않은 그룹 루틴 일정입니다.");
        }
    }

    private void validateRoutineTitleNotDuplicated(Long groupId, String title) {
        if (groupRoutineRepository.existsByGroupIdAndTitle(groupId, title)) {
            throw new GroupException(GroupErrorCode.DUPLICATE_GROUP_ROUTINE_TITLE);
        }
    }

    private void saveGroupRoutine(GroupRoutine groupRoutine) {
        try {
            groupRoutineRepository.saveAndFlush(groupRoutine);
        } catch (DataIntegrityViolationException e) {
            throw new GroupException(GroupErrorCode.DUPLICATE_GROUP_ROUTINE_TITLE);
        }
    }
}
