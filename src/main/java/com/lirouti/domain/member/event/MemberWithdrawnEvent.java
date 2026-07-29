package com.lirouti.domain.member.event;

public record MemberWithdrawnEvent(
        Long memberId,
        String accessToken
) {
}
