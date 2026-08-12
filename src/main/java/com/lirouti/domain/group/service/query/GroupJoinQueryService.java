package com.lirouti.domain.group.service.query;

import com.lirouti.domain.group.converter.GroupConverter;
import com.lirouti.domain.group.dto.response.GroupResDTO;
import com.lirouti.domain.group.entity.Group;
import com.lirouti.domain.group.entity.GroupMember;
import com.lirouti.domain.group.enums.GroupJoinUnavailableReason;
import com.lirouti.domain.group.enums.GroupMemberStatus;
import com.lirouti.domain.group.enums.GroupStatus;
import com.lirouti.domain.group.exception.GroupException;
import com.lirouti.domain.group.exception.code.error.GroupErrorCode;
import com.lirouti.domain.group.repository.GroupMemberRepository;
import com.lirouti.domain.group.repository.GroupRepository;
import com.lirouti.domain.group.repository.GroupRoutineRepository;
import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.service.query.MemberQueryService;
import com.lirouti.domain.shop.repository.MemberAvatarEquipmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 초대코드 입력 시 보여줄 그룹 참여 Preview를 읽기 전용으로 구성한다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class GroupJoinQueryService {
    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final GroupRoutineRepository groupRoutineRepository;
    private final MemberAvatarEquipmentRepository memberAvatarEquipmentRepository;
    private final MemberQueryService memberQueryService;

    /**
     * Preview는 현재 DB 상태의 안내용 스냅샷이며 잠금·가입 관계·할당을 생성하지 않는다.
     * 실제 가입 Command는 이 결과를 신뢰하지 않고 잠금 후 모든 조건을 다시 검증한다.
     */
    @Transactional(readOnly = true)
    public GroupResDTO.JoinPreview getJoinPreview(Long memberId, String inviteCode) {
        Member member = memberQueryService.getActiveMember(memberId);
        Group group = groupRepository.findByInviteCode(normalizeInviteCode(inviteCode))
                .orElseThrow(() -> new GroupException(GroupErrorCode.GROUP_NOT_FOUND));

        if (group.getStatus() != GroupStatus.ACTIVE) {
            throw new GroupException(GroupErrorCode.GROUP_INACTIVE);
        }
        if (group.isLocked()) {
            throw new GroupException(GroupErrorCode.GROUP_LOCKED);
        }

        long activeMemberCount = groupMemberRepository.countActiveMembersByGroupId(
                group.getId(), GroupMemberStatus.ACTIVE);
        List<Long> activeMemberIds = groupMemberRepository
                .findMemberIdsByGroupIdAndStatusOrderByJoinedAtAscIdAsc(
                        group.getId(), GroupMemberStatus.ACTIVE);
        Map<Long, GroupResDTO.Avatar> avatarsByMemberId = GroupConverter.toAvatarsByMemberId(
                activeMemberIds,
                activeMemberIds.isEmpty()
                        ? List.of()
                        : memberAvatarEquipmentRepository
                                .findAllByMemberIdInWithMemberAndAvatarItem(activeMemberIds)
        );
        long totalRoutineCount = groupRoutineRepository.countByGroupIdAndActiveTrue(group.getId());
        GroupJoinUnavailableReason unavailableReason = findUnavailableReason(
                group, member, activeMemberCount);
        boolean joinable = unavailableReason == null;

        log.debug("초대코드 기반 그룹 참여 Preview를 조회했습니다. groupId={}, memberId={}, "
                        + "activeMemberCount={}, totalRoutineCount={}, joinable={}, unavailableReason={}",
                group.getId(), member.getId(), activeMemberCount, totalRoutineCount,
                joinable, unavailableReason);
        return GroupConverter.toJoinPreview(
                group,
                activeMemberCount,
                totalRoutineCount,
                activeMemberIds,
                avatarsByMemberId,
                joinable,
                unavailableReason
        );
    }

    private GroupJoinUnavailableReason findUnavailableReason(
            Group group,
            Member member,
            long activeMemberCount
    ) {
        GroupMemberStatus membershipStatus = groupMemberRepository
                .findByGroupIdAndMemberId(group.getId(), member.getId())
                .map(GroupMember::getStatus)
                .orElse(null);
        if (membershipStatus == GroupMemberStatus.ACTIVE) {
            return GroupJoinUnavailableReason.ALREADY_ACTIVE_MEMBER;
        }
        if (membershipStatus == GroupMemberStatus.KICKED) {
            return GroupJoinUnavailableReason.KICKED_MEMBER;
        }

        long activeGroupCount = groupMemberRepository.countByMemberIdAndStatusAndGroupStatus(
                member.getId(), GroupMemberStatus.ACTIVE, GroupStatus.ACTIVE);
        if (activeGroupCount >= GroupMember.MAX_ACTIVE_GROUP_COUNT) {
            return GroupJoinUnavailableReason.MEMBER_GROUP_LIMIT_REACHED;
        }
        if (activeMemberCount >= GroupMember.MAX_ACTIVE_MEMBER_COUNT_PER_GROUP) {
            return GroupJoinUnavailableReason.GROUP_MEMBER_LIMIT_REACHED;
        }
        return null;
    }

    private String normalizeInviteCode(String inviteCode) {
        return inviteCode == null ? "" : inviteCode.trim().toUpperCase(Locale.ROOT);
    }
}
