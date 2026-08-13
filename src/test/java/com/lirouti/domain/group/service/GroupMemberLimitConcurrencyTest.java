package com.lirouti.domain.group.service;

import com.lirouti.domain.group.entity.Group;
import com.lirouti.domain.group.entity.GroupMember;
import com.lirouti.domain.group.enums.GroupMemberRole;
import com.lirouti.domain.group.enums.GroupMemberStatus;
import com.lirouti.domain.group.exception.GroupException;
import com.lirouti.domain.group.exception.code.error.GroupErrorCode;
import com.lirouti.domain.group.dto.request.GroupReqDTO;
import com.lirouti.domain.group.repository.GroupMemberRepository;
import com.lirouti.domain.group.repository.GroupRepository;
import com.lirouti.domain.group.service.command.GroupJoinCommandService;
import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.enums.Role;
import com.lirouti.domain.member.enums.SocialProvider;
import com.lirouti.domain.member.repository.MemberRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
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
import static com.lirouti.support.testdb.MemberFixtureCleanup.deleteDependencies;

@SpringBootTest
@DisplayName("그룹별 활성 그룹원 상한 동시성 테스트")
class GroupMemberLimitConcurrencyTest {
    @Autowired
    private GroupJoinCommandService groupJoinCommandService;
    @Autowired
    private GroupMemberRepository groupMemberRepository;
    @Autowired
    private GroupRepository groupRepository;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long groupId;
    private String inviteCode;
    private final List<Long> memberIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        if (groupId == null) {
            return;
        }
        jdbcTemplate.update("delete from group_member where group_id = ?", groupId);
        jdbcTemplate.update("delete from member_group where id = ?", groupId);
        memberIds.forEach(memberId -> {
            deleteDependencies(jdbcTemplate, memberId);
            jdbcTemplate.update("delete from member where id = ?", memberId);
        });
    }

    @Test
    @DisplayName("5명 그룹의 동시 가입 요청은 하나만 성공해 최대 6명을 유지한다")
    void join_ConcurrentRequests_DoesNotExceedSixActiveMembers() throws InterruptedException {
        // given
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        Long[] candidateIds = transaction.execute(status -> createSeed());
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger limitExceeded = new AtomicInteger();
        AtomicInteger unexpected = new AtomicInteger();

        Runnable first = joinTask(candidateIds[0], ready, start, done,
                success, limitExceeded, unexpected);
        Runnable second = joinTask(candidateIds[1], ready, start, done,
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
        assertThat(groupMemberRepository.countActiveMembersByGroupId(
                groupId, GroupMemberStatus.ACTIVE
        )).isEqualTo(GroupMember.MAX_ACTIVE_MEMBER_COUNT_PER_GROUP);
    }

    private Long[] createSeed() {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        inviteCode = suffix.substring(0, 7).toUpperCase();
        Group group = groupRepository.save(Group.builder()
                .name("그룹원 상한 그룹")
                .inviteCode(inviteCode)
                .build());
        groupId = group.getId();

        List<Member> members = new ArrayList<>();
        for (int index = 0; index < 7; index++) {
            Member member = memberRepository.save(Member.builder()
                    .email("member-limit-" + index + "-" + suffix + "@example.com")
                    .nickname("그룹원" + index)
                    .socialProvider(SocialProvider.GOOGLE)
                    .role(Role.ROLE_USER)
                    .socialId("member-limit-" + index + "-" + suffix)
                    .build());
            members.add(member);
            memberIds.add(member.getId());
        }
        for (int index = 0; index < 5; index++) {
            groupMemberRepository.save(GroupMember.builder()
                    .member(members.get(index))
                    .group(group)
                    .role(index == 0 ? GroupMemberRole.OWNER : GroupMemberRole.MEMBER)
                    .build());
        }
        groupMemberRepository.flush();
        return new Long[]{members.get(5).getId(), members.get(6).getId()};
    }

    private Runnable joinTask(
            Long memberId,
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
                groupJoinCommandService.join(memberId, new GroupReqDTO.JoinGroup(inviteCode));
                success.incrementAndGet();
            } catch (GroupException exception) {
                if (exception.getCode() == GroupErrorCode.GROUP_MEMBER_LIMIT_EXCEEDED) {
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

}
