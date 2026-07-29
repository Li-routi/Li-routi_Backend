package com.lirouti.domain.member.dto.response;

import com.lirouti.domain.member.enums.SocialProvider;
import lombok.Builder;

public final class MemberResDTO {
    private MemberResDTO() {
    }

    @Builder
    public record MemberInfo(
            Long memberId,
            String email,
            String nickname,
            SocialProvider socialProvider,
            boolean onboardingCompleted
    ){
    }
}
