package com.lirouti.domain.media.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.lirouti.domain.media.converter.MediaConverter;
import com.lirouti.domain.media.dto.request.MediaReqDTO;
import com.lirouti.domain.media.dto.response.MediaResDTO;
import com.lirouti.domain.media.enums.MediaCategory;
import com.lirouti.domain.media.enums.MediaContentType;
import com.lirouti.domain.media.enums.MediaPurpose;
import com.lirouti.domain.media.exception.MediaException;
import com.lirouti.domain.media.exception.code.error.MediaErrorCode;
import com.lirouti.global.properties.S3Properties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

/**
 * 미디어 업로드용 presigned URL을 발급한다.
 * DB를 다루지 않아 조회/변경 구분이 무의미하므로 CQRS를 적용하지 않는다.
 * (service_convention.md의 CQRS 예외 도메인)
 *
 * <p>현재는 사진만 업로드한다. 영상은 형식·용도·용량 구조만 갖춰두었고 실제로 켜져 있지 않다.
 * 영상을 켜려면 {@link MediaContentType}에 영상 형식을 추가하고, 해당 {@link MediaPurpose}의
 * 허용 카테고리에 {@link MediaCategory#VIDEO}를 넣으면 이 검증 흐름이 그대로 적용된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MediaService {
    private final S3Presigner s3Presigner;
    private final S3Properties s3Properties;

    public MediaResDTO.PresignedUrl issuePresignedUrl(MediaReqDTO.PresignedUrl request) {
        MediaContentType contentType = resolveContentType(request.contentType());
        validatePurposeAllows(request.purpose(), contentType);
        validateFileSize(contentType.getCategory(), request.contentLength());

        String mediaKey = generateMediaKey(request.purpose(), contentType);
        Duration expiration = s3Properties.getPresignedUrlExpiration();

        // 서명에 Content-Type과 Content-Length를 포함시킨다.
        // 클라이언트가 다른 형식이나 크기로 업로드하면 S3가 거부하므로,
        // 서버가 파일 바이트를 보지 않고도 검증 결과를 강제할 수 있다.
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(s3Properties.getBucket())
                .key(mediaKey)
                .contentType(contentType.getMimeType())
                .contentLength(request.contentLength())
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(expiration)
                .putObjectRequest(putObjectRequest)
                .build();

        String uploadUrl = presign(presignRequest, mediaKey);
        log.debug("미디어 업로드 URL을 발급했습니다. key={}", mediaKey);

        return MediaConverter.toPresignedUrl(
                uploadUrl,
                mediaKey,
                s3Properties.getPublicBaseUrl(),
                LocalDateTime.now().plus(expiration)
        );
    }

    private String presign(PutObjectPresignRequest presignRequest, String mediaKey) {
        try {
            return s3Presigner.presignPutObject(presignRequest).url().toString();
        } catch (RuntimeException e) {
            log.error("S3 presigned URL 발급에 실패했습니다. key={}", mediaKey, e);
            throw new MediaException(MediaErrorCode.PRESIGNED_URL_ISSUE_FAILED);
        }
    }

    private MediaContentType resolveContentType(String contentType) {
        return MediaContentType.from(contentType)
                .orElseThrow(() -> {
                    log.warn("허용하지 않는 미디어 형식으로 업로드를 시도했습니다. contentType={}", contentType);
                    return new MediaException(MediaErrorCode.UNSUPPORTED_CONTENT_TYPE);
                });
    }

    /**
     * 형식 자체는 허용되더라도, 용도가 그 카테고리를 허용하지 않으면 거부한다.
     * (예: 영상이 켜졌을 때 프로필에는 사진만 허용) 사진만 운영하는 현재는
     * 모든 용도가 IMAGE만 허용하므로, 사진 형식 요청은 이 검증을 통과한다.
     */
    private void validatePurposeAllows(MediaPurpose purpose, MediaContentType contentType) {
        if (!purpose.allows(contentType.getCategory())) {
            log.warn("해당 용도에서 허용하지 않는 형식입니다. purpose={}, contentType={}",
                    purpose, contentType.getMimeType());
            throw new MediaException(MediaErrorCode.CONTENT_TYPE_NOT_ALLOWED_FOR_PURPOSE);
        }
    }

    private void validateFileSize(MediaCategory category, long contentLength) {
        long maxFileSize = maxFileSizeFor(category);
        if (contentLength > maxFileSize) {
            log.warn("최대 용량을 초과한 업로드를 시도했습니다. category={}, contentLength={}, max={}",
                    category, contentLength, maxFileSize);
            throw new MediaException(MediaErrorCode.FILE_TOO_LARGE);
        }
    }

    private long maxFileSizeFor(MediaCategory category) {
        return switch (category) {
            case IMAGE -> s3Properties.getMaxImageSize();
            case VIDEO -> s3Properties.getMaxVideoSize();
        };
    }

    /**
     * key는 추측할 수 없어야 한다. UUID를 사용해 다른 사용자의 미디어 경로를 유추하지 못하게 한다.
     * 클라이언트가 보낸 파일명은 신뢰하지 않고 사용하지 않는다(경로 조작 방지).
     */
    private String generateMediaKey(MediaPurpose purpose, MediaContentType contentType) {
        return "%s/%s.%s".formatted(
                purpose.getPathPrefix(),
                UUID.randomUUID(),
                contentType.getExtension()
        );
    }
}
