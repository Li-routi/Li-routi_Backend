package com.lirouti.domain.member.converter;

import com.lirouti.domain.member.dto.response.MemberResDTO;
import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.enums.Role;
import com.lirouti.domain.member.enums.SocialProvider;

public final class MemberConverter {
    private MemberConverter() {
    }

    public static Member toSocialMember(
            SocialProvider socialProvider,
            String socialId,
            String email,
            String nickname
    ) {
        return Member.builder()
                .socialProvider(socialProvider)
                .socialId(socialId)
                .email(email)
                .nickname(nickname)
                .role(Role.ROLE_USER)
                .build();
    }

    public static MemberResDTO.MemberInfo toMemberInfo(Member member) {
        return MemberResDTO.MemberInfo.builder()
                .memberId(member.getId())
                .email(member.getEmail())
                .nickname(member.getNickname())
                .socialProvider(member.getSocialProvider())
                .onboardingCompleted(member.isOnboardingCompleted())
                .build();
    }
}
