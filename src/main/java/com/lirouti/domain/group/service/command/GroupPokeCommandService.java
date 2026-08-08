package com.lirouti.domain.group.service.command;

import com.lirouti.domain.group.dto.response.GroupResDTO;
import com.lirouti.domain.group.entity.GroupMember;
import com.lirouti.domain.group.enums.GroupMemberStatus;
import com.lirouti.domain.group.exception.GroupException;
import com.lirouti.domain.group.exception.code.error.GroupErrorCode;
import com.lirouti.domain.group.repository.GroupMemberRepository;
import com.lirouti.domain.group.repository.GroupMemberLockCandidate;
import com.lirouti.domain.group.service.GroupValidationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 그룹 구성원 찌르기의 권한 검증과 누적 카운터 증가를 처리한다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class GroupPokeCommandService {
    private final GroupValidationService groupValidationService;
    private final GroupMemberRepository groupMemberRepository;

    /**
     * 요청자와 대상 참여 관계만 GroupMember ID 순서로 잠근 뒤 대상의 누적 찌르기 수를 증가시킨다.
     * 그룹 행은 잠그지 않아 서로 다른 구성원 쌍의 요청은 병렬로 처리할 수 있다.
     */
    @Transactional
    public GroupResDTO.PokeResult poke(Long groupId, Long requesterMemberId, Long targetMemberId) {
        groupValidationService.validateActiveGroupMember(groupId, requesterMemberId);
        if (requesterMemberId.equals(targetMemberId)) {
            throw new GroupException(GroupErrorCode.CANNOT_POKE_SELF);
        }

        List<GroupMemberLockCandidate> candidates = groupMemberRepository
                .findActiveMembershipLockCandidatesByGroupIdAndMemberIds(
                        groupId,
                        List.of(requesterMemberId, targetMemberId)
                );
        Map<Long, GroupMember> membershipsByMemberId = candidates.stream()
                .map(candidate -> groupMemberRepository.findByIdForUpdate(candidate.groupMemberId())
                        .orElse(null))
                .filter(java.util.Objects::nonNull)
                .filter(membership -> membership.getStatus() == GroupMemberStatus.ACTIVE)
                .collect(Collectors.toMap(
                        membership -> membership.getMember().getId(),
                        Function.identity()
                ));

        if (!membershipsByMemberId.containsKey(requesterMemberId)) {
            throw new GroupException(GroupErrorCode.GROUP_MEMBER_ACCESS_DENIED);
        }
        GroupMember targetMembership = membershipsByMemberId.get(targetMemberId);
        if (targetMembership == null) {
            throw new GroupException(GroupErrorCode.ACTIVE_GROUP_MEMBER_NOT_FOUND);
        }

        targetMembership.increaseTotalPokeCount();
        log.info("그룹 구성원을 찔렀습니다. groupId={}, requesterMemberId={}, targetMemberId={}, totalPokeCount={}",
                groupId, requesterMemberId, targetMemberId, targetMembership.getTotalPokeCount());
        return new GroupResDTO.PokeResult(targetMemberId, targetMembership.getTotalPokeCount());
    }
}
