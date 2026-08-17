package com.lirouti.global.properties;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

/**
 * 미참조 이미지 정리 설정.
 *
 * 이 배치는 <b>파일을 지운다.</b> 그래서 기본값을 전부 "가장 안 지우는 쪽"으로 잡았다 —
 * 꺼져 있고, 켜도 처음엔 흉내만 낸다.
 */
@Getter
@Setter
@Validated
@Configuration
@ConfigurationProperties(prefix = "media.cleanup")
public class MediaCleanupProperties {

    /**
     * 정리 배치를 돌릴지 여부. <b>기본은 꺼짐.</b>
     *
     * 지금 운영 IAM 역할에는 {@code s3:ListBucket}·{@code s3:DeleteObject}가 없다
     * (최소 권한으로 일부러 좁혀 뒀다 — deploy/iam-policy.json). 권한이 붙기 전에 켜면
     * 매일 AccessDenied 로그만 쌓인다. 권한을 넓힌 뒤 켜는 순서다.
     */
    private boolean enabled = false;

    /**
     * 실제로 지우지 않고 "지웠을 것"만 로그로 남길지. <b>기본은 흉내만 냄.</b>
     *
     * 되돌릴 수 없는 작업이라 한 단계를 더 뒀다. 켠 뒤 며칠은 이 상태로 두고
     * "몇 개를 지우려 하는지"를 눈으로 확인한 다음 끄는 것을 권한다.
     * 숫자가 예상보다 크면 참조처(MediaReferenceSource)가 빠졌다는 신호다.
     */
    private boolean dryRun = true;

    /**
     * 업로드 후 이 일수가 지나야 정리 대상이 된다.
     *
     * presigned URL 유효 시간은 5분이라 기술적 하한은 훨씬 낮다. 넉넉히 잡은 것은
     * "업로드는 됐는데 저장 API가 실패해서 앱이 나중에 재시도하는" 경우를 살려두기 위해서다.
     */
    @Positive(message = "정리 유예 기간은 1일 이상이어야 합니다.")
    private int graceDays = 7;

    /**
     * 유예 기간이 지난 날짜를 며칠 치까지 거슬러 올라가 볼지.
     *
     * 하루에 딱 하루치만 훑으면 배치가 한 번 걸러졌을 때 그 날짜는 <b>영영</b> 정리되지 않는다.
     * 이미 정리된 날짜를 다시 훑는 비용은 목록 조회 한 번이라 싸다.
     */
    @PositiveOrZero(message = "정리 소급 일수는 0 이상이어야 합니다.")
    private int catchUpDays = 7;

    /**
     * 한 번 실행에서 지울 수 있는 최대 개수. 폭주 방지 장치다.
     *
     * 참조처 조회가 잘못되면 <b>모든 파일이 미참조로 보인다.</b> 그때 상한이 없으면
     * 배치 한 번에 버킷이 비워진다. 상한에 닿으면 더 지우지 않고 중단하고 경고를 남긴다 —
     * 정상 운영에서 닿을 수 없는 숫자이므로, 닿았다면 그 자체가 버그 신호다.
     */
    @Positive(message = "1회 최대 삭제 개수는 양수여야 합니다.")
    private int maxDeletionsPerRun = 1000;
}
