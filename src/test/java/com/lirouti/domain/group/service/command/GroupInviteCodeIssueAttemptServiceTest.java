package com.lirouti.domain.group.service.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import com.lirouti.domain.group.entity.Group;
import com.lirouti.domain.group.entity.GroupMember;
import com.lirouti.domain.group.dto.response.GroupResDTO;
import com.lirouti.domain.group.exception.GroupException;
import com.lirouti.domain.group.exception.code.error.GroupErrorCode;
import com.lirouti.domain.group.repository.GroupRepository;
import com.lirouti.domain.group.service.GroupValidationService;

@ExtendWith(MockitoExtension.class)
@DisplayName("GroupInviteCodeIssueAttemptService 테스트")
class GroupInviteCodeIssueAttemptServiceTest {
    private static final Long GROUP_ID = 10L;
    private static final Long OWNER_ID = 1L;
    private static final LocalDateTime ISSUED_AT = LocalDateTime.of(2026, 7, 29, 10, 0);

    @Mock
    private GroupValidationService groupValidationService;
    @Mock
    private GroupRepository groupRepository;
    @Mock
    private Clock clock;
    @Mock
    private GroupMember ownerMembership;

    @InjectMocks
    private GroupInviteCodeIssueAttemptService issueAttemptService;

    private Group group;

    @BeforeEach
    void setUp() {
        group = Group.builder()
                .name("테스트 그룹")
                .inviteCode("OLD1234")
                .build();
    }

    @Test
    @DisplayName("방장이 발급을 시도하면 새 코드와 10분 후 말소 시각을 저장한다")
    void issueOnce_Owner_SavesCodeAndExpiration() {
        // given
        givenOwnerGroup();

        // when
        GroupResDTO.InviteCode result = issueAttemptService.issueOnce(GROUP_ID, OWNER_ID);

        // then
        assertThat(result.inviteCode())
                .hasSize(7)
                .matches("[A-Z0-9]{7}");
        assertThat(result.expiresAt()).isEqualTo(ISSUED_AT.plusMinutes(10));
        assertThat(group.getInviteCode()).isEqualTo(result.inviteCode());
        assertThat(group.getInviteCodeExpiresAt()).isEqualTo(result.expiresAt());
        verify(groupRepository).saveAndFlush(group);
    }

    @Test
    @DisplayName("사전 중복 확인에서 충돌하면 다음 후보를 생성한다")
    void issueOnce_PrecheckFindsDuplicate_UsesNextCandidate() {
        // given
        givenOwnerGroup();
        when(groupRepository.existsByInviteCode(anyString())).thenReturn(true, false);

        // when
        GroupResDTO.InviteCode result = issueAttemptService.issueOnce(GROUP_ID, OWNER_ID);

        // then
        assertThat(result.inviteCode()).hasSize(7);
        verify(groupRepository, times(2)).existsByInviteCode(anyString());
        verify(groupRepository).saveAndFlush(group);
    }

    @Test
    @DisplayName("후보가 최대 횟수까지 중복이면 발급 실패를 반환한다")
    void issueOnce_PrecheckAlwaysFindsDuplicate_ThrowsIssueFailed() {
        // given
        when(groupValidationService.validateGroupOwner(GROUP_ID, OWNER_ID))
                .thenReturn(ownerMembership);
        when(ownerMembership.getGroup()).thenReturn(group);
        when(groupRepository.existsByInviteCode(anyString())).thenReturn(true);

        // when & then
        assertThatThrownBy(() -> issueAttemptService.issueOnce(GROUP_ID, OWNER_ID))
                .isInstanceOf(GroupException.class)
                .extracting("code")
                .isEqualTo(GroupErrorCode.INVITE_CODE_ISSUE_FAILED);
        verify(groupRepository, never()).saveAndFlush(group);
    }

    @Test
    @DisplayName("DB 무결성 오류는 호출자에게 전파한다")
    void issueOnce_SaveFails_PropagatesDataIntegrityViolation() {
        // given
        givenOwnerGroup();
        DataIntegrityViolationException exception =
                new DataIntegrityViolationException("duplicate invite code");
        when(groupRepository.saveAndFlush(group)).thenThrow(exception);

        // when & then
        assertThatThrownBy(() -> issueAttemptService.issueOnce(GROUP_ID, OWNER_ID))
                .isSameAs(exception);
    }

    @Test
    @DisplayName("방장 검증에 실패하면 저장을 시도하지 않는다")
    void issueOnce_NotOwner_PropagatesExceptionWithoutSave() {
        // given
        GroupException exception = new GroupException(GroupErrorCode.GROUP_OWNER_ACCESS_DENIED);
        when(groupValidationService.validateGroupOwner(GROUP_ID, OWNER_ID))
                .thenThrow(exception);

        // when & then
        assertThatThrownBy(() -> issueAttemptService.issueOnce(GROUP_ID, OWNER_ID))
                .isSameAs(exception);
        verify(groupRepository, never()).existsByInviteCode(anyString());
        verify(groupRepository, never()).saveAndFlush(group);
    }

    private void givenOwnerGroup() {
        when(clock.instant()).thenReturn(Instant.parse("2026-07-29T01:00:00Z"));
        when(clock.getZone()).thenReturn(ZoneId.of("Asia/Seoul"));
        when(groupValidationService.validateGroupOwner(GROUP_ID, OWNER_ID))
                .thenReturn(ownerMembership);
        when(ownerMembership.getGroup()).thenReturn(group);
        when(groupRepository.existsByInviteCode(anyString())).thenReturn(false);
    }
}
