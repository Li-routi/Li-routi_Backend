package com.lirouti.domain.group.service.command;

import com.lirouti.domain.group.converter.GroupConverter;
import com.lirouti.domain.group.dto.request.GroupReqDTO;
import com.lirouti.domain.group.dto.response.GroupResDTO;
import com.lirouti.domain.group.entity.Group;
import com.lirouti.domain.group.entity.GroupMember;
import com.lirouti.domain.group.entity.GroupRoutine;
import com.lirouti.domain.group.entity.GroupRoutineCategory;
import com.lirouti.domain.group.exception.GroupException;
import com.lirouti.domain.group.exception.code.error.GroupErrorCode;
import com.lirouti.domain.group.repository.GroupMemberRepository;
import com.lirouti.domain.group.repository.GroupRepository;
import com.lirouti.domain.group.repository.GroupRoutineCategoryRepository;
import com.lirouti.domain.group.repository.GroupRoutineRepository;
import com.lirouti.domain.group.service.GroupValidationService;
import com.lirouti.domain.member.entity.Member;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 그룹 통합 생성 한 번을 독립된 트랜잭션에서 수행한다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class GroupCreationAttemptService {
    private final GroupValidationService groupValidationService;
    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final GroupRoutineCategoryRepository groupRoutineCategoryRepository;
    private final GroupRoutineRepository groupRoutineRepository;
    private final GroupRoutineAssignmentCommandService assignmentCommandService;
    private final GroupInviteCodeGenerator inviteCodeGenerator;

    /**
     * 회원 잠금부터 OWNER 할당까지 전체 생성을 한 번 시도한다.
     * 실패하면 이번 시도에서 저장한 모든 데이터를 롤백한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public GroupResDTO.CreateResult createOnce(
            Long memberId,
            GroupReqDTO.CreateGroup request
    ) {
        Member owner = groupValidationService
                .lockActiveMemberAndValidateParticipationLimit(memberId);
        String inviteCode = inviteCodeGenerator.generate();

        Group group = GroupConverter.toGroup(request, inviteCode);
        groupRepository.saveAndFlush(group);

        GroupMember ownerMembership = GroupConverter.toOwnerMembership(owner, group);
        groupMemberRepository.saveAndFlush(ownerMembership);
        group.addMember(ownerMembership);

        List<GroupConverter.CreatedCategory> createdCategories =
                createCustomCategories(group, request.customCategories());
        Map<String, GroupRoutineCategory> customCategoriesByKey = createdCategories.stream()
                .collect(Collectors.toMap(
                        GroupConverter.CreatedCategory::clientKey,
                        GroupConverter.CreatedCategory::category,
                        (first, second) -> first,
                        LinkedHashMap::new
                ));

        List<GroupConverter.CreatedRoutine> createdRoutines = new ArrayList<>();
        for (GroupReqDTO.CreateGroupRoutine routineRequest : request.routines()) {
            GroupRoutineCategory category = resolveInitialRoutineCategory(
                    group,
                    routineRequest,
                    customCategoriesByKey,
                    memberId
            );
            GroupRoutine routine = GroupConverter.toGroupRoutine(routineRequest, group, category);
            groupRoutineRepository.saveAndFlush(routine);
            group.addRoutine(routine);
            category.addRoutine(routine);
            int assignmentCount = assignmentCommandService
                    .assignRoutineToActiveMembersToday(routine);
            createdRoutines.add(new GroupConverter.CreatedRoutine(routine, assignmentCount));
        }

        log.info("그룹 통합 생성 시도를 완료했습니다. groupId={}, ownerId={}, "
                        + "categoryCount={}, routineCount={}, assignmentCount={}",
                group.getId(), memberId, createdCategories.size(), createdRoutines.size(),
                createdRoutines.stream()
                        .mapToInt(GroupConverter.CreatedRoutine::assignmentCount)
                        .sum());
        return GroupConverter.toCreateResult(group, createdCategories, createdRoutines);
    }

    private List<GroupConverter.CreatedCategory> createCustomCategories(
            Group group,
            List<GroupReqDTO.CreateGroupCategory> requests
    ) {
        long existingCount = groupRoutineCategoryRepository
                .countByGroupIdAndActiveTrue(group.getId());
        if (existingCount + requests.size()
                > GroupRoutineCategory.MAX_GROUP_CATEGORY_COUNT) {
            throw new GroupException(GroupErrorCode.GROUP_ROUTINE_CATEGORY_LIMIT_EXCEEDED);
        }

        List<GroupConverter.CreatedCategory> created = new ArrayList<>(requests.size());
        for (GroupReqDTO.CreateGroupCategory request : requests) {
            if (groupRoutineCategoryRepository.existsReservedName(group.getId(), request.name())) {
                log.warn("예약된 그룹 루틴 카테고리 이름을 차단했습니다. groupId={}, name={}",
                        group.getId(), request.name());
                throw new GroupException(GroupErrorCode.DUPLICATE_GROUP_ROUTINE_CATEGORY_NAME);
            }
            GroupRoutineCategory category = GroupConverter
                    .toGroupRoutineCategory(request, group);
            groupRoutineCategoryRepository.saveAndFlush(category);
            group.addRoutineCategory(category);
            created.add(new GroupConverter.CreatedCategory(request.clientKey(), category));
        }
        return created;
    }

    private GroupRoutineCategory resolveInitialRoutineCategory(
            Group group,
            GroupReqDTO.CreateGroupRoutine request,
            Map<String, GroupRoutineCategory> customCategoriesByKey,
            Long memberId
    ) {
        if (hasText(request.categoryKey())) {
            GroupRoutineCategory category = customCategoriesByKey.get(request.categoryKey());
            if (category == null) {
                throw new GroupException(GroupErrorCode.ROUTINE_CATEGORY_NOT_FOUND);
            }
            if (!category.isUsableBy(group.getId())) {
                throw new GroupException(GroupErrorCode.GROUP_ROUTINE_CATEGORY_ACCESS_DENIED);
            }
            return category;
        }

        GroupRoutineCategory category = groupRoutineCategoryRepository
                .findByIdAndActiveTrue(request.categoryId())
                .orElseThrow(() -> new GroupException(GroupErrorCode.ROUTINE_CATEGORY_NOT_FOUND));
        if (!category.isUsableBy(group.getId()) || !category.isFixed()) {
            log.warn("통합 생성 categoryId로 사용할 수 없는 카테고리를 차단했습니다. "
                            + "groupId={}, memberId={}, categoryId={}",
                    group.getId(), memberId, request.categoryId());
            throw new GroupException(GroupErrorCode.GROUP_ROUTINE_CATEGORY_ACCESS_DENIED);
        }
        return category;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
