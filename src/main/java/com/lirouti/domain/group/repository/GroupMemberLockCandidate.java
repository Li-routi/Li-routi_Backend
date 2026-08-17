package com.lirouti.domain.group.repository;

/** GroupMember 행 잠금 전에 회원 ID와 고정 잠금 순서를 함께 전달한다. */
public record GroupMemberLockCandidate(
        Long memberId,
        Long groupMemberId
) {
}
