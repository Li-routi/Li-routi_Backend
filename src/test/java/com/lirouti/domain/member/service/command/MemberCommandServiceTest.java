package com.lirouti.domain.member.service.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.enums.SocialProvider;
import com.lirouti.domain.member.exception.MemberException;
import com.lirouti.domain.member.exception.code.error.MemberErrorCode;
import com.lirouti.domain.member.repository.MemberRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("MemberCommandService 테스트")
class MemberCommandServiceTest {
    private static final SocialProvider PROVIDER = SocialProvider.GOOGLE;
    private static final String SOCIAL_ID = "google-subject";
    private static final String EMAIL = "member@example.com";
    private static final String NICKNAME = "member";

    @Mock
    private MemberRepository memberRepository;

    @InjectMocks
    private MemberCommandService memberCommandService;

    @Test
    @DisplayName("기존 활성 회원이면 그대로 반환한다")
    void findOrCreateSocialMember_ExistingActiveMember_ReturnsMember() {
        // given
        Member member = mock(Member.class);
        when(member.getIsActive()).thenReturn(true);
        when(member.getDeletedAt()).thenReturn(null);
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
        when(member.getIsActive()).thenReturn(true);
        when(member.getDeletedAt()).thenReturn(LocalDateTime.now());
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
}
