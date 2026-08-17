package com.lirouti.domain.media.config;

import com.lirouti.domain.media.enums.MediaPurpose;
import com.lirouti.global.ratelimit.RateLimitGuard;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * {@link MediaPurpose} 가 가리키는 레이트 리밋 정책이 설정에 실제로 있는지 부팅 때 확인한다.
 *
 * <p><b>없으면 부팅을 실패시킨다.</b> {@code RateLimitGuard} 는 없는 정책을 만나면 통과시키는데
 * (fail-open — 오타 때문에 정상 요청이 막히는 것보다 낫다는 판단), 그 결정은 <b>런타임에</b>
 * 유효하다. 부팅 때는 얘기가 다르다. 정책 이름은 enum 에 문자열로 박혀 있고 한도는 yaml 에
 * 있어서, 한쪽만 고치면 <b>그 용도의 제한이 조용히 사라진다.</b> ERROR 로그는 남지만 그때는 이미
 * 제한 없이 돌고 있고, 아무도 그 로그를 보지 않는다.
 *
 * <p>이 프로젝트가 {@code JWT_SECRET} 을 기본값 없이 두어 미주입 시 부팅을 막는 것과 같은
 * 방식이다. 조용히 잘못 도는 것보다 시끄럽게 안 뜨는 편이 낫다.
 *
 * <p>정책이 {@code null} 인 용도({@code CHAT_EMOTICON})는 검사하지 않는다. 사용자에게
 * presigned PUT 을 발급하지 않아 셀 것이 없다는 뜻이고, 그건 오타가 아니라 의도다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MediaRateLimitPolicyValidator {
    private final RateLimitGuard rateLimitGuard;

    @PostConstruct
    void verifyPoliciesExist() {
        List<MediaPurpose> missing = Arrays.stream(MediaPurpose.values())
                .filter(purpose -> purpose.getRateLimitPolicy() != null)
                .filter(purpose -> !rateLimitGuard.hasPolicy(purpose.getRateLimitPolicy()))
                .toList();

        if (!missing.isEmpty()) {
            String detail = missing.stream()
                    .map(purpose -> "%s -> %s".formatted(purpose, purpose.getRateLimitPolicy()))
                    .collect(Collectors.joining(", "));
            throw new IllegalStateException(
                    "MediaPurpose 가 가리키는 레이트 리밋 정책이 rate-limit.policies 에 없습니다: "
                            + detail);
        }

        log.debug("미디어 용도별 레이트 리밋 정책을 모두 확인했습니다.");
    }
}
