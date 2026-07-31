package com.lirouti.domain.group.service.command;

import com.lirouti.domain.group.dto.response.GroupResDTO;
import com.lirouti.domain.group.exception.GroupException;
import com.lirouti.domain.group.exception.code.error.GroupErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class GroupInviteCodeCommandService {
    private static final int MAX_ISSUE_ATTEMPTS = 10;
    private static final String INVITE_CODE_UNIQUE_CONSTRAINT = "UKmgt3kl7whp0n031hlo8x6jupi";

    private final GroupInviteCodeIssueAttemptService issueAttemptService;

    @Transactional
    public GroupResDTO.InviteCode issueInviteCode(Long groupId, Long memberId) {
        for (int attempt = 0; attempt < MAX_ISSUE_ATTEMPTS; attempt++) {
            try {
                GroupResDTO.InviteCode result = issueAttemptService.issueOnce(groupId, memberId);
                log.info("그룹 초대코드 발급을 완료했습니다. groupId={}, memberId={}, expiresAt={}",
                        groupId, memberId, result.expiresAt());
                return result;
            } catch (DataIntegrityViolationException exception) {
                if (!isInviteCodeUniqueViolation(exception)) {
                    log.warn("초대코드 저장 중 초대코드 외 무결성 제약을 위반했습니다. "
                                    + "groupId={}, memberId={}",
                            groupId, memberId, exception);
                    throw issueFailedException();
                }

                log.warn("초대코드 unique 충돌이 발생해 재시도합니다. "
                                + "groupId={}, memberId={}, attempt={}",
                        groupId, memberId, attempt + 1);
            }
        }

        log.error("초대코드 unique 충돌로 최대 재시도 횟수를 초과했습니다. "
                        + "groupId={}, memberId={}, maxAttempts={}",
                groupId, memberId, MAX_ISSUE_ATTEMPTS);
        throw issueFailedException();
    }

    // 초대코드 unique 제약 위반 여부를 확인한다.
    private boolean isInviteCodeUniqueViolation(DataIntegrityViolationException exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof ConstraintViolationException constraintViolationException
                    && constraintViolationException.getKind()
                    == ConstraintViolationException.ConstraintKind.UNIQUE) {
                return isInviteCodeConstraint(constraintViolationException.getConstraintName());
            }
            cause = cause.getCause();
        }
        return false;
    }

    private boolean isInviteCodeConstraint(String constraintName) {
        return INVITE_CODE_UNIQUE_CONSTRAINT.equals(constraintName)
                || constraintName != null
                && constraintName.endsWith("." + INVITE_CODE_UNIQUE_CONSTRAINT);
    }

    private GroupException issueFailedException() {
        return new GroupException(GroupErrorCode.INVITE_CODE_ISSUE_FAILED);
    }
}
