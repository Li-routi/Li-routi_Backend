package com.lirouti.domain.media.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.lirouti.domain.media.enums.MediaPurpose;
import com.lirouti.global.properties.MediaCleanupProperties;
import com.lirouti.global.properties.S3Properties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.S3Error;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;

/**
 * 업로드됐지만 DB 어디서도 참조하지 않는 오브젝트를 지운다.
 *
 * <p><b>왜 필요한가.</b> presigned URL로 S3에 올린 뒤 저장 API를 호출하지 않으면 그 파일은
 * 아무도 모르는 채 영원히 남는다. 앱이 죽거나 사용자가 이탈하면 생기고, 당일 재인증으로
 * {@code image_url}을 덮어쓸 때도 이전 key가 그대로 남는다. 비용보다 <b>개인정보</b> 문제다.
 *
 * <h2>왜 라이프사이클 규칙이 아닌가</h2>
 * 이슈 본문은 S3 라이프사이클(N일 지난 객체 자동 삭제)을 "가장 단순"이라고 적었지만
 * <b>그대로 하면 살아 있는 사진이 지워진다.</b> 라이프사이클은 객체의 <b>나이만</b> 알고
 * "DB가 참조하는지"는 모르는데, 미참조 파일과 정상 인증 사진이 같은 날짜 prefix에 섞여 있다.
 * 그래서 <b>S3 목록과 DB를 대조</b>하는 쪽을 택했다.
 *
 * <h2>훑는 범위</h2>
 * key에 날짜 구간이 있어서({@code challenge-verifications/2026/07/30/...}) 버킷 전체가 아니라
 * <b>날짜 하나씩</b>만 목록 조회하면 된다. 날짜가 없었다면 매번 용도 전체를 스캔해야 했고,
 * 그 비용은 데이터가 쌓일수록 계속 커진다.
 *
 * <p><b>날짜 없는 옛 key는 이 배치가 지우지 못한다.</b> 날짜 구간을 넣기 전에 발급된
 * {@code challenge-verifications/{UUID}.jpg} 형태는 날짜 prefix 목록에 잡히지 않는다.
 * 수가 적고 전부 정상 저장된 것들이라 그대로 둔다 — 문제가 되면 콘솔에서 한 번 정리한다.
 *
 * <h2>안전 장치</h2>
 * <ul>
 *   <li>참조처({@link MediaReferenceSource})가 없는 용도는 <b>건드리지 않는다.</b></li>
 *   <li>발급 규칙에 맞지 않는 key는 남긴다 — 우리가 만든 파일이 아니다.</li>
 *   <li>1회 삭제 상한에 닿으면 멈춘다.</li>
 *   <li>기본값이 꺼짐 + 흉내내기다.</li>
 * </ul>
 *
 * <p><b>트랜잭션을 걸지 않는다.</b> 대부분의 시간이 S3 왕복이라, 트랜잭션으로 감싸면
 * DB 커넥션을 그동안 붙잡는다(service_convention). 참조 조회만 각 구현체 안에서 짧게 연다.
 *
 * <p>DB를 직접 다루지 않아 조회/변경 구분이 무의미하므로 CQRS를 적용하지 않는다
 * ({@link MediaService}와 같은 CQRS 예외 도메인).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MediaCleanupService {

    /** key의 날짜 구간 포맷. 생성 쪽({@link MediaService})과 같은 규칙이어야 목록이 맞는다. */
    private static final DateTimeFormatter KEY_DATE_PATH = DateTimeFormatter
            .ofPattern("uuuu/MM/dd")
            .withResolverStyle(ResolverStyle.STRICT);

    /** DeleteObjects 한 번에 보낼 수 있는 최대 개수(S3 API 제한). */
    private static final int DELETE_BATCH_SIZE = 1000;

    private final S3Client s3Client;
    private final S3Properties s3Properties;
    private final MediaCleanupProperties cleanupProperties;
    /** 참조처가 하나도 없으면 이 배치는 아무것도 하지 않는다. 그게 맞는 동작이다. */
    private final List<MediaReferenceSource> referenceSources;

    /**
     * 유예 기간이 지난 날짜들을 훑어 미참조 오브젝트를 지운다.
     *
     * @param today KST 기준 오늘. 스케줄러가 넘긴다(테스트에서 날짜를 고정하기 위해).
     * @return 지운(흉내만 낸 경우 지웠을) 오브젝트 수
     */
    public int sweepOrphans(LocalDate today) {
        if (!cleanupProperties.isEnabled()) {
            log.debug("미참조 미디어 정리가 꺼져 있어 건너뜁니다.");
            return 0;
        }

        Map<MediaPurpose, List<MediaReferenceSource>> sourcesByPurpose = groupSourcesByPurpose();
        if (sourcesByPurpose.isEmpty()) {
            // 참조처를 아무도 등록하지 않았다. 지울 대상을 판단할 근거가 없으므로 아무것도 안 한다.
            log.warn("미디어 참조처가 하나도 등록되지 않아 정리를 건너뜁니다. "
                    + "MediaReferenceSource 구현체가 빠졌는지 확인하세요.");
            return 0;
        }

        // 가장 오래된 날짜부터 훑는다. 상한에 걸려 중단되더라도 오래된 것부터 지워진다.
        LocalDate newest = today.minusDays(cleanupProperties.getGraceDays());
        LocalDate oldest = newest.minusDays(cleanupProperties.getCatchUpDays());

        int scanned = 0;
        int deleted = 0;
        int failedDates = 0;
        boolean stoppedAtLimit = false;

        outer:
        for (LocalDate date = oldest; !date.isAfter(newest); date = date.plusDays(1)) {
            for (Map.Entry<MediaPurpose, List<MediaReferenceSource>> entry : sourcesByPurpose.entrySet()) {
                if (deleted >= cleanupProperties.getMaxDeletionsPerRun()) {
                    stoppedAtLimit = true;
                    break outer;
                }
                SweepResult result = sweepDate(entry.getKey(), entry.getValue(), date,
                        cleanupProperties.getMaxDeletionsPerRun() - deleted);
                scanned += result.scanned();
                deleted += result.deleted();
                if (result.failed()) {
                    failedDates++;
                }
            }
        }

        if (stoppedAtLimit) {
            log.warn("1회 삭제 상한({})에 도달해 정리를 중단합니다. 정상 운영에서는 닿지 않는 값이므로 "
                            + "참조처 조회가 비어 있지 않은지 확인하세요.",
                    cleanupProperties.getMaxDeletionsPerRun());
        }

        // 0건이어도 반드시 남긴다. 이 줄이 없으면 "돌았는데 지울 게 없었다"와 "아예 안 돌았다"를
        // 운영에서 구분할 수 없다. 켠 직후 며칠은 이 로그로만 동작을 확인하게 되므로,
        // 조용한 성공은 실패와 똑같아 보인다.
        log.info("미참조 미디어 정리 {}. 대상 기간={} ~ {}, 훑은 오브젝트={}건, {}={}건{}",
                failedDates > 0 ? "일부 실패" : "완료",
                oldest, newest, scanned,
                cleanupProperties.isDryRun() ? "삭제 예정" : "삭제",
                deleted,
                failedDates > 0 ? ", 훑지 못한 구간=%d개(위 오류 로그 확인)".formatted(failedDates) : "");
        return deleted;
    }

    /**
     * 한 구간을 훑은 결과.
     *
     * {@code failed}를 따로 두는 이유는 목록 조회가 막혔을 때와 훑었는데 지울 게 없을 때가
     * 둘 다 "0건"으로 보이기 때문이다. 권한 문제를 정상 동작으로 오해하면 안 된다.
     */
    private record SweepResult(int scanned, int deleted, boolean failed) {
    }

    /** 용도 하나 × 날짜 하나. 목록 → 대조 → 삭제를 페이지 단위로 반복한다. */
    private SweepResult sweepDate(MediaPurpose purpose,
                                  List<MediaReferenceSource> sources,
                                  LocalDate date,
                                  int remainingBudget) {
        String prefix = "%s/%s/".formatted(purpose.getPathPrefix(), date.format(KEY_DATE_PATH));
        int scanned = 0;
        int deleted = 0;
        String continuationToken = null;

        do {
            ListObjectsV2Response page;
            try {
                page = listPage(prefix, continuationToken);
            } catch (S3Exception e) {
                logS3Failure("목록 조회", prefix, e);
                return new SweepResult(scanned, deleted, true);
            } catch (SdkException e) {
                log.error("미디어 목록 조회에 실패했습니다. prefix={}", prefix, e);
                return new SweepResult(scanned, deleted, true);
            }

            scanned += page.contents().size();
            List<String> candidates = page.contents().stream()
                    .map(S3Object::key)
                    // 우리가 발급한 형식이 아닌 오브젝트는 손대지 않는다. 콘솔에서 사람이 올린
                    // 파일이나 다른 도구가 만든 파일을 배치가 지우면 안 된다.
                    .filter(key -> matchesIssuedFormat(key, purpose))
                    .toList();

            if (!candidates.isEmpty()) {
                Set<String> referenced = collectReferencedKeys(sources, candidates);
                List<String> orphans = candidates.stream()
                        .filter(key -> !referenced.contains(key))
                        .limit(Math.max(0, remainingBudget - deleted))
                        .toList();
                deleted += deleteAll(orphans, prefix);
            }

            continuationToken = Boolean.TRUE.equals(page.isTruncated()) ? page.nextContinuationToken() : null;
        } while (continuationToken != null && deleted < remainingBudget);

        return new SweepResult(scanned, deleted, false);
    }

    private ListObjectsV2Response listPage(String prefix, String continuationToken) {
        return s3Client.listObjectsV2(ListObjectsV2Request.builder()
                .bucket(s3Properties.getBucket())
                .prefix(prefix)
                .continuationToken(continuationToken)
                .build());
    }

    /**
     * 참조처들이 각각 답한 결과를 합집합으로 모은다.
     *
     * <p>한 용도를 여러 구현체가 나눠 볼 수 있으므로(예: 인증 사진과 챌린지 대표 이미지가
     * 같은 prefix를 쓰게 되는 경우) <b>하나라도 "쓰고 있다"고 하면 남긴다.</b>
     *
     * <p>구현체 하나가 터지면 그 용도의 정리를 <b>통째로 포기한다.</b> 부분 결과로 삭제하면
     * 그 구현체가 지키던 파일이 전부 미참조로 보여 지워진다.
     */
    private Set<String> collectReferencedKeys(List<MediaReferenceSource> sources, Collection<String> candidates) {
        Set<String> referenced = new HashSet<>();
        for (MediaReferenceSource source : sources) {
            try {
                referenced.addAll(source.findReferencedKeys(candidates));
            } catch (RuntimeException e) {
                log.error("미디어 참조 조회에 실패해 이번 정리를 건너뜁니다. source={}",
                        source.getClass().getSimpleName(), e);
                // 후보 전체를 참조로 취급 = 아무것도 지우지 않는다.
                return Set.copyOf(candidates);
            }
        }
        return referenced;
    }

    private int deleteAll(List<String> keys, String prefix) {
        if (keys.isEmpty()) {
            return 0;
        }
        if (cleanupProperties.isDryRun()) {
            log.info("[흉내내기] 미참조 미디어 {}건을 지웠을 것입니다. prefix={}, 예시={}",
                    keys.size(), prefix, keys.getFirst());
            return keys.size();
        }

        int deleted = 0;
        for (int from = 0; from < keys.size(); from += DELETE_BATCH_SIZE) {
            List<String> batch = keys.subList(from, Math.min(from + DELETE_BATCH_SIZE, keys.size()));
            try {
                deleted += deleteBatch(batch);
            } catch (S3Exception e) {
                logS3Failure("삭제", prefix, e);
                return deleted;
            } catch (SdkException e) {
                log.error("미참조 미디어 삭제에 실패했습니다. prefix={}", prefix, e);
                return deleted;
            }
        }
        return deleted;
    }

    private int deleteBatch(List<String> batch) {
        List<ObjectIdentifier> objects = new ArrayList<>(batch.size());
        for (String key : batch) {
            objects.add(ObjectIdentifier.builder().key(key).build());
        }
        DeleteObjectsResponse response = s3Client.deleteObjects(DeleteObjectsRequest.builder()
                .bucket(s3Properties.getBucket())
                // quiet 모드에서도 실패한 항목은 errors()로 돌아온다. 성공 목록만 생략된다.
                .delete(Delete.builder().objects(objects).quiet(true).build())
                .build());

        // DeleteObjects는 일부만 실패해도 200을 준다. 예외가 아니라 응답 본문을 봐야 한다.
        for (S3Error error : response.errors()) {
            log.warn("미참조 미디어 삭제가 일부 실패했습니다. key={}, code={}, message={}",
                    error.key(), error.code(), error.message());
        }
        int failed = response.errors().size();
        log.info("미참조 미디어 {}건을 삭제했습니다.", batch.size() - failed);
        return batch.size() - failed;
    }

    /**
     * 권한 없음은 설정 문제라 스택 트레이스보다 "무엇을 해야 하는지"가 중요하다.
     * 다른 실패와 달리 사람이 콘솔에서 조치해야 끝난다.
     */
    private void logS3Failure(String action, String prefix, S3Exception e) {
        if (e.statusCode() == 403) {
            log.error("미디어 {} 권한이 없습니다. 운영 IAM 역할에 s3:ListBucket·s3:DeleteObject를 "
                            + "추가해야 정리가 동작합니다(deploy/iam-policy.json). prefix={}",
                    action, prefix);
            return;
        }
        log.error("미디어 {}에 실패했습니다. prefix={}", action, prefix, e);
    }

    /**
     * 그 용도로 <b>서버가 발급했을 법한</b> key인지 본다.
     *
     * 여기서는 형식만 본다 — 날짜가 달력에 있는 날인지까지 따지지 않는다. 목록 자체를
     * 유효한 날짜 prefix로 뽑았으므로 잡히는 key의 날짜는 이미 실제 날짜다.
     */
    private boolean matchesIssuedFormat(String key, MediaPurpose purpose) {
        int lastSlash = key.lastIndexOf('/');
        int extensionSeparator = key.lastIndexOf('.');
        if (lastSlash < 0 || extensionSeparator < lastSlash) {
            return false;
        }
        String baseName = key.substring(lastSlash + 1, extensionSeparator);
        String extension = key.substring(extensionSeparator + 1);
        return MediaKeyFormat.isIssuedLeaf(baseName) && MediaKeyFormat.isAllowedExtension(purpose, extension);
    }

    private Map<MediaPurpose, List<MediaReferenceSource>> groupSourcesByPurpose() {
        Map<MediaPurpose, List<MediaReferenceSource>> grouped = new EnumMap<>(MediaPurpose.class);
        for (MediaReferenceSource source : referenceSources) {
            for (MediaPurpose purpose : source.coveredPurposes()) {
                grouped.computeIfAbsent(purpose, key -> new ArrayList<>()).add(source);
            }
        }
        return grouped;
    }
}
