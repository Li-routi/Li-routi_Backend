package com.lirouti.domain.routine.service.command;

import static org.assertj.core.api.Assertions.assertThat;

import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.enums.Role;
import com.lirouti.domain.member.enums.SocialProvider;
import com.lirouti.domain.member.repository.MemberRepository;
import com.lirouti.domain.routine.dto.request.RoutineReqDTO;
import com.lirouti.domain.routine.entity.MemberRoutine;
import com.lirouti.domain.routine.exception.RoutineException;
import com.lirouti.domain.routine.exception.code.error.RoutineErrorCode;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 활성 루틴 상한이 동시 요청에서도 지켜지는지 검증한다.
 *
 * <p>상한은 "지금 개수 + 이번 요청"으로 판단하므로, 세는 것과 저장하는 것 사이에 같은 회원의
 * 다른 요청이 끼어들면 양쪽 모두 검사를 통과할 수 있다. 유니크 제약처럼 DB로 표현할 수 있는
 * 규칙이 아니라서 회원 행 잠금으로 구간을 직렬화하는데, 순차 호출로는 그 잠금이 실제로
 * 동작하는지 증명되지 않아 실제 스레드로 확인한다.
 *
 * <p>고정 카테고리(id 2 = 건강)는 R__seed_routine.sql이 넣는다.
 */
@SpringBootTest
@DisplayName("개인 루틴 활성 상한 동시성 테스트")
class RoutineActiveLimitConcurrencyTest {
    private static final Long HEALTH_CATEGORY_ID = 2L;

    /** 상한까지 한 개만 남겨 둔다. 두 요청이 동시에 들어오면 한쪽만 성공해야 한다. */
    private static final int PRESET_COUNT = MemberRoutine.MAX_ACTIVE_COUNT - 1;

    @Autowired
    private RoutineCommandService routineCommandService;
    @Autowired
    private MemberRoutineRepository memberRoutineRepository;
    @Autowired
    private MemberRepository memberRepository;

    private Long memberId;

    @BeforeEach
    void setUp() {
        Member member = memberRepository.save(Member.builder()
                .email("routine-concurrency@example.com")
                .nickname("동시성회원")
                .socialProvider(SocialProvider.GOOGLE)
                .role(Role.ROLE_USER)
                .socialId("routine-concurrency-social")
                .build());
        memberId = member.getId();

        routineCommandService.createRoutines(memberId, new RoutineReqDTO.CreateRoutines(
                IntStream.range(0, PRESET_COUNT)
                        .mapToObj(index -> item("미리 채운 루틴" + index))
                        .toList()
        ));
    }

    @AfterEach
    void tearDown() {
        memberRoutineRepository.deleteAll(memberRoutineRepository.findAll().stream()
                .filter(routine -> routine.getMember().getId().equals(memberId))
                .toList());
        memberRepository.deleteById(memberId);
    }

    @Test
    @DisplayName("상한 직전에 두 요청이 동시에 들어오면 한 건만 생성된다")
    void createRoutines_ConcurrentRequestsAtLimit_OnlyOneSucceeds() throws InterruptedException {
        // given
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger limitExceeded = new AtomicInteger();
        AtomicInteger unexpected = new AtomicInteger();

        Runnable task = () -> {
            try {
                ready.countDown();
                start.await();
                routineCommandService.createRoutines(memberId,
                        new RoutineReqDTO.CreateRoutines(List.of(item("마지막 한 칸"))));
                success.incrementAndGet();
            } catch (RoutineException exception) {
                if (exception.getCode() == RoutineErrorCode.ACTIVE_ROUTINE_LIMIT_EXCEEDED) {
                    limitExceeded.incrementAndGet();
                } else {
                    unexpected.incrementAndGet();
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } catch (RuntimeException exception) {
                unexpected.incrementAndGet();
            } finally {
                done.countDown();
            }
        };

        // when
        pool.submit(task);
        pool.submit(task);
        boolean workersReady = ready.await(5, TimeUnit.SECONDS);
        start.countDown();
        boolean finished = done.await(20, TimeUnit.SECONDS);
        pool.shutdownNow();

        // then
        assertThat(workersReady).isTrue();
        assertThat(finished).isTrue();
        assertThat(success.get()).isEqualTo(1);
        assertThat(limitExceeded.get()).isEqualTo(1);
        assertThat(unexpected.get()).isZero();
        assertThat(memberRoutineRepository.countByMemberIdAndActiveTrue(memberId))
                .isEqualTo(MemberRoutine.MAX_ACTIVE_COUNT);
    }

    /** 기본 루틴을 고르지 않은 직접 추가 항목이다. 사용자 루틴끼리는 같은 이름도 허용된다. */
    private RoutineReqDTO.CreateRoutine item(String name) {
        return new RoutineReqDTO.CreateRoutine(
                HEALTH_CATEGORY_ID, null, name, null, null, null);
    }
}
