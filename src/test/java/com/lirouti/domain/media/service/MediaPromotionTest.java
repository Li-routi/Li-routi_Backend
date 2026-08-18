package com.lirouti.domain.media.service;

import com.lirouti.domain.media.enums.MediaPurpose;
import com.lirouti.domain.media.exception.MediaException;
import com.lirouti.domain.media.exception.code.error.MediaErrorCode;
import com.lirouti.global.properties.S3Properties;
import com.lirouti.global.ratelimit.RateLimitGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 심사를 통과한 사진을 공개 prefix 로 옮기는 부분만 따로 본다.
 *
 * <p>이 단계가 없으면 AI 심사가 "공개 전에 거르는" 장치가 되지 못한다. 예전에는 업로드 순간부터
 * 공개 주소가 살아 있어서, <b>반려해도 그 사이 열람하거나 공유한 것은 회수되지 않았다.</b>
 */
@DisplayName("심사 통과 사진의 공개 prefix 승격")
class MediaPromotionTest {

    private static final String BUCKET = "lirouti-test";
    private static final String STAGING_KEY =
            "challenge-verifications-staging/2026/08/07/aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa.jpg";
    /** 승격된 key 의 모양. UUID 는 매번 달라 값으로 못 박지 못한다. */
    private static final Pattern PROMOTED_KEY = Pattern.compile(
            "challenge-verifications/2026/08/07/"
                    + "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.jpg");

    /** 심사 때 읽은 오브젝트의 ETag. 승격은 이 값과 같을 때만 복사한다. */
    private static final String REVIEWED_ETAG = "\"abc123\"";

    private S3Client s3Client;
    private MediaService mediaService;

    @BeforeEach
    void setUp() {
        s3Client = Mockito.mock(S3Client.class);
        S3Properties properties = new S3Properties();
        properties.setBucket(BUCKET);
        mediaService = new MediaService(Mockito.mock(S3Presigner.class), s3Client, properties,
                Mockito.mock(RateLimitGuard.class));
    }

