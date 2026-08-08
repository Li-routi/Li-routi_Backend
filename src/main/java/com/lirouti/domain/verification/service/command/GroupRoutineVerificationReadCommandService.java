package com.lirouti.domain.verification.service.command;

import com.lirouti.domain.group.entity.GroupMember;
import com.lirouti.domain.group.service.GroupValidationService;
import com.lirouti.domain.verification.dto.response.VerificationResDTO;
import com.lirouti.domain.verification.exception.VerificationException;
import com.lirouti.domain.verification.exception.code.error.VerificationErrorCode;
import com.lirouti.domain.verification.repository.GroupRoutineVerificationReadRepository;
import com.lirouti.domain.verification.repository.GroupRoutineVerificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

/** 실제로 확인한 인증까지만 그룹별 읽음 커서를 단조 증가시킨다. */
@Service
@RequiredArgsConstructor
public class GroupRoutineVerificationReadCommandService {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private final GroupValidationService groupValidationService;
    private final GroupRoutineVerificationRepository groupRoutineVerificationRepository;
    private final GroupRoutineVerificationReadRepository groupRoutineVerificationReadRepository;
    private final Clock clock;

    @Transactional
    public VerificationResDTO.GroupRoutineVerificationRead markRead(
            Long memberId,
            Long groupId,
            Long verificationId
    ) {
        GroupMember membership = groupValidationService.validateActiveGroupMember(groupId, memberId);
        LocalDateTime membershipStartOfDay = membership.getJoinedAt()
                .atZone(KST).toLocalDate().atStartOfDay(KST).toLocalDateTime();
        if (!groupRoutineVerificationRepository
                .existsReadableByIdAndGroupIdAndViewerIdAndMembershipStartOfDay(
                        verificationId, groupId, memberId, membershipStartOfDay)) {
            throw new VerificationException(VerificationErrorCode.GROUP_ROUTINE_VERIFICATION_NOT_FOUND);
        }

        groupRoutineVerificationReadRepository.upsertIfAhead(
                groupId, memberId, verificationId, LocalDateTime.now(clock));
        Long lastReadVerificationId = groupRoutineVerificationReadRepository
                .findLastReadVerificationIdByGroupIdAndMemberId(groupId, memberId)
                .orElseThrow(() -> new IllegalStateException("그룹 루틴 인증 읽음 위치 저장에 실패했습니다."));
        return new VerificationResDTO.GroupRoutineVerificationRead(lastReadVerificationId);
    }
}
