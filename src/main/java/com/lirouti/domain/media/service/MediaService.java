package com.lirouti.domain.media.service;

import java.time.Duration;
import java.util.Arrays;
import java.util.UUID;
import java.util.regex.Pattern;

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
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

/**
 * 미디어 업로드용 presigned URL을 발급하고, 저장된 미디어 key의 검증·URL 조립을 담당한다.
 * DB에는 오브젝트 key만 저장하므로(database-schema.md), key ↔ 공개 URL 규칙은 이 클래스가 소유한다.
 * DB를 다루지 않아 조회/변경 구분이 무의미하므로 CQRS를 적용하지 않는다.
 * (service_convention.md의 CQRS 예외 도메인)
 *
 * 현재는 사진만 업로드한다. 영상은 형식·용도·용량 구조만 갖춰두었고 실제로 켜져 있지 않다.
 * 영상을 켜려면 MediaContentType에 영상 형식을 추가하고, 해당 MediaPurpose의 허용 카테고리에
 * MediaCategory.VIDEO를 넣으면 이 검증 흐름이 그대로 적용된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MediaService {
    // generateMediaKey가 UUID.randomUUID()로 만드는 형태. 소문자 16진수 고정이다.
    private static final Pattern ISSUED_KEY_UUID =
            Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    private final S3Presigner s3Presigner;
    private final S3Properties s3Properties;

    public MediaResDTO.PresignedUrl issuePresignedUrl(MediaReqDTO.PresignedUrl request) {
        MediaContentType contentType = resolveContentType(request.contentType());
        validatePurposeAllows(request.purpose(), contentType);
        validateFileSize(contentType.getCategory(), request.contentLength());

        String mediaKey = generateMediaKey(request.purpose(), contentType);
        Duration expiration = s3Properties.getPresignedUrlExpiration();

        // 서명에는 정규화된 MIME 값을 쓴다. 요청 원본(대소문자·공백)이 아니라 이 값이 서명에 들어가므로,
        // 클라이언트는 응답으로 돌려주는 이 값을 그대로 PUT 헤더에 실어야 서명이 일치한다.
        String signedContentType = contentType.getMimeType();

        // 서명에 Content-Type과 Content-Length를 포함시킨다.
        // 클라이언트가 다른 형식이나 크기로 업로드하면 S3가 거부하므로,
        // 서버가 파일 바이트를 보지 않고도 검증 결과를 강제할 수 있다.
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(s3Properties.getBucket())
                .key(mediaKey)
                .contentType(signedContentType)
                .contentLength(request.contentLength())
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(expiration)
                .putObjectRequest(putObjectRequest)
                .build();

        PresignedPutObjectRequest presigned = presign(presignRequest, mediaKey);
        log.debug("미디어 업로드 URL을 발급했습니다. key={}", mediaKey);

        // 만료 시각은 로컬에서 재계산하지 않고, SDK가 서명에 실제로 부여한 값을 그대로 쓴다.
        // now().plus(...)는 서명 시점과 미세하게 어긋날 수 있다.
        return MediaConverter.toPresignedUrl(
                presigned.url().toString(),
                mediaKey,
                resolvePublicUrl(mediaKey),
                signedContentType,
                request.contentLength(),
                presigned.expiration()
        );
    }

    /**
     * 저장된 오브젝트 key를 클라이언트가 읽을 수 있는 공개 URL로 조립한다.
     * DB에는 key만 저장하므로 조회 응답을 만들 때마다 이 메서드를 거친다.
     * public-base-url은 필수 설정이라 null·빈 값은 부팅 시점에 걸러진다.
     */
    public String resolvePublicUrl(String mediaKey) {
        String base = s3Properties.getPublicBaseUrl();
        String normalizedBase = base.endsWith("/")
                ? base.substring(0, base.length() - 1)
                : base;
        return normalizedBase + "/" + mediaKey;
    }

    /**
     * 클라이언트가 보낸 key가 그 용도로 발급한 key의 형식인지 검증한다.
     *
     * key는 서버가 발급하지만 업로드 후 요청 본문으로 되돌아오므로 그대로 믿을 수 없다.
     * 다른 용도의 경로나 임의 문자열이 저장되지 않도록 발급 규칙({@code prefix/UUID.확장자})과 대조한다.
     * 실제 오브젝트가 업로드됐는지, 그 바이트가 정말 이미지인지는 확인하지 않는다(#22·#19 범위).
     */
    public void validateMediaKey(String mediaKey, MediaPurpose purpose) {
        if (!matchesIssuedKeyFormat(mediaKey, purpose)) {
            log.warn("발급 규칙에 맞지 않는 미디어 key입니다. purpose={}, mediaKey={}", purpose, mediaKey);
            throw new MediaException(MediaErrorCode.INVALID_MEDIA_KEY);
        }
    }

    private boolean matchesIssuedKeyFormat(String mediaKey, MediaPurpose purpose) {
        if (mediaKey == null) {
            return false;
        }
        String prefix = purpose.getPathPrefix() + "/";
        if (!mediaKey.startsWith(prefix)) {
            return false;
        }
        String fileName = mediaKey.substring(prefix.length());
        int extensionSeparator = fileName.lastIndexOf('.');
        if (extensionSeparator < 0) {
            return false;
        }
        String baseName = fileName.substring(0, extensionSeparator);
        String extension = fileName.substring(extensionSeparator + 1);
        return ISSUED_KEY_UUID.matcher(baseName).matches() && isExtensionAllowedFor(purpose, extension);
    }

    /** 그 용도가 허용하는 카테고리의 형식들만 확장자로 인정한다(사진 전용 용도에 mp4 key 방지). */
    private boolean isExtensionAllowedFor(MediaPurpose purpose, String extension) {
        return Arrays.stream(MediaContentType.values())
                .filter(type -> purpose.allows(type.getCategory()))
                .anyMatch(type -> type.getExtension().equals(extension));
    }

    private PresignedPutObjectRequest presign(PutObjectPresignRequest presignRequest, String mediaKey) {
        try {
            return s3Presigner.presignPutObject(presignRequest);
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
