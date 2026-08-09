package com.lirouti.domain.group.service.command;

import com.lirouti.domain.group.dto.request.GroupReqDTO;
import com.lirouti.domain.group.dto.response.GroupResDTO;
import com.lirouti.domain.group.entity.Group;
import com.lirouti.domain.group.entity.GroupMember;
import com.lirouti.domain.group.enums.GroupMemberRole;
import com.lirouti.domain.group.enums.GroupMemberStatus;
import com.lirouti.domain.group.exception.GroupException;
import com.lirouti.domain.group.exception.code.error.GroupErrorCode;
import com.lirouti.domain.group.repository.GroupMemberRepository;
import com.lirouti.domain.group.repository.GroupRepository;
import com.lirouti.domain.group.service.GroupValidationService;
import com.lirouti.domain.member.entity.Member;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;
import com.lirouti.domain.notification.enums.NotificationCategory;
import com.lirouti.domain.notification.enums.NotificationType;
import com.lirouti.domain.notification.event.NotificationRequestedEvent;

import java.time.Clock;
import java.time.LocalDateTime;

/** 초대코드 기반 그룹 가입을 잠금·관계 변경·당일 할당 하나의 트랜잭션으로 처리한다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class GroupJoinCommandService {
    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final GroupValidationService groupValidationService;
    private final GroupRoutineAssignmentCommandService assignmentCommandService;
    private final Clock clock;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public GroupResDTO.JoinResult join(Long memberId, GroupReqDTO.JoinGroup request) {
        if (request == null) {
            throw new IllegalArgumentException("유효하지 않은 그룹 가입 요청입니다.");
        }

        // 일반 조회가 REPEATABLE READ 스냅샷을 먼저 만들지 않도록 초대코드 조회부터 그룹 행을 잠근다.
        Long groupId = groupRepository.findByInviteCodeForUpdate(request.inviteCode())
                .map(Group::getId)
                .orElseThrow(() -> new GroupException(GroupErrorCode.GROUP_NOT_FOUND));

        // 초대코드 조회 결과는 groupId 식별에만 사용한다. 이후에는 잠금 조회된 엔티티만 사용한다.
        GroupValidationService.JoinLimitContext context = groupValidationService
                .lockActiveGroupAndMemberForJoin(groupId, memberId);
        Group lockedGroup = context.group();
        Member lockedMember = context.member();
        if (lockedGroup.isLocked()) {
            throw new GroupException(GroupErrorCode.GROUP_LOCKED);
        }

        GroupMember existingMembership = groupMemberRepository.findByGroupIdAndMemberId(
                lockedGroup.getId(), lockedMember.getId()).orElse(null);
        validateJoinableMembership(existingMembership);

        // LEFT 관계를 먼저 ACTIVE로 바꾸면 아래 count 쿼리의 자동 flush에 포함된다.
        // 따라서 상태 거부만 먼저 판단한 뒤, 두 상한 검증을 마치고 재활성화한다.
        groupValidationService.validateJoinLimits(lockedGroup.getId(), lockedMember.getId());
        LocalDateTime joinedAt = LocalDateTime.now(clock);
        GroupMember membership = createOrRejoinMembership(
                existingMembership, lockedGroup, lockedMember, joinedAt);

        saveMembership(membership, lockedGroup);
        assignmentCommandService.assignTodayRoutinesToMember(
                lockedGroup.getId(), lockedMember.getId(), joinedAt);

        groupMemberRepository.findAllByGroupIdAndStatus(lockedGroup.getId(), GroupMemberStatus.ACTIVE)
                .stream().map(GroupMember::getMember)
                .filter(member -> !member.getId().equals(memberId))
                .forEach(member -> eventPublisher.publishEvent(new NotificationRequestedEvent(
                        member.getId(), NotificationCategory.GROUP_ROUTINE,
                        NotificationType.GROUP_MEMBER_JOINED, "새로운 그룹원이 참여했어요",
                        lockedMember.getNickname() + "님이 " + lockedGroup.getName() + "에 참여했습니다.",
                        lockedGroup.getId(), memberId, "GROUP_MEMBER",
                        "group-joined:" + lockedGroup.getId() + ":" + memberId + ":" + joinedAt)));

        log.info("초대코드 기반 그룹 가입을 완료했습니다. groupId={}, memberId={}",
                lockedGroup.getId(), lockedMember.getId());
        return new GroupResDTO.JoinResult(
                lockedGroup.getId(),
                lockedGroup.getName(),
                membership.getStatus()
        );
    }

    private void validateJoinableMembership(GroupMember membership) {
        if (membership == null) {
            return;
        }
        if (membership.getStatus() == GroupMemberStatus.ACTIVE) {
            throw new GroupException(GroupErrorCode.ALREADY_ACTIVE_GROUP_MEMBER);
        }
        if (membership.getStatus() == GroupMemberStatus.KICKED) {
            throw new GroupException(GroupErrorCode.KICKED_MEMBER_CANNOT_REJOIN);
        }
    }

    private GroupMember createOrRejoinMembership(
            GroupMember existingMembership,
            Group group,
            Member member,
            LocalDateTime joinedAt
    ) {
        if (existingMembership == null) {
            return GroupMember.builder()
                        .group(group)
                        .member(member)
                        .role(GroupMemberRole.MEMBER)
                        .joinedAt(joinedAt)
                        .build();
        }
        existingMembership.rejoin(joinedAt);
        return existingMembership;
    }

    private void saveMembership(
            GroupMember membership,
            Group group
    ) {
        if (membership.getId() != null) {
            groupMemberRepository.saveAndFlush(membership);
            return;
        }
        try {
            GroupMember saved = groupMemberRepository.saveAndFlush(membership);
            group.addMember(saved);
        } catch (DataIntegrityViolationException exception) {
            if (GroupConstraintViolationInspector.isUniqueConstraintViolation(
                    exception, GroupDatabaseConstraints.GROUP_MEMBER)) {
                throw new GroupException(GroupErrorCode.ALREADY_ACTIVE_GROUP_MEMBER);
            }
            throw exception;
        }
    }
}
