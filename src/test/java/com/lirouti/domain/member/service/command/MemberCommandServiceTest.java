package com.lirouti.domain.member.service.command;

import com.lirouti.domain.auth.exception.AuthException;
import com.lirouti.domain.auth.exception.code.error.AuthErrorCode;
import com.lirouti.domain.auth.service.TokenService;
import com.lirouti.domain.media.enums.MediaPurpose;
import com.lirouti.domain.media.service.MediaService;
import com.lirouti.domain.member.dto.request.MemberReqDTO;
import com.lirouti.domain.member.dto.response.MemberResDTO;
import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.enums.Role;
import com.lirouti.domain.member.enums.SocialProvider;
import com.lirouti.domain.member.event.MemberWithdrawnEvent;
import com.lirouti.domain.member.exception.MemberException;
import com.lirouti.domain.member.exception.code.error.MemberErrorCode;
import com.lirouti.domain.member.repository.MemberRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import com.lirouti.domain.character.service.command.CharacterUnlockCommandService;
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
    private static final String PROFILE_IMAGE_KEY = "profiles/2026/08/18/profile.png";
    private static final String PROFILE_IMAGE_URL = "https://example.com/profile.png";

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private TokenService tokenService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private MediaService mediaService;

    // 가입 직후 기본 캐릭터를 주는 판정이 붙었다. 이 단위 테스트의 관심사가 아니라 목으로 둔다.
    @Mock
    private CharacterUnlockCommandService characterUnlockCommandService;

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
        // 이 검증이 없으면 가입에서 판정을 지워도 테스트가 통과한다 — 기본 캐릭터가 조용히
        // 안 들어오고, 그 상태로는 캐릭터를 하나도 못 고른다(선택이 보유를 요구한다).
        verify(characterUnlockCommandService).evaluateAndUnlock(result.getId());
        assertThat(result.isOnboardingCompleted()).isFalse();
        verify(memberRepository).save(result);
    }

    @Test
    @DisplayName("프로필 수정 시 이미지 key가 null이면 기존 이미지를 유지한다")
    void updateProfile_NullProfileImageKey_PreservesExistingImage() {
        // given
        Member member = Member.builder()
                .email(EMAIL)
                .nickname("기존 닉네임")
                .socialProvider(PROVIDER)
                .socialId(SOCIAL_ID)
                .role(Role.ROLE_USER)
                .build();
        member.updateProfile("기존 닉네임", PROFILE_IMAGE_KEY);
        when(memberRepository.findByIdForUpdate(MEMBER_ID)).thenReturn(Optional.of(member));
        when(memberRepository.save(member)).thenReturn(member);
        when(mediaService.resolveViewUrl(PROFILE_IMAGE_KEY, MediaPurpose.PROFILE))
                .thenReturn(PROFILE_IMAGE_URL);

        // when
        MemberReqDTO.UpdateProfile request = new MemberReqDTO.UpdateProfile("새 닉네임", null);
        var result = memberCommandService.updateProfile(MEMBER_ID, request);

        // then
        assertThat(member.getNickname()).isEqualTo("새 닉네임");
        assertThat(member.getProfileImageKey()).isEqualTo(PROFILE_IMAGE_KEY);
        assertThat(result.profileImageUrl()).isEqualTo(PROFILE_IMAGE_URL);
        verify(memberRepository).save(member);
    }

    @Test
    @DisplayName("프로필 이미지 삭제 시 기존 이미지 key를 제거한다")
    void deleteProfileImage_ExistingImage_ClearsImage() {
        // given
        Member member = memberWithProfileImage(PROFILE_IMAGE_KEY);
        when(memberRepository.findByIdForUpdate(MEMBER_ID)).thenReturn(Optional.of(member));
        when(memberRepository.save(member)).thenReturn(member);

        // when
        MemberResDTO.MemberInfo result = memberCommandService.deleteProfileImage(MEMBER_ID);

        // then
        assertThat(member.getProfileImageKey()).isNull();
        assertThat(result.profileImageUrl()).isNull();
        verify(memberRepository).save(member);
    }

    @Test
    @DisplayName("존재하지 않는 회원의 프로필 이미지 삭제는 회원 없음 예외를 던진다")
    void deleteProfileImage_MemberNotFound_ThrowsMemberNotFound() {
        // given
        when(memberRepository.findByIdForUpdate(MEMBER_ID)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> memberCommandService.deleteProfileImage(MEMBER_ID))
                .isInstanceOf(MemberException.class)
                .extracting("code")
                .isEqualTo(MemberErrorCode.MEMBER_NOT_FOUND);
        verify(memberRepository, never()).save(any(Member.class));
    }

    @Test
    @DisplayName("비활성 회원의 프로필 이미지 삭제는 탈퇴 회원 예외를 던진다")
    void deleteProfileImage_InactiveMember_ThrowsWithdrawnMember() {
        // given
        Member member = mock(Member.class);
        when(member.isActiveMember()).thenReturn(false);
        when(memberRepository.findByIdForUpdate(MEMBER_ID)).thenReturn(Optional.of(member));

        // when & then
        assertThatThrownBy(() -> memberCommandService.deleteProfileImage(MEMBER_ID))
                .isInstanceOf(MemberException.class)
                .extracting("code")
                .isEqualTo(MemberErrorCode.WITHDRAWN_MEMBER);
        verify(memberRepository, never()).save(any(Member.class));
    }

    @Test
    @DisplayName("프로필 이미지가 없어도 이미지 삭제를 다시 요청할 수 있다")
    void deleteProfileImage_WithoutImage_IsIdempotent() {
        // given
        Member member = memberWithProfileImage(null);
        when(memberRepository.findByIdForUpdate(MEMBER_ID)).thenReturn(Optional.of(member));
        when(memberRepository.save(member)).thenReturn(member);

        // when
        MemberResDTO.MemberInfo result = memberCommandService.deleteProfileImage(MEMBER_ID);

        // then
        assertThat(member.getProfileImageKey()).isNull();
        assertThat(result.profileImageUrl()).isNull();
        verify(memberRepository).save(member);
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

    private Member memberWithProfileImage(String profileImageKey) {
        Member member = Member.builder()
                .email(EMAIL)
                .nickname("기존 닉네임")
                .socialProvider(PROVIDER)
                .socialId(SOCIAL_ID)
                .role(Role.ROLE_USER)
                .build();
        if (profileImageKey != null) {
            member.updateProfile("기존 닉네임", profileImageKey);
        }
        return member;
    }
}
