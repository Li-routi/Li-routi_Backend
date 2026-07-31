package com.lirouti.domain.member.event;

import com.lirouti.domain.auth.service.TokenService;
import com.lirouti.global.config.RetryConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.data.redis.RedisConnectionFailureException;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MemberWithdrawnEventListener 테스트")
class MemberWithdrawnEventListenerTest {
    private static final Long MEMBER_ID = 1L;
    private static final String ACCESS_TOKEN = "access-token";

    @Mock
    private TokenService tokenService;

    @Test
    @DisplayName("회원 탈퇴 이벤트를 받으면 토큰 폐기를 요청한다")
    void handle_MemberWithdrawnEvent_RevokesTokens() {
        // given
        MemberWithdrawnEventListener listener = new MemberWithdrawnEventListener(tokenService);
        MemberWithdrawnEvent event = new MemberWithdrawnEvent(MEMBER_ID, ACCESS_TOKEN);

        // when
        listener.handle(event);

        // then
        verify(tokenService).logout(ACCESS_TOKEN);
    }

    @Test
    @DisplayName("Redis 연결 실패 시 토큰 폐기를 3회 시도한다")
    void handle_RedisConnectionFailure_RetriesThreeTimes() {
        // given
        doThrow(new RedisConnectionFailureException("redis unavailable"))
                .when(tokenService)
                .logout(ACCESS_TOKEN);
        MemberWithdrawnEvent event = new MemberWithdrawnEvent(MEMBER_ID, ACCESS_TOKEN);

        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(RetryConfig.class);
            context.registerBean(TokenService.class, () -> tokenService);
            context.registerBean(
                    MemberWithdrawnEventListener.class,
                    () -> new MemberWithdrawnEventListener(tokenService)
            );
            context.refresh();

            // when
            MemberWithdrawnEventListener listener = context.getBean(MemberWithdrawnEventListener.class);
            listener.handle(event);
        }

        // then
        verify(tokenService, times(3)).logout(ACCESS_TOKEN);
    }
}
