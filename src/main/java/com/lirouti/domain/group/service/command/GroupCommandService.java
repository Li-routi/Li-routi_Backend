package com.lirouti.domain.group.service.command;

import com.lirouti.domain.group.converter.GroupConverter;
import com.lirouti.domain.group.dto.request.GroupReqDTO;
import com.lirouti.domain.group.dto.response.GroupResDTO;
import com.lirouti.domain.group.entity.Group;
import com.lirouti.domain.group.entity.GroupRoutine;
import com.lirouti.domain.group.entity.GroupRoutineCategory;
import com.lirouti.domain.group.exception.GroupException;
import com.lirouti.domain.group.exception.code.error.GroupErrorCode;
import com.lirouti.domain.group.repository.GroupRoutineCategoryRepository;
import com.lirouti.domain.group.repository.GroupRoutineRepository;
import com.lirouti.domain.group.service.GroupValidationService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class GroupCommandService {
    private static final int MAX_CREATE_ATTEMPTS = 10;

    private final GroupValidationService groupValidationService;
    private final GroupRoutineCategoryRepository groupRoutineCategoryRepository;
    private final GroupRoutineRepository groupRoutineRepository;
    private final GroupRoutineAssignmentCommandService assignmentCommandService;
    private final GroupCreationAttemptService groupCreationAttemptService;
    private final GroupInviteCodeUniqueViolationDetector uniqueViolationDetector;
    private final Validator validator;

    /**
     * 초대코드 unique 충돌일 때만 독립된 전체 생성 트랜잭션을 새로 시작한다.
     */
    public GroupResDTO.CreateResult createGroup(
            Long memberId,
            GroupReqDTO.CreateGroup request
    ) {
        validateCreateGroupRequest(request);
        for (int attempt = 1; attempt <= MAX_CREATE_ATTEMPTS; attempt++) {
            try {
                return groupCreationAttemptService.createOnce(memberId, request);
            } catch (DataIntegrityViolationException exception) {
                if (!uniqueViolationDetector.isInviteCodeUniqueViolation(exception)) {
                    throw exception;
                }
                log.warn("그룹 통합 생성 중 초대코드 unique 충돌이 발생해 전체 생성을 "
                                + "재시도합니다. memberId={}, attempt={}",
                        memberId, attempt);
            }
        }

        log.error("그룹 통합 생성의 초대코드 unique 충돌 재시도 횟수를 초과했습니다. "
                        + "memberId={}, maxAttempts={}", memberId, MAX_CREATE_ATTEMPTS);
        throw new GroupException(GroupErrorCode.INVITE_CODE_ISSUE_FAILED);
    }

    private void validateCreateGroupRequest(GroupReqDTO.CreateGroup request) {
        if (request == null
                || request.customCategories() == null
                || request.routines() == null) {
            throw new IllegalArgumentException("유효하지 않은 그룹 통합 생성 요청입니다.");
        }
        if (request.customCategories().size()
                > GroupRoutineCategory.MAX_GROUP_CATEGORY_COUNT) {
            throw new GroupException(GroupErrorCode.GROUP_ROUTINE_CATEGORY_LIMIT_EXCEEDED);
        }
        if (request.routines().size() > GroupRoutine.MAX_GROUP_ROUTINE_COUNT) {
            throw new GroupException(GroupErrorCode.GROUP_ROUTINE_LIMIT_EXCEEDED);
        }

        Set<ConstraintViolation<GroupReqDTO.CreateGroup>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            log.warn("그룹 통합 생성 요청 검증에 실패했습니다. violationCount={}", violations.size());
            throw new IllegalArgumentException("유효하지 않은 그룹 통합 생성 요청입니다.");
        }
    }

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

        // REPEATABLE READ에서 상한 집계가 잠금 대기 전 스냅샷을 보지 않도록 그룹을 먼저 잠근다.
        Group group = groupValidationService.lockActiveGroupForUpdate(groupId);
        groupValidationService.validateGroupOwner(groupId, memberId);
        validateRoutineLimit(groupId);

        GroupRoutineCategory category = getUsableCategory(
                groupId,
                request.categoryId(),
                memberId,
                null
        );

        validateRoutineTitleNotDuplicated(groupId, request.title());

        GroupRoutine groupRoutine = GroupConverter.toGroupRoutine(request, group, category);
        saveGroupRoutine(groupRoutine);

        int assignmentCount = assignmentCommandService
                .assignRoutineToActiveMembersToday(groupRoutine);

        log.info("그룹 루틴 생성을 완료했습니다. groupId={}, routineId={}, memberId={}, assignmentCount={}",
                groupId, groupRoutine.getId(), memberId, assignmentCount);

        return GroupConverter.toRoutineCreateResult(groupRoutine, assignmentCount);
    }

    /**
     * ACTIVE OWNER와 대상 루틴의 그룹 소속을 검증한 뒤 기본 정보·반복 일정·오늘 할당을 수정한다.
     * 전체 변경은 하나의 트랜잭션으로 처리되어 할당 동기화 실패 시 루틴과 일정도 롤백된다.
     *
     * @param groupId 요청 대상 그룹 ID
     * @param routineId 수정 대상 그룹 루틴 ID
     * @param memberId 수정을 요청한 회원 ID
     * @param request 그룹 루틴 전체 수정 요청
     * @return 수정된 루틴과 동기화 후 오늘 할당 수
     */
    @Transactional
    public GroupResDTO.RoutineUpdateResult updateRoutine(
            Long groupId,
            Long routineId,
            Long memberId,
            GroupReqDTO.UpdateRoutine request
    ) {
        validateRequest(request);
        groupValidationService.validateGroupOwner(groupId, memberId);

        GroupRoutine groupRoutine = groupRoutineRepository
                .findByIdAndGroupIdForUpdate(routineId, groupId)
                .orElseThrow(() -> {
                    log.warn("수정할 그룹 루틴을 찾을 수 없습니다. "
                                    + "groupId={}, routineId={}, memberId={}",
                            groupId, routineId, memberId);
                    return new GroupException(GroupErrorCode.GROUP_ROUTINE_NOT_FOUND);
                });
        GroupRoutineCategory category = getUsableCategory(
                groupId,
                request.categoryId(),
                memberId,
                routineId
        );
        validateRoutineTitleNotDuplicated(groupId, routineId, request.title());

        groupRoutine.update(category, request.title(), request.description());
        groupRoutine.replaceSchedules(request.schedules().stream()
                .map(schedule -> new GroupRoutine.ScheduleUpdate(
                        schedule.repeatDay(),
                        schedule.startTime(),
                        schedule.endTime()
                ))
                .toList());
        saveGroupRoutine(groupRoutine);

        int assignmentCount = assignmentCommandService
                .synchronizeRoutineAssignmentsToday(groupRoutine);
        log.info("그룹 루틴 수정을 완료했습니다. "
                        + "groupId={}, routineId={}, memberId={}, assignmentCount={}",
                groupId, routineId, memberId, assignmentCount);
        return GroupConverter.toRoutineUpdateResult(groupRoutine, assignmentCount);
    }

    /**
     * Controller 외의 호출 경로에서도 생성 요청의 필수값과 일정 규칙을 방어적으로 검증한다.
     *
     * @param request 검증할 그룹 루틴 생성 요청
     * @throws IllegalArgumentException 필수값, 길이, 요일 또는 시간 범위가 유효하지 않은 경우
     */
    private void validateRequest(GroupReqDTO.CreateRoutine request) {
        if (request == null) {
            throw new IllegalArgumentException("유효하지 않은 그룹 루틴 생성 요청입니다.");
        }
        validateRoutineRequest(
                request.categoryId(),
                request.title(),
                request.description(),
                request.schedules()
        );
    }

    private void validateRequest(GroupReqDTO.UpdateRoutine request) {
        if (request == null) {
            throw new IllegalArgumentException("유효하지 않은 그룹 루틴 수정 요청입니다.");
        }
        validateRoutineRequest(
                request.categoryId(),
                request.title(),
                request.description(),
                request.schedules()
        );
    }

    private void validateRoutineRequest(
            Long categoryId,
            String title,
            String description,
            java.util.List<GroupReqDTO.RoutineSchedule> schedules
    ) {
        if (categoryId == null
                || categoryId <= 0
                || title == null
                || title.isBlank()
                || title.length() > 20
                || description == null
                || description.isBlank()
                || description.length() > 255
                || schedules == null
                || schedules.isEmpty()
                || schedules.size() > 7) {
            log.warn("그룹 루틴 요청 검증에 실패했습니다.");
            throw new IllegalArgumentException("유효하지 않은 그룹 루틴 요청입니다.");
        }

        Set<java.time.DayOfWeek> repeatDays = new HashSet<>();
        boolean invalidSchedule = schedules.stream().anyMatch(schedule ->
                schedule == null
                        || schedule.repeatDay() == null
                        || schedule.startTime() == null
                        || schedule.endTime() == null
                        || !schedule.startTime().isBefore(schedule.endTime())
                        || !repeatDays.add(schedule.repeatDay())
        );
        if (invalidSchedule) {
            log.warn("그룹 루틴 일정 검증에 실패했습니다.");
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
            log.warn("동일한 제목의 그룹 루틴 생성을 차단했습니다. groupId={}, title={}",
                    groupId, title);
            throw new GroupException(GroupErrorCode.DUPLICATE_GROUP_ROUTINE_TITLE);
        }
    }

    /** 그룹 행 잠금을 획득한 상태에서 31번째 루틴 생성을 차단한다. */
    private void validateRoutineLimit(Long groupId) {
        long routineCount = groupRoutineRepository.countByGroupId(groupId);
        if (routineCount >= GroupRoutine.MAX_GROUP_ROUTINE_COUNT) {
            log.warn("그룹 루틴 개수 상한을 초과했습니다. groupId={}, routineCount={}",
                    groupId, routineCount);
            throw new GroupException(GroupErrorCode.GROUP_ROUTINE_LIMIT_EXCEEDED);
        }
    }

    /** 활성 카테고리를 조회하고 기본 카테고리 또는 요청 그룹 소유인지 확인한다. */
    private GroupRoutineCategory getUsableCategory(
            Long groupId,
            Long categoryId,
            Long memberId,
            Long routineId
    ) {
        GroupRoutineCategory category = groupRoutineCategoryRepository
                .findByIdAndActiveTrue(categoryId)
                .orElseThrow(() -> {
                    log.warn("활성 그룹 루틴 카테고리 조회에 실패했습니다. "
                                    + "groupId={}, routineId={}, memberId={}, categoryId={}",
                            groupId, routineId, memberId, categoryId);
                    return new GroupException(GroupErrorCode.ROUTINE_CATEGORY_NOT_FOUND);
                });
        if (!category.isUsableBy(groupId)) {
            log.warn("다른 그룹의 루틴 카테고리 사용을 차단했습니다. "
                            + "groupId={}, routineId={}, memberId={}, categoryId={}",
                    groupId, routineId, memberId, categoryId);
            throw new GroupException(GroupErrorCode.GROUP_ROUTINE_CATEGORY_ACCESS_DENIED);
        }
        return category;
    }

    private void validateRoutineTitleNotDuplicated(
            Long groupId,
            Long routineId,
            String title
    ) {
        if (groupRoutineRepository.existsByGroupIdAndTitleAndIdNot(groupId, title, routineId)) {
            log.warn("동일한 제목의 그룹 루틴 수정을 차단했습니다. "
                            + "groupId={}, routineId={}, title={}",
                    groupId, routineId, title);
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
            log.warn("그룹 루틴 저장 중 무결성 제약을 위반했습니다. groupId={}, title={}",
                    groupRoutine.getGroup().getId(), groupRoutine.getTitle());
            if (isRoutineTitleConstraintViolation(e)) {
                throw new GroupException(GroupErrorCode.DUPLICATE_GROUP_ROUTINE_TITLE);
            }
            throw e;
        }
    }

    private boolean isRoutineTitleConstraintViolation(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null
                    && message.toLowerCase().contains("uk_group_routine_group_title")) {
                return true;
            }
            if (current.getCause() == current) {
                break;
            }
            current = current.getCause();
        }
        return false;
    }
}
