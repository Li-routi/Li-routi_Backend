package com.lirouti.domain.media.service;

import com.lirouti.domain.media.dto.request.MediaReqDTO;
import com.lirouti.domain.media.dto.response.MediaResDTO;
import com.lirouti.domain.media.enums.MediaPurpose;
import com.lirouti.domain.media.exception.MediaException;
import com.lirouti.domain.media.exception.code.error.MediaErrorCode;
import com.lirouti.global.properties.S3Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MediaService 테스트")
class MediaServiceTest {
    private static final String BUCKET = "lirouti-media";
    private static final String PUBLIC_BASE_URL = "https://cdn.lirouti.com";
    private static final long MAX_IMAGE_SIZE = 10 * 1024 * 1024L;
    private static final long MAX_VIDEO_SIZE = 50 * 1024 * 1024L;
    private static final String UPLOAD_URL = "https://lirouti-media.s3.amazonaws.com/signed";
    private static final Instant EXPIRES_AT = Instant.parse("2026-07-15T12:00:00Z");
    // 업로드용(5분)과 일부러 다른 값을 쓴다. 둘을 바꿔 쓰면 테스트가 잡아야 한다.
    private static final Duration VIEW_EXPIRATION = Duration.ofMinutes(15);
    private static final String VIEW_URL = "https://lirouti-media.s3.amazonaws.com/signed-get";

    @Mock
    private S3Presigner s3Presigner;
    @Mock
    private S3Client s3Client;

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
        s3Properties.setViewUrlExpiration(VIEW_EXPIRATION);

        mediaService = new MediaService(s3Presigner, s3Client, s3Properties);
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
    @DisplayName("챌린지 인증은 비공개 대기 prefix로 받는다 — 심사 전에는 공개 주소가 없다")
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
        // 공개 prefix 가 아니라 대기 prefix 다. 뒤의 슬래시 때문에 둘은 서로 접두사가 아니다 —
        // 그래서 버킷 정책의 challenge-verifications/* 와일드카드에 걸리지 않는다.
        assertThat(response.mediaKey())
                .startsWith("challenge-verifications-staging/")
                .endsWith(".jpg");
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
            "challenge-verifications-staging/../../etc/passwd",             // 경로 조작 시도
            "challenge-verifications-staging/not-a-uuid.jpg",               // UUID가 아님
            "challenge-verifications-staging/6d3f5a20-1b2c-4d5e-8f90-0a1b2c3d4e5f.mp4", // 사진 전용 용도인데 영상 확장자
            "challenge-verifications-staging/6d3f5a20-1b2c-4d5e-8f90-0a1b2c3d4e5f",     // 확장자 없음
            "6d3f5a20-1b2c-4d5e-8f90-0a1b2c3d4e5f.jpg",             // 용도 경로 없음
            // 날짜 구간이 형식에 안 맞는 경우(#39). 임의 세그먼트를 끼워 넣지 못하게 한다.
            "challenge-verifications-staging/2026/7/30/6d3f5a20-1b2c-4d5e-8f90-0a1b2c3d4e5f.jpg",  // 월·일이 두 자리 아님
            "challenge-verifications-staging/2026/07/6d3f5a20-1b2c-4d5e-8f90-0a1b2c3d4e5f.jpg",    // 구간이 둘뿐
            "challenge-verifications-staging/3/412/6d3f5a20-1b2c-4d5e-8f90-0a1b2c3d4e5f.jpg",      // 식별자를 끼워 넣음
            "challenge-verifications-staging/2026/07/30/31/6d3f5a20-1b2c-4d5e-8f90-0a1b2c3d4e5f.jpg" // 구간이 넷
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

    // ── 업로드 바이트 검증 (#22) ──

    private static final String JPEG_KEY =
            "challenge-verifications-staging/aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa.jpg";

    /** S3가 Range GET으로 돌려줄 앞부분 바이트를 흉내낸다. */
    private void mockHeadBytes(int... bytes) {
        byte[] head = new byte[bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            head[i] = (byte) bytes[i];
        }
        when(s3Client.getObject(any(GetObjectRequest.class)))
                .thenReturn(new ResponseInputStream<>(
                        GetObjectResponse.builder().build(),
                        AbortableInputStream.create(new ByteArrayInputStream(head))));
    }

