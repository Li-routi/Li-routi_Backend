package com.lirouti.domain.group.service.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.lirouti.domain.group.dto.response.GroupResDTO;
import com.lirouti.domain.group.entity.Group;
import com.lirouti.domain.group.entity.GroupMember;
import com.lirouti.domain.group.enums.GroupMemberRole;
import com.lirouti.domain.group.repository.GroupRepository;
import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.enums.Role;
import com.lirouti.domain.member.enums.SocialProvider;
import com.lirouti.domain.member.repository.MemberRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@SpringBootTest
@DisplayName("그룹 초대코드 발급 트랜잭션 통합 테스트")
class GroupInviteCodeIssueAttemptIntegrationTest {
    private static final String COLLISION_GROUP_NAME = "초대코드 충돌 테스트 그룹";

    @Autowired
    private GroupInviteCodeCommandService groupInviteCodeCommandService;
    @MockitoBean
    private GroupRepository groupRepository;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @PersistenceContext
    private EntityManager entityManager;

    private Long groupId;
    private Long groupMemberId;
    private Long memberId;
    private final AtomicReference<Long> collisionGroupId = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        Seed seed = transaction.execute(status -> createSeed());
        groupId = seed.groupId();
        groupMemberId = seed.groupMemberId();
        memberId = seed.memberId();
        when(groupRepository.findById(groupId)).thenAnswer(invocation ->
                Optional.ofNullable(entityManager.find(Group.class, groupId)));
        when(groupRepository.existsByInviteCode(anyString())).thenReturn(false);
    }

    @AfterEach
    void tearDown() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(status -> {
            Long duplicateId = collisionGroupId.get();
            if (duplicateId != null) {
                entityManager.remove(entityManager.find(Group.class, duplicateId));
            }
            entityManager.remove(entityManager.find(GroupMember.class, groupMemberId));
            entityManager.remove(entityManager.find(Group.class, groupId));
            entityManager.remove(entityManager.find(Member.class, memberId));
        });
    }

    @Test
    @DisplayName("사전 중복 확인 후 DB unique 충돌이 발생하면 새 트랜잭션에서 재시도한다")
    void issueInviteCode_DatabaseUniqueConflict_RetriesInNewTransaction() {
        // given
        AtomicBoolean collisionInjected = new AtomicBoolean();
        doAnswer(invocation -> {
            Group group = invocation.getArgument(0);
            if (collisionInjected.compareAndSet(false, true)) {
                insertCollisionGroupInNewTransaction(group.getInviteCode());
            }
            try {
                entityManager.merge(group);
                entityManager.flush();
                return group;
            } catch (RuntimeException exception) {
                throw new DataIntegrityViolationException(
                        "초대코드 저장 중 무결성 제약을 위반했습니다.",
                        exception
                );
            }
        }).when(groupRepository).saveAndFlush(any(Group.class));

        // when
        GroupResDTO.InviteCode result = groupInviteCodeCommandService
                .issueInviteCode(groupId, memberId);

        // then
        assertThat(collisionInjected).isTrue();
        assertThat(result.inviteCode()).hasSize(7);
        String persistedInviteCode = new TransactionTemplate(transactionManager)
                .execute(status -> entityManager.find(Group.class, groupId).getInviteCode());
        assertThat(persistedInviteCode).isEqualTo(result.inviteCode());
        assertThat(collisionGroupId.get()).isNotNull();
    }

    private Seed createSeed() {
        String suffix = Long.toString(System.nanoTime());
        Member member = memberRepository.save(Member.builder()
                .email("invite-attempt-" + suffix + "@example.com")
                .nickname("초대코드회원")
                .socialProvider(SocialProvider.GOOGLE)
                .role(Role.ROLE_USER)
                .socialId("invite-attempt-" + suffix)
                .build());
        Group group = Group.builder()
                .name("초대코드 테스트 그룹")
                .inviteCode(suffix.substring(Math.max(0, suffix.length() - 7)))
                .build();
        GroupMember groupMember = GroupMember.builder()
                .member(member)
                .group(group)
                .role(GroupMemberRole.OWNER)
                .build();
        entityManager.persist(group);
        entityManager.persist(groupMember);
        entityManager.flush();
        return new Seed(group.getId(), groupMember.getId(), member.getId());
    }

    private void insertCollisionGroupInNewTransaction(String inviteCode) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.executeWithoutResult(status -> {
            Group collisionGroup = Group.builder()
                    .name(COLLISION_GROUP_NAME)
                    .inviteCode(inviteCode)
                    .build();
            entityManager.persist(collisionGroup);
            entityManager.flush();
            collisionGroupId.set(collisionGroup.getId());
        });
    }

    private record Seed(Long groupId, Long groupMemberId, Long memberId) {
    }
}
