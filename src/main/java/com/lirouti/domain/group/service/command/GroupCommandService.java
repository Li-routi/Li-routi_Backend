package com.lirouti.domain.group.service.command;

import com.lirouti.domain.group.converter.GroupConverter;
import com.lirouti.domain.group.dto.request.GroupReqDTO;
import com.lirouti.domain.group.dto.response.GroupResDTO;
import com.lirouti.domain.group.entity.Group;
import com.lirouti.domain.group.entity.GroupMember;
import com.lirouti.domain.group.entity.GroupRoutine;
import com.lirouti.domain.group.entity.RoutineCategory;
import com.lirouti.domain.group.exception.GroupException;
import com.lirouti.domain.group.exception.code.error.GroupErrorCode;
import com.lirouti.domain.group.repository.GroupRoutineRepository;
import com.lirouti.domain.group.repository.RoutineCategoryRepository;
import com.lirouti.domain.group.service.GroupValidationService;
import java.util.HashSet;
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
    private final GroupRoutineAssignmentCommandService assignmentCommandService;

    /**
     * ACTIVE OWNER 권한과 카테고리·제목을 검증한 뒤 루틴, 일정, 당일 할당을 생성한다.
     * 전체 과정은 하나의 트랜잭션으로 처리되어 할당 실패 시 루틴과 일정도 롤백된다.
     *
     * @param groupId 루틴을 생성할 그룹 ID
     * @param memberId 생성을 요청한 회원 ID
     * @param request 그룹 루틴 생성 요청
     * @return 생성된 루틴과 당일 할당 대상 수
     */
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

        GroupRoutine groupRoutine = GroupConverter.toGroupRoutine(request, group, category);
        saveGroupRoutine(groupRoutine);

        int assignmentCount = assignmentCommandService
                .assignRoutineToActiveMembersToday(groupRoutine);

        return GroupConverter.toRoutineCreateResult(groupRoutine, assignmentCount);
    }

    /**
     * Controller 외의 호출 경로에서도 생성 요청의 필수값과 일정 규칙을 방어적으로 검증한다.
     *
     * @param request 검증할 그룹 루틴 생성 요청
     * @throws IllegalArgumentException 필수값, 길이, 요일 또는 시간 범위가 유효하지 않은 경우
     */
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

    /**
     * 애플리케이션 레벨에서 동일 그룹 내 제목 중복을 사전 검사한다.
     *
     * @param groupId 대상 그룹 ID
     * @param title 생성할 루틴 제목
     * @throws GroupException 동일 제목이 이미 존재하는 경우
     */
    private void validateRoutineTitleNotDuplicated(Long groupId, String title) {
        if (groupRoutineRepository.existsByGroupIdAndTitle(groupId, title)) {
            throw new GroupException(GroupErrorCode.DUPLICATE_GROUP_ROUTINE_TITLE);
        }
    }

    /**
     * 루틴과 cascade로 연결된 일정을 즉시 반영하고 저장 중 무결성 오류를 도메인 예외로 변환한다.
     *
     * @param groupRoutine 저장할 그룹 루틴
     * @throws GroupException DB 저장 과정에서 무결성 오류가 발생한 경우
     */
    private void saveGroupRoutine(GroupRoutine groupRoutine) {
        try {
            groupRoutineRepository.saveAndFlush(groupRoutine);
        } catch (DataIntegrityViolationException e) {
            throw new GroupException(GroupErrorCode.DUPLICATE_GROUP_ROUTINE_TITLE);
        }
    }
}
