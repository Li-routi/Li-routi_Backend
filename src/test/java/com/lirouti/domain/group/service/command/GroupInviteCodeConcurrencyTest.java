package com.lirouti.domain.group.service.command;

import com.lirouti.domain.group.dto.response.GroupResDTO;
import com.lirouti.domain.group.entity.Group;
import com.lirouti.domain.group.entity.GroupMember;
import com.lirouti.domain.group.enums.GroupMemberRole;
import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.enums.Role;
import com.lirouti.domain.member.enums.SocialProvider;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest
@DisplayName("그룹 초대코드 재발급 동시성 테스트")
class GroupInviteCodeConcurrencyTest {
    private static final LocalDateTime FIRST_EXPIRES_AT =
            LocalDateTime.of(2026, 8, 1, 12, 10);
    private static final LocalDateTime SECOND_EXPIRES_AT =
            LocalDateTime.of(2026, 8, 1, 12, 20);

    @Autowired
    private GroupInviteCodeCommandService groupInviteCodeCommandService;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @PersistenceContext
    private EntityManager entityManager;
    @MockitoBean
    private GroupInviteCodeGenerator inviteCodeGenerator;

    private Long groupId;
    private Long groupMemberId;
    private Long memberId;

    @BeforeEach
    void setUp() {
        Seed seed = transaction().execute(status -> createSeed());
        groupId = seed.groupId();
        groupMemberId = seed.groupMemberId();
        memberId = seed.memberId();
    }

    @AfterEach
    void tearDown() {
        transaction().executeWithoutResult(status -> {
            entityManager.remove(entityManager.find(GroupMember.class, groupMemberId));
            entityManager.remove(entityManager.find(Group.class, groupId));
            entityManager.remove(entityManager.find(Member.class, memberId));
        });
    }

    @Test
    @DisplayName("동일 그룹의 동시 재발급은 그룹 행 잠금으로 직렬화한다")
    void issueInviteCode_ConcurrentRequestsForSameGroup_AreSerialized() throws Exception {
        CountDownLatch firstGeneratorEntered = new CountDownLatch(1);
        CountDownLatch releaseFirstGenerator = new CountDownLatch(1);
        CountDownLatch secondGeneratorEntered = new CountDownLatch(1);
        AtomicInteger invocationCount = new AtomicInteger();
        when(inviteCodeGenerator.generate()).thenAnswer(invocation -> {
            if (invocationCount.incrementAndGet() == 1) {
                firstGeneratorEntered.countDown();
                assertThat(releaseFirstGenerator.await(5, TimeUnit.SECONDS)).isTrue();
                return new GroupInviteCodeGenerator.GeneratedInviteCode(
                        "LOCK001",
                        FIRST_EXPIRES_AT
                );
            }
            secondGeneratorEntered.countDown();
            return new GroupInviteCodeGenerator.GeneratedInviteCode(
                    "LOCK002",
                    SECOND_EXPIRES_AT
            );
        });

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<GroupResDTO.InviteCode> first = executor.submit(() ->
                    groupInviteCodeCommandService.issueInviteCode(groupId, memberId));
            assertThat(firstGeneratorEntered.await(5, TimeUnit.SECONDS)).isTrue();

            Future<GroupResDTO.InviteCode> second = executor.submit(() ->
                    groupInviteCodeCommandService.issueInviteCode(groupId, memberId));
            assertThat(secondGeneratorEntered.await(300, TimeUnit.MILLISECONDS)).isFalse();

            releaseFirstGenerator.countDown();

            assertThat(first.get(5, TimeUnit.SECONDS).inviteCode()).isEqualTo("LOCK001");
            assertThat(second.get(5, TimeUnit.SECONDS).inviteCode()).isEqualTo("LOCK002");
        }

        assertThat(secondGeneratorEntered.getCount()).isZero();
        Group persistedGroup = transaction().execute(status ->
                entityManager.find(Group.class, groupId));
        assertThat(persistedGroup.getInviteCode()).isEqualTo("LOCK002");
        assertThat(persistedGroup.getInviteCodeExpiresAt()).isEqualTo(SECOND_EXPIRES_AT);
    }

    private Seed createSeed() {
        String suffix = Long.toString(System.nanoTime());
        Member member = Member.builder()
                .email("invite-lock-" + suffix + "@example.com")
                .nickname("초대코드잠금회원")
                .socialProvider(SocialProvider.GOOGLE)
                .role(Role.ROLE_USER)
                .socialId("invite-lock-" + suffix)
                .build();
        Group group = Group.builder()
                .name("초대코드 잠금 그룹")
                .inviteCode(("L" + suffix).substring(0, 7))
                .build();
        GroupMember groupMember = GroupMember.builder()
                .member(member)
                .group(group)
                .role(GroupMemberRole.OWNER)
                .build();
        entityManager.persist(member);
        entityManager.persist(group);
        entityManager.persist(groupMember);
        entityManager.flush();
        return new Seed(group.getId(), groupMember.getId(), member.getId());
    }

    private TransactionTemplate transaction() {
        return new TransactionTemplate(transactionManager);
    }

    private record Seed(Long groupId, Long groupMemberId, Long memberId) {
    }
}
