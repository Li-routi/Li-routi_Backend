package com.lirouti.global.properties;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Validated
@Configuration
@ConfigurationProperties(prefix = "aws.s3")
public class S3Properties {
    /**
     * 환경변수 미주입을 부팅 시점에 잡기 위해 형식까지 검증한다.
     * 기본값 없이 ${AWS_S3_BUCKET}만 선언하면 부팅이 실패할 것 같지만, 실제로는
     * 해석되지 못한 플레이스홀더가 "${AWS_S3_BUCKET}" 문자열 그대로 바인딩된다.
     * @NotBlank만으로는 이를 통과시키므로, S3 버킷 명명 규칙으로 걸러낸다.
     */
    @NotBlank(message = "S3 버킷 이름은 필수입니다. AWS_S3_BUCKET 환경변수를 주입하세요.")
    @Pattern(
            regexp = "^[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]$",
            message = "S3 버킷 이름 형식이 올바르지 않습니다. AWS_S3_BUCKET 환경변수가 주입되지 않았을 수 있습니다."
    )
    private String bucket;

    @NotNull(message = "presigned URL 유효 시간은 필수입니다.")
    private Duration presignedUrlExpiration;

    // 카테고리별 최대 업로드 용량(바이트). 사진과 영상은 파일 크기가 크게 달라 한도를 분리한다.
    @Positive(message = "최대 이미지 업로드 용량은 양수여야 합니다.")
    private long maxImageSize;

    // 영상은 아직 사용하지 않지만, 켜질 때 바로 쓰도록 한도를 미리 둔다.
    @Positive(message = "최대 영상 업로드 용량은 양수여야 합니다.")
    private long maxVideoSize;

    /**
     * 미디어를 읽을 때 사용할 공개 주소(CloudFront 등).
     * DB에는 오브젝트 key만 저장하고 읽기 URL은 이 값 + key로 조립한다.
     * 미주입 시 application.yaml이 버킷·리전으로 S3 가상 호스팅 주소를 계산해 채우므로 항상 값이 있다
     * (설정 없이도 앱이 부팅되게 하기 위함). 여기서는 그렇게 채워진 값이 유효한 URL 형식인지만 확인한다 —
     * 해석되지 못한 플레이스홀더가 문자열로 바인딩되는 것을 스킴 검증으로 걸러낸다.
     */
    @NotBlank(message = "미디어 공개 주소가 비어 있습니다. AWS_S3_BUCKET이 주입되지 않았을 수 있습니다.")
    @Pattern(
            regexp = "^https?://[^\\s${}]+$",
            message = "미디어 공개 주소 형식이 올바르지 않습니다. AWS_S3_BUCKET/AWS_S3_PUBLIC_BASE_URL 주입 상태를 확인하세요."
    )
    private String publicBaseUrl;

    // S3 presigned URL은 최대 7일이다.
    private static final Duration MAX_PRESIGNED_EXPIRATION = Duration.ofDays(7);

    /**
     * presigned URL 유효 시간을 부팅 시점에 검증한다.
     * {@code @NotNull}만으로는 0·음수·7일 초과를 통과시키고, 그 경우 URL 발급 시점에야
     * 실패한다. S3 서명 방식(SigV4)상 유효 시간은 최대 7일이므로 그 범위를 강제한다.
     */
    @AssertTrue(message = "presigned URL 유효 시간은 1초 이상 7일 이하여야 합니다.")
    public boolean isPresignedUrlExpirationInRange() {
        // null은 @NotNull이 별도로 잡으므로 여기서는 통과시킨다(중복 위반 메시지 방지).
        if (presignedUrlExpiration == null) {
            return true;
        }
        return !presignedUrlExpiration.isZero()
                && !presignedUrlExpiration.isNegative()
                && presignedUrlExpiration.compareTo(MAX_PRESIGNED_EXPIRATION) <= 0;
    }
}
