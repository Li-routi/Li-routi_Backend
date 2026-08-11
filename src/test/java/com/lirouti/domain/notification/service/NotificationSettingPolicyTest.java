package com.lirouti.domain.notification.service;

import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.enums.Role;
import com.lirouti.domain.member.enums.SocialProvider;
import com.lirouti.domain.member.repository.MemberRepository;
import com.lirouti.domain.notification.enums.NotificationSettingType;
import com.lirouti.domain.notification.enums.NotificationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationSettingPolicy 알림 설정 정책 테스트")
class NotificationSettingPolicyTest {
    private static final Long MEMBER_ID = 1L;

    @Mock
    private MemberRepository memberRepository;

    @InjectMocks
    private NotificationSettingPolicy settingPolicy;

    @Test
    @DisplayName("신규 회원은 여섯 종류 알림을 모두 허용한다")
    void allows_DefaultSettings_AllowsEveryConfigurableType() {
        Member member = member();
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));

        for (NotificationType type : NotificationType.values()) {
            if (type.getSettingType() != NotificationSettingType.ALWAYS) {
                assertThat(settingPolicy.allows(MEMBER_ID, type)).isTrue();
            }
        }
    }

    @Test
    @DisplayName("콕콕 설정을 끄면 콕콕만 차단하고 다른 설정 알림은 허용한다")
    void allows_PokeDisabled_BlocksOnlyPoke() {
        Member member = member();
        member.updateNotificationSettings(null, null, null, false, null, null);
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));

        assertThat(settingPolicy.allows(MEMBER_ID, NotificationType.GROUP_MEMBER_POKED)).isFalse();
        assertThat(settingPolicy.allows(MEMBER_ID, NotificationType.GROUP_CHAT_MESSAGE)).isTrue();
        assertThat(settingPolicy.allows(MEMBER_ID, NotificationType.CHALLENGE_VERIFICATION_LIKED)).isTrue();
    }

    @Test
    @DisplayName("중요 알림은 회원 설정을 조회하지 않고 항상 허용한다")
    void allows_AlwaysType_DoesNotLoadSettings() {
        assertThat(settingPolicy.allows(MEMBER_ID, NotificationType.CHALLENGE_REVIEW_APPROVED)).isTrue();

        verify(memberRepository, never()).findById(MEMBER_ID);
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
