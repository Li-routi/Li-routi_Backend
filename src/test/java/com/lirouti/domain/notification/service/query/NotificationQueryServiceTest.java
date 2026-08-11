package com.lirouti.domain.notification.service.query;

import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.enums.Role;
import com.lirouti.domain.member.enums.SocialProvider;
import com.lirouti.domain.member.repository.MemberRepository;
import com.lirouti.domain.notification.dto.response.NotificationResDTO;
import com.lirouti.domain.notification.enums.NotificationCategory;
import com.lirouti.domain.notification.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationQueryService 설정·목록 필터 테스트")
class NotificationQueryServiceTest {
    private static final Long MEMBER_ID = 1L;
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-11T03:00:00Z"), ZoneOffset.UTC
    );

    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private MemberRepository memberRepository;

    private NotificationQueryService queryService;

    @BeforeEach
    void setUp() {
        queryService = new NotificationQueryService(notificationRepository, memberRepository, CLOCK);
    }

    @Test
    @DisplayName("회원의 여섯 알림 설정을 모두 반환한다")
    void getSettings_ReturnsAllSettings() {
        Member member = member();
        member.updateNotificationSettings(false, true, false, true, false, true);
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));

        NotificationResDTO.Settings result = queryService.getSettings(MEMBER_ID);

        assertThat(result.routineDeadlineEnabled()).isFalse();
        assertThat(result.newVerificationEnabled()).isTrue();
        assertThat(result.verificationReactionEnabled()).isFalse();
        assertThat(result.pokeEnabled()).isTrue();
        assertThat(result.newChatEnabled()).isFalse();
        assertThat(result.likeEnabled()).isTrue();
    }

    @Test
    @DisplayName("그룹 루틴 탭의 category와 cursor를 저장소 필터에 그대로 전달한다")
    void getNotifications_GroupRoutine_PassesCategoryAndCursor() {
        LocalDateTime since = LocalDateTime.of(2026, 8, 4, 3, 0);
        when(notificationRepository.findPage(
                MEMBER_ID,
                NotificationCategory.GROUP_ROUTINE,
                42L,
                since,
                PageRequest.of(0, 21)
        )).thenReturn(List.of());

        NotificationResDTO.Page result = queryService.getNotifications(
                MEMBER_ID, NotificationCategory.GROUP_ROUTINE, 42L, 20
        );

        assertThat(result.notifications()).isEmpty();
        assertThat(result.nextCursor()).isNull();
        assertThat(result.hasNext()).isFalse();
        verify(notificationRepository).findPage(
                MEMBER_ID,
                NotificationCategory.GROUP_ROUTINE,
                42L,
                since,
                PageRequest.of(0, 21)
        );
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
