package com.lirouti.domain.image.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.lirouti.domain.image.converter.ImageConverter;
import com.lirouti.domain.image.dto.request.ImageReqDTO;
import com.lirouti.domain.image.dto.response.ImageResDTO;
import com.lirouti.domain.image.enums.ImageContentType;
import com.lirouti.domain.image.enums.ImagePurpose;
import com.lirouti.domain.image.exception.ImageException;
import com.lirouti.domain.image.exception.code.error.ImageErrorCode;
import com.lirouti.global.properties.S3Properties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

/**
 * 이미지 업로드용 presigned URL을 발급한다.
 * DB를 다루지 않아 조회/변경 구분이 의미 없으므로 CQRS를 적용하지 않는다.
 * (service_convention.md의 CQRS 예외 도메인)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImageService {
    private final S3Presigner s3Presigner;
    private final S3Properties s3Properties;

    public ImageResDTO.PresignedUrl issuePresignedUrl(ImageReqDTO.PresignedUrl request) {
        ImageContentType contentType = validateContentType(request.contentType());
        validateFileSize(request.contentLength());

        String imageKey = generateImageKey(request.purpose(), contentType);
        Duration expiration = s3Properties.getPresignedUrlExpiration();

        // 서명에 Content-Type과 Content-Length를 포함시킨다.
        // 클라이언트가 다른 형식이나 크기로 업로드하면 S3가 거부하므로,
        // 서버가 파일 바이트를 보지 않고도 검증 결과를 강제할 수 있다.
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(s3Properties.getBucket())
                .key(imageKey)
                .contentType(contentType.getMimeType())
                .contentLength(request.contentLength())
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(expiration)
                .putObjectRequest(putObjectRequest)
                .build();

        String uploadUrl = presign(presignRequest, imageKey);
        log.debug("이미지 업로드 URL을 발급했습니다. key={}", imageKey);

        return ImageConverter.toPresignedUrl(
                uploadUrl,
                imageKey,
                s3Properties.getPublicBaseUrl(),
                LocalDateTime.now().plus(expiration)
        );
    }

    private String presign(PutObjectPresignRequest presignRequest, String imageKey) {
        try {
            return s3Presigner.presignPutObject(presignRequest).url().toString();
        } catch (RuntimeException e) {
            log.error("S3 presigned URL 발급에 실패했습니다. key={}", imageKey, e);
            throw new ImageException(ImageErrorCode.PRESIGNED_URL_ISSUE_FAILED);
        }
    }

    private ImageContentType validateContentType(String contentType) {
        return ImageContentType.from(contentType)
                .orElseThrow(() -> {
                    log.warn("허용하지 않는 이미지 형식으로 업로드를 시도했습니다. contentType={}", contentType);
                    return new ImageException(ImageErrorCode.UNSUPPORTED_CONTENT_TYPE);
                });
    }

    private void validateFileSize(long contentLength) {
        if (contentLength > s3Properties.getMaxFileSize()) {
            log.warn("최대 용량을 초과한 업로드를 시도했습니다. contentLength={}, maxFileSize={}",
                    contentLength, s3Properties.getMaxFileSize());
            throw new ImageException(ImageErrorCode.FILE_TOO_LARGE);
        }
    }

    /**
     * key는 추측할 수 없어야 한다. UUID를 사용해 다른 사용자의 이미지 경로를 유추하지 못하게 한다.
     * 클라이언트가 보낸 파일명은 신뢰하지 않고 사용하지 않는다(경로 조작 방지).
     */
    private String generateImageKey(ImagePurpose purpose, ImageContentType contentType) {
        return "%s/%s.%s".formatted(
                purpose.getPathPrefix(),
                UUID.randomUUID(),
                contentType.getExtension()
        );
    }
}
