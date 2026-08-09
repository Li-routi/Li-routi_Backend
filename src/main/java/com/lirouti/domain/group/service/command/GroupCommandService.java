package com.lirouti.domain.group.service.command;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.lirouti.domain.group.converter.GroupConverter;
import com.lirouti.domain.group.dto.request.GroupReqDTO;
import com.lirouti.domain.group.dto.response.GroupResDTO;
import com.lirouti.domain.group.entity.Group;
import com.lirouti.domain.group.entity.GroupMember;
import com.lirouti.domain.group.entity.GroupRoutine;
import com.lirouti.domain.group.entity.GroupRoutineCategory;
import com.lirouti.domain.group.enums.GroupStatus;
import com.lirouti.domain.group.enums.GroupMemberStatus;
import com.lirouti.domain.group.exception.GroupException;
import com.lirouti.domain.group.exception.code.error.GroupErrorCode;
import com.lirouti.domain.group.repository.GroupRepository;
import com.lirouti.domain.group.repository.GroupMemberRepository;
import com.lirouti.domain.group.repository.GroupRoutineCategoryRepository;
import com.lirouti.domain.group.repository.GroupRoutineRepository;
import com.lirouti.domain.group.service.GroupValidationService;
import com.lirouti.domain.verification.repository.GroupRoutineVerificationReadRepository;
import com.lirouti.domain.notification.enums.NotificationCategory;
import com.lirouti.domain.notification.enums.NotificationType;
import com.lirouti.domain.notification.event.NotificationRequestedEvent;
import com.lirouti.global.websocket.WebSocketSessionRegistry;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class GroupCommandService {
    private static final int MAX_CREATE_ATTEMPTS = 10;

    private final GroupValidationService groupValidationService;
    private final GroupRepository groupRepository;
    private final GroupRoutineCategoryRepository groupRoutineCategoryRepository;
    private final GroupRoutineRepository groupRoutineRepository;
    private final GroupRoutineVerificationReadRepository groupRoutineVerificationReadRepository;
    private final GroupRoutineAssignmentCommandService assignmentCommandService;
    private final GroupCreationAttemptService groupCreationAttemptService;
    private final GroupInviteCodeUniqueViolationDetector uniqueViolationDetector;
    private final Validator validator;
    private final GroupMemberRepository groupMemberRepository;
    private final ApplicationEventPublisher eventPublisher;

    private final WebSocketSessionRegistry webSocketSessionRegistry;

    public GroupCommandService(
            GroupValidationService groupValidationService,
            GroupRepository groupRepository,
            GroupRoutineCategoryRepository groupRoutineCategoryRepository,
            GroupRoutineRepository groupRoutineRepository,
            GroupRoutineVerificationReadRepository groupRoutineVerificationReadRepository,
            GroupRoutineAssignmentCommandService assignmentCommandService,
            GroupCreationAttemptService groupCreationAttemptService,
            GroupInviteCodeUniqueViolationDetector uniqueViolationDetector,
            Validator validator,
            WebSocketSessionRegistry webSocketSessionRegistry,
            GroupMemberRepository groupMemberRepository,
            ApplicationEventPublisher eventPublisher
    ) {
        this.groupValidationService = groupValidationService;
        this.groupRepository = groupRepository;
        this.groupRoutineCategoryRepository = groupRoutineCategoryRepository;
        this.groupRoutineRepository = groupRoutineRepository;
        this.groupRoutineVerificationReadRepository = groupRoutineVerificationReadRepository;
        this.assignmentCommandService = assignmentCommandService;
        this.groupCreationAttemptService = groupCreationAttemptService;
        this.uniqueViolationDetector = uniqueViolationDetector;
        this.validator = validator;
        this.webSocketSessionRegistry = webSocketSessionRegistry;
        this.groupMemberRepository = groupMemberRepository;
        this.eventPublisher = eventPublisher;
    }

    /** 잠긴 ACTIVE OWNER 그룹을 애그리거트 루트에서 Hard Delete한다. */
    @Transactional
    public void deleteGroup(Long groupId, Long memberId) {
        Group group = groupRepository.findByIdForUpdate(groupId)
                .orElseThrow(() -> new GroupException(GroupErrorCode.GROUP_NOT_FOUND));

        if (group.getStatus() != GroupStatus.ACTIVE) {
            throw new GroupException(GroupErrorCode.GROUP_NOT_FOUND);
        }

        groupValidationService.validateGroupOwner(group, memberId);
        // 읽음 행은 Group 애그리거트의 JPA cascade 대상이 아니다. 먼저 지워 FK 삭제를 열어 둔다.
        groupRoutineVerificationReadRepository.deleteAllByGroupId(groupId);
        groupRepository.delete(group);
    }

    /** ACTIVE OWNER가 그룹 행 잠금 안에서 신규 참여를 차단한다. 이미 잠긴 경우에도 성공한다. */
    @Transactional
    public GroupResDTO.LockState lockGroup(Long groupId, Long memberId) {
        Group group = groupValidationService.lockActiveGroupForUpdate(groupId);
        groupValidationService.validateGroupOwner(group, memberId);
        group.lock();
        return GroupConverter.toLockState(group);
    }

    /** ACTIVE OWNER가 그룹 행 잠금 안에서 신규 참여를 다시 허용한다. 이미 해제된 경우에도 성공한다. */
    @Transactional
    public GroupResDTO.LockState unlockGroup(Long groupId, Long memberId) {
        Group group = groupValidationService.lockActiveGroupForUpdate(groupId);
        groupValidationService.validateGroupOwner(group, memberId);
        group.unlock();
        return GroupConverter.toLockState(group);
    }

    /** ACTIVE OWNER가 그룹 행 잠금 안에서 그룹 이름을 변경한다. */
    @Transactional
    public void updateGroupName(
            Long groupId,
            Long memberId,
            GroupReqDTO.UpdateName request
    ) {
        Group group = groupValidationService.lockActiveGroupForUpdate(groupId);
        groupValidationService.validateGroupOwner(group, memberId);
        group.updateName(request.name());
    }

    /**
     * 동일 그룹의 동시 위임을 그룹 행 비관적 잠금으로 직렬화하고 OWNER 권한을 원자적으로 교체한다.
     */
    @Transactional
    public void transferGroupOwner(
            Long groupId,
            Long ownerId,
            GroupReqDTO.TransferOwner request
    ) {
        Group group = groupValidationService.lockActiveGroupForUpdate(groupId);
        GroupMember owner = groupValidationService.validateGroupOwner(group, ownerId);
        Long targetMemberId = request.targetMemberId();
        if (ownerId.equals(targetMemberId)) {
            throw new GroupException(GroupErrorCode.OWNER_CANNOT_TRANSFER_TO_SELF);
        }

        GroupMember target = groupValidationService
                .validateActiveGroupMember(groupId, targetMemberId);
        owner.demoteToMember();
        target.promoteToOwner();
    }

    /** 그룹 행 잠금 안에서 OWNER 권한, 상한, 이름 중복을 검증하고 사용자 카테고리를 생성한다. */
    @Transactional
    public GroupResDTO.Category createCategory(
            Long groupId,
            Long memberId,
            GroupReqDTO.CreateCategory request
    ) {
        if (request == null) {
            log.warn("그룹 카테고리 생성 요청이 비어 있습니다. groupId={}, memberId={}",
                    groupId, memberId);
            throw new IllegalArgumentException("유효하지 않은 그룹 카테고리 생성 요청입니다.");
        }

        Group group = groupValidationService.lockActiveGroupForUpdate(groupId);
        groupValidationService.validateGroupOwner(groupId, memberId);
        String name = normalizeCategoryName(groupId, memberId, request.name());

        long categoryCount = groupRoutineCategoryRepository
                .countByGroupIdAndActiveTrue(groupId);
        if (categoryCount >= GroupRoutineCategory.MAX_GROUP_CATEGORY_COUNT) {
            log.warn("그룹 사용자 카테고리 개수 상한을 초과했습니다. "
                            + "groupId={}, memberId={}, categoryCount={}",
                    groupId, memberId, categoryCount);
            throw new GroupException(GroupErrorCode.GROUP_ROUTINE_CATEGORY_LIMIT_EXCEEDED);
        }
        if (groupRoutineCategoryRepository.existsReservedName(groupId, name)) {
            log.warn("예약된 그룹 카테고리 이름을 차단했습니다. groupId={}, memberId={}, name={}",
                    groupId, memberId, name);
            throw new GroupException(GroupErrorCode.DUPLICATE_GROUP_ROUTINE_CATEGORY_NAME);
        }

        GroupReqDTO.CreateCategory normalizedRequest =
                new GroupReqDTO.CreateCategory(name, request.color());
        GroupRoutineCategory category = GroupConverter
                .toGroupRoutineCategory(normalizedRequest, group);
        saveGroupRoutineCategory(groupId, memberId, category);
        group.addRoutineCategory(category);

        log.info("그룹 사용자 카테고리를 생성했습니다. groupId={}, memberId={}, categoryId={}",
                groupId, memberId, category.getId());
        return GroupConverter.toCategory(category);
    }

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

    /**
     * ACTIVE 구성원을 탈퇴 처리하고 미완료 할당을 정리한 뒤, 커밋 후 채팅 세션을 회수한다.
     * 완료·미이행 할당과 인증 이력은 보존한다.
     */
    @Transactional
    public void leaveGroup(Long groupId, Long memberId) {
        // 채팅 전송도 같은 그룹 행을 잠그므로 권한 회수와 진행 중인 전송의 순서가 확정된다.
        groupValidationService.lockActiveGroupForUpdate(groupId);
        GroupMember groupMember = groupValidationService
                .validateActiveGroupMember(groupId, memberId);
        groupMember.leave();
        int deletedAssignmentCount = assignmentCommandService
                .deleteUnfinishedAssignmentsForLeaver(groupId, memberId);
        closeMemberSessionsAfterCommit(memberId);

        log.info("그룹 탈퇴를 완료했습니다. groupId={}, memberId={}, deletedAssignmentCount={}",
                groupId, memberId, deletedAssignmentCount);
    }

    /** ACTIVE OWNER가 대상 구성원을 강제 퇴장시키고 대상 회원의 모든 채팅 세션을 회수한다. */
    @Transactional
    public void kickMember(Long groupId, Long ownerId, Long targetMemberId) {
        groupValidationService.lockActiveGroupForUpdate(groupId);
        groupValidationService.validateGroupOwner(groupId, ownerId);
        if (targetMemberId == null) {
            throw new GroupException(GroupErrorCode.GROUP_MEMBER_ACCESS_DENIED);
        }

        GroupMember target = groupValidationService
                .validateActiveGroupMember(groupId, targetMemberId);

        target.kick();
        closeMemberSessionsAfterCommit(targetMemberId);
    }

    /** ACTIVE OWNER 또는 MEMBER가 자신이 참여한 그룹 안의 상태 메시지만 수정한다. */
    @Transactional
    public GroupResDTO.StatusMessageUpdate updateMyStatusMessage(
            Long groupId,
            Long memberId,
            GroupReqDTO.UpdateMyStatusMessage request
    ) {
        validateStatusMessageRequest(request);
        GroupMember groupMember = groupValidationService
                .validateActiveGroupMember(groupId, memberId);
        groupMember.updateStatusMessage(request.statusMessage());

        log.info("그룹별 상태 메시지를 수정했습니다. groupId={}, memberId={}", groupId, memberId);
        return GroupConverter.toStatusMessageUpdate(groupMember);
    }

    /** Controller 밖의 호출도 strip된 최종 상태 메시지 정책을 우회하지 못하게 한다. */
    private void validateStatusMessageRequest(GroupReqDTO.UpdateMyStatusMessage request) {
        if (request == null
                || request.statusMessage() == null
                || request.statusMessage().isBlank()
                || request.statusMessage().length() > GroupMember.MAX_STATUS_MESSAGE_LENGTH) {
            throw new IllegalArgumentException("유효하지 않은 그룹별 상태 메시지 요청입니다.");
        }
    }

    private void closeMemberSessionsAfterCommit(Long memberId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            webSocketSessionRegistry.closeMemberSessions(memberId);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                webSocketSessionRegistry.closeMemberSessions(memberId);
            }
        });
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
    public GroupResDTO.GroupRoutineCreateResult createRoutine(
            Long groupId,
            Long memberId,
            GroupReqDTO.GroupRoutineCreateRequest request
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
        group.addRoutine(groupRoutine);
        category.addRoutine(groupRoutine);

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

        GroupRoutineCategory previousCategory = groupRoutine.getCategory();
        groupRoutine.update(category, request.title(), request.description());
        if (previousCategory != category) {
            previousCategory.removeRoutine(groupRoutine);
            category.addRoutine(groupRoutine);
        }
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
        publishRoutineUpdatedNotifications(groupRoutine, memberId);
        return GroupConverter.toRoutineUpdateResult(groupRoutine, assignmentCount);
    }

    /** 일정 변경을 수정자 외의 현재 참여자에게 트랜잭션 커밋 후 알린다. */
    private void publishRoutineUpdatedNotifications(GroupRoutine routine, Long actorId) {
        // 일부 기존 순수 단위 테스트는 알림 협력자 없이 서비스 생성자를 직접 호출한다.
        if (groupMemberRepository == null || eventPublisher == null) {
            return;
        }
        String updateEventId = UUID.randomUUID().toString();
        var recipients = groupMemberRepository.findAllByGroupIdAndStatus(
                routine.getGroup().getId(), GroupMemberStatus.ACTIVE);
        if (recipients == null) {
            return;
        }
        for (GroupMember groupMember : recipients) {
            Long recipientId = groupMember.getMember().getId();
            if (recipientId.equals(actorId)) {
                continue;
            }
            eventPublisher.publishEvent(new NotificationRequestedEvent(
                    recipientId,
                    NotificationCategory.GROUP_ROUTINE,
                    NotificationType.GROUP_ROUTINE_UPDATED,
                    "그룹 루틴이 변경됐어요",
                    routine.getTitle() + "의 변경 내용을 확인해 주세요.",
                    routine.getGroup().getId(),
                    routine.getId(),
                    "GROUP_ROUTINE",
                    "group-routine-updated:" + routine.getId() + ":" + updateEventId
                            + ":" + recipientId
            ));
        }
    }

    /**
     * ACTIVE OWNER와 루틴 소속을 검증하고 미확정 할당 삭제와 루틴 비활성화를 함께 처리한다.
     */
    @Transactional
    public void deleteRoutine(Long groupId, Long routineId, Long memberId) {
        groupValidationService.validateGroupOwner(groupId, memberId);

        GroupRoutine groupRoutine = groupRoutineRepository
                .findByIdAndGroupIdForUpdate(routineId, groupId)
                .orElseThrow(() -> {
                    log.warn("삭제할 활성 그룹 루틴을 찾을 수 없습니다. "
                                    + "groupId={}, routineId={}, memberId={}",
                            groupId, routineId, memberId);
                    return new GroupException(GroupErrorCode.GROUP_ROUTINE_NOT_FOUND);
                });

        int deletedAssignmentCount = assignmentCommandService
                .deleteMutableAssignments(routineId);
        groupRoutine.delete();

        log.info("그룹 루틴 삭제를 완료했습니다. "
                        + "groupId={}, routineId={}, memberId={}, deletedAssignmentCount={}",
                groupId, routineId, memberId, deletedAssignmentCount);
    }

    /**
     * Controller 외의 호출 경로에서도 생성 요청의 필수값과 일정 규칙을 방어적으로 검증한다.
     *
     * @param request 검증할 그룹 루틴 생성 요청
     * @throws IllegalArgumentException 필수값, 길이, 요일 또는 시간 범위가 유효하지 않은 경우
     */
    private void validateRequest(GroupReqDTO.GroupRoutineCreateRequest request) {
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
        if (groupRoutineRepository.existsByGroupIdAndTitleAndActiveTrue(groupId, title)) {
            log.warn("동일한 제목의 그룹 루틴 생성을 차단했습니다. groupId={}, title={}",
                    groupId, title);
            throw new GroupException(GroupErrorCode.DUPLICATE_GROUP_ROUTINE_TITLE);
        }
    }

    /** 그룹 행 잠금을 획득한 상태에서 31번째 루틴 생성을 차단한다. */
    private void validateRoutineLimit(Long groupId) {
        long routineCount = groupRoutineRepository.countByGroupIdAndActiveTrue(groupId);
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
        if (groupRoutineRepository.existsByGroupIdAndTitleAndActiveTrueAndIdNot(groupId, title, routineId)) {
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
            if (GroupConstraintViolationInspector.isUniqueConstraintViolation(
                    e,
                    GroupDatabaseConstraints.ROUTINE_TITLE
            )) {
                throw new GroupException(GroupErrorCode.DUPLICATE_GROUP_ROUTINE_TITLE);
            }
            throw e;
        }
    }

    private String normalizeCategoryName(Long groupId, Long memberId, String rawName) {
        String name = rawName == null ? "" : rawName.trim();
        if (name.isEmpty()
                || name.length() > GroupRoutineCategory.MAX_GROUP_CATEGORY_NAME_LENGTH
                || name.indexOf('\n') >= 0
                || name.indexOf('\r') >= 0) {
            log.warn("그룹 카테고리 이름 검증에 실패했습니다. "
                            + "groupId={}, memberId={}, length={}",
                    groupId, memberId, name.length());
            throw new GroupException(GroupErrorCode.INVALID_GROUP_ROUTINE_CATEGORY_NAME);
        }
        return name;
    }

    private void saveGroupRoutineCategory(
            Long groupId,
            Long memberId,
            GroupRoutineCategory category
    ) {
        try {
            groupRoutineCategoryRepository.saveAndFlush(category);
        } catch (DataIntegrityViolationException exception) {
            if (!GroupConstraintViolationInspector.isUniqueConstraintViolation(
                    exception,
                    GroupDatabaseConstraints.ROUTINE_CATEGORY_NAME
            )) {
                throw exception;
            }
            log.warn("같은 이름의 그룹 카테고리 저장을 차단했습니다. "
                            + "groupId={}, memberId={}, name={}",
                    groupId, memberId, category.getName());
            throw new GroupException(GroupErrorCode.DUPLICATE_GROUP_ROUTINE_CATEGORY_NAME);
        }
    }
}
