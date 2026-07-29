package com.lirouti.domain.member.service.command;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lirouti.domain.auth.service.TokenService;
import com.lirouti.domain.member.converter.MemberConverter;
import com.lirouti.domain.member.dto.request.MemberReqDTO;
import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.event.MemberWithdrawnEvent;
import com.lirouti.domain.member.enums.SocialProvider;
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

        Member member = memberRepository.findByIdForUpdate(memberId)
                .orElseThrow(() -> {
                    log.warn("존재하지 않는 회원의 탈퇴를 시도했습니다. memberId={}", memberId);
                    return new MemberException(MemberErrorCode.MEMBER_NOT_FOUND);
                });

        if (!member.isActiveMember()) {
            log.warn("이미 탈퇴하거나 비활성화된 회원의 탈퇴를 시도했습니다. memberId={}", memberId);
            throw new MemberException(MemberErrorCode.WITHDRAWN_MEMBER);
        }

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
        validateSignupProfile(email, nickname);
        if (memberRepository.existsByEmail(email)) {
            log.warn("다른 소셜 계정에서 사용 중인 이메일로 가입을 시도했습니다. provider={}", socialProvider);
            throw new MemberException(MemberErrorCode.EMAIL_ALREADY_REGISTERED_WITH_OTHER_PROVIDER);
        }

        Member member = MemberConverter.toSocialMember(socialProvider, socialId, email, nickname);
        Member savedMember = memberRepository.save(member);
        log.info("신규 소셜 회원을 생성했습니다. memberId={}, provider={}",
                savedMember.getId(), savedMember.getSocialProvider());
        return savedMember;
    }

    private void validateSignupProfile(String email, String nickname) {
        if (email == null || email.isBlank()) {
            log.warn("검증된 이메일이 없어 소셜 회원가입을 중단했습니다.");
            throw new MemberException(MemberErrorCode.SOCIAL_EMAIL_REQUIRED);
        }
        if (nickname == null || nickname.isBlank()) {
            log.warn("닉네임이 없어 소셜 회원가입을 중단했습니다.");
            throw new MemberException(MemberErrorCode.SOCIAL_NICKNAME_REQUIRED);
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
}
