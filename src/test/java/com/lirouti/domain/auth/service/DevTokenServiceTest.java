package com.lirouti.domain.auth.service;

import com.lirouti.domain.auth.dto.response.AuthResDTO;
import com.lirouti.domain.auth.exception.AuthException;
import com.lirouti.domain.auth.exception.code.error.AuthErrorCode;
import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.enums.Role;
import com.lirouti.domain.member.enums.SocialProvider;
import com.lirouti.domain.member.repository.MemberRepository;
import com.lirouti.global.properties.JwtProperties;
import com.lirouti.global.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DevTokenService 테스트")
class DevTokenServiceTest {
    private static final long DEV_EXPIRATION = 1_209_600_000L;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private JwtUtil jwtUtil;

    private DevTokenService devTokenService;

    @BeforeEach
    void setUp() {
        // JwtProperties는 값 홀더라 mock 할 이유가 없다. 실제 객체를 써야 설정한 만료 값이
        // 응답에 그대로 실리는지가 확인된다. @InjectMocks 대신 직접 조립하는 이유이기도 하다.
        JwtProperties jwtProperties = new JwtProperties();
        jwtProperties.getDevToken().setExpirationTime(DEV_EXPIRATION);
        devTokenService = new DevTokenService(memberRepository, jwtUtil, jwtProperties);
    }

    private static Member member() {
        Member member = Member.builder()
                .email("dev@lirouti.local")
                .nickname("더미유저1")
                .socialProvider(SocialProvider.GOOGLE)
                .role(Role.ROLE_USER)
                .socialId("dummy-google-9001")
                .build();
        // id는 영속화가 채우는 필드라 단위 테스트에서는 직접 심는다.
        ReflectionTestUtils.setField(member, "id", 9001L);
        return member;
    }

    @Test
    @DisplayName("활성 회원이면 토큰과 만료 시간을 돌려준다")
    void issue_ActiveMember_ReturnsTokenWithExpiration() {
        // given
        Member member = member();
        when(memberRepository.findById(9001L)).thenReturn(Optional.of(member));
        when(jwtUtil.createDevToken(member.getId())).thenReturn("dev.jwt.token");

        // when
        AuthResDTO.DevToken result = devTokenService.issue(9001L);

        // then
        assertAll(
                () -> assertEquals("dev.jwt.token", result.accessToken()),
                () -> assertEquals(DEV_EXPIRATION, result.accessTokenExpiresIn())
        );
    }

    @Test
    @DisplayName("없는 회원이면 404로 막고 토큰을 만들지 않는다")
    void issue_MemberNotFound_ThrowsAndCreatesNoToken() {
        // given
        when(memberRepository.findById(1L)).thenReturn(Optional.empty());

        // when
        AuthException exception = assertThrows(
                AuthException.class, () -> devTokenService.issue(1L));

        // then
        assertEquals(AuthErrorCode.DEV_TOKEN_MEMBER_NOT_FOUND.getCode(),
                exception.getCode().getCode());
        verify(jwtUtil, never()).createDevToken(eq(1L));
    }

    @Test
    @DisplayName("탈퇴한 회원에게는 발급하지 않는다")
    void issue_WithdrawnMember_Throws() {
        // given — 행은 남아 있지만 서비스에 접근할 수 없는 상태다.
        Member withdrawn = member();
        withdrawn.withdraw("withdrawn@lirouti.local", "withdrawn-sid", LocalDateTime.now());
        when(memberRepository.findById(9001L)).thenReturn(Optional.of(withdrawn));

        // when
        AuthException exception = assertThrows(
                AuthException.class, () -> devTokenService.issue(9001L));

        // then
        assertEquals(AuthErrorCode.DEV_TOKEN_MEMBER_NOT_FOUND.getCode(),
                exception.getCode().getCode());
    }
}
