package com.lirouti.domain.media.service;

import com.lirouti.domain.media.enums.MediaPurpose;
import com.lirouti.global.properties.MediaCleanupProperties;
import com.lirouti.global.properties.S3Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.http.SdkHttpResponse;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 미참조 미디어 정리.
 *
 * 이 배치는 <b>파일을 지운다.</b> 그래서 "지워야 할 걸 지우는가"보다
 * <b>"지우면 안 되는 걸 안 지키는가"</b>에 무게를 뒀다. 잘못 남기면 쓰레기가 하루 더 있을 뿐이고,
 * 잘못 지우면 사용자 사진이 사라진다.
 */
class MediaCleanupServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 7, 30);
    /** graceDays=7 이므로 오늘로부터 7일 전. catchUpDays=0 으로 두면 이 하루만 훑는다. */
    private static final String SWEPT_PREFIX = "challenge-verifications/2026/07/23/";
    private static final String ORPHAN = SWEPT_PREFIX + "11111111-1111-1111-1111-111111111111.jpg";
    private static final String REFERENCED = SWEPT_PREFIX + "22222222-2222-2222-2222-222222222222.jpg";

    private S3Client s3Client;
    private MediaCleanupProperties properties;

    @BeforeEach
    void setUp() {
        s3Client = Mockito.mock(S3Client.class);
        properties = new MediaCleanupProperties();
        properties.setEnabled(true);
        properties.setDryRun(false);
        properties.setGraceDays(7);
        // 날짜 하나만 훑게 해서 단언을 단순하게 유지한다. 소급 동작은 별도 테스트에서 본다.
        properties.setCatchUpDays(0);
    }

    private MediaCleanupService service(MediaReferenceSource... sources) {
        S3Properties s3Properties = new S3Properties();
        s3Properties.setBucket("test-bucket");
        return new MediaCleanupService(s3Client, s3Properties, properties, List.of(sources));
    }

    /** 주어진 key들을 참조 중이라고 답하는 챌린지 인증 참조처. */
    private MediaReferenceSource sourceReferencing(String... referenced) {
        Set<String> keys = Set.of(referenced);
        return new MediaReferenceSource() {
            @Override
            public Set<MediaPurpose> coveredPurposes() {
                return Set.of(MediaPurpose.CHALLENGE_VERIFICATION);
            }

            @Override
            public Set<String> findReferencedKeys(Collection<String> candidateKeys) {
                return keys;
            }
        };
    }

    private void givenObjects(String... keys) {
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                .thenReturn(ListObjectsV2Response.builder()
                        .contents(Stream.of(keys).map(k -> S3Object.builder().key(k).build()).toList())
                        .isTruncated(false)
                        .build());
    }

    private void givenDeleteSucceeds() {
        when(s3Client.deleteObjects(any(DeleteObjectsRequest.class)))
                .thenReturn(DeleteObjectsResponse.builder().build());
    }

    private List<String> deletedKeys() {
        ArgumentCaptor<DeleteObjectsRequest> captor = ArgumentCaptor.forClass(DeleteObjectsRequest.class);
        verify(s3Client).deleteObjects(captor.capture());
        return captor.getValue().delete().objects().stream().map(ObjectIdentifier::key).toList();
    }

    @Test
    @DisplayName("DB가 참조하지 않는 오브젝트만 지운다")
    void sweepOrphans_UnreferencedOnly_IsDeleted() {
        // given
        givenObjects(ORPHAN, REFERENCED);
        givenDeleteSucceeds();
        MediaCleanupService service = service(sourceReferencing(REFERENCED));

        // when
        int deleted = service.sweepOrphans(TODAY);

        // then
        assertThat(deleted).isEqualTo(1);
        assertThat(deletedKeys()).containsExactly(ORPHAN);
    }

    @Test
    @DisplayName("참조처가 없는 용도는 목록조차 훑지 않는다 — 프로필 사진이 지워지는 사고를 막는다")
    void sweepOrphans_PurposeWithoutReferenceSource_IsNeverListed() {
        // given: 인증 사진 참조처만 있고 프로필 참조처는 없다(현재 코드베이스의 실제 상태)
        givenObjects(ORPHAN);
        givenDeleteSucceeds();
        MediaCleanupService service = service(sourceReferencing());

        // when
        service.sweepOrphans(TODAY);

        // then
        ArgumentCaptor<ListObjectsV2Request> captor = ArgumentCaptor.forClass(ListObjectsV2Request.class);
        verify(s3Client).listObjectsV2(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(ListObjectsV2Request::prefix)
                .noneMatch(prefix -> prefix.startsWith(MediaPurpose.PROFILE.getPathPrefix()));
    }

    @Test
    @DisplayName("참조처가 하나도 없으면 S3를 아예 호출하지 않는다")
    void sweepOrphans_NoReferenceSources_DoesNothing() {
        // given
        MediaCleanupService service = service();

        // when
        int deleted = service.sweepOrphans(TODAY);

        // then
        assertThat(deleted).isZero();
        verify(s3Client, never()).listObjectsV2(any(ListObjectsV2Request.class));
    }

    @Test
    @DisplayName("참조 조회가 실패하면 그 회차는 아무것도 지우지 않는다")
    void sweepOrphans_ReferenceLookupFails_DeletesNothing() {
        // given: 조회가 터졌다고 해서 "참조 없음"으로 보면 전부 지워진다
        givenObjects(ORPHAN, REFERENCED);
        MediaCleanupService service = service(new MediaReferenceSource() {
            @Override
            public Set<MediaPurpose> coveredPurposes() {
                return Set.of(MediaPurpose.CHALLENGE_VERIFICATION);
            }

            @Override
            public Set<String> findReferencedKeys(Collection<String> candidateKeys) {
                throw new IllegalStateException("DB 연결 실패");
            }
        });

        // when
        int deleted = service.sweepOrphans(TODAY);

        // then
        assertThat(deleted).isZero();
        verify(s3Client, never()).deleteObjects(any(DeleteObjectsRequest.class));
    }

    @Test
    @DisplayName("발급 규칙에 맞지 않는 오브젝트는 미참조여도 남긴다 — 우리가 만든 파일이 아니다")
    void sweepOrphans_ForeignObject_IsKept() {
        // given: 콘솔에서 사람이 올린 파일, 확장자가 다른 파일
        String humanUpload = SWEPT_PREFIX + "notes.txt";
        String notUuid = SWEPT_PREFIX + "banner.jpg";
        givenObjects(humanUpload, notUuid, ORPHAN);
        givenDeleteSucceeds();
        MediaCleanupService service = service(sourceReferencing());

        // when
        service.sweepOrphans(TODAY);

        // then
        assertThat(deletedKeys()).containsExactly(ORPHAN);
    }

    @Test
    @DisplayName("흉내내기에서는 건수만 세고 실제로 지우지 않는다")
    void sweepOrphans_DryRun_CountsWithoutDeleting() {
        // given
        properties.setDryRun(true);
        givenObjects(ORPHAN);
        MediaCleanupService service = service(sourceReferencing());

        // when
        int deleted = service.sweepOrphans(TODAY);

        // then
        assertThat(deleted).isEqualTo(1);
        verify(s3Client, never()).deleteObjects(any(DeleteObjectsRequest.class));
    }

    @Test
    @DisplayName("꺼져 있으면 S3를 호출하지 않는다")
    void sweepOrphans_Disabled_DoesNothing() {
        // given
        properties.setEnabled(false);
        MediaCleanupService service = service(sourceReferencing());

        // when
        int deleted = service.sweepOrphans(TODAY);

        // then
        assertThat(deleted).isZero();
        verify(s3Client, never()).listObjectsV2(any(ListObjectsV2Request.class));
    }

    @Test
    @DisplayName("유예 기간이 지나지 않은 날짜는 훑지 않는다")
    void sweepOrphans_WithinGracePeriod_IsNotSwept() {
        // given
        givenObjects();
        MediaCleanupService service = service(sourceReferencing());

        // when
        service.sweepOrphans(TODAY);

        // then: 오늘로부터 7일 전 하루만 본다. 그보다 최근 날짜는 대상이 아니다.
        ArgumentCaptor<ListObjectsV2Request> captor = ArgumentCaptor.forClass(ListObjectsV2Request.class);
        verify(s3Client).listObjectsV2(captor.capture());
        assertThat(captor.getValue().prefix()).isEqualTo(SWEPT_PREFIX);
    }

    @Test
    @DisplayName("소급 일수만큼 이전 날짜도 함께 훑는다 — 배치를 거른 날이 영영 남지 않게")
    void sweepOrphans_CatchUpDays_SweepsOlderDatesToo() {
        // given
        properties.setCatchUpDays(2);
        givenObjects();
        MediaCleanupService service = service(sourceReferencing());

        // when
        service.sweepOrphans(TODAY);

        // then: 7/21 · 7/22 · 7/23 — 오래된 날짜부터
        ArgumentCaptor<ListObjectsV2Request> captor = ArgumentCaptor.forClass(ListObjectsV2Request.class);
        verify(s3Client, Mockito.times(3)).listObjectsV2(captor.capture());
        assertThat(captor.getAllValues()).extracting(ListObjectsV2Request::prefix)
                .containsExactly(
                        "challenge-verifications/2026/07/21/",
                        "challenge-verifications/2026/07/22/",
                        "challenge-verifications/2026/07/23/");
    }

    @Test
    @DisplayName("1회 삭제 상한을 넘기지 않는다 — 참조 조회가 비면 버킷이 비워지는 것을 막는다")
    void sweepOrphans_ExceedsMaxDeletions_StopsAtLimit() {
        // given: 참조가 하나도 없다고 답하는(=버그난) 참조처
        properties.setMaxDeletionsPerRun(2);
        givenObjects(
                SWEPT_PREFIX + "11111111-1111-1111-1111-111111111111.jpg",
                SWEPT_PREFIX + "22222222-2222-2222-2222-222222222222.jpg",
                SWEPT_PREFIX + "33333333-3333-3333-3333-333333333333.jpg");
        givenDeleteSucceeds();
        MediaCleanupService service = service(sourceReferencing());

        // when
        int deleted = service.sweepOrphans(TODAY);

        // then
        assertThat(deleted).isEqualTo(2);
        assertThat(deletedKeys()).hasSize(2);
    }

    @Test
    @DisplayName("지울 게 없어도 실행 사실이 집계된다 — 조용한 성공이 실패와 구분돼야 한다")
    void sweepOrphans_NothingToDelete_StillCompletes() {
        // given: 훑을 오브젝트가 하나도 없는 날
        givenObjects();
        MediaCleanupService service = service(sourceReferencing());

        // when
        int deleted = service.sweepOrphans(TODAY);

        // then: 목록은 실제로 훑었고, 지운 것만 0이다
        assertThat(deleted).isZero();
        verify(s3Client).listObjectsV2(any(ListObjectsV2Request.class));
        verify(s3Client, never()).deleteObjects(any(DeleteObjectsRequest.class));
    }

    @Test
    @DisplayName("권한이 없으면(403) 조용히 넘어가고 삭제를 시도하지 않는다")
    void sweepOrphans_AccessDenied_SkipsWithoutDeleting() {
        // given: IAM에 s3:ListBucket이 없는 현재 운영 상태
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class))).thenThrow(
                S3Exception.builder()
                        .statusCode(403)
                        .awsErrorDetails(AwsErrorDetails.builder()
                                .errorCode("AccessDenied")
                                .sdkHttpResponse(SdkHttpResponse.builder().statusCode(403).build())
                                .build())
                        .build());
        MediaCleanupService service = service(sourceReferencing());

        // when
        int deleted = service.sweepOrphans(TODAY);

        // then
        assertThat(deleted).isZero();
        verify(s3Client, never()).deleteObjects(any(DeleteObjectsRequest.class));
    }
}
