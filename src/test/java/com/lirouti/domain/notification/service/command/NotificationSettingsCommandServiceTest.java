package com.lirouti.domain.notification.service.command;

import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.enums.Role;
import com.lirouti.domain.member.enums.SocialProvider;
import com.lirouti.domain.member.repository.MemberRepository;
import com.lirouti.domain.notification.dto.request.NotificationReqDTO;
import com.lirouti.domain.notification.dto.response.NotificationResDTO;
import com.lirouti.domain.notification.repository.FcmDeviceRepository;
import com.lirouti.domain.notification.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationCommandService 알림 설정 변경 테스트")
class NotificationSettingsCommandServiceTest {
    private static final Long MEMBER_ID = 1L;

    @Mock
    private FcmDeviceRepository fcmDeviceRepository;
    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private MemberRepository memberRepository;

    private NotificationCommandService commandService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-11T03:00:00Z"), ZoneOffset.UTC);
        commandService = new NotificationCommandService(
                fcmDeviceRepository,
                notificationRepository,
                memberRepository,
                clock
        );
    }

    @Test
    @DisplayName("PATCH에 포함된 설정만 바꾸고 나머지는 기존 값을 유지한다")
    void updateSettings_PartialRequest_PreservesOmittedValues() {
        Member member = member();
        when(memberRepository.findByIdForUpdate(MEMBER_ID)).thenReturn(Optional.of(member));
        NotificationReqDTO.UpdateSettings request = new NotificationReqDTO.UpdateSettings(
                null, null, null, null, false, null
        );

        NotificationResDTO.Settings result = commandService.updateSettings(MEMBER_ID, request);

        assertThat(result.routineDeadlineEnabled()).isTrue();
        assertThat(result.newVerificationEnabled()).isTrue();
        assertThat(result.verificationReactionEnabled()).isTrue();
        assertThat(result.pokeEnabled()).isTrue();
        assertThat(result.newChatEnabled()).isFalse();
        assertThat(result.likeEnabled()).isTrue();
    }

    @Test
    @DisplayName("빈 PATCH 요청은 설정을 변경하지 않고 현재 상태를 반환한다")
    void updateSettings_EmptyRequest_ReturnsCurrentSettings() {
        Member member = member();
        when(memberRepository.findByIdForUpdate(MEMBER_ID)).thenReturn(Optional.of(member));
        NotificationReqDTO.UpdateSettings request = new NotificationReqDTO.UpdateSettings(
                null, null, null, null, null, null
        );

        NotificationResDTO.Settings result = commandService.updateSettings(MEMBER_ID, request);

        assertThat(result)
                .extracting(
                        NotificationResDTO.Settings::routineDeadlineEnabled,
                        NotificationResDTO.Settings::newVerificationEnabled,
                        NotificationResDTO.Settings::verificationReactionEnabled,
                        NotificationResDTO.Settings::pokeEnabled,
                        NotificationResDTO.Settings::newChatEnabled,
                        NotificationResDTO.Settings::likeEnabled
                )
                .containsExactly(true, true, true, true, true, true);
    }

    private Member member() {
        return Member.builder()
                .email("member@example.com")
                .nickname("회원")
                .socialProvider(SocialProvider.GOOGLE)
                .role(Role.ROLE_USER)
                .socialId("google-member")
                .build();
    }
}
