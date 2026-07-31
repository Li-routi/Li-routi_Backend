package com.lirouti.domain.member.service.command;

import com.lirouti.domain.auth.exception.AuthException;
import com.lirouti.domain.auth.exception.code.error.AuthErrorCode;
import com.lirouti.domain.auth.service.TokenService;
import com.lirouti.domain.member.dto.request.MemberReqDTO;
import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.enums.SocialProvider;
import com.lirouti.domain.member.event.MemberWithdrawnEvent;
import com.lirouti.domain.member.exception.MemberException;
import com.lirouti.domain.member.exception.code.error.MemberErrorCode;
import com.lirouti.domain.member.repository.MemberRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MemberCommandService 테스트")
class MemberCommandServiceTest {
    private static final Long MEMBER_ID = 1L;
    private static final String ACCESS_TOKEN = "access-token";
    private static final SocialProvider PROVIDER = SocialProvider.GOOGLE;
    private static final String SOCIAL_ID = "google-subject";
    private static final String EMAIL = "member@example.com";
    private static final String NICKNAME = "member";

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private TokenService tokenService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private MemberCommandService memberCommandService;

    @Test
    @DisplayName("기존 활성 회원이면 그대로 반환한다")
    void findOrCreateSocialMember_ExistingActiveMember_ReturnsMember() {
        // given
        Member member = mock(Member.class);
        when(member.isActiveMember()).thenReturn(true);
        when(memberRepository.findBySocialProviderAndSocialId(PROVIDER, SOCIAL_ID))
                .thenReturn(Optional.of(member));

        // when
        Member result = memberCommandService.findOrCreateSocialMember(
                PROVIDER, SOCIAL_ID, null, null);

        // then
        assertThat(result).isSameAs(member);
        verify(memberRepository, never()).save(any(Member.class));
    }

    @Test
    @DisplayName("탈퇴한 회원이면 소셜 로그인 시 예외를 던진다")
    void findOrCreateSocialMember_WithdrawnMember_ThrowsException() {
        // given
        Member member = mock(Member.class);
        when(member.isActiveMember()).thenReturn(false);
        when(memberRepository.findBySocialProviderAndSocialId(PROVIDER, SOCIAL_ID))
                .thenReturn(Optional.of(member));

        // when & then
        assertThatThrownBy(() -> memberCommandService.findOrCreateSocialMember(
                PROVIDER, SOCIAL_ID, EMAIL, NICKNAME))
                .isInstanceOf(MemberException.class)
                .extracting("code")
                .isEqualTo(MemberErrorCode.WITHDRAWN_MEMBER);
    }

    @Test
    @DisplayName("다른 소셜 계정이 사용하는 이메일이면 예외를 던진다")
    void findOrCreateSocialMember_DuplicateEmail_ThrowsException() {
        // given
        when(memberRepository.findBySocialProviderAndSocialId(PROVIDER, SOCIAL_ID))
                .thenReturn(Optional.empty());
        when(memberRepository.existsByEmail(EMAIL)).thenReturn(true);

        // when & then
        assertThatThrownBy(() -> memberCommandService.findOrCreateSocialMember(
                PROVIDER, SOCIAL_ID, EMAIL, NICKNAME))
                .isInstanceOf(MemberException.class)
                .extracting("code")
                .isEqualTo(MemberErrorCode.EMAIL_ALREADY_REGISTERED_WITH_OTHER_PROVIDER);
    }

