package com.lirouti.domain.group.service.command;

import com.lirouti.domain.group.dto.response.GroupResDTO;
import com.lirouti.domain.group.exception.GroupException;
import com.lirouti.domain.group.exception.code.error.GroupErrorCode;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("GroupInviteCodeCommandService 테스트")
class GroupInviteCodeCommandServiceTest {
    private static final Long GROUP_ID = 10L;
    private static final Long OWNER_ID = 1L;
    private static final String INVITE_CODE_UNIQUE_CONSTRAINT = "UKmgt3kl7whp0n031hlo8x6jupi";

    @Mock
    private GroupInviteCodeIssueAttemptService issueAttemptService;
    @Mock
    private GroupInviteCodeUniqueViolationDetector uniqueViolationDetector;

    @InjectMocks
    private GroupInviteCodeCommandService groupInviteCodeCommandService;

    @Test
    @DisplayName("unique 충돌 후 다음 발급 시도가 성공하면 성공 결과를 반환한다")
    void issueInviteCode_UniqueConflictThenSuccess_RetriesAndReturnsResult() {
        // given
        GroupResDTO.InviteCode result = inviteCodeResult("NEW1234");
        when(issueAttemptService.issueOnce(GROUP_ID, OWNER_ID))
                .thenThrow(inviteCodeUniqueViolation())
                .thenReturn(result);
        when(uniqueViolationDetector.isInviteCodeUniqueViolation(any()))
                .thenReturn(true);

        // when
        GroupResDTO.InviteCode actual = groupInviteCodeCommandService
                .issueInviteCode(GROUP_ID, OWNER_ID);

        // then
        assertThat(actual).isEqualTo(result);
        verify(issueAttemptService, times(2)).issueOnce(GROUP_ID, OWNER_ID);
    }

    @Test
    @DisplayName("초대코드 외 무결성 오류는 재시도하지 않고 발급 실패를 반환한다")
    void issueInviteCode_NonUniqueIntegrityViolation_ThrowsIssueFailedWithoutRetry() {
        // given
        when(issueAttemptService.issueOnce(GROUP_ID, OWNER_ID))
                .thenThrow(new DataIntegrityViolationException("not-null violation"));

        // when & then
        assertThatThrownBy(() -> groupInviteCodeCommandService
                .issueInviteCode(GROUP_ID, OWNER_ID))
                .isInstanceOf(GroupException.class)
                .extracting("code")
                .isEqualTo(GroupErrorCode.INVITE_CODE_ISSUE_FAILED);
        verify(issueAttemptService).issueOnce(GROUP_ID, OWNER_ID);
    }

    @Test
    @DisplayName("unique 충돌이 최대 시도 횟수까지 계속되면 발급 실패를 반환한다")
    void issueInviteCode_UniqueConflictUntilLimit_ThrowsIssueFailed() {
        // given
        when(issueAttemptService.issueOnce(GROUP_ID, OWNER_ID))
                .thenThrow(inviteCodeUniqueViolation());
        when(uniqueViolationDetector.isInviteCodeUniqueViolation(any()))
                .thenReturn(true);

        // when & then
        assertThatThrownBy(() -> groupInviteCodeCommandService
                .issueInviteCode(GROUP_ID, OWNER_ID))
                .isInstanceOf(GroupException.class)
                .extracting("code")
                .isEqualTo(GroupErrorCode.INVITE_CODE_ISSUE_FAILED);
        verify(issueAttemptService, times(10)).issueOnce(GROUP_ID, OWNER_ID);
    }

    @Test
    @DisplayName("발급 시도 서비스의 권한 오류는 재시도하지 않고 전파한다")
    void issueInviteCode_AttemptServiceThrowsDomainException_PropagatesWithoutRetry() {
        // given
        GroupException exception = new GroupException(GroupErrorCode.GROUP_OWNER_ACCESS_DENIED);
        when(issueAttemptService.issueOnce(GROUP_ID, OWNER_ID)).thenThrow(exception);

        // when & then
        assertThatThrownBy(() -> groupInviteCodeCommandService
                .issueInviteCode(GROUP_ID, OWNER_ID))
                .isSameAs(exception);
        verify(issueAttemptService).issueOnce(GROUP_ID, OWNER_ID);
    }

    private GroupResDTO.InviteCode inviteCodeResult(String inviteCode) {
        return GroupResDTO.InviteCode.builder()
                .inviteCode(inviteCode)
                .expiresAt(LocalDateTime.of(2026, 7, 29, 10, 10))
                .build();
    }

    private DataIntegrityViolationException inviteCodeUniqueViolation() {
        SQLException sqlException = new SQLException("duplicate invite code", "23000", 1062);
        ConstraintViolationException constraintViolationException = new ConstraintViolationException(
                "duplicate invite code",
                sqlException,
                ConstraintViolationException.ConstraintKind.UNIQUE,
                INVITE_CODE_UNIQUE_CONSTRAINT
        );
        return new DataIntegrityViolationException("duplicate invite code", constraintViolationException);
    }
}
