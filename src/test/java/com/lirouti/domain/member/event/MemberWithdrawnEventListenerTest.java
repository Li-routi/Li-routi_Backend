package com.lirouti.domain.member.event;

import static org.mockito.Mockito.verify;

import com.lirouti.domain.auth.service.TokenService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
}
