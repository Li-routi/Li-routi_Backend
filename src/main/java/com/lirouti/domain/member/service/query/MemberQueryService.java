package com.lirouti.domain.member.service.query;

import com.lirouti.domain.member.entity.Member;
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
public class MemberQueryService {
    private final MemberRepository memberRepository;

    /**
     * 회원이 존재하며 현재 활성 상태인지 검증한다.
     */
    @Transactional(readOnly = true)
    public Member getActiveMember(Long memberId) {
        if (memberId == null) {
            log.warn("활성 회원 조회에 실패했습니다. memberId가 없습니다.");
            throw new MemberException(MemberErrorCode.MEMBER_NOT_FOUND);
        }

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> {
                    log.warn("회원 조회에 실패했습니다. memberId={}", memberId);
                    return new MemberException(MemberErrorCode.MEMBER_NOT_FOUND);
                });

        if (!Boolean.TRUE.equals(member.getIsActive()) || member.getDeletedAt() != null) {
            log.warn("탈퇴하거나 비활성화된 회원입니다. memberId={}", memberId);
            throw new MemberException(MemberErrorCode.WITHDRAWN_MEMBER);
        }
        return member;
    }
}
