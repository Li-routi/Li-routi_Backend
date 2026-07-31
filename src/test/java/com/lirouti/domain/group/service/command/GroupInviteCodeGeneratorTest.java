package com.lirouti.domain.group.service.command;

import com.lirouti.domain.group.exception.GroupException;
import com.lirouti.domain.group.exception.code.error.GroupErrorCode;
import com.lirouti.domain.group.repository.GroupRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("GroupInviteCodeGenerator 테스트")
class GroupInviteCodeGeneratorTest {
    private static final LocalDateTime ISSUED_AT = LocalDateTime.of(2026, 7, 29, 10, 0);

    @Mock
    private GroupRepository groupRepository;
    @Mock
    private Clock clock;

    @InjectMocks
    private GroupInviteCodeGenerator inviteCodeGenerator;

    @Test
    @DisplayName("중복되지 않는 7자리 코드와 주입된 Clock 기준 10분 만료 시각을 생성한다")
    void generate_AvailableCandidate_ReturnsCodeAndExpiration() {
        // given
        givenClock();
        when(groupRepository.existsByInviteCode(anyString())).thenReturn(false);

        // when
        GroupInviteCodeGenerator.GeneratedInviteCode result = inviteCodeGenerator.generate();

        // then
        assertThat(result.value()).hasSize(7).matches("[A-Z0-9]{7}");
        assertThat(result.expiresAt()).isEqualTo(ISSUED_AT.plusMinutes(10));
    }

    @Test
    @DisplayName("사전 중복 확인에서 충돌하면 다음 후보를 생성한다")
    void generate_PrecheckFindsDuplicate_UsesNextCandidate() {
        // given
        givenClock();
        when(groupRepository.existsByInviteCode(anyString())).thenReturn(true, false);

        // when
        inviteCodeGenerator.generate();

        // then
        verify(groupRepository, times(2)).existsByInviteCode(anyString());
    }

    @Test
    @DisplayName("후보가 최대 횟수까지 중복이면 발급 실패를 반환한다")
    void generate_PrecheckAlwaysFindsDuplicate_ThrowsIssueFailed() {
        // given
        when(groupRepository.existsByInviteCode(anyString())).thenReturn(true);

        // when & then
        assertThatThrownBy(inviteCodeGenerator::generate)
                .isInstanceOf(GroupException.class)
                .extracting("code")
                .isEqualTo(GroupErrorCode.INVITE_CODE_ISSUE_FAILED);
        verify(groupRepository, times(10)).existsByInviteCode(anyString());
    }

    private void givenClock() {
        when(clock.instant()).thenReturn(Instant.parse("2026-07-29T01:00:00Z"));
        when(clock.getZone()).thenReturn(ZoneId.of("Asia/Seoul"));
    }
}
