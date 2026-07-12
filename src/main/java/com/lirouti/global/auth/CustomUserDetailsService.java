package com.lirouti.global.auth;

import com.lirouti.domain.auth.exception.AuthException;
import com.lirouti.domain.auth.exception.code.error.AuthErrorCode;
import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomUserDetailsService implements UserDetailsService {
    private final MemberRepository memberRepository;

    @Override
    public UserDetails loadUserByUsername(String memberId) {
        Long id = parseMemberId(memberId);

        Member member = memberRepository.findById(id)
                .filter(foundMember -> Boolean.TRUE.equals(foundMember.getIsActive()))
                .filter(foundMember -> foundMember.getDeletedAt() == null)
                .orElseThrow(() -> new AuthException(AuthErrorCode.TOKEN_INVALID));

        return new CustomUserDetails(member);
    }

    private Long parseMemberId(String memberId) {
        try {
            return Long.valueOf(memberId);
        } catch (NumberFormatException e) {
            throw new AuthException(AuthErrorCode.TOKEN_INVALID);
        }
    }
}
