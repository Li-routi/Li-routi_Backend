package com.lirouti.domain.group.service.command;

import com.lirouti.domain.group.entity.Group;
import com.lirouti.domain.group.entity.GroupMember;
import com.lirouti.domain.group.dto.request.GroupReqDTO;
import com.lirouti.domain.group.enums.GroupMemberRole;
import com.lirouti.domain.group.enums.GroupMemberStatus;
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

@SpringBootTest
@DisplayName("그룹 탈퇴·강퇴 동시성 테스트")
class GroupMembershipCommandConcurrencyTest {
    @Autowired
    private GroupCommandService groupCommandService;
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
    private final List<Long> memberIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        if (groupId == null) {
            return;
        }
        jdbcTemplate.update("delete from group_member where group_id = ?", groupId);
        jdbcTemplate.update("delete from member_group where id = ?", groupId);
        memberIds.forEach(memberId ->
                jdbcTemplate.update("delete from member where id = ?", memberId));
    }

    @Test
    @DisplayName("같은 구성원에 대한 동시 탈퇴·강퇴는 하나만 성공한다")
    void leaveAndKick_ConcurrentRequests_OnlyOneSucceeds() throws InterruptedException {
        // given
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        Seed seed = transaction.execute(status -> createSeed());
        assertThat(seed).isNotNull();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger accessDenied = new AtomicInteger();
        AtomicInteger unexpected = new AtomicInteger();

        // when
        pool.submit(commandTask(
                () -> groupCommandService.leaveGroup(seed.groupId(), seed.targetMemberId()),
                ready, start, done, success, accessDenied, unexpected
        ));
        pool.submit(commandTask(
                () -> groupCommandService.kickMember(
                        seed.groupId(), seed.ownerMemberId(), seed.targetMemberId()
                ),
                ready, start, done, success, accessDenied, unexpected
        ));

        boolean workersReady = ready.await(5, TimeUnit.SECONDS);
        start.countDown();
        boolean finished = done.await(15, TimeUnit.SECONDS);
        pool.shutdownNow();

        // then
        assertThat(workersReady).isTrue();
        assertThat(finished).isTrue();
        assertThat(success.get()).isEqualTo(1);
        assertThat(accessDenied.get()).isEqualTo(1);
        assertThat(unexpected.get()).isZero();

        GroupMember target = transaction.execute(status ->
                groupMemberRepository.findByGroupIdAndMemberId(
                        seed.groupId(), seed.targetMemberId()
                ).orElseThrow());
        assertThat(target.getStatus()).isIn(
                GroupMemberStatus.LEFT,
                GroupMemberStatus.KICKED
        );
        assertThat(groupMemberRepository.countActiveMembersByGroupId(
                seed.groupId(), GroupMemberStatus.ACTIVE
        )).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 그룹의 동시 방장 위임은 직렬화되어 하나만 성공하고 ACTIVE OWNER를 한 명 유지한다")
    void transferOwner_ConcurrentRequests_OnlyOneSucceedsAndKeepsOneOwner()
            throws InterruptedException {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        Seed seed = transaction.execute(status -> createTransferSeed());
        assertThat(seed).isNotNull();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger ownerAccessDenied = new AtomicInteger();
        AtomicInteger unexpected = new AtomicInteger();

        pool.submit(transferTask(
                () -> groupCommandService.transferGroupOwner(
                        seed.groupId(), seed.ownerMemberId(),
                        new GroupReqDTO.TransferOwner(seed.targetMemberId())
                ),
                ready, start, done, success, ownerAccessDenied, unexpected
        ));
        pool.submit(transferTask(
                () -> groupCommandService.transferGroupOwner(
                        seed.groupId(), seed.ownerMemberId(),
                        new GroupReqDTO.TransferOwner(seed.otherTargetMemberId())
                ),
                ready, start, done, success, ownerAccessDenied, unexpected
        ));

        boolean workersReady = ready.await(5, TimeUnit.SECONDS);
        start.countDown();
        boolean finished = done.await(15, TimeUnit.SECONDS);
        pool.shutdownNow();

        assertThat(workersReady).isTrue();
        assertThat(finished).isTrue();
        assertThat(success.get()).isEqualTo(1);
        assertThat(ownerAccessDenied.get()).isEqualTo(1);
        assertThat(unexpected.get()).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from group_member where group_id = ? and status = 'ACTIVE' and role = 'OWNER'",
                Long.class,
                seed.groupId()
        )).isEqualTo(1L);
    }

    private Runnable commandTask(
            Runnable command,
            CountDownLatch ready,
            CountDownLatch start,
            CountDownLatch done,
            AtomicInteger success,
            AtomicInteger accessDenied,
            AtomicInteger unexpected
    ) {
        return () -> {
            try {
                ready.countDown();
                start.await();
                command.run();
                success.incrementAndGet();
            } catch (GroupException exception) {
                if (exception.getCode() == GroupErrorCode.GROUP_MEMBER_ACCESS_DENIED) {
                    accessDenied.incrementAndGet();
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

    private Runnable transferTask(
            Runnable command,
            CountDownLatch ready,
            CountDownLatch start,
            CountDownLatch done,
            AtomicInteger success,
            AtomicInteger ownerAccessDenied,
            AtomicInteger unexpected
    ) {
        return () -> {
            try {
                ready.countDown();
                start.await();
                command.run();
                success.incrementAndGet();
            } catch (GroupException exception) {
                if (exception.getCode() == GroupErrorCode.GROUP_OWNER_ACCESS_DENIED) {
                    ownerAccessDenied.incrementAndGet();
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

    private Seed createSeed() {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        Group group = groupRepository.save(Group.builder()
                .name("동시성그룹" + suffix.substring(0, 8))
                .inviteCode(suffix.substring(0, 7).toUpperCase())
                .build());
        groupId = group.getId();

        Member owner = saveMember("owner", suffix);
        Member target = saveMember("target", suffix);
        groupMemberRepository.save(GroupMember.builder()
                .member(owner)
                .group(group)
                .role(GroupMemberRole.OWNER)
                .build());
        groupMemberRepository.save(GroupMember.builder()
                .member(target)
                .group(group)
                .role(GroupMemberRole.MEMBER)
                .build());
        groupMemberRepository.flush();
        return new Seed(group.getId(), owner.getId(), target.getId(), null);
    }

    private Seed createTransferSeed() {
        Seed seed = createSeed();
        String suffix = UUID.randomUUID().toString().replace("-", "");
        Member otherTarget = saveMember("other-target", suffix);
        Group group = groupRepository.findById(seed.groupId()).orElseThrow();
        groupMemberRepository.save(GroupMember.builder()
                .member(otherTarget)
                .group(group)
                .role(GroupMemberRole.MEMBER)
                .build());
        groupMemberRepository.flush();
        return new Seed(
                seed.groupId(), seed.ownerMemberId(), seed.targetMemberId(), otherTarget.getId());
    }

    private Member saveMember(String role, String suffix) {
        Member member = memberRepository.save(Member.builder()
                .email("group-membership-" + role + "-" + suffix + "@example.com")
                .nickname("동시성" + role)
                .socialProvider(SocialProvider.GOOGLE)
                .role(Role.ROLE_USER)
                .socialId("group-membership-" + role + "-" + suffix)
                .build());
        memberIds.add(member.getId());
        return member;
    }

    private record Seed(
            Long groupId,
            Long ownerMemberId,
            Long targetMemberId,
            Long otherTargetMemberId
    ) {
    }
}
