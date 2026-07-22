package com.lirouti.domain.media.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.lirouti.domain.media.dto.request.MediaReqDTO;
import com.lirouti.domain.media.dto.response.MediaResDTO;
import com.lirouti.domain.media.enums.MediaPurpose;
import com.lirouti.domain.media.exception.MediaException;
import com.lirouti.domain.media.exception.code.error.MediaErrorCode;
import com.lirouti.global.properties.S3Properties;

import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@ExtendWith(MockitoExtension.class)
@DisplayName("MediaService 테스트")
class MediaServiceTest {
    private static final String BUCKET = "lirouti-media";
    private static final String PUBLIC_BASE_URL = "https://cdn.lirouti.com";
    private static final long MAX_IMAGE_SIZE = 10 * 1024 * 1024L;
    private static final long MAX_VIDEO_SIZE = 50 * 1024 * 1024L;
    private static final String UPLOAD_URL = "https://lirouti-media.s3.amazonaws.com/signed";
    private static final Instant EXPIRES_AT = Instant.parse("2026-07-15T12:00:00Z");

    @Mock
    private S3Presigner s3Presigner;

    private S3Properties s3Properties;
    private MediaService mediaService;

    @BeforeEach
    void setUp() {
        s3Properties = new S3Properties();
        s3Properties.setBucket(BUCKET);
        s3Properties.setPresignedUrlExpiration(Duration.ofMinutes(5));
        s3Properties.setMaxImageSize(MAX_IMAGE_SIZE);
        s3Properties.setMaxVideoSize(MAX_VIDEO_SIZE);
        s3Properties.setPublicBaseUrl(PUBLIC_BASE_URL);

        mediaService = new MediaService(s3Presigner, s3Properties);
    }

