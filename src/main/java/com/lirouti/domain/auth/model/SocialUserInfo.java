package com.lirouti.domain.auth.model;

import com.lirouti.domain.member.enums.SocialProvider;

public record SocialUserInfo(
        SocialProvider provider,
        String socialId,
        String email,
        String nickname
) {
}
