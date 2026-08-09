package com.lirouti.domain.group.service.command;

import com.lirouti.domain.group.entity.Group;
import com.lirouti.domain.group.entity.GroupMember;
import com.lirouti.domain.group.enums.GroupMemberRole;
import com.lirouti.domain.group.repository.GroupMemberRepository;
import com.lirouti.domain.group.repository.GroupRepository;
import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.enums.Role;
import com.lirouti.domain.member.enums.SocialProvider;
import com.lirouti.domain.member.repository.MemberRepository;
import com.lirouti.domain.notification.enums.NotificationCategory;
import com.lirouti.domain.notification.enums.NotificationType;
import com.lirouti.domain.notification.service.NotificationCreationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@SpringBootTest
@DisplayName("누적형 그룹 poke 알림 실패 격리 테스트")
class GroupPokeNotificationFailureIsolationTest {
    @Autowired private GroupPokeCommandService groupPokeCommandService;
    @Autowired private GroupRepository groupRepository;
    @Autowired private GroupMemberRepository groupMemberRepository;
    @Autowired private MemberRepository memberRepository;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired @Qualifier("notificationTaskExecutor")
    private ThreadPoolTaskExecutor notificationTaskExecutor;

    @MockitoBean private NotificationCreationService notificationCreationService;

    private Seed seed;

    @AfterEach
    void tearDown() {
        if (seed == null) return;
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(status -> {
            groupMemberRepository.deleteById(seed.requesterMembershipId());
            groupMemberRepository.deleteById(seed.targetMembershipId());
            groupRepository.deleteById(seed.groupId());
            memberRepository.deleteById(seed.requesterId());
            memberRepository.deleteById(seed.targetId());
        });
    }

    @Test
    @DisplayName("커밋 후 알림 저장이 실패해도 누적 poke와 카운터 증가는 성공한다")
    void poke_NotificationCreationFailure_DoesNotRollBackPoke() {
        seed = createSeed();
        doThrow(new IllegalStateException("notification creation failure"))
                .when(notificationCreationService)
                .create(any(), any(NotificationCategory.class), any(NotificationType.class), any(), any(),
                        any(), any(), any(), any());

        groupPokeCommandService.poke(seed.groupId(), seed.requesterId(), seed.targetId());

        GroupMember target = groupMemberRepository.findById(seed.targetMembershipId()).orElseThrow();
        assertThat(target.getTotalPokeCount()).isEqualTo(1L);
        verify(notificationCreationService, timeout(5_000)).create(
                eq(seed.targetId()), eq(NotificationCategory.GROUP_ROUTINE), eq(NotificationType.GROUP_MEMBER_POKED),
                eq("그룹원이 회원님을 찔렀어요!"),
                eq("requester-" + seed.suffix() + "님이 루틴을 기다리고 있어요 🔥"),
                eq(seed.groupId()), eq(seed.requesterId()), eq("GROUP_MEMBER"), any());
    }

    @Test
    @DisplayName("notification executor가 포화되어도 poke 성공과 카운터 증가는 유지된다")
    void poke_NotificationExecutorSaturated_DoesNotFailPoke() throws InterruptedException {
        seed = createSeed();
        CountDownLatch workersStarted = new CountDownLatch(4);
        CountDownLatch releaseWorkers = new CountDownLatch(1);
        try {
            for (int index = 0; index < 4; index++) {
                notificationTaskExecutor.execute(() -> {
                    workersStarted.countDown();
                    try {
                        releaseWorkers.await();
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                    }
                });
            }
            assertThat(workersStarted.await(5, TimeUnit.SECONDS)).isTrue();
            for (int index = 0; index < 100; index++) {
                notificationTaskExecutor.execute(() -> { });
            }

            groupPokeCommandService.poke(seed.groupId(), seed.requesterId(), seed.targetId());

            GroupMember target = groupMemberRepository.findById(seed.targetMembershipId()).orElseThrow();
            assertThat(target.getTotalPokeCount()).isEqualTo(1L);
            verifyNoInteractions(notificationCreationService);
        } finally {
            releaseWorkers.countDown();
            awaitExecutorQueueDrained();
        }
    }

    private Seed createSeed() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        return transaction.execute(status -> {
            String suffix = UUID.randomUUID().toString();
            Group group = groupRepository.save(Group.builder().name("poke 알림 실패 그룹")
                    .inviteCode(suffix.replace("-", "").substring(0, 7).toUpperCase()).build());
            Member requester = memberRepository.save(member("requester-" + suffix));
            Member target = memberRepository.save(member("target-" + suffix));
            List<GroupMember> memberships = groupMemberRepository.saveAll(List.of(
                    GroupMember.builder().group(group).member(requester).role(GroupMemberRole.MEMBER).build(),
                    GroupMember.builder().group(group).member(target).role(GroupMemberRole.MEMBER).build()
            ));
            return new Seed(group.getId(), requester.getId(), target.getId(),
                    memberships.getFirst().getId(), memberships.getLast().getId(), suffix);
        });
    }

    private Member member(String identifier) {
        return Member.builder().email(identifier + "@example.com").nickname(identifier)
                .socialProvider(SocialProvider.GOOGLE).role(Role.ROLE_USER).socialId(identifier).build();
    }

    private void awaitExecutorQueueDrained() throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!notificationTaskExecutor.getThreadPoolExecutor().getQueue().isEmpty()
                && System.nanoTime() < deadline) {
            Thread.sleep(25);
        }
        assertThat(notificationTaskExecutor.getThreadPoolExecutor().getQueue()).isEmpty();
    }

    private record Seed(Long groupId, Long requesterId, Long targetId,
                        Long requesterMembershipId, Long targetMembershipId, String suffix) {
    }
}
