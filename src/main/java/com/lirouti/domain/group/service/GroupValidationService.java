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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 그룹 기능에서 공통으로 사용하는 구성원 및 방장 권한 검증 서비스다.
 *
 * <p>Controller는 인증 객체의 {@code CustomUserDetails.memberId}와 요청의 groupId를 전달하고,
 * 그룹 루틴 등 하위 기능은 이 서비스의 동일한 접근 제어 정책을 재사용한다.</p>
 *
 * <p>검증된 {@link GroupMember}를 반환하는 이유는 후속 서비스가 동일 참여 관계를 다시 조회하지 않고
 * 필요한 그룹별 role과 상태를 사용할 수 있게 하기 위함이다. Controller에 Entity를 직접 반환하는
 * 용도가 아니다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GroupValidationService {
    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final MemberRepository memberRepository;

    /**
     * 로그인 회원이 요청 대상 그룹의 ACTIVE 구성원인지 검증한다.
     */
    @Transactional(readOnly = true)
    public GroupMember validateActiveGroupMember(Long groupId, Long memberId) {
        return getValidatedGroupMember(groupId, memberId);
    }

    /**
     * ACTIVE 구성원 검증 후 해당 그룹에서 OWNER인지 추가로 검증한다.
     * 다른 그룹의 OWNER 권한은 현재 요청 대상 그룹에 영향을 주지 않는다.
     */
    @Transactional(readOnly = true)
    public GroupMember validateGroupOwner(Long groupId, Long memberId) {
        GroupMember groupMember = getValidatedGroupMember(groupId, memberId);
        if (groupMember.getRole() != GroupMemberRole.OWNER) {
            log.warn("그룹 방장 권한 검증에 실패했습니다. groupId={}, memberId={}, role={}",
                    groupId, memberId, groupMember.getRole());
            throw new GroupException(GroupErrorCode.GROUP_OWNER_ACCESS_DENIED);
        }
        return groupMember;
    }

    /**
     * 회원과 그룹 자체가 유효한지 확인한 뒤 두 ID의 참여 관계를 조회한다.
     * 실패 로그는 전역 예외 로그에 없는 groupId/memberId 맥락만 보완한다.
     */
    private GroupMember getValidatedGroupMember(Long groupId, Long memberId) {
        Member member = getActiveMember(memberId);
        Group group = getActiveGroup(groupId);

        GroupMember groupMember = groupMemberRepository
                .findByGroupIdAndMemberId(group.getId(), member.getId())
                .orElseThrow(() -> {
                    log.warn("그룹 구성원 조회에 실패했습니다. groupId={}, memberId={}",
                            groupId, memberId);
                    return new GroupException(GroupErrorCode.GROUP_MEMBER_ACCESS_DENIED);
                });

        if (groupMember.getStatus() != GroupMemberStatus.ACTIVE) {
            log.warn("활성 상태가 아닌 그룹 구성원의 접근을 거부했습니다. "
                            + "groupId={}, memberId={}, status={}",
                    groupId, memberId, groupMember.getStatus());
            throw new GroupException(GroupErrorCode.GROUP_MEMBER_ACCESS_DENIED);
        }
        return groupMember;
    }

    /**
     * JWT에 담긴 ID만 신뢰해 권한을 부여하지 않고 현재 DB의 회원 상태를 다시 확인한다.
     */
    private Member getActiveMember(Long memberId) {
        if (memberId == null) {
            log.warn("로그인 회원 검증에 실패했습니다. memberId가 없습니다.");
            throw new MemberException(MemberErrorCode.MEMBER_NOT_FOUND);
        }

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> {
                    log.warn("로그인 회원 조회에 실패했습니다. memberId={}", memberId);
                    return new MemberException(MemberErrorCode.MEMBER_NOT_FOUND);
                });

        if (!Boolean.TRUE.equals(member.getIsActive()) || member.getDeletedAt() != null) {
            log.warn("탈퇴하거나 비활성화된 회원의 그룹 접근을 차단했습니다. memberId={}", memberId);
            throw new MemberException(MemberErrorCode.WITHDRAWN_MEMBER);
        }
        return member;
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

        if (group.getStatus() != GroupStatus.ACTIVE) {
            log.warn("비활성 그룹에 대한 접근을 차단했습니다. groupId={}, status={}",
                    groupId, group.getStatus());
            throw new GroupException(GroupErrorCode.GROUP_INACTIVE);
        }
        return group;
    }
}