    @Test
    @DisplayName("날짜는 그대로 두고 UUID는 새로 뽑는다")
    void promote_KeepsDate_ButMintsNewUuid() {
        String promoted = mediaService.promote(STAGING_KEY, MediaPurpose.CHALLENGE_VERIFICATION, REVIEWED_ETAG);

        assertThat(promoted)
                // 날짜를 유지하는 이유는 미참조 정리가 날짜 prefix로 목록을 훑기 때문이다.
                .matches(PROMOTED_KEY)
                .doesNotContain("-staging/")
                .doesNotContain("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
    }

    @Test
    @DisplayName("같은 대기 key를 두 번 승격해도 공개 key가 겹치지 않는다")
    void promote_Twice_ProducesDifferentPublicKeys() {
        // 대기본 삭제는 실패해도 넘어가므로 대기본이 남을 수 있다. 그 상태에서 만료 전
        // presigned URL로 다른 사진을 올려 다시 승격하면, key가 같으면 이미 DB가 참조하는
        // 공개본을 덮어써 지난 인증의 사진이 조용히 바뀐다.
        String first = mediaService.promote(STAGING_KEY, MediaPurpose.CHALLENGE_VERIFICATION, REVIEWED_ETAG);
        String second = mediaService.promote(STAGING_KEY, MediaPurpose.CHALLENGE_VERIFICATION, REVIEWED_ETAG);

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    @DisplayName("복사만 하고 대기본은 지우지 않는다 — 삭제는 저장이 커밋된 뒤다")
    void promote_CopiesOnly_LeavesStagingToCaller() {
        String promoted = mediaService.promote(STAGING_KEY, MediaPurpose.CHALLENGE_VERIFICATION, REVIEWED_ETAG);

        ArgumentCaptor<CopyObjectRequest> copy = ArgumentCaptor.forClass(CopyObjectRequest.class);
        verify(s3Client).copyObject(copy.capture());
        assertThat(copy.getValue().sourceKey()).isEqualTo(STAGING_KEY);
        assertThat(copy.getValue().destinationKey()).isEqualTo(promoted);

        // 여기서 지우면 저장보다 앞서게 되어 문서가 정한 순서(복사 → 저장 → 삭제)와 어긋난다.
        verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    @DisplayName("복사가 실패하면 대기본을 지우지 않는다 — 사진이 사라진 채 인증만 저장되면 안 된다")
    void promote_CopyFails_KeepsStagingObject() {
        when(s3Client.copyObject(any(CopyObjectRequest.class)))
                .thenThrow(SdkClientException.create("copy failed"));

        assertThatThrownBy(() ->
                mediaService.promote(STAGING_KEY, MediaPurpose.CHALLENGE_VERIFICATION, REVIEWED_ETAG))
                .isInstanceOf(MediaException.class)
                .hasFieldOrPropertyWithValue("code", MediaErrorCode.MEDIA_PROMOTION_FAILED);

        verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    @DisplayName("심사한 바이트일 때만 복사한다 — 조건을 S3에 맡긴다")
    void promote_CopiesOnlyWhenBytesMatchReviewed() {
        mediaService.promote(STAGING_KEY, MediaPurpose.CHALLENGE_VERIFICATION, REVIEWED_ETAG);

        ArgumentCaptor<CopyObjectRequest> copy = ArgumentCaptor.forClass(CopyObjectRequest.class);
        verify(s3Client).copyObject(copy.capture());
        assertThat(copy.getValue().copySourceIfMatch())
                .as("우리가 읽어서 비교하면 비교와 복사 사이에 또 바뀔 수 있다")
                .isEqualTo(REVIEWED_ETAG);
        // 조건이 있으면 굳이 현재 ETag를 다시 읽을 이유가 없다.
        verify(s3Client, never()).headObject(any(HeadObjectRequest.class));
    }

    @Test
    @DisplayName("심사 뒤 다른 사진이 올라왔으면 공개하지 않는다 — 412를 409로 바꾼다")
    void promote_BytesChangedAfterReview_Rejects() {
        when(s3Client.copyObject(any(CopyObjectRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(412).message("precondition failed").build());

        assertThatThrownBy(() ->
                mediaService.promote(STAGING_KEY, MediaPurpose.CHALLENGE_VERIFICATION, REVIEWED_ETAG))
                .isInstanceOf(MediaException.class)
                .hasFieldOrPropertyWithValue("code", MediaErrorCode.MEDIA_CHANGED_AFTER_REVIEW);

        // 심사하지 않은 바이트를 공개하지 않았으니 대기본도 남겨 둔다.
        verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    @DisplayName("심사를 못 했으면 직전 ETag로라도 묶는다 — 조건 없는 복사보다는 좁다")
    void promote_WithoutReviewedETag_UsesCurrentETag() {
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder().eTag(REVIEWED_ETAG).build());

        mediaService.promote(STAGING_KEY, MediaPurpose.CHALLENGE_VERIFICATION, null);

        ArgumentCaptor<CopyObjectRequest> copy = ArgumentCaptor.forClass(CopyObjectRequest.class);
        verify(s3Client).copyObject(copy.capture());
        assertThat(copy.getValue().copySourceIfMatch()).isEqualTo(REVIEWED_ETAG);
    }

    @Test
    @DisplayName("대기 prefix가 없는 용도는 그대로 둔다 — 루틴 인증은 승격 자체가 없다")
    void promote_PurposeWithoutStaging_ReturnsSameKey() {
        String key = "member-routine-verifications/7/2026/08/07/"
                + "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb.jpg";

        assertThat(mediaService.promote(key, MediaPurpose.MEMBER_ROUTINE_VERIFICATION, REVIEWED_ETAG))
                .isEqualTo(key);
        verify(s3Client, never()).copyObject(any(CopyObjectRequest.class));
    }
}
