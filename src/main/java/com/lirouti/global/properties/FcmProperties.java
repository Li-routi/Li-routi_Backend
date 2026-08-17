package com.lirouti.global.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Firebase Cloud Messaging 초기화 옵션을 환경 설정에서 바인딩한다.
 *
 * <p>서비스 계정 JSON과 같은 비밀 정보는 이 객체에 바인딩하지 않는다.
 * Firebase Admin SDK가 {@code GOOGLE_APPLICATION_CREDENTIALS} 환경변수를 통해
 * Application Default Credentials를 찾도록 둔다.
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "fcm")
public class FcmProperties {

    /** 키가 없는 환경에서는 Firebase Bean을 만들지 않도록 제어한다. */
    private boolean enabled;

    /**
     * 메시지를 전송할 Firebase 프로젝트 ID이다.
     *
     * <p>비어 있으면 Firebase Admin SDK가 서비스 계정 자격 증명에서
     * 프로젝트 ID를 자동으로 결정한다.
     */
    private String projectId;
}
