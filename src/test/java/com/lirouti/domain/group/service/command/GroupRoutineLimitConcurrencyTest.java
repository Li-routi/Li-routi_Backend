package com.lirouti.domain.group.service.command;

import com.lirouti.domain.group.dto.request.GroupReqDTO;
import com.lirouti.domain.group.entity.Group;
import com.lirouti.domain.group.entity.GroupMember;
import com.lirouti.domain.group.entity.GroupRoutine;
import com.lirouti.domain.group.entity.GroupRoutineCategory;
import com.lirouti.domain.group.enums.GroupMemberRole;
import com.lirouti.domain.group.exception.GroupException;
import com.lirouti.domain.group.exception.code.error.GroupErrorCode;
import com.lirouti.domain.group.repository.GroupMemberRepository;
import com.lirouti.domain.group.repository.GroupRepository;
import com.lirouti.domain.group.repository.GroupRoutineCategoryRepository;
import com.lirouti.domain.group.repository.GroupRoutineRepository;
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

import java.time.DayOfWeek;
import java.time.LocalTime;
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
@DisplayName("그룹 루틴 개수 상한 동시성 테스트")
class GroupRoutineLimitConcurrencyTest {
    @Autowired
    private GroupCommandService groupCommandService;
    @Autowired
    private GroupRoutineRepository groupRoutineRepository;
    @Autowired
    private GroupRoutineCategoryRepository groupRoutineCategoryRepository;
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

    private Long memberId;
    private Long groupId;
    private Long categoryId;

    @AfterEach
    void tearDown() {
        if (groupId == null) {
            return;
        }
        jdbcTemplate.update("""
                delete assignment
                from group_routine_assignment assignment
                join group_routine routine on routine.id = assignment.group_routine_id
                where routine.group_id = ?
                """, groupId);
        jdbcTemplate.update("""
                delete schedule
                from group_routine_schedule schedule
                join group_routine routine on routine.id = schedule.group_routine_id
                where routine.group_id = ?
                """, groupId);
        jdbcTemplate.update("delete from group_routine where group_id = ?", groupId);
        jdbcTemplate.update("delete from group_member where group_id = ?", groupId);
        jdbcTemplate.update("delete from group_routine_category where id = ?", categoryId);
        jdbcTemplate.update("delete from member_group where id = ?", groupId);
        jdbcTemplate.update("delete from member where id = ?", memberId);
    }

    @Test
    @DisplayName("29개 루틴이 있는 그룹의 동시 생성 요청은 하나만 성공해 최대 30개를 유지한다")
    void createRoutine_ConcurrentRequests_DoesNotExceedThirtyRoutines()
            throws InterruptedException {
        // given
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(status -> createSeed());
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger limitExceeded = new AtomicInteger();
        AtomicInteger unexpected = new AtomicInteger();

        Runnable first = createTask("동시 루틴 A", ready, start, done,
                success, limitExceeded, unexpected);
        Runnable second = createTask("동시 루틴 B", ready, start, done,
                success, limitExceeded, unexpected);

        // when
        pool.submit(first);
        pool.submit(second);
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
        assertThat(groupRoutineRepository.countByGroupId(groupId))
                .isEqualTo(GroupRoutine.MAX_GROUP_ROUTINE_COUNT);
    }

    private void createSeed() {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        Member member = memberRepository.save(Member.builder()
                .email("routine-limit-" + suffix + "@example.com")
                .nickname("루틴상한회원")
                .socialProvider(SocialProvider.GOOGLE)
                .role(Role.ROLE_USER)
                .socialId("routine-limit-" + suffix)
                .build());
        Group group = groupRepository.save(Group.builder()
                .name("루틴 상한 그룹")
                .inviteCode(suffix.substring(0, 7).toUpperCase())
                .build());
        GroupRoutineCategory category = groupRoutineCategoryRepository.save(
                GroupRoutineCategory.builder()
                        .group(group)
                        .name("상한 카테고리")
                        .active(true)
                        .build()
        );
        groupMemberRepository.save(GroupMember.builder()
                .member(member)
                .group(group)
                .role(GroupMemberRole.OWNER)
                .build());

        List<GroupRoutine> routines = new ArrayList<>();
        for (int index = 0; index < GroupRoutine.MAX_GROUP_ROUTINE_COUNT - 1; index++) {
            routines.add(GroupRoutine.builder()
                    .group(group)
                    .category(category)
                    .title("기존 루틴 " + index)
                    .description("상한 검증용 기존 루틴")
                    .build());
        }
        groupRoutineRepository.saveAllAndFlush(routines);

        memberId = member.getId();
        groupId = group.getId();
        categoryId = category.getId();
    }

    private Runnable createTask(
            String title,
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
                groupCommandService.createRoutine(groupId, memberId, request(title));
                success.incrementAndGet();
            } catch (GroupException exception) {
                if (exception.getCode() == GroupErrorCode.GROUP_ROUTINE_LIMIT_EXCEEDED) {
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

    private GroupReqDTO.GroupRoutineCreateRequest request(String title) {
        return new GroupReqDTO.GroupRoutineCreateRequest(
                categoryId,
                title,
                "동시 생성 상한을 검증합니다.",
                List.of(new GroupReqDTO.RoutineSchedule(
                        DayOfWeek.MONDAY,
                        LocalTime.of(9, 0),
                        LocalTime.of(10, 0)
                ))
        );
    }
}
