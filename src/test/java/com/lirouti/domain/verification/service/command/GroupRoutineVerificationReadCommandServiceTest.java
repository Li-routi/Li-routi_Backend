package com.lirouti.domain.verification.service.command;

import com.lirouti.domain.group.entity.GroupMember;
import com.lirouti.domain.group.exception.GroupException;
import com.lirouti.domain.group.exception.code.error.GroupErrorCode;
import com.lirouti.domain.group.service.GroupValidationService;
import com.lirouti.domain.verification.dto.response.VerificationResDTO;
import com.lirouti.domain.verification.repository.GroupRoutineVerificationReadRepository;
import com.lirouti.domain.verification.repository.GroupRoutineVerificationRereadRepository;
import com.lirouti.domain.verification.repository.GroupRoutineVerificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GroupRoutineVerificationReadCommandServiceTest {
    private static final Long GROUP_ID = 1L;
    private static final Long MEMBER_ID = 2L;
    private static final Long VERIFICATION_ID = 10L;

    @Mock private GroupValidationService groupValidationService;
    @Mock private GroupRoutineVerificationRepository verificationRepository;
    @Mock private GroupRoutineVerificationReadRepository readRepository;
    @Mock private GroupRoutineVerificationRereadRepository rereadRepository;
    @Mock private GroupMember membership;
    private GroupRoutineVerificationReadCommandService service;

    @BeforeEach
    void setUp() {
        service = new GroupRoutineVerificationReadCommandService(
                groupValidationService, verificationRepository, readRepository, rereadRepository,
                Clock.fixed(Instant.parse("2026-08-08T03:00:00Z"), ZoneId.of("Asia/Seoul")));
    }

    @Test
    void markRead_ActiveMember_UpsertsAndReturnsActualCursor() {
        when(groupValidationService.validateActiveGroupMember(GROUP_ID, MEMBER_ID)).thenReturn(membership);
        when(membership.getJoinedAt()).thenReturn(LocalDateTime.of(2026, 8, 8, 10, 0));
        when(verificationRepository.existsReadableByIdAndGroupIdAndViewerIdAndMembershipStartOfDay(
                VERIFICATION_ID, GROUP_ID, MEMBER_ID, LocalDateTime.of(2026, 8, 8, 0, 0))).thenReturn(true);
        when(readRepository.findLastReadVerificationIdByGroupIdAndMemberId(GROUP_ID, MEMBER_ID))
                .thenReturn(Optional.of(VERIFICATION_ID));

        VerificationResDTO.GroupRoutineVerificationRead result =
                service.markRead(MEMBER_ID, GROUP_ID, VERIFICATION_ID);

        verify(readRepository).upsertIfAhead(eq(GROUP_ID), eq(MEMBER_ID), eq(VERIFICATION_ID), any());
        verify(rereadRepository).deleteByGroupIdAndMemberIdAndVerificationId(
                GROUP_ID, MEMBER_ID, VERIFICATION_ID);
        assertThat(result.lastReadVerificationId()).isEqualTo(VERIFICATION_ID);
    }

    @Test
    void markRead_InactiveMember_StopsBeforeVerificationAndCursorAccess() {
        when(groupValidationService.validateActiveGroupMember(GROUP_ID, MEMBER_ID))
                .thenThrow(new GroupException(GroupErrorCode.GROUP_MEMBER_ACCESS_DENIED));

        assertThatThrownBy(() -> service.markRead(MEMBER_ID, GROUP_ID, VERIFICATION_ID))
                .isInstanceOf(GroupException.class);
        verifyNoInteractions(verificationRepository, readRepository, rereadRepository);
    }
}
