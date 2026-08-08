package com.lirouti.domain.verification.service.command;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lirouti.domain.group.service.GroupValidationService;
import com.lirouti.domain.group.entity.GroupMember;
import com.lirouti.domain.group.repository.GroupMemberRepository;
import com.lirouti.domain.group.service.command.GroupMemberActivityCommandService;
import com.lirouti.domain.verification.entity.GroupRoutineVerification;
import com.lirouti.domain.verification.entity.GroupRoutineVerificationLike;
import com.lirouti.domain.verification.converter.VerificationConverter;
import com.lirouti.domain.verification.dto.response.VerificationResDTO;
import com.lirouti.domain.verification.exception.VerificationException;
import com.lirouti.domain.verification.exception.code.error.VerificationErrorCode;
import com.lirouti.domain.verification.repository.GroupRoutineVerificationLikeRepository;
import com.lirouti.domain.verification.repository.GroupRoutineVerificationRepository;

import lombok.RequiredArgsConstructor;

/** 그룹 루틴 인증 게시물 좋아요의 권한·소속 검증과 멱등 쓰기 트랜잭션. */
@Service
@RequiredArgsConstructor
public class GroupRoutineVerificationLikeCommandService {
    private final GroupValidationService groupValidationService;
    private final GroupMemberActivityCommandService groupMemberActivityCommandService;
    private final GroupMemberRepository groupMemberRepository;
    private final GroupRoutineVerificationRepository groupRoutineVerificationRepository;
    private final GroupRoutineVerificationLikeRepository groupRoutineVerificationLikeRepository;

    @Transactional
    public VerificationResDTO.GroupRoutineLike like(
            Long memberId,
            Long groupId,
            Long verificationId
    ) {
        GroupRoutineVerification verification = validateLikeTarget(memberId, groupId, verificationId);
        int inserted = groupRoutineVerificationLikeRepository.insertIfAbsent(verificationId, memberId);
        if (inserted == 1) {
            AuthorVerificationContext author = authorContext(verification);
            GroupMember authorMembership = lockAuthorMembership(groupId, author.memberId());
            groupMemberRepository.incrementTotalLikeCountForCurrentActiveMembership(
                    authorMembership.getId(), author.assignmentId());
        }
        return buildResult(verificationId, true);
    }

    @Transactional
    public VerificationResDTO.GroupRoutineLike unlike(
            Long memberId,
            Long groupId,
        Long verificationId
    ) {
        GroupRoutineVerification verification = validateLikeTarget(memberId, groupId, verificationId);
        GroupRoutineVerificationLike existingLike = groupRoutineVerificationLikeRepository
                .findByVerificationIdAndMemberIdForUpdate(verificationId, memberId)
                .orElse(null);
        if (existingLike != null) {
            AuthorVerificationContext author = authorContext(verification);
            if (groupRoutineVerificationLikeRepository.deleteLike(verificationId, memberId) != 1) {
                return buildResult(verificationId, false);
            }
            GroupMember authorMembership = lockAuthorMembership(groupId, author.memberId());
            groupMemberRepository.decrementTotalLikeCountForCurrentActiveMembershipIfPositive(
                    authorMembership.getId(), author.assignmentId());
        }
        return buildResult(verificationId, false);
    }

    /**
     * 탈퇴·강제 퇴장과 같은 그룹 행 잠금을 먼저 획득한 뒤 ACTIVE 참여 관계를 검증한다.
     * 그룹 명령도 같은 순서(그룹 잠금 → 구성원 검증)를 쓰므로, 검증 뒤 새 Like 행이 생기는
     * 시간 창이 없다. 인증 소속 조회는 그 뒤에 하며 추가 잠금을 잡지 않는다.
     */
    private GroupRoutineVerification validateLikeTarget(
            Long memberId,
            Long groupId,
            Long verificationId
    ) {
        groupValidationService.lockActiveGroupForUpdate(groupId);
        groupValidationService.validateActiveGroupMember(groupId, memberId);
        return groupRoutineVerificationRepository.findByIdAndGroupId(verificationId, groupId)
                .orElseThrow(() -> new VerificationException(
                        VerificationErrorCode.GROUP_ROUTINE_VERIFICATION_NOT_FOUND));
    }

    private AuthorVerificationContext authorContext(GroupRoutineVerification verification) {
        return new AuthorVerificationContext(
                verification.getAssignment().getMember().getId(),
                verification.getAssignment().getId()
        );
    }

    private GroupMember lockAuthorMembership(Long groupId, Long authorMemberId) {
        return groupMemberActivityCommandService.lockMembership(groupId, authorMemberId);
    }

    private record AuthorVerificationContext(Long memberId, Long assignmentId) {
    }

    private VerificationResDTO.GroupRoutineLike buildResult(Long verificationId, boolean liked) {
        long likeCount = groupRoutineVerificationLikeRepository
                .countByVerificationIds(List.of(verificationId))
                .getOrDefault(verificationId, 0L);
        return VerificationConverter.toGroupRoutineLike(verificationId, likeCount, liked);
    }
}