    @Test
    @DisplayName("신규 회원 가입 시 이메일이 없으면 예외를 던진다")
    void findOrCreateSocialMember_MissingEmail_ThrowsException() {
        // given
        when(memberRepository.findBySocialProviderAndSocialId(PROVIDER, SOCIAL_ID))
                .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> memberCommandService.findOrCreateSocialMember(
                PROVIDER, SOCIAL_ID, null, NICKNAME))
                .isInstanceOf(MemberException.class)
                .extracting("code")
                .isEqualTo(MemberErrorCode.SOCIAL_EMAIL_REQUIRED);
    }

    @Test
    @DisplayName("신규 회원 가입 시 회원을 저장한다")
    void findOrCreateSocialMember_NewMember_SavesMember() {
        // given
        when(memberRepository.findBySocialProviderAndSocialId(PROVIDER, SOCIAL_ID))
                .thenReturn(Optional.empty());
        when(memberRepository.existsByEmail(EMAIL)).thenReturn(false);
        when(memberRepository.save(any(Member.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // when
        Member result = memberCommandService.findOrCreateSocialMember(
                PROVIDER, SOCIAL_ID, EMAIL, NICKNAME);

        // then
        assertThat(result.getSocialProvider()).isEqualTo(PROVIDER);
        assertThat(result.getSocialId()).isEqualTo(SOCIAL_ID);
        assertThat(result.getEmail()).isEqualTo(EMAIL);
        assertThat(result.getNickname()).isEqualTo(NICKNAME);
        assertThat(result.getIsActive()).isTrue();
        assertThat(result.isOnboardingCompleted()).isFalse();
        verify(memberRepository).save(result);
    }

    @Test
    @DisplayName("정확한 탈퇴 확인 문구면 회원 탈퇴를 진행한다")
    void withdraw_ExactText_DoesNotThrow() {
        // given
        Member member = mock(Member.class);
        when(member.isActiveMember()).thenReturn(true);
        when(memberRepository.findByIdForUpdate(MEMBER_ID))
                .thenReturn(Optional.of(member));
        MemberReqDTO.Withdraw request =
                new MemberReqDTO.Withdraw("리루티를 탈퇴합니다");

        // when
        assertDoesNotThrow(() -> memberCommandService.withdraw(MEMBER_ID, request, ACCESS_TOKEN));

        // then
        verify(eventPublisher).publishEvent(any(MemberWithdrawnEvent.class));
        verify(tokenService).validateAccessTokenOwner(ACCESS_TOKEN, MEMBER_ID);
    }

    @Test
    @DisplayName("탈퇴 확인 문구의 앞뒤 공백은 제거 후 검증한다")
    void withdraw_OuterWhitespace_DoesNotThrow() {
        // given
        Member member = mock(Member.class);
        when(member.isActiveMember()).thenReturn(true);
        when(memberRepository.findByIdForUpdate(MEMBER_ID))
                .thenReturn(Optional.of(member));
        MemberReqDTO.Withdraw request =
                new MemberReqDTO.Withdraw(" \t리루티를 탈퇴합니다 \n");

        // when
        assertDoesNotThrow(() -> memberCommandService.withdraw(MEMBER_ID, request, ACCESS_TOKEN));

        // then
        verify(eventPublisher).publishEvent(any(MemberWithdrawnEvent.class));
        verify(tokenService).validateAccessTokenOwner(ACCESS_TOKEN, MEMBER_ID);
    }

    @Test
    @DisplayName("access token의 subject가 탈퇴 대상 회원과 다르면 탈퇴하지 않는다")
    void withdraw_AccessTokenOwnerMismatch_ThrowsException() {
        // given
        MemberReqDTO.Withdraw request =
                new MemberReqDTO.Withdraw("리루티를 탈퇴합니다");
        doThrow(new AuthException(AuthErrorCode.TOKEN_INVALID))
                .when(tokenService)
                .validateAccessTokenOwner(ACCESS_TOKEN, MEMBER_ID);

        // when & then
        assertThatThrownBy(
                () -> memberCommandService.withdraw(MEMBER_ID, request, ACCESS_TOKEN))
                .isInstanceOf(AuthException.class)
                .extracting("code")
                .isEqualTo(AuthErrorCode.TOKEN_INVALID);
        verify(tokenService).validateAccessTokenOwner(ACCESS_TOKEN, MEMBER_ID);
        verifyNoInteractions(memberRepository, eventPublisher);
    }

    @Test
    @DisplayName("탈퇴 확인 문구의 중간 공백이 다르면 예외를 던진다")
    void withdraw_InnerWhitespaceMismatch_ThrowsException() {
        // given
        MemberReqDTO.Withdraw request =
                new MemberReqDTO.Withdraw("리루티를  탈퇴합니다");

        // when
        Throwable thrown = catchThrowable(
                () -> memberCommandService.withdraw(MEMBER_ID, request, ACCESS_TOKEN));

        // then
        assertThat(thrown).isInstanceOf(MemberException.class);
        assertThat(((MemberException) thrown).getCode())
                .isEqualTo(MemberErrorCode.INVALID_WITHDRAWAL_CONFIRMATION);
        verifyNoInteractions(memberRepository, tokenService, eventPublisher);
    }

    @Test
    @DisplayName("탈퇴 확인 요청이 null이면 예외를 던진다")
    void withdraw_NullRequest_ThrowsException() {
        // when
        Throwable thrown = catchThrowable(
                () -> memberCommandService.withdraw(MEMBER_ID, null, ACCESS_TOKEN));

        // then
        assertThat(thrown).isInstanceOf(MemberException.class);
        assertThat(((MemberException) thrown).getCode())
                .isEqualTo(MemberErrorCode.INVALID_WITHDRAWAL_CONFIRMATION);
        verifyNoInteractions(memberRepository, tokenService, eventPublisher);
    }
}