    private void mockPresign() {
        PresignedPutObjectRequest presigned = mock(PresignedPutObjectRequest.class);
        try {
            when(presigned.url()).thenReturn(URI.create(UPLOAD_URL).toURL());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        when(presigned.expiration()).thenReturn(EXPIRES_AT);
        when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(presigned);
    }

    @Test
    @DisplayName("허용된 사진 형식이면 업로드 URL과 key를 발급한다")
    void issuePresignedUrl_ValidImage_ReturnsUploadUrlAndKey() {
        // given
        mockPresign();
        MediaReqDTO.PresignedUrl request = new MediaReqDTO.PresignedUrl(
                MediaPurpose.CHALLENGE_VERIFICATION, "image/jpeg", 1024L
        );

        // when
        MediaResDTO.PresignedUrl response = mediaService.issuePresignedUrl(request);

        // then
        assertThat(response.uploadUrl()).isEqualTo(UPLOAD_URL);
        assertThat(response.mediaKey()).startsWith("challenge-verifications/").endsWith(".jpg");
        assertThat(response.mediaUrl()).isEqualTo(PUBLIC_BASE_URL + "/" + response.mediaKey());
        // 만료 시각은 로컬 재계산이 아니라 SDK가 서명에 부여한 값을 그대로 사용한다.
        assertThat(response.expiresAt()).isEqualTo(EXPIRES_AT);
    }

    @Test
    @DisplayName("대소문자·공백이 섞인 Content-Type도 허용하고, 응답에는 서명에 쓴 정규화 값을 내려준다")
    void issuePresignedUrl_MixedCaseContentType_ReturnsNormalizedValueToUse() {
        // given
        mockPresign();
        MediaReqDTO.PresignedUrl request = new MediaReqDTO.PresignedUrl(
                MediaPurpose.CHALLENGE_VERIFICATION, " iMAge/JPeG ", 1024L
        );

        // when
        MediaResDTO.PresignedUrl response = mediaService.issuePresignedUrl(request);

        // then — 클라이언트는 원본이 아니라 이 값을 PUT 헤더로 써야 서명이 일치한다
        assertThat(response.contentType()).isEqualTo("image/jpeg");
        assertThat(response.contentLength()).isEqualTo(1024L);
        assertThat(response.mediaKey()).endsWith(".jpg");
    }

    @Test
    @DisplayName("서명에 사용한 Content-Type(정규화 값)을 서명 요청과 응답에 동일하게 싣는다")
    void issuePresignedUrl_SignedContentTypeMatchesResponse() {
        // given
        mockPresign();
        MediaReqDTO.PresignedUrl request = new MediaReqDTO.PresignedUrl(
                MediaPurpose.PROFILE, "IMAGE/PNG", 2048L
        );

        // when
        MediaResDTO.PresignedUrl response = mediaService.issuePresignedUrl(request);

        // then — 서명에 들어간 값과 응답으로 안내하는 값이 같아야 한다
        ArgumentCaptor<PutObjectPresignRequest> captor =
                ArgumentCaptor.forClass(PutObjectPresignRequest.class);
        verify(s3Presigner).presignPutObject(captor.capture());
        String signed = captor.getValue().putObjectRequest().contentType();

        assertThat(signed).isEqualTo("image/png");
        assertThat(response.contentType()).isEqualTo(signed);
    }

    @Test
    @DisplayName("서명에 Content-Type과 Content-Length를 포함시켜 S3가 위반을 거부하게 한다")
    void issuePresignedUrl_SignsContentTypeAndLength() {
        // given
        mockPresign();
        MediaReqDTO.PresignedUrl request = new MediaReqDTO.PresignedUrl(
                MediaPurpose.PROFILE, "image/png", 2048L
        );

        // when
        mediaService.issuePresignedUrl(request);

        // then
        ArgumentCaptor<PutObjectPresignRequest> captor =
                ArgumentCaptor.forClass(PutObjectPresignRequest.class);
        verify(s3Presigner).presignPutObject(captor.capture());

        PutObjectRequest signed = captor.getValue().putObjectRequest();
        assertThat(signed.bucket()).isEqualTo(BUCKET);
        assertThat(signed.contentType()).isEqualTo("image/png");
        assertThat(signed.contentLength()).isEqualTo(2048L);
        assertThat(signed.key()).startsWith("profiles/").endsWith(".png");
    }

    @Test
    @DisplayName("같은 요청이어도 매번 다른 key가 나온다 (UUID, 파일명 미사용)")
    void issuePresignedUrl_KeyIsUnguessable() {
        // given
        mockPresign();
        MediaReqDTO.PresignedUrl request = new MediaReqDTO.PresignedUrl(
                MediaPurpose.CHALLENGE_VERIFICATION, "image/webp", 512L
        );

        // when
        String firstKey = mediaService.issuePresignedUrl(request).mediaKey();
        String secondKey = mediaService.issuePresignedUrl(request).mediaKey();

        // then
        assertThat(firstKey).isNotEqualTo(secondKey);
    }

    @Test
    @DisplayName("허용하지 않는 형식이면 예외를 던지고 서명을 시도하지 않는다")
    void issuePresignedUrl_UnsupportedContentType_ThrowsException() {
        // given
        MediaReqDTO.PresignedUrl request = new MediaReqDTO.PresignedUrl(
                MediaPurpose.CHALLENGE_VERIFICATION, "application/pdf", 1024L
        );

        // when & then
        assertThatThrownBy(() -> mediaService.issuePresignedUrl(request))
                .isInstanceOf(MediaException.class)
                .hasFieldOrPropertyWithValue("code", MediaErrorCode.UNSUPPORTED_CONTENT_TYPE);

        verify(s3Presigner, never()).presignPutObject(any(PutObjectPresignRequest.class));
    }

    @Test
    @DisplayName("이미지 최대 용량을 초과하면 예외를 던진다")
    void issuePresignedUrl_ImageTooLarge_ThrowsException() {
        // given
        MediaReqDTO.PresignedUrl request = new MediaReqDTO.PresignedUrl(
                MediaPurpose.CHALLENGE_VERIFICATION, "image/jpeg", MAX_IMAGE_SIZE + 1
        );

        // when & then
        assertThatThrownBy(() -> mediaService.issuePresignedUrl(request))
                .isInstanceOf(MediaException.class)
                .hasFieldOrPropertyWithValue("code", MediaErrorCode.FILE_TOO_LARGE);

        verify(s3Presigner, never()).presignPutObject(any(PutObjectPresignRequest.class));
    }

    @Test
    @DisplayName("이미지 최대 용량과 같으면 발급에 성공한다 (경계값)")
    void issuePresignedUrl_ExactlyMaxImageSize_Succeeds() {
        // given
        mockPresign();
        MediaReqDTO.PresignedUrl request = new MediaReqDTO.PresignedUrl(
                MediaPurpose.CHALLENGE_VERIFICATION, "image/jpeg", MAX_IMAGE_SIZE
        );

        // when
        MediaResDTO.PresignedUrl response = mediaService.issuePresignedUrl(request);

        // then
        assertThat(response.uploadUrl()).isEqualTo(UPLOAD_URL);
    }

    @Test
    @DisplayName("공개 주소 끝의 슬래시는 중복되지 않게 정리한다")
    void resolvePublicUrl_TrailingSlashBase_DoesNotDuplicateSeparator() {
        // given
        s3Properties.setPublicBaseUrl(PUBLIC_BASE_URL + "/");

        // when
        String url = mediaService.resolvePublicUrl("challenge-verifications/abc.jpg");

        // then
        assertThat(url).isEqualTo(PUBLIC_BASE_URL + "/challenge-verifications/abc.jpg");
    }

    @Test
    @DisplayName("발급한 key는 그대로 검증을 통과한다")
    void validateMediaKey_IssuedKey_Passes() {
        // given — 실제 발급 경로로 만든 key를 그대로 검증에 넣는다(발급 규칙과 검증 규칙의 불일치 방지)
        mockPresign();
        String issuedKey = mediaService.issuePresignedUrl(new MediaReqDTO.PresignedUrl(
                MediaPurpose.CHALLENGE_VERIFICATION, "image/jpeg", 1024L
        )).mediaKey();

        // when & then
        assertThatCode(() ->
                mediaService.validateMediaKey(issuedKey, MediaPurpose.CHALLENGE_VERIFICATION))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @DisplayName("발급 규칙에 맞지 않는 key는 거부한다")
    @ValueSource(strings = {
            "profiles/6d3f5a20-1b2c-4d5e-8f90-0a1b2c3d4e5f.jpg",   // 다른 용도의 경로
            "challenge-verifications/../../etc/passwd",             // 경로 조작 시도
            "challenge-verifications/not-a-uuid.jpg",               // UUID가 아님
            "challenge-verifications/6d3f5a20-1b2c-4d5e-8f90-0a1b2c3d4e5f.mp4", // 사진 전용 용도인데 영상 확장자
            "challenge-verifications/6d3f5a20-1b2c-4d5e-8f90-0a1b2c3d4e5f",     // 확장자 없음
            "6d3f5a20-1b2c-4d5e-8f90-0a1b2c3d4e5f.jpg"              // 용도 경로 없음
    })
    void validateMediaKey_NotIssuedFormat_ThrowsInvalidMediaKey(String mediaKey) {
        assertThatThrownBy(() ->
                mediaService.validateMediaKey(mediaKey, MediaPurpose.CHALLENGE_VERIFICATION))
                .isInstanceOf(MediaException.class)
                .hasFieldOrPropertyWithValue("code", MediaErrorCode.INVALID_MEDIA_KEY);
    }

    @Test
    @DisplayName("key가 null이면 거부한다")
    void validateMediaKey_Null_ThrowsInvalidMediaKey() {
        assertThatThrownBy(() ->
                mediaService.validateMediaKey(null, MediaPurpose.CHALLENGE_VERIFICATION))
                .isInstanceOf(MediaException.class)
                .hasFieldOrPropertyWithValue("code", MediaErrorCode.INVALID_MEDIA_KEY);
    }

    @Test
    @DisplayName("S3 서명에 실패하면 발급 실패 예외로 변환한다")
    void issuePresignedUrl_PresignerFails_ThrowsIssueFailed() {
        // given
        when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class)))
                .thenThrow(new RuntimeException("S3 unavailable"));
        MediaReqDTO.PresignedUrl request = new MediaReqDTO.PresignedUrl(
                MediaPurpose.CHALLENGE_VERIFICATION, "image/jpeg", 1024L
        );

        // when & then
        assertThatThrownBy(() -> mediaService.issuePresignedUrl(request))
                .isInstanceOf(MediaException.class)
                .hasFieldOrPropertyWithValue("code", MediaErrorCode.PRESIGNED_URL_ISSUE_FAILED);
    }
}
