package com.lirouti.domain.image.service;

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

import com.lirouti.domain.image.dto.request.ImageReqDTO;
import com.lirouti.domain.image.dto.response.ImageResDTO;
import com.lirouti.domain.image.enums.ImagePurpose;
import com.lirouti.domain.image.exception.ImageException;
import com.lirouti.domain.image.exception.code.error.ImageErrorCode;
import com.lirouti.global.properties.S3Properties;

import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@ExtendWith(MockitoExtension.class)
@DisplayName("ImageService 테스트")
class ImageServiceTest {
    private static final String BUCKET = "lirouti-images";
    private static final String PUBLIC_BASE_URL = "https://cdn.lirouti.com";
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024L;
    private static final String UPLOAD_URL = "https://lirouti-images.s3.amazonaws.com/signed";

    @Mock
    private S3Presigner s3Presigner;

    private S3Properties s3Properties;
    private ImageService imageService;

    @BeforeEach
    void setUp() {
        s3Properties = new S3Properties();
        s3Properties.setBucket(BUCKET);
        s3Properties.setPresignedUrlExpiration(Duration.ofMinutes(5));
        s3Properties.setMaxFileSize(MAX_FILE_SIZE);
        s3Properties.setPublicBaseUrl(PUBLIC_BASE_URL);

        imageService = new ImageService(s3Presigner, s3Properties);
    }

    @Test
    @DisplayName("허용된 형식이면 업로드 URL과 key를 발급한다")
    void issuePresignedUrl_ValidRequest_ReturnsUploadUrlAndKey() throws Exception {
        // given
        PresignedPutObjectRequest presigned = mock(PresignedPutObjectRequest.class);
        when(presigned.url()).thenReturn(URI.create(UPLOAD_URL).toURL());
        when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(presigned);

        ImageReqDTO.PresignedUrl request = new ImageReqDTO.PresignedUrl(
                ImagePurpose.CHALLENGE_VERIFICATION, "image/jpeg", 1024L
        );

        // when
        ImageResDTO.PresignedUrl response = imageService.issuePresignedUrl(request);

        // then
        assertThat(response.uploadUrl()).isEqualTo(UPLOAD_URL);
        assertThat(response.imageKey()).startsWith("challenge-verifications/").endsWith(".jpg");
        assertThat(response.imageUrl()).isEqualTo(PUBLIC_BASE_URL + "/" + response.imageKey());
        assertThat(response.expiresAt()).isAfter(LocalDateTime.now());
    }

