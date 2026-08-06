package com.lirouti.global.ratelimit;

import lombok.extern.slf4j.Slf4j;

import java.time.Clock;
import java.time.Duration;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Redis를 못 쓸 때만 대신 세는 카운터.
 *
 * <p><b>평소에는 아무 일도 하지 않는다.</b> {@link RateLimiter}가 예외를 던질 때만 호출된다.
 * 그래서 여기 쌓이는 항목은 장애 구간의 것뿐이고, 장애가 끝나면 만료되어 사라진다.
 *
 * <h3>왜 필요한가</h3>
 * 이전에는 Redis가 죽으면 요청을 그냥 통과시켰다. 그 판단 자체는 근거가 있었다 — 레이트 리밋은
 * 인가가 아니라 보호 장치이고, 업로드를 통째로 막으면 막으려던 남용보다 큰 장애가 된다.
 * <b>문제는 선택지가 "전부 막기"와 "전부 통과"뿐이었다는 것이다.</b> presigned 발급은 한 건이
 * 10MB 업로드를 허용하므로, 그 사이 상한이 통째로 사라졌다.
 *
 * <h3>단일 인스턴스라 사실상 완전한 대체다</h3>
 * 앱이 EC2 한 대에서 돌기 때문에 프로세스 안 카운터가 곧 전역 카운터다. <b>인스턴스를 늘리면</b>
 * 인스턴스별 제한으로 degrade 되어 실효 한도가 대수만큼 늘어난다 — 그래도 무제한보다는 낫지만,
 * 그 시점에는 Redis 의존을 줄이는 다른 방법을 봐야 한다.
 *
 * <h3>고정 창이고, Redis 경로와 의미가 같다</h3>
 * 창 경계에서 최대 2배까지 몰릴 수 있는 것도 같다. 두 경로의 셈법이 다르면 장애 전후로 사용자가
 * 겪는 한도가 달라지므로 일부러 맞췄다.
 */
@Slf4j
public class InMemoryRateLimiter {

    /**
     * 담아둘 최대 항목 수.
     *
     * <p>키가 회원 id 또는 IP라 장애가 길어지면 무한히 늘 수 있다. 상한에 닿으면 만료된 항목을
     * 먼저 걷어내고, 그래도 자리가 없으면 <b>새 항목을 받지 않고 통과시킨다.</b> 폴백이 메모리를
     * 밀어내 앱을 죽이는 것은 막으려던 문제보다 나쁘다.
     */
    private static final int MAX_ENTRIES = 10_000;

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final Clock clock;

    public InMemoryRateLimiter(Clock clock) {
        this.clock = clock;
    }

    /**
     * 요청 하나를 소비한다. 반환 의미는 {@link RateLimiter#consume}과 같다.
     *
     * <p>상한에 닿아 셀 수 없으면 허용으로 답한다 — 이유는 {@link #MAX_ENTRIES} 참고.
     */
    public RateLimiter.Result consume(String key, int limit, Duration window) {
        long now = clock.millis();

        if (windows.size() >= MAX_ENTRIES && !windows.containsKey(key)) {
            evictExpired(now);
            if (windows.size() >= MAX_ENTRIES) {
                log.warn("폴백 레이트 리밋 저장소가 가득 차 이 요청은 세지 않습니다. size={}", windows.size());
                return new RateLimiter.Result(true, 0, 0);
            }
        }

        Window current = windows.compute(key, (k, existing) ->
                (existing == null || now >= existing.expiresAt())
                        ? new Window(now + window.toMillis(), 1)
                        : new Window(existing.expiresAt(), existing.count() + 1));

        long remainingMillis = Math.max(1, current.expiresAt() - now);
        return new RateLimiter.Result(
                current.count() <= limit,
                current.count(),
                Math.max(1, (remainingMillis + 999) / 1000));
    }

    /** 테스트와 진단용. 지금 담고 있는 항목 수. */
    public int size() {
        return windows.size();
    }

    private void evictExpired(long now) {
        Iterator<Map.Entry<String, Window>> it = windows.entrySet().iterator();
        while (it.hasNext()) {
            if (now >= it.next().getValue().expiresAt()) {
                it.remove();
            }
        }
    }

    /**
     * 창 하나. 새 창이 열릴 때마다 통째로 교체하므로 값이 변하지 않는다 —
     * {@code compute} 안에서만 갱신되어 같은 키의 동시 요청이 직렬화된다.
     */
    private record Window(long expiresAt, long count) {
    }
}
