package com.lirouti.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.time.Duration;

@Configuration
public class S3Config {
    private final Region region;

    public S3Config(@Value("${aws.region}") String region) {
        this.region = Region.of(region);
    }

    /**
     * 자격증명은 코드나 설정 파일에 두지 않는다.
     * DefaultCredentialsProvider가 실행 환경에서 자동으로 찾는다.
     * 운영(EC2)은 인스턴스 프로필(IAM Role), 로컬은 AWS CLI 프로파일을 사용한다.
     */
    @Bean
    public S3Presigner s3Presigner() {
        return S3Presigner.builder()
                .region(region)
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    /**
     * 업로드된 오브젝트를 실제로 읽기 위한 클라이언트(#22).
     *
     * presigner는 서명만 계산하지만 이 클라이언트는 S3를 실제로 호출한다.
     * 그래서 운영 EC2의 instance profile에 {@code s3:GetObject}가 있어야 한다(deploy/README.md).
     *
     * 타임아웃을 명시하는 이유: 인증 요청 경로에서 동기로 호출되므로, 기본값(수십 초)으로 두면
     * S3가 느려질 때 요청 스레드가 그만큼 붙잡힌다. 앞 12바이트({@link
     * com.lirouti.domain.media.enums.MediaContentType#SIGNATURE_LENGTH})만 읽는 호출이라 짧게 잡는다.
     */
    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
                .region(region)
                .credentialsProvider(DefaultCredentialsProvider.create())
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                        .apiCallTimeout(Duration.ofSeconds(5))
                        .apiCallAttemptTimeout(Duration.ofSeconds(2))
                        .build())
                .build();
    }
}
