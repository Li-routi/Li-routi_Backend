package com.lirouti.domain.member.service.command;

import com.lirouti.domain.member.converter.MemberConverter;
import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.enums.SocialProvider;
import com.lirouti.domain.member.exception.MemberException;
import com.lirouti.domain.member.exception.code.error.MemberErrorCode;
import com.lirouti.domain.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemberCommandService {
    private final MemberRepository memberRepository;

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

    private Member getActiveMember(Member member) {
        if (!Boolean.TRUE.equals(member.getIsActive()) || member.getDeletedAt() != null) {
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
}
