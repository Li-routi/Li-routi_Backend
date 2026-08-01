package com.lirouti.domain.group.service;

import com.lirouti.domain.group.entity.Group;
import com.lirouti.domain.group.entity.GroupMember;
import com.lirouti.domain.group.enums.GroupMemberRole;
import com.lirouti.domain.group.enums.GroupMemberStatus;
import com.lirouti.domain.group.enums.GroupStatus;
import com.lirouti.domain.group.exception.GroupException;
import com.lirouti.domain.group.exception.code.error.GroupErrorCode;
import com.lirouti.domain.group.repository.GroupMemberRepository;
import com.lirouti.domain.group.repository.GroupRepository;
import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.enums.Role;
import com.lirouti.domain.member.enums.SocialProvider;
import com.lirouti.domain.member.repository.MemberRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(GroupParticipationLimitConcurrencyTest.JoinHarness.class)
@DisplayName("활성 그룹 참여 상한 동시성 테스트")
class GroupParticipationLimitConcurrencyTest {
    @Autowired
    private JoinHarness joinHarness;
    @Autowired
    private GroupMemberRepository groupMemberRepository;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private GroupRepository groupRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long memberId;
    private final List<Long> groupIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        if (memberId == null) {
            return;
        }
        jdbcTemplate.update("delete from group_member where member_id = ?", memberId);
        groupIds.forEach(groupId ->
                jdbcTemplate.update("delete from member_group where id = ?", groupId));
        jdbcTemplate.update("delete from member where id = ?", memberId);
    }

    @Test
    @DisplayName("5개 그룹 참여 상태의 동시 가입 요청은 하나만 성공해 최대 6개를 유지한다")
    void join_ConcurrentRequests_DoesNotExceedSixActiveGroups() throws InterruptedException {
        // given
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        Long[] targetGroupIds = transaction.execute(status -> createSeed());
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger limitExceeded = new AtomicInteger();
        AtomicInteger unexpected = new AtomicInteger();

        Runnable first = joinTask(targetGroupIds[0], ready, start, done,
                success, limitExceeded, unexpected);
        Runnable second = joinTask(targetGroupIds[1], ready, start, done,
                success, limitExceeded, unexpected);

        // when
        pool.submit(first);
        pool.submit(second);
        boolean workersReady = ready.await(5, TimeUnit.SECONDS);
        start.countDown();
        boolean finished = done.await(15, TimeUnit.SECONDS);
        pool.shutdownNow();

        // then
        assertThat(workersReady).isTrue();
        assertThat(finished).isTrue();
        assertThat(success.get()).isEqualTo(1);
        assertThat(limitExceeded.get()).isEqualTo(1);
        assertThat(unexpected.get()).isZero();
        assertThat(groupMemberRepository.countByMemberIdAndStatusAndGroupStatus(
                memberId, GroupMemberStatus.ACTIVE, GroupStatus.ACTIVE
        )).isEqualTo(GroupMember.MAX_ACTIVE_GROUP_COUNT);
    }

    private Long[] createSeed() {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        Member member = memberRepository.save(Member.builder()
                .email("group-limit-" + suffix + "@example.com")
                .nickname("참여상한회원")
                .socialProvider(SocialProvider.GOOGLE)
                .role(Role.ROLE_USER)
                .socialId("group-limit-" + suffix)
                .build());
        memberId = member.getId();

        List<Group> groups = new ArrayList<>();
        for (int index = 0; index < 7; index++) {
            Group group = groupRepository.save(Group.builder()
                    .name("참여 상한 그룹 " + index)
                    .inviteCode((suffix.substring(0, 6) + index).toUpperCase())
                    .build());
            groups.add(group);
            groupIds.add(group.getId());
        }
        for (int index = 0; index < 5; index++) {
            groupMemberRepository.save(GroupMember.builder()
                    .member(member)
                    .group(groups.get(index))
                    .role(index == 0 ? GroupMemberRole.OWNER : GroupMemberRole.MEMBER)
                    .build());
        }
        groupMemberRepository.flush();
        return new Long[]{groups.get(5).getId(), groups.get(6).getId()};
    }

    private Runnable joinTask(
            Long groupId,
            CountDownLatch ready,
            CountDownLatch start,
            CountDownLatch done,
            AtomicInteger success,
            AtomicInteger limitExceeded,
            AtomicInteger unexpected
    ) {
        return () -> {
            try {
                ready.countDown();
                start.await();
                joinHarness.join(memberId, groupId);
                success.incrementAndGet();
            } catch (GroupException exception) {
                if (exception.getCode() == GroupErrorCode.GROUP_PARTICIPATION_LIMIT_EXCEEDED) {
                    limitExceeded.incrementAndGet();
                } else {
                    unexpected.incrementAndGet();
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                unexpected.incrementAndGet();
            } catch (RuntimeException exception) {
                unexpected.incrementAndGet();
            } finally {
                done.countDown();
            }
        };
    }

    static class JoinHarness {
        private final GroupValidationService groupValidationService;
        private final GroupMemberRepository groupMemberRepository;

        JoinHarness(
                GroupValidationService groupValidationService,
                GroupMemberRepository groupMemberRepository
        ) {
            this.groupValidationService = groupValidationService;
            this.groupMemberRepository = groupMemberRepository;
        }

        @Transactional
        public void join(Long memberId, Long groupId) {
            GroupValidationService.JoinLimitContext context = groupValidationService
                    .lockAndValidateJoinLimits(groupId, memberId);
            groupMemberRepository.saveAndFlush(GroupMember.builder()
                    .member(context.member())
                    .group(context.group())
                    .role(GroupMemberRole.MEMBER)
                    .build());
        }
    }
}
