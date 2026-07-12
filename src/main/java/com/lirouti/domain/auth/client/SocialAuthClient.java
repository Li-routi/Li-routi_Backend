package com.lirouti.domain.auth.client;

import com.lirouti.domain.auth.model.SocialUserInfo;
import com.lirouti.domain.member.enums.SocialProvider;

public interface SocialAuthClient {
    boolean supports(SocialProvider provider);

    SocialUserInfo authenticate(String providerToken, String nonce);
}
