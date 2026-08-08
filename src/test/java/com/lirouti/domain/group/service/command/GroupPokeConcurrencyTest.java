package com.lirouti.domain.group.service.command;

import com.lirouti.domain.group.entity.Group;
import com.lirouti.domain.group.entity.GroupMember;
import com.lirouti.domain.group.enums.GroupMemberRole;
import com.lirouti.domain.group.enums.GroupMemberStatus;
import com.lirouti.domain.group.exception.GroupException;
import com.lirouti.domain.group.repository.GroupMemberRepository;
import com.lirouti.domain.group.repository.GroupRepository;
import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.enums.Role;
import com.lirouti.domain.member.enums.SocialProvider;
import com.lirouti.domain.member.repository.MemberRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DisplayName("그룹 구성원 찌르기 동시성 테스트")
class GroupPokeConcurrencyTest {
    private static final int REQUEST_COUNT = 12;

    @Autowired private GroupPokeCommandService groupPokeCommandService;
    @Autowired private GroupCommandService groupCommandService;
    @Autowired private GroupRepository groupRepository;
    @Autowired private GroupMemberRepository groupMemberRepository;
    @Autowired private MemberRepository memberRepository;

    private Long groupId;
    private Long requesterId;
    private Long targetId;
    private Long ownerId;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString();
        Group group = groupRepository.save(Group.builder().name("찌르기 동시성 그룹")
                .inviteCode(suffix.replace("-", "").substring(0, 7).toUpperCase()).build());
        Member requester = memberRepository.save(member("requester-" + suffix));
        Member target = memberRepository.save(member("target-" + suffix));
        Member owner = memberRepository.save(member("owner-" + suffix));
        groupMemberRepository.saveAll(List.of(
                GroupMember.builder().group(group).member(requester).role(GroupMemberRole.MEMBER).build(),
                GroupMember.builder().group(group).member(target).role(GroupMemberRole.MEMBER).build(),
                GroupMember.builder().group(group).member(owner).role(GroupMemberRole.OWNER).build()
        ));
        groupMemberRepository.flush();
        groupId = group.getId();
        requesterId = requester.getId();
        targetId = target.getId();
        ownerId = owner.getId();
    }

    @AfterEach
    void tearDown() {
        if (groupId != null) {
            groupRepository.deleteById(groupId);
        }
        if (requesterId != null) {
            memberRepository.deleteById(requesterId);
        }
        if (targetId != null) {
            memberRepository.deleteById(targetId);
        }
        if (ownerId != null) {
            memberRepository.deleteById(ownerId);
        }
    }

    @Test
    @DisplayName("같은 대상에 대한 동시 찌르기 후 누적값은 성공 요청 수와 일치한다")
    void poke_ConcurrentRequests_DoesNotLoseIncrements() throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(REQUEST_COUNT);
        CountDownLatch ready = new CountDownLatch(REQUEST_COUNT);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(REQUEST_COUNT);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger failure = new AtomicInteger();

        for (int index = 0; index < REQUEST_COUNT; index++) {
            pool.submit(() -> {
                try {
                    ready.countDown();
                    start.await();
                    groupPokeCommandService.poke(groupId, requesterId, targetId);
                    success.incrementAndGet();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    failure.incrementAndGet();
                } catch (RuntimeException exception) {
                    failure.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }

        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        assertThat(done.await(20, TimeUnit.SECONDS)).isTrue();
        pool.shutdownNow();

        assertThat(failure.get()).isZero();
        assertThat(success.get()).isEqualTo(REQUEST_COUNT);
        assertThat(groupMemberRepository.findByGroupIdAndMemberId(groupId, targetId).orElseThrow()
                .getTotalPokeCount()).isEqualTo(REQUEST_COUNT);
    }

    @Test
    @DisplayName("A와 B가 서로를 동시에 찔러도 deadlock 없이 각자 한 번씩 누적된다")
    void poke_BidirectionalConcurrentRequests_IncrementsBothWithoutDeadlock() throws Exception {
        List<Throwable> failures = runConcurrently(
                () -> groupPokeCommandService.poke(groupId, requesterId, targetId),
                () -> groupPokeCommandService.poke(groupId, targetId, requesterId)
        );

        assertThat(failures).containsOnlyNulls();
        assertThat(groupMemberRepository.findByGroupIdAndMemberId(groupId, requesterId).orElseThrow()
                .getTotalPokeCount()).isEqualTo(1L);
        assertThat(groupMemberRepository.findByGroupIdAndMemberId(groupId, targetId).orElseThrow()
                .getTotalPokeCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("찌르기와 대상 탈퇴가 동시에 실행되면 deadlock 없이 순서에 맞는 카운터와 LEFT 상태가 남는다")
    void poke_ConcurrentTargetLeave_LeavesConsistentState() throws Exception {
        AtomicBoolean pokeSucceeded = new AtomicBoolean();
        List<Throwable> failures = runConcurrently(
                () -> {
                    groupPokeCommandService.poke(groupId, requesterId, targetId);
                    pokeSucceeded.set(true);
                },
                () -> groupCommandService.leaveGroup(groupId, targetId)
        );

        assertOnlyExpectedPokeRejection(failures);
        GroupMember target = groupMemberRepository.findByGroupIdAndMemberId(groupId, targetId).orElseThrow();
        assertThat(target.getStatus()).isEqualTo(GroupMemberStatus.LEFT);
        assertThat(target.getTotalPokeCount()).isEqualTo(pokeSucceeded.get() ? 1L : 0L);
    }

    @Test
    @DisplayName("찌르기와 대상 강퇴가 동시에 실행되면 deadlock 없이 순서에 맞는 카운터와 KICKED 상태가 남는다")
    void poke_ConcurrentTargetKick_LeavesConsistentState() throws Exception {
        AtomicBoolean pokeSucceeded = new AtomicBoolean();
        List<Throwable> failures = runConcurrently(
                () -> {
                    groupPokeCommandService.poke(groupId, requesterId, targetId);
                    pokeSucceeded.set(true);
                },
                () -> groupCommandService.kickMember(groupId, ownerId, targetId)
        );

        assertOnlyExpectedPokeRejection(failures);
        GroupMember target = groupMemberRepository.findByGroupIdAndMemberId(groupId, targetId).orElseThrow();
        assertThat(target.getStatus()).isEqualTo(GroupMemberStatus.KICKED);
        assertThat(target.getTotalPokeCount()).isEqualTo(pokeSucceeded.get() ? 1L : 0L);
    }

    private List<Throwable> runConcurrently(ThrowingRunnable first, ThrowingRunnable second) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Throwable> firstResult = pool.submit(() -> runAfterStart(ready, start, first));
            Future<Throwable> secondResult = pool.submit(() -> runAfterStart(ready, start, second));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return java.util.Arrays.asList(
                    firstResult.get(20, TimeUnit.SECONDS),
                    secondResult.get(20, TimeUnit.SECONDS)
            );
        } finally {
            pool.shutdownNow();
        }
    }

    private Throwable runAfterStart(
            CountDownLatch ready,
            CountDownLatch start,
            ThrowingRunnable action
    ) {
        ready.countDown();
        try {
            start.await();
            action.run();
            return null;
        } catch (Throwable throwable) {
            return throwable;
        }
    }

    private void assertOnlyExpectedPokeRejection(List<Throwable> failures) {
        assertThat(failures).allSatisfy(failure -> {
            if (failure != null) {
                assertThat(failure).isInstanceOf(GroupException.class);
            }
        });
        assertThat(failures.stream().filter(failure -> failure instanceof GroupException)).hasSizeLessThanOrEqualTo(1);
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run();
    }

    private Member member(String identifier) {
        return Member.builder().email(identifier + "@example.com").nickname(identifier)
                .socialProvider(SocialProvider.GOOGLE).role(Role.ROLE_USER).socialId(identifier).build();
    }
}