    @Test
    @DisplayName("서명에 Content-Type과 Content-Length를 포함시켜 S3가 위반을 거부하게 한다")
    void issuePresignedUrl_ValidRequest_SignsContentTypeAndLength() throws Exception {
        // given
        PresignedPutObjectRequest presigned = mock(PresignedPutObjectRequest.class);
        when(presigned.url()).thenReturn(URI.create(UPLOAD_URL).toURL());
        when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(presigned);

        ImageReqDTO.PresignedUrl request = new ImageReqDTO.PresignedUrl(
                ImagePurpose.PROFILE, "image/png", 2048L
        );

        // when
        imageService.issuePresignedUrl(request);

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
    @DisplayName("클라이언트가 보낸 파일명은 key에 사용하지 않는다")
    void issuePresignedUrl_KeyIsUnguessable() throws Exception {
        // given
        PresignedPutObjectRequest presigned = mock(PresignedPutObjectRequest.class);
        when(presigned.url()).thenReturn(URI.create(UPLOAD_URL).toURL());
        when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(presigned);

        ImageReqDTO.PresignedUrl request = new ImageReqDTO.PresignedUrl(
                ImagePurpose.CHALLENGE_VERIFICATION, "image/webp", 512L
        );

        // when
        String firstKey = imageService.issuePresignedUrl(request).imageKey();
        String secondKey = imageService.issuePresignedUrl(request).imageKey();

        // then — 같은 요청이어도 매번 다른 key가 나온다(UUID)
        assertThat(firstKey).isNotEqualTo(secondKey);
    }

    @Test
    @DisplayName("허용하지 않는 형식이면 예외를 던지고 서명을 시도하지 않는다")
    void issuePresignedUrl_UnsupportedContentType_ThrowsException() {
        // given
        ImageReqDTO.PresignedUrl request = new ImageReqDTO.PresignedUrl(
                ImagePurpose.CHALLENGE_VERIFICATION, "application/pdf", 1024L
        );

        // when & then
        assertThatThrownBy(() -> imageService.issuePresignedUrl(request))
                .isInstanceOf(ImageException.class)
                .hasFieldOrPropertyWithValue("code", ImageErrorCode.UNSUPPORTED_CONTENT_TYPE);

        verify(s3Presigner, never()).presignPutObject(any(PutObjectPresignRequest.class));
    }

    @Test
    @DisplayName("최대 용량을 초과하면 예외를 던지고 서명을 시도하지 않는다")
    void issuePresignedUrl_FileTooLarge_ThrowsException() {
        // given
        ImageReqDTO.PresignedUrl request = new ImageReqDTO.PresignedUrl(
                ImagePurpose.CHALLENGE_VERIFICATION, "image/jpeg", MAX_FILE_SIZE + 1
        );

        // when & then
        assertThatThrownBy(() -> imageService.issuePresignedUrl(request))
                .isInstanceOf(ImageException.class)
                .hasFieldOrPropertyWithValue("code", ImageErrorCode.FILE_TOO_LARGE);

        verify(s3Presigner, never()).presignPutObject(any(PutObjectPresignRequest.class));
    }

    @Test
    @DisplayName("최대 용량과 같으면 발급에 성공한다(경계값)")
    void issuePresignedUrl_ExactlyMaxFileSize_Succeeds() throws Exception {
        // given
        PresignedPutObjectRequest presigned = mock(PresignedPutObjectRequest.class);
        when(presigned.url()).thenReturn(URI.create(UPLOAD_URL).toURL());
        when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(presigned);

        ImageReqDTO.PresignedUrl request = new ImageReqDTO.PresignedUrl(
                ImagePurpose.CHALLENGE_VERIFICATION, "image/jpeg", MAX_FILE_SIZE
        );

        // when
        ImageResDTO.PresignedUrl response = imageService.issuePresignedUrl(request);

        // then
        assertThat(response.uploadUrl()).isEqualTo(UPLOAD_URL);
    }

    @Test
    @DisplayName("공개 주소가 설정되지 않으면 imageUrl은 null이고 key만 내려준다")
    void issuePresignedUrl_NoPublicBaseUrl_ReturnsNullImageUrl() throws Exception {
        // given
        s3Properties.setPublicBaseUrl("");

        PresignedPutObjectRequest presigned = mock(PresignedPutObjectRequest.class);
        when(presigned.url()).thenReturn(URI.create(UPLOAD_URL).toURL());
        when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(presigned);

        ImageReqDTO.PresignedUrl request = new ImageReqDTO.PresignedUrl(
                ImagePurpose.CHALLENGE_VERIFICATION, "image/jpeg", 1024L
        );

        // when
        ImageResDTO.PresignedUrl response = imageService.issuePresignedUrl(request);

        // then
        assertThat(response.imageUrl()).isNull();
        assertThat(response.imageKey()).isNotBlank();
    }

    @Test
    @DisplayName("S3 서명에 실패하면 발급 실패 예외로 변환한다")
    void issuePresignedUrl_PresignerFails_ThrowsIssueFailed() {
        // given
        when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class)))
                .thenThrow(new RuntimeException("S3 unavailable"));

        ImageReqDTO.PresignedUrl request = new ImageReqDTO.PresignedUrl(
                ImagePurpose.CHALLENGE_VERIFICATION, "image/jpeg", 1024L
        );

        // when & then
        assertThatThrownBy(() -> imageService.issuePresignedUrl(request))
                .isInstanceOf(ImageException.class)
                .hasFieldOrPropertyWithValue("code", ImageErrorCode.PRESIGNED_URL_ISSUE_FAILED);
    }
}
