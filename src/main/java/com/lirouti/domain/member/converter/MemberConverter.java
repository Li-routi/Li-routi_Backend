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

    // Converter가 정적 유틸이라 MediaService를 주입받을 수 없음. 따라서 Service에서 key -> URL로 변환하고, 변환된 문자열을 Converter에 넘기도록 수정
    public static MemberResDTO.MemberInfo toMemberInfo(Member member, String profileImageUrl) {
        return MemberResDTO.MemberInfo.builder()
                .memberId(member.getId())
                .email(member.getEmail())
                .nickname(member.getNickname())
                .profileImageUrl(profileImageUrl)
                .socialProvider(member.getSocialProvider())
                .onboardingCompleted(member.isOnboardingCompleted())
                .build();
    }
}
