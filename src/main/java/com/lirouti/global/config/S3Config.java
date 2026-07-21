package com.lirouti.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

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
}