    @Test
    @DisplayName("바이트가 선언한 형식(JPEG)과 맞으면 통과한다")
    void validateUploadedBytes_MatchingSignature_Passes() {
        mockHeadBytes(0xFF, 0xD8, 0xFF, 0xE0, 0x00, 0x10, 0x4A, 0x46, 0x49, 0x46, 0x00, 0x01);

        assertThatCode(() ->
                mediaService.validateUploadedBytes(JPEG_KEY, MediaPurpose.CHALLENGE_VERIFICATION))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("확장자는 jpg인데 실제 바이트가 이미지가 아니면 422 — presigned 서명이 못 막는 경로다")
    void validateUploadedBytes_NotAnImage_Throws422() {
        // ELF 실행 파일 헤더(0x7F 'E' 'L' 'F'). Content-Type: image/jpeg로 선언해도 올라갈 수 있다.
        mockHeadBytes(0x7F, 0x45, 0x4C, 0x46, 0x02, 0x01, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00);

        assertThatThrownBy(() ->
                mediaService.validateUploadedBytes(JPEG_KEY, MediaPurpose.CHALLENGE_VERIFICATION))
                .isInstanceOf(MediaException.class)
                .hasFieldOrPropertyWithValue("code", MediaErrorCode.MEDIA_CONTENT_MISMATCH);
    }

    @Test
    @DisplayName("PNG 바이트를 jpg key로 올려도 422 — 형식이 서로 뒤바뀐 경우")
    void validateUploadedBytes_WrongImageFormat_Throws422() {
        mockHeadBytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D);

        assertThatThrownBy(() ->
                mediaService.validateUploadedBytes(JPEG_KEY, MediaPurpose.CHALLENGE_VERIFICATION))
                .isInstanceOf(MediaException.class)
                .hasFieldOrPropertyWithValue("code", MediaErrorCode.MEDIA_CONTENT_MISMATCH);
    }

    @Test
    @DisplayName("파일이 시그니처보다 짧아도 422 — 빈 파일을 올린 경우")
    void validateUploadedBytes_TooShort_Throws422() {
        mockHeadBytes(0xFF, 0xD8);

        assertThatThrownBy(() ->
                mediaService.validateUploadedBytes(JPEG_KEY, MediaPurpose.CHALLENGE_VERIFICATION))
                .isInstanceOf(MediaException.class)
                .hasFieldOrPropertyWithValue("code", MediaErrorCode.MEDIA_CONTENT_MISMATCH);
    }

