package com.lirouti.domain.member.service.command;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lirouti.domain.auth.service.TokenService;
import com.lirouti.domain.character.service.command.CharacterUnlockCommandService;
import com.lirouti.domain.media.enums.MediaPurpose;
import com.lirouti.domain.media.service.MediaService;
import com.lirouti.domain.member.converter.MemberConverter;
import com.lirouti.domain.member.dto.request.MemberReqDTO;
import com.lirouti.domain.member.dto.response.MemberResDTO;
import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.enums.SocialProvider;
import com.lirouti.domain.member.event.MemberWithdrawnEvent;
import com.lirouti.domain.member.exception.MemberException;
import com.lirouti.domain.member.exception.code.error.MemberErrorCode;
import com.lirouti.domain.member.repository.MemberRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemberCommandService {
    // 탈퇴 확인 문구
    private static final String WITHDRAWAL_CONFIRMATION = "리루티를 탈퇴합니다";
    private static final String WITHDRAWN_EMAIL_DOMAIN = "@deleted.invalid";

    private final MemberRepository memberRepository;
    private final TokenService tokenService;
    private final ApplicationEventPublisher eventPublisher;

    // 닉네임 제공받지 못한 경우 defalut 값
    private static final String DEFAULT_NICKNAME_PREFIX = "user_";
    private final MediaService mediaService;
    private final CharacterUnlockCommandService characterUnlockCommandService;

    // 소셜 회원 조회 또는 생성
    @Transactional
    public Member findOrCreateSocialMember(
            SocialProvider socialProvider,
            String socialId,
            String email,
            String nickname
    ) {
        return memberRepository.findBySocialProviderAndSocialId(socialProvider, socialId)
                .map(this::getActiveMember)
                .orElseGet(() -> createSocialMember(socialProvider, socialId, email, nickname));
    }

    // 회원 탈퇴 처리
    @Transactional
    public void withdraw(Long memberId, MemberReqDTO.Withdraw request, String accessToken) {
        log.info("회원 탈퇴 처리를 시작합니다. memberId={}", memberId);
        validateWithdrawalConfirmation(request);
        tokenService.validateAccessTokenOwner(accessToken, memberId);

        Member member = findActiveMemberForUpdate(memberId);

        String tombstoneId = UUID.randomUUID().toString();

        member.withdraw(
                "withdrawn-" + tombstoneId + WITHDRAWN_EMAIL_DOMAIN,
                "withdrawn-" + tombstoneId,
                LocalDateTime.now()
        );
        memberRepository.save(member);
        eventPublisher.publishEvent(new MemberWithdrawnEvent(memberId, accessToken));

        log.info("회원 탈퇴 처리를 완료했습니다. memberId={}", memberId);
    }

    public void logout(String accessToken) {
        Long memberId = tokenService.logout(accessToken);
        log.info("회원 로그아웃 처리를 완료했습니다. memberId={}", memberId);
    }

    // 프로필 수정
    @Transactional
    public MemberResDTO.MemberInfo updateProfile(Long memberId, MemberReqDTO.UpdateProfile request) {
        Member member = findActiveMemberForUpdate(memberId);

        member.updateProfile(request.nickname(), request.profileImageKey());
        Member savedMember = memberRepository.save(member);
        log.info("회원 프로필 수정을 완료했습니다.");

        String profileImageUrl = savedMember.getProfileImageKey() != null
                ? mediaService.resolveViewUrl(savedMember.getProfileImageKey(), MediaPurpose.PROFILE)
                : null;
        return MemberConverter.toMemberInfo(savedMember, profileImageUrl);
    }

    // 프로필 이미지를 삭제하고 기본 이미지 상태로 되돌린다.
    @Transactional
    public MemberResDTO.MemberInfo deleteProfileImage(Long memberId) {
        Member member = findActiveMemberForUpdate(memberId);

        member.clearProfileImage();
        Member savedMember = memberRepository.save(member);
        log.info("회원 프로필 이미지 삭제를 완료했습니다.");
        return MemberConverter.toMemberInfo(savedMember, null);
    }

    private Member getActiveMember(Member member) {
        if (!member.isActiveMember()) {
            log.warn("탈퇴하거나 비활성화된 회원의 소셜 로그인 시도를 차단했습니다. memberId={}", member.getId());
            throw new MemberException(MemberErrorCode.WITHDRAWN_MEMBER);
        }
        log.debug("기존 소셜 회원을 조회했습니다. memberId={}, provider={}",
                member.getId(), member.getSocialProvider());
        return member;
    }

    private Member createSocialMember(
            SocialProvider socialProvider,
            String socialId,
            String email,
            String nickname
    ) {
        validateSignupEmail(email);
        if (memberRepository.existsByEmail(email)) {
            log.warn("다른 소셜 계정에서 사용 중인 이메일로 가입을 시도했습니다. provider={}", socialProvider);
            throw new MemberException(MemberErrorCode.EMAIL_ALREADY_REGISTERED_WITH_OTHER_PROVIDER);
        }

        String initialNickname = resolveInitialNickname(nickname);
        Member member = MemberConverter.toSocialMember(socialProvider, socialId, email, initialNickname);
        Member savedMember = memberRepository.save(member);

        // 기본 캐릭터를 여기서 준다. 조건 행이 하나도 없는 캐릭터가 곧 기본이라, 판정을 한 번
        // 돌리면 그 자리에서 들어온다 -- "가입 시 지급" 을 따로 구현하지 않아도 된다.
        // 팝업은 띄우지 않는다: 가입하자마자 "새 친구가 왔어요" 가 뜨면 무엇을 해서 얻었는지 알 수 없다.
        characterUnlockCommandService.evaluateAndUnlock(savedMember.getId());

        log.info("신규 소셜 회원을 생성했습니다. memberId={}, provider={}",
                savedMember.getId(), savedMember.getSocialProvider());
        return savedMember;
    }

    private void validateSignupEmail(String email) {
        if (email == null || email.isBlank()) {
            log.warn("검증된 이메일이 없어 소셜 회원가입을 중단했습니다.");
            throw new MemberException(MemberErrorCode.SOCIAL_EMAIL_REQUIRED);
        }
    }

    // 회원 탈퇴 확인 문구 검증
    private void validateWithdrawalConfirmation(MemberReqDTO.Withdraw request) {
        if (request == null
                || request.confirmation() == null
                || !WITHDRAWAL_CONFIRMATION.equals(request.confirmation().strip())) {
            throw new MemberException(MemberErrorCode.INVALID_WITHDRAWAL_CONFIRMATION);
        }
    }

    private String resolveInitialNickname(String providerNickname) {
        if(providerNickname != null && !providerNickname.isBlank()) {
            return providerNickname;
        }
        return DEFAULT_NICKNAME_PREFIX + UUID.randomUUID().toString().substring(0, 8);
    }

    private Member findActiveMemberForUpdate(Long memberId) {
        Member member = memberRepository.findByIdForUpdate(memberId)
                .orElseThrow(() -> {
                    log.warn("존재하지 않는 회원입니다. memberId={}", memberId);
                    return new MemberException(MemberErrorCode.MEMBER_NOT_FOUND);
                });
        if (!member.isActiveMember()) {
            log.warn("탈퇴하거나 비활성화된 회원입니다. memberId={}", memberId);
            throw new MemberException(MemberErrorCode.WITHDRAWN_MEMBER);
        }
        return member;
    }
}
