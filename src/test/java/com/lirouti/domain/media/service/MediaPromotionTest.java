package com.lirouti.domain.media.service;

import com.lirouti.domain.media.enums.MediaPurpose;
import com.lirouti.domain.media.exception.MediaException;
import com.lirouti.domain.media.exception.code.error.MediaErrorCode;
import com.lirouti.global.properties.S3Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mockito;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

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
    private static final String PUBLIC_KEY =
            "challenge-verifications/2026/08/07/aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa.jpg";

    /** 심사 때 읽은 오브젝트의 ETag. 승격은 이 값과 같을 때만 복사한다. */
    private static final String REVIEWED_ETAG = "\"abc123\"";

    private S3Client s3Client;
    private MediaService mediaService;

    @BeforeEach
    void setUp() {
        s3Client = Mockito.mock(S3Client.class);
        S3Properties properties = new S3Properties();
        properties.setBucket(BUCKET);
        mediaService = new MediaService(Mockito.mock(S3Presigner.class), s3Client, properties);
    }

    @Test
    @DisplayName("prefix만 갈아끼운다 — 날짜와 UUID는 그대로다")
    void promote_KeepsDateAndUuid() {
        String promoted = mediaService.promote(STAGING_KEY, MediaPurpose.CHALLENGE_VERIFICATION, REVIEWED_ETAG);

        // 정리 배치가 날짜 prefix로 목록을 훑으므로, 승격 전후 key가 한 글자만 달라야 한다.
        assertThat(promoted).isEqualTo(PUBLIC_KEY);
    }

    @Test
    @DisplayName("복사한 뒤 대기본을 지운다 — 순서가 뒤집히면 사진이 없는 구간이 생긴다")
    void promote_CopiesThenDeletes() {
        mediaService.promote(STAGING_KEY, MediaPurpose.CHALLENGE_VERIFICATION, REVIEWED_ETAG);

        // 각각 불렸는지만 보면 삭제 → 복사로 뒤집혀도 통과한다. 순서까지 고정한다.
        InOrder inOrder = Mockito.inOrder(s3Client);
        ArgumentCaptor<CopyObjectRequest> copy = ArgumentCaptor.forClass(CopyObjectRequest.class);
        inOrder.verify(s3Client).copyObject(copy.capture());
        assertThat(copy.getValue().sourceKey()).isEqualTo(STAGING_KEY);
        assertThat(copy.getValue().destinationKey()).isEqualTo(PUBLIC_KEY);

        ArgumentCaptor<DeleteObjectRequest> delete = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        inOrder.verify(s3Client).deleteObject(delete.capture());
        assertThat(delete.getValue().key())
                .as("지우는 것은 대기본이다. 공개본을 지우면 방금 공개한 사진이 사라진다")
                .isEqualTo(STAGING_KEY);
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
    @DisplayName("대기본 삭제가 실패해도 승격은 성공이다 — 이미 공개본이 만들어져 있다")
    void promote_DeleteFails_StillSucceeds() {
        when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenThrow(SdkClientException.create("delete failed"));

        // 대기본 하나를 못 지운 것 때문에 이미 저장될 인증을 되돌리는 편이 더 나쁘다.
        // 남은 대기본은 나이 기반 수명 주기가 치운다.
        assertThat(mediaService.promote(STAGING_KEY, MediaPurpose.CHALLENGE_VERIFICATION, REVIEWED_ETAG))
                .isEqualTo(PUBLIC_KEY);
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