    @Test
    @DisplayName("업로드하지 않은 key면 404 — 존재 확인을 겸한다")
    void validateUploadedBytes_NotUploaded_Throws404() {
        when(s3Client.getObject(any(GetObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().message("not found").build());

        assertThatThrownBy(() ->
                mediaService.validateUploadedBytes(JPEG_KEY, MediaPurpose.CHALLENGE_VERIFICATION))
                .isInstanceOf(MediaException.class)
                .hasFieldOrPropertyWithValue("code", MediaErrorCode.MEDIA_NOT_UPLOADED);
    }

    @Test
    @DisplayName("S3 조회 자체가 실패하면 500 — 사용자 잘못이 아니므로 저장은 막되 4xx로 돌리지 않는다")
    void validateUploadedBytes_S3Failure_Throws500() {
        when(s3Client.getObject(any(GetObjectRequest.class)))
                .thenThrow(SdkClientException.create("timeout"));

        assertThatThrownBy(() ->
                mediaService.validateUploadedBytes(JPEG_KEY, MediaPurpose.CHALLENGE_VERIFICATION))
                .isInstanceOf(MediaException.class)
                .hasFieldOrPropertyWithValue("code", MediaErrorCode.MEDIA_VALIDATION_FAILED);
    }

    @Test
    @DisplayName("검증을 끄면 S3를 아예 호출하지 않는다 — 탈출구가 실제로 동작하는지")
    void validateUploadedBytes_Disabled_SkipsS3Call() {
        s3Properties.setByteValidationEnabled(false);

        assertThatCode(() ->
                mediaService.validateUploadedBytes(JPEG_KEY, MediaPurpose.CHALLENGE_VERIFICATION))
                .doesNotThrowAnyException();
        verifyNoInteractions(s3Client);
    }

    @Test
    @DisplayName("없는 key에 403이 와도 404로 돌려준다 — ListBucket이 없으면 S3가 존재 여부를 숨긴다")
    void validateUploadedBytes_ForbiddenBecauseNoListBucket_TreatedAsNotUploaded() {
        // 실측(2026-07-27): 최소 권한 정책에는 s3:ListBucket이 없어서, 업로드하지 않은 key를
        // GetObject 하면 NoSuchKey가 아니라 AccessDenied(403)가 온다.
        when(s3Client.getObject(any(GetObjectRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(403).message("AccessDenied").build());

        assertThatThrownBy(() ->
                mediaService.validateUploadedBytes(JPEG_KEY, MediaPurpose.CHALLENGE_VERIFICATION))
                .isInstanceOf(MediaException.class)
                .hasFieldOrPropertyWithValue("code", MediaErrorCode.MEDIA_NOT_UPLOADED);
    }

    @Test
    @DisplayName("빈 오브젝트의 416은 422로 돌려준다 — 읽을 바이트가 없다는 뜻이지 서버 오류가 아니다")
    void validateUploadedBytes_EmptyObjectRange_Throws422() {
        // 0바이트 오브젝트는 시작 오프셋 0조차 범위 밖이라 Range GET이 416(InvalidRange)을 준다.
        when(s3Client.getObject(any(GetObjectRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(416).message("InvalidRange").build());

        assertThatThrownBy(() ->
                mediaService.validateUploadedBytes(JPEG_KEY, MediaPurpose.CHALLENGE_VERIFICATION))
                .isInstanceOf(MediaException.class)
                .hasFieldOrPropertyWithValue("code", MediaErrorCode.MEDIA_CONTENT_MISMATCH);
    }

    // WEBP만 시그니처가 두 구간(0~3 "RIFF", 8~11 "WEBP")으로 나뉜다.
    // 앞 구간만 보는 구현이면 다른 RIFF 컨테이너가 통과해버리므로 따로 덮는다.
    private static final String WEBP_KEY =
            "challenge-verifications-staging/bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb.webp";

    @Test
    @DisplayName("WEBP 바이트가 맞으면 통과한다 — RIFF와 오프셋 8의 WEBP를 모두 본다")
    void validateUploadedBytes_Webp_Passes() {
        // "RIFF" + 파일 크기 4바이트 + "WEBP"
        mockHeadBytes(0x52, 0x49, 0x46, 0x46, 0x24, 0x00, 0x00, 0x00, 0x57, 0x45, 0x42, 0x50);

        assertThatCode(() ->
                mediaService.validateUploadedBytes(WEBP_KEY, MediaPurpose.CHALLENGE_VERIFICATION))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("RIFF 헤더만 맞고 오프셋 8이 다르면 422 — AVI를 webp로 올린 경우")
    void validateUploadedBytes_RiffButNotWebp_Throws422() {
        // "RIFF" + 크기 + "AVI " — 앞 4바이트만 비교하는 구현이었다면 여기서 통과해버린다.
        mockHeadBytes(0x52, 0x49, 0x46, 0x46, 0x24, 0x00, 0x00, 0x00, 0x41, 0x56, 0x49, 0x20);

        assertThatThrownBy(() ->
                mediaService.validateUploadedBytes(WEBP_KEY, MediaPurpose.CHALLENGE_VERIFICATION))
                .isInstanceOf(MediaException.class)
                .hasFieldOrPropertyWithValue("code", MediaErrorCode.MEDIA_CONTENT_MISMATCH);
    }

    // ── key 구조 (#39) ──

    private static final java.time.ZoneId KST = java.time.ZoneId.of("Asia/Seoul");
    private static final java.time.format.DateTimeFormatter KEY_DATE_PATH =
            java.time.format.DateTimeFormatter.ofPattern("yyyy/MM/dd");

    @Test
    @DisplayName("발급한 key에 KST 날짜 구간이 들어간다")
    void issuePresignedUrl_KeyContainsKstDatePath() {
        mockPresign();

        // 호출 전후로 날짜를 읽는다. KST 자정이 그 사이에 지나면 두 값이 달라지므로
        // 한쪽만 기대하면 간헐적으로 실패한다.
        java.time.LocalDate before = java.time.LocalDate.now(KST);
        String key = mediaService.issuePresignedUrl(new MediaReqDTO.PresignedUrl(
                MediaPurpose.CHALLENGE_VERIFICATION, "image/jpeg", 1024L)).mediaKey();
        java.time.LocalDate after = java.time.LocalDate.now(KST);

        assertThat(key).satisfiesAnyOf(
                k -> assertThat(k).startsWith("challenge-verifications-staging/" + before.format(KEY_DATE_PATH) + "/"),
                k -> assertThat(k).startsWith("challenge-verifications-staging/" + after.format(KEY_DATE_PATH) + "/"));
        assertThat(key).endsWith(".jpg");
    }

    @ParameterizedTest
    @DisplayName("달력에 없는 날짜가 든 key는 거부한다 — 정규식은 자릿수만 보기 때문")
    @ValueSource(strings = {
            "challenge-verifications-staging/2026/02/30/6d3f5a20-1b2c-4d5e-8f90-0a1b2c3d4e5f.jpg", // 2월 30일
            "challenge-verifications-staging/2025/13/99/6d3f5a20-1b2c-4d5e-8f90-0a1b2c3d4e5f.jpg", // 13월 99일
            "challenge-verifications-staging/2025/02/29/6d3f5a20-1b2c-4d5e-8f90-0a1b2c3d4e5f.jpg", // 평년의 2월 29일
            "challenge-verifications-staging/2026/00/10/6d3f5a20-1b2c-4d5e-8f90-0a1b2c3d4e5f.jpg"  // 0월
    })
    void validateMediaKey_ImpossibleDate_ThrowsInvalidMediaKey(String mediaKey) {
        assertThatThrownBy(() ->
                mediaService.validateMediaKey(mediaKey, MediaPurpose.CHALLENGE_VERIFICATION))
                .isInstanceOf(MediaException.class)
                .hasFieldOrPropertyWithValue("code", MediaErrorCode.INVALID_MEDIA_KEY);
    }

    @Test
    @DisplayName("윤년의 2월 29일은 통과한다 — 실재하는 날짜는 막지 않는다")
    void validateMediaKey_LeapDay_Passes() {
        String leapDayKey =
                "challenge-verifications-staging/2028/02/29/6d3f5a20-1b2c-4d5e-8f90-0a1b2c3d4e5f.jpg";

        assertThatCode(() ->
                mediaService.validateMediaKey(leapDayKey, MediaPurpose.CHALLENGE_VERIFICATION))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("공개 용도의 key에는 회원·챌린지 식별자가 들어가지 않는다 — 공개 URL로 노출되므로")
    void issuePresignedUrl_PublicKeyCarriesNoIdentifier() {
        mockPresign();

        String key = mediaService.issuePresignedUrl(new MediaReqDTO.PresignedUrl(
                MediaPurpose.CHALLENGE_VERIFICATION, "image/jpeg", 1024L)).mediaKey();

        // challenge-verifications / yyyy / MM / dd / {uuid}.jpg → 정확히 5개 세그먼트
        assertThat(key.split("/")).hasSize(5);
    }

    @Test
    @DisplayName("날짜 도입 전에 발급된 flat key도 검증을 통과한다 — 기존 데이터를 옮기지 않는다")
    void validateMediaKey_LegacyFlatKey_StillPasses() {
        String legacyKey = "challenge-verifications-staging/6d3f5a20-1b2c-4d5e-8f90-0a1b2c3d4e5f.jpg";

        assertThatCode(() ->
                mediaService.validateMediaKey(legacyKey, MediaPurpose.CHALLENGE_VERIFICATION))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("두 형태 모두 같은 규칙으로 공개 URL이 조립된다")
    void resolvePublicUrl_BothKeyShapes() {
        String dated = "challenge-verifications/2026/07/30/6d3f5a20-1b2c-4d5e-8f90-0a1b2c3d4e5f.jpg";
        String legacy = "challenge-verifications/6d3f5a20-1b2c-4d5e-8f90-0a1b2c3d4e5f.jpg";

        assertThat(mediaService.resolvePublicUrl(dated)).isEqualTo(PUBLIC_BASE_URL + "/" + dated);
        assertThat(mediaService.resolvePublicUrl(legacy)).isEqualTo(PUBLIC_BASE_URL + "/" + legacy);
    }

    // ── 조회 URL: 용도에 따라 공개 주소와 서명 주소로 갈린다 ──
    //
    // 이 갈림이 틀리면 조용히 새거나 조용히 깨진다 — 비공개 사진에 공개 주소를 주면 403이고,
    // 공개 사진에 서명을 붙이면 유효 시간이 지나 깨진다. 그래서 양쪽을 다 확인한다.

    private void mockPresignGet() {
        PresignedGetObjectRequest presigned = mock(PresignedGetObjectRequest.class);
        try {
            when(presigned.url()).thenReturn(URI.create(VIEW_URL).toURL());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(presigned);
    }

    @Test
    @DisplayName("공개 용도는 서명하지 않고 공개 주소를 그대로 준다")
    void resolveViewUrl_PublicPurpose_ReturnsPublicUrlWithoutSigning() {
        // given
        // 공개 URL 조립은 승격된 공개 key 를 받는다. 대기 key 는 여기까지 오지 않는다.
        String key = "challenge-verifications/2026/07/30/6d3f5a20-1b2c-4d5e-8f90-0a1b2c3d4e5f.jpg";

        // when
        String url = mediaService.resolveViewUrl(key, MediaPurpose.CHALLENGE_VERIFICATION);

        // then: 서명을 부르지 않는 것 자체가 계약이다. 부르면 불필요한 만료가 생긴다.
        assertThat(url).isEqualTo(PUBLIC_BASE_URL + "/" + key);
        verify(s3Presigner, never()).presignGetObject(any(GetObjectPresignRequest.class));
    }

    @Test
    @DisplayName("비공개 용도는 조회용 유효 시간으로 서명한 주소를 준다")
    void resolveViewUrl_PrivatePurpose_SignsWithViewExpiration() {
        // given
        mockPresignGet();
        String key = "member-routine-verifications/2026/07/30/6d3f5a20-1b2c-4d5e-8f90-0a1b2c3d4e5f.jpg";

        // when
        String url = mediaService.resolveViewUrl(key, MediaPurpose.MEMBER_ROUTINE_VERIFICATION);

        // then
        ArgumentCaptor<GetObjectPresignRequest> captor =
                ArgumentCaptor.forClass(GetObjectPresignRequest.class);
        verify(s3Presigner).presignGetObject(captor.capture());
        GetObjectPresignRequest request = captor.getValue();

        assertAll(
                () -> assertThat(url).isEqualTo(VIEW_URL),
                () -> assertThat(request.getObjectRequest().bucket()).isEqualTo(BUCKET),
                () -> assertThat(request.getObjectRequest().key()).isEqualTo(key),
                // 업로드용(5분)이 아니라 조회용(15분)을 써야 한다. 둘을 바꿔 쓰면 여기서 잡힌다.
                () -> assertThat(request.signatureDuration()).isEqualTo(VIEW_EXPIRATION)
        );
    }

    @Test
    @DisplayName("서명 발급이 실패하면 조회를 실패시킨다 — 깨진 이미지로 넘기지 않는다")
    void resolveViewUrl_PresignFails_ThrowsMediaException() {
        // given
        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class)))
                .thenThrow(SdkClientException.create("presign failed"));
        String key = "group-routine-verifications/2026/07/30/6d3f5a20-1b2c-4d5e-8f90-0a1b2c3d4e5f.jpg";

        // when & then
        assertThatThrownBy(() ->
                mediaService.resolveViewUrl(key, MediaPurpose.GROUP_ROUTINE_VERIFICATION))
                .isInstanceOf(MediaException.class)
                .hasFieldOrPropertyWithValue("code", MediaErrorCode.PRESIGNED_URL_ISSUE_FAILED);
    }
}
