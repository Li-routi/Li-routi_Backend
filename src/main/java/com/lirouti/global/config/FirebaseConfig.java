package com.lirouti.global.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import com.lirouti.global.properties.FcmProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.io.IOException;

/**
 * Application Default Credentials를 사용해 Firebase Admin SDK를 초기화한다.
 *
 * <p>{@code fcm.enabled=true}인 환경에서만 활성화된다. 로컬에서는
 * {@code GOOGLE_APPLICATION_CREDENTIALS}에 서비스 계정 JSON의 절대 경로를 지정한다.
 * 이 방식은 키 파일이 리소스나 실행 JAR에 포함되는 것을 막는다.
 */
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "fcm", name = "enabled", havingValue = "true")
public class FirebaseConfig {

    private final FcmProperties properties;

    /**
     * 기본 Firebase 앱을 초기화한다.
     *
     * <p>같은 JVM에서 이미 초기화된 기본 앱이 있으면 재사용한다.
     * 스프링 컨텍스트 재생성 등으로 인한 중복 초기화 예외를 방지하기 위함이다.
     *
     * @return 푸시 알림 전송에 사용할 기본 Firebase 앱
     * @throws IOException Application Default Credentials를 읽지 못한 경우
     */
    @Bean
    public FirebaseApp firebaseApp() throws IOException {
        if (!FirebaseApp.getApps().isEmpty()) {
            return FirebaseApp.getInstance();
        }

        FirebaseOptions.Builder options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.getApplicationDefault());

        // 명시한 프로젝트 ID가 있을 때만 자격 증명의 자동 탐지 결과를 덮어쓴다.
        if (StringUtils.hasText(properties.getProjectId())) {
            options.setProjectId(properties.getProjectId());
        }

        return FirebaseApp.initializeApp(options.build());
    }

    /**
     * FCM 메시지 전송 클라이언트를 스프링 Bean으로 제공한다.
     *
     * @param firebaseApp 초기화된 기본 Firebase 앱
     * @return 해당 앱에 연결된 FCM 메시지 클라이언트
     */
    @Bean
    public FirebaseMessaging firebaseMessaging(FirebaseApp firebaseApp) {
        return FirebaseMessaging.getInstance(firebaseApp);
    }
}
