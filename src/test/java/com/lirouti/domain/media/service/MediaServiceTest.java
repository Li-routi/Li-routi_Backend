package com.lirouti.domain.media.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.Duration;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
        assertThat(response.expiresAt()).isAfter(LocalDateTime.now());
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
    @DisplayName("공개 주소가 설정되지 않으면 mediaUrl은 null이고 key만 내려준다")
    void issuePresignedUrl_NoPublicBaseUrl_ReturnsNullMediaUrl() {
        // given
        s3Properties.setPublicBaseUrl("");
        mockPresign();
        MediaReqDTO.PresignedUrl request = new MediaReqDTO.PresignedUrl(
                MediaPurpose.CHALLENGE_VERIFICATION, "image/jpeg", 1024L
        );

        // when
        MediaResDTO.PresignedUrl response = mediaService.issuePresignedUrl(request);

        // then
        assertThat(response.mediaUrl()).isNull();
        assertThat(response.mediaKey()).isNotBlank();
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
