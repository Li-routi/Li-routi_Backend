package com.lirouti.domain.group.service.command;

import com.lirouti.domain.group.dto.request.GroupReqDTO;
import com.lirouti.domain.group.entity.Group;
import com.lirouti.domain.group.entity.GroupMember;
import com.lirouti.domain.group.entity.GroupRoutineCategory;
import com.lirouti.domain.group.enums.GroupMemberRole;
import com.lirouti.domain.group.exception.GroupException;
import com.lirouti.domain.group.exception.code.error.GroupErrorCode;
import com.lirouti.domain.group.repository.GroupMemberRepository;
import com.lirouti.domain.group.repository.GroupRepository;
import com.lirouti.domain.group.repository.GroupRoutineCategoryRepository;
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
@DisplayName("그룹 카테고리 생성 동시성 테스트")
class GroupCategoryConcurrencyTest {
    @Autowired private GroupCommandService commandService;
    @Autowired private GroupRoutineCategoryRepository categoryRepository;
    @Autowired private GroupMemberRepository groupMemberRepository;
    @Autowired private GroupRepository groupRepository;
    @Autowired private MemberRepository memberRepository;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private JdbcTemplate jdbcTemplate;

    private final List<Long> groupIds = new ArrayList<>();
    private final List<Long> memberIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        for (Long groupId : groupIds) {
            jdbcTemplate.update("delete from group_routine_category where group_id = ?", groupId);
            jdbcTemplate.update("delete from group_member where group_id = ?", groupId);
            jdbcTemplate.update("delete from member_group where id = ?", groupId);
        }
        for (Long memberId : memberIds) {
            jdbcTemplate.update("delete from member where id = ?", memberId);
        }
    }

    @Test
    @DisplayName("4개인 그룹의 서로 다른 동시 생성은 한 건만 성공해 5개를 유지한다")
    void createCategory_ConcurrentAtLimit_DoesNotExceedFive() throws InterruptedException {
        // given
        Seed seed = transaction().execute(status -> seed(4));
        Counters counters = runConcurrently(
                seed,
                new GroupReqDTO.CreateCategory("마지막A", null),
                new GroupReqDTO.CreateCategory("마지막B", null)
        );

        // then
        assertThat(counters.success.get()).isEqualTo(1);
        assertThat(counters.limit.get()).isEqualTo(1);
        assertThat(counters.duplicate.get()).isZero();
        assertThat(counters.unexpected.get()).isZero();
        assertThat(categoryRepository.countByGroupIdAndActiveTrue(seed.groupId))
                .isEqualTo(GroupRoutineCategory.MAX_GROUP_CATEGORY_COUNT);
    }

    @Test
    @DisplayName("동일 이름 동시 생성은 한 건만 성공한다")
    void createCategory_ConcurrentSameName_OnlyOneSucceeds() throws InterruptedException {
        // given
        Seed seed = transaction().execute(status -> seed(0));
        GroupReqDTO.CreateCategory request = new GroupReqDTO.CreateCategory("같은 이름", null);

        // when
        Counters counters = runConcurrently(seed, request, request);

        // then
        assertThat(counters.success.get()).isEqualTo(1);
        assertThat(counters.duplicate.get()).isEqualTo(1);
        assertThat(counters.limit.get()).isZero();
        assertThat(counters.unexpected.get()).isZero();
        assertThat(categoryRepository.countByGroupIdAndActiveTrue(seed.groupId)).isEqualTo(1L);
    }

    private Counters runConcurrently(
            Seed seed,
            GroupReqDTO.CreateCategory firstRequest,
            GroupReqDTO.CreateCategory secondRequest
    ) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        Counters counters = new Counters();

        pool.submit(task(seed, firstRequest, ready, start, done, counters));
        pool.submit(task(seed, secondRequest, ready, start, done, counters));
        boolean workersReady = ready.await(5, TimeUnit.SECONDS);
        start.countDown();
        boolean finished = done.await(20, TimeUnit.SECONDS);
        pool.shutdownNow();

        assertThat(workersReady).isTrue();
        assertThat(finished).isTrue();
        return counters;
    }

    private Runnable task(
            Seed seed,
            GroupReqDTO.CreateCategory request,
            CountDownLatch ready,
            CountDownLatch start,
            CountDownLatch done,
            Counters counters
    ) {
        return () -> {
            try {
                ready.countDown();
                start.await();
                commandService.createCategory(seed.groupId, seed.ownerId, request);
                counters.success.incrementAndGet();
            } catch (GroupException exception) {
                if (exception.getCode() == GroupErrorCode.GROUP_ROUTINE_CATEGORY_LIMIT_EXCEEDED) {
                    counters.limit.incrementAndGet();
                } else if (exception.getCode()
                        == GroupErrorCode.DUPLICATE_GROUP_ROUTINE_CATEGORY_NAME) {
                    counters.duplicate.incrementAndGet();
                } else {
                    counters.unexpected.incrementAndGet();
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } catch (RuntimeException exception) {
                counters.unexpected.incrementAndGet();
            } finally {
                done.countDown();
            }
        };
    }

    private Seed seed(int categoryCount) {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        Member owner = memberRepository.save(Member.builder()
                .email("group-category-" + suffix + "@example.com")
                .nickname("카테고리방장")
                .socialProvider(SocialProvider.GOOGLE)
                .role(Role.ROLE_USER)
                .socialId("group-category-" + suffix)
                .build());
        Group group = groupRepository.save(Group.builder()
                .name("카테고리 동시성")
                .inviteCode(suffix.substring(0, 7).toUpperCase())
                .build());
        groupMemberRepository.save(GroupMember.builder()
                .group(group).member(owner).role(GroupMemberRole.OWNER).build());
        for (int index = 0; index < categoryCount; index++) {
            categoryRepository.save(GroupRoutineCategory.builder()
                    .group(group).name("기존" + index).active(true).build());
        }
        categoryRepository.flush();
        groupIds.add(group.getId());
        memberIds.add(owner.getId());
        return new Seed(group.getId(), owner.getId());
    }

    private TransactionTemplate transaction() {
        return new TransactionTemplate(transactionManager);
    }

    private record Seed(Long groupId, Long ownerId) {
    }

    private static final class Counters {
        private final AtomicInteger success = new AtomicInteger();
        private final AtomicInteger limit = new AtomicInteger();
        private final AtomicInteger duplicate = new AtomicInteger();
        private final AtomicInteger unexpected = new AtomicInteger();
    }
}
