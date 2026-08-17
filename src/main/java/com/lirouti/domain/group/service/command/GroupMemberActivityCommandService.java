package com.lirouti.domain.group.service.command;

import java.time.LocalDate;
import java.util.List;

import com.lirouti.domain.group.enums.GroupMemberStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.lirouti.domain.group.entity.GroupMember;
import com.lirouti.domain.group.entity.GroupRoutineAssignment;
import com.lirouti.domain.group.enums.GroupRoutineAssignmentStatus;
import com.lirouti.domain.group.exception.GroupException;
import com.lirouti.domain.group.exception.code.error.GroupErrorCode;
import com.lirouti.domain.group.repository.GroupMemberRepository;
import com.lirouti.domain.group.repository.GroupRoutineAssignmentRepository;

import lombok.RequiredArgsConstructor;

/** 그룹 행 잠금 안에서 GroupMember 활동 상태를 변경한다. */
@Service
@RequiredArgsConstructor
public class GroupMemberActivityCommandService {
    private final GroupMemberRepository groupMemberRepository;
    private final GroupRoutineAssignmentRepository groupRoutineAssignmentRepository;

    /**
     * 실제 COMPLETED 전이 뒤에만 호출한다. 현재 가입 회차의 당일 Assignment가 모두 완료된 경우
     * GroupMember 행 잠금 안에서 하루 한 번 스트릭을 증가시킨다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordStreakIfAllAssignmentsCompleted(
            Long groupId,
            Long memberId,
            LocalDate assignedDate
    ) {
        GroupMember groupMember = groupMemberRepository
                .findByGroupIdAndMemberIdForUpdate(groupId, memberId)
                .orElseThrow(() -> new GroupException(GroupErrorCode.GROUP_MEMBER_ACCESS_DENIED));

        List<GroupRoutineAssignment> assignments = groupRoutineAssignmentRepository
                .findAllByGroupIdAndMemberIdAndAssignedDateAndCreatedAtAfterOrEqualForUpdate(
                        groupId,
                        memberId,
                        assignedDate,
                        groupMember.getJoinedAt());
        if (assignments.isEmpty()) {
            return;
        }
        boolean allCompleted = assignments.stream()
                .allMatch(assignment -> assignment.getStatus()
                        == GroupRoutineAssignmentStatus.COMPLETED);
        if (allCompleted) {
            groupMember.recordStreakCompletion(assignedDate);
        }
    }

    /** 한 batch에서 실제 MISSED로 전이된 현재 가입 회차 멤버만 초기화한다. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void resetCurrentStreaksForMissedAssignments(List<Long> assignmentIds) {
        if (assignmentIds.isEmpty()) {
            return;
        }
        groupMemberRepository.findAllActiveCurrentMembershipsByAssignmentIdsForUpdate(assignmentIds)
                .forEach(GroupMember::resetCurrentStreak);
    }

    /** Like 행 변경과 동일 트랜잭션에서 작성자 활동 상태를 변경한다. */
    @Transactional(propagation = Propagation.MANDATORY)
    public GroupMember lockMembership(Long groupId, Long memberId) {
        return groupMemberRepository.findByGroupIdAndMemberIdForUpdate(groupId, memberId)
                .orElseThrow(() -> new GroupException(GroupErrorCode.GROUP_MEMBER_ACCESS_DENIED));
    }

    /**
     * SP-004(루틴 하우스 메이트) 판정용. 그룹의 현재 ACTIVE 구성원 전원이
     * 그 날짜에 배정된 할당을 모두 COMPLETED로 마쳤는지 확인한다.
     * 그 날 배정된 할당이 아예 없는 구성원이 한 명이라도 있으면 "함께 인증"이 아니므로 false.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean isAllMembersCompletedToday(Long groupId, LocalDate assignedDate) {
        List<GroupMember> activeMembers = groupMemberRepository
                .findAllByGroupIdAndStatus(groupId, GroupMemberStatus.ACTIVE);
        if (activeMembers.isEmpty()) {
            return false;
        }

        for (GroupMember member : activeMembers) {
            List<GroupRoutineAssignment> assignments = groupRoutineAssignmentRepository
                    .findAllByGroupIdAndMemberIdAndAssignedDateAndCreatedAtAfterOrEqualForUpdate(
                            groupId,
                            member.getMember().getId(),
                            assignedDate,
                            member.getJoinedAt());
            if (assignments.isEmpty()) {
                return false;
            }
            boolean allCompleted = assignments.stream()
                    .allMatch(assignment -> assignment.getStatus()
                            == GroupRoutineAssignmentStatus.COMPLETED);
            if (!allCompleted) {
                return false;
            }
        }
        return true;
    }
}
