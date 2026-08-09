package com.lirouti.domain.group.service;

import com.lirouti.domain.group.entity.Group;
import com.lirouti.domain.group.entity.GroupMember;
import com.lirouti.domain.group.enums.GroupMemberRole;
import com.lirouti.domain.group.enums.GroupMemberStatus;
import com.lirouti.domain.group.enums.GroupStatus;
import com.lirouti.domain.group.exception.GroupException;
import com.lirouti.domain.group.exception.code.error.GroupErrorCode;
import com.lirouti.domain.group.repository.GroupMemberRepository;
import com.lirouti.domain.group.repository.GroupRepository;
import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.exception.MemberException;
import com.lirouti.domain.member.exception.code.error.MemberErrorCode;
import com.lirouti.domain.member.repository.MemberRepository;
import com.lirouti.domain.member.service.query.MemberQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 그룹 기능에서 공통으로 사용하는 구성원 및 방장 권한 검증 서비스다.
 * Controller는 인증 객체의 {@code CustomUserDetails.memberId}와 요청의 groupId를 전달하고,
 * 그룹 루틴 등 하위 기능은 이 서비스의 동일한 접근 제어 정책을 재사용한다.
 * 검증된 {@link GroupMember}를 반환하는 이유는 후속 서비스가 동일 참여 관계를 다시 조회하지 않고
 * 필요한 그룹별 role과 상태를 사용할 수 있게 하기 위함이다. Controller에 Entity를 직접 반환하는
 * 용도가 아니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GroupValidationService {
    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final MemberRepository memberRepository;
    private final MemberQueryService memberQueryService;

    /**
     * 회원 행을 잠근 뒤 역할과 관계없이 현재 참여 중인 활성 그룹 수를 검증한다.
     * 그룹 생성과 향후 가입 서비스는 이 메서드를 자신의 쓰기 트랜잭션 안에서 호출하고,
     * 반환된 회원으로 참여 관계를 저장해야 잠금이 저장 시점까지 유지된다.
     *
     * @param memberId 참여할 회원 ID
     * @return 잠금 및 활성 상태 검증을 통과한 회원
     * @throws GroupException 이미 활성 그룹에 6개 참여 중인 경우
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Member lockActiveMemberAndValidateParticipationLimit(Long memberId) {
        Member member = lockActiveMember(memberId);
        validateParticipationLimit(memberId);
        return member;
    }

    /**
     * 그룹 행을 잠근 뒤 현재 활성 계정인 ACTIVE 그룹원 수를 검증한다.
     * 향후 가입 서비스는 같은 쓰기 트랜잭션에서 이 메서드와 GroupMember 저장을 함께 수행해야 한다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Group lockActiveGroupAndValidateMemberLimit(Long groupId) {
        Group group = lockActiveGroup(groupId);
        validateGroupMemberLimit(groupId);
        return group;
    }

    /**
     * 가입 시 필요한 두 상한을 동시 요청에도 안전한 잠금 순서로 함께 검증한다.
     * 모든 잠금을 먼저 획득한 뒤 집계해야 REPEATABLE READ의 오래된 스냅샷을 피할 수 있다.
     * 향후 가입 API는 개별 검증 메서드를 조합하지 않고 이 메서드를 재사용한다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public JoinLimitContext lockAndValidateJoinLimits(Long groupId, Long memberId) {
        JoinLimitContext context = lockActiveGroupAndMemberForJoin(groupId, memberId);
        validateJoinLimits(groupId, memberId);
        return context;
    }

    /**
     * 가입 Command가 관계 상태를 검사하기 전에 그룹과 회원 행을 정해진 순서로 잠근다.
     * 상한은 관계 상태 검사 이후에 {@link #validateJoinLimits(Long, Long)}로 검증한다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public JoinLimitContext lockActiveGroupAndMemberForJoin(Long groupId, Long memberId) {
        Group group = lockActiveGroup(groupId);
        Member member = lockActiveMember(memberId);
        return new JoinLimitContext(group, member);
    }

    /** 이미 그룹과 회원 잠금을 획득한 가입 Command에서 두 참여 상한을 검사한다. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void validateJoinLimits(Long groupId, Long memberId) {
        validateGroupMemberLimit(groupId);
        validateParticipationLimit(memberId);
    }

    private Member lockActiveMember(Long memberId) {
        if (memberId == null) {
            log.warn("참여 상한 검증에 실패했습니다. memberId가 없습니다.");
            throw new MemberException(MemberErrorCode.MEMBER_NOT_FOUND);
        }

        Member member = memberRepository.findByIdForUpdate(memberId)
                .orElseThrow(() -> {
                    log.warn("참여 상한 검증 대상 회원을 찾을 수 없습니다. memberId={}", memberId);
                    return new MemberException(MemberErrorCode.MEMBER_NOT_FOUND);
                });
        if (!Boolean.TRUE.equals(member.getIsActive()) || member.getDeletedAt() != null) {
            log.warn("탈퇴하거나 비활성화된 회원의 그룹 참여를 차단했습니다. memberId={}", memberId);
            throw new MemberException(MemberErrorCode.WITHDRAWN_MEMBER);
        }
        return member;
    }

    private void validateParticipationLimit(Long memberId) {
        long activeGroupCount = groupMemberRepository.countByMemberIdAndStatusAndGroupStatus(
                memberId,
                GroupMemberStatus.ACTIVE,
                GroupStatus.ACTIVE
        );
        if (activeGroupCount >= GroupMember.MAX_ACTIVE_GROUP_COUNT) {
            log.warn("활성 그룹 참여 상한을 초과했습니다. memberId={}, activeGroupCount={}",
                    memberId, activeGroupCount);
            throw new GroupException(GroupErrorCode.GROUP_PARTICIPATION_LIMIT_EXCEEDED);
        }
    }

    /**
     * 그룹 행을 잠그고 현재 활성 상태인지 검증한다.
     * 루틴·카테고리 개수 검사부터 저장까지 그룹 단위로 직렬화하는 명령에서 사용한다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Group lockActiveGroupForUpdate(Long groupId) {
        return lockActiveGroup(groupId);
    }

    private Group lockActiveGroup(Long groupId) {
        if (groupId == null) {
            log.warn("그룹 잠금에 실패했습니다. groupId가 없습니다.");
            throw new GroupException(GroupErrorCode.GROUP_NOT_FOUND);
        }

        Group group = groupRepository.findByIdForUpdate(groupId)
                .orElseThrow(() -> {
                    log.warn("잠글 그룹을 찾을 수 없습니다. groupId={}", groupId);
                    return new GroupException(GroupErrorCode.GROUP_NOT_FOUND);
                });
        validateActiveGroup(groupId, group);
        return group;
    }

    private void validateGroupMemberLimit(Long groupId) {
        long activeMemberCount = groupMemberRepository.countActiveMembersByGroupId(
                groupId,
                GroupMemberStatus.ACTIVE
        );
        if (activeMemberCount >= GroupMember.MAX_ACTIVE_MEMBER_COUNT_PER_GROUP) {
            log.warn("활성 그룹원 상한을 초과했습니다. groupId={}, activeMemberCount={}",
                    groupId, activeMemberCount);
            throw new GroupException(GroupErrorCode.GROUP_MEMBER_LIMIT_EXCEEDED);
        }
    }

    /** 가입 상한 검증을 통과한 잠긴 그룹과 회원이다. */
    public record JoinLimitContext(Group group, Member member) {
    }

    /**
     * 로그인 회원이 요청 대상 그룹의 ACTIVE 구성원인지 검증한다.
     */
    @Transactional(readOnly = true)
    public GroupMember validateActiveGroupMember(Long groupId, Long memberId) {
        GroupMember groupMember = getValidatedGroupMember(groupId, memberId);
        log.debug("활성 그룹 구성원 검증을 완료했습니다. groupId={}, memberId={}",
                groupId, memberId);
        return groupMember;
    }

    /**
     * 이미 그룹 행을 잠근 명령에서 참여 관계까지 잠가 상태 변경을 직렬화한다.
     * 탈퇴·강퇴는 찌르기와 같은 GroupMember 행을 잠그므로 오래된 엔티티 상태가 카운터를 덮어쓰지 않는다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public GroupMember validateActiveGroupMemberForUpdate(Group group, Long memberId) {
        if (group == null) {
            throw new GroupException(GroupErrorCode.GROUP_NOT_FOUND);
        }
        validateActiveGroup(group.getId(), group);
        Member member = memberQueryService.getActiveMember(memberId);

        GroupMember groupMember = groupMemberRepository
                .findByGroupIdAndMemberIdForUpdate(group.getId(), member.getId())
                .orElseThrow(() -> new GroupException(GroupErrorCode.GROUP_MEMBER_ACCESS_DENIED));
        if (groupMember.getStatus() != GroupMemberStatus.ACTIVE) {
            throw new GroupException(GroupErrorCode.GROUP_MEMBER_ACCESS_DENIED);
        }
        return groupMember;
    }

    /**
     * ACTIVE 구성원 검증 후 해당 그룹에서 OWNER인지 추가로 검증한다.
     * 다른 그룹의 OWNER 권한은 현재 요청 대상 그룹에 영향을 주지 않는다.
     */
    @Transactional(readOnly = true)
    public GroupMember validateGroupOwner(Long groupId, Long memberId) {
        GroupMember groupMember = getValidatedGroupMember(groupId, memberId);
        return validateOwner(groupMember, groupId, memberId);
    }

    /**
     * 이미 잠금 획득이 끝난 그룹을 사용해 OWNER 권한을 검증한다.
     * 삭제처럼 호출부가 그룹 행을 먼저 잠가야 하는 명령에서 그룹을 다시 조회하지 않는다.
     */
    @Transactional(readOnly = true)
    public GroupMember validateGroupOwner(Group group, Long memberId) {
        if (group == null) {
            throw new GroupException(GroupErrorCode.GROUP_NOT_FOUND);
        }
        validateActiveGroup(group.getId(), group);
        Member member = memberQueryService.getActiveMember(memberId);
        GroupMember groupMember = getValidatedGroupMember(group, member);
        return validateOwner(groupMember, group.getId(), memberId);
    }

    private GroupMember validateOwner(
            GroupMember groupMember,
            Long groupId,
            Long memberId
    ) {
        if (groupMember.getRole() != GroupMemberRole.OWNER) {
            log.warn("그룹 방장 권한 검증에 실패했습니다. groupId={}, memberId={}, role={}",
                    groupId, memberId, groupMember.getRole());
            throw new GroupException(GroupErrorCode.GROUP_OWNER_ACCESS_DENIED);
        }
        log.debug("그룹 방장 권한 검증을 완료했습니다. groupId={}, memberId={}",
                groupId, memberId);
        return groupMember;
    }

    /**
     * 회원과 그룹 자체가 유효한지 확인한 뒤 두 ID의 참여 관계를 조회한다.
     * 실패 로그는 전역 예외 로그에 없는 groupId/memberId 맥락만 보완한다.
     */
    private GroupMember getValidatedGroupMember(Long groupId, Long memberId) {
        Member member = memberQueryService.getActiveMember(memberId);
        Group group = getActiveGroup(groupId);

        return getValidatedGroupMember(group, member);
    }

    private GroupMember getValidatedGroupMember(Group group, Member member) {

        GroupMember groupMember = groupMemberRepository
                .findByGroupIdAndMemberId(group.getId(), member.getId())
                .orElseThrow(() -> {
                    log.warn("그룹 구성원 조회에 실패했습니다. groupId={}, memberId={}",
                            group.getId(), member.getId());
                    return new GroupException(GroupErrorCode.GROUP_MEMBER_ACCESS_DENIED);
                });

        if (groupMember.getStatus() != GroupMemberStatus.ACTIVE) {
            log.warn("활성 상태가 아닌 그룹 구성원의 접근을 거부했습니다. "
                            + "groupId={}, memberId={}, status={}",
                    group.getId(), member.getId(), groupMember.getStatus());
            throw new GroupException(GroupErrorCode.GROUP_MEMBER_ACCESS_DENIED);
        }
        return groupMember;
    }

    /**
     * 삭제 상태의 그룹은 데이터가 남아 있어도 그룹 기능의 대상으로 취급하지 않는다.
     */
    private Group getActiveGroup(Long groupId) {
        if (groupId == null) {
            log.warn("그룹 검증에 실패했습니다. groupId가 없습니다.");
            throw new GroupException(GroupErrorCode.GROUP_NOT_FOUND);
        }

        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> {
                    log.warn("그룹 조회에 실패했습니다. groupId={}", groupId);
                    return new GroupException(GroupErrorCode.GROUP_NOT_FOUND);
                });

        validateActiveGroup(groupId, group);
        return group;
    }

    private void validateActiveGroup(Long groupId, Group group) {
        if (group.getStatus() != GroupStatus.ACTIVE) {
            log.warn("비활성 그룹에 대한 접근을 차단했습니다. groupId={}, status={}",
                    groupId, group.getStatus());
            throw new GroupException(GroupErrorCode.GROUP_INACTIVE);
        }
    }
}
