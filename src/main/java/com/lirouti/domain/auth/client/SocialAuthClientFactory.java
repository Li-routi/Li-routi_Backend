package com.lirouti.domain.auth.client;

import com.lirouti.domain.auth.exception.AuthException;
import com.lirouti.domain.auth.exception.code.error.AuthErrorCode;
import com.lirouti.domain.member.enums.SocialProvider;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SocialAuthClientFactory {
    private final List<SocialAuthClient> socialAuthClients;

    public SocialAuthClient getClient(SocialProvider provider) {
        return socialAuthClients.stream()
                .filter(client -> client.supports(provider))
                .findFirst()
                .orElseThrow(() -> {
                    log.warn("지원하지 않는 소셜 로그인 제공자입니다. provider={}", provider);
                    return new AuthException(AuthErrorCode.UNSUPPORTED_PROVIDER);
                });
    }
}
