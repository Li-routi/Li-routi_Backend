package com.lirouti.domain.group.service.command;

import com.lirouti.domain.group.entity.Group;
import com.lirouti.domain.group.entity.GroupMember;
import com.lirouti.domain.group.enums.GroupMemberRole;
import com.lirouti.domain.group.exception.GroupException;
import com.lirouti.domain.group.exception.code.error.GroupErrorCode;
import com.lirouti.domain.group.repository.GroupMemberRepository;
import com.lirouti.domain.group.repository.GroupRepository;
import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.enums.Role;
import com.lirouti.domain.member.enums.SocialProvider;
import com.lirouti.domain.member.repository.MemberRepository;
import com.lirouti.domain.notification.entity.Notification;
import com.lirouti.domain.notification.enums.NotificationCategory;
import com.lirouti.domain.notification.enums.NotificationType;
import com.lirouti.domain.notification.repository.NotificationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static com.lirouti.support.testdb.MemberFixtureCleanup.deleteDependencies;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@DisplayName("누적형 그룹 poke 알림 통합 테스트")
class GroupPokeNotificationIntegrationTest {
    @Autowired private GroupPokeCommandService groupPokeCommandService;
    @Autowired private GroupRepository groupRepository;
    @Autowired private GroupMemberRepository groupMemberRepository;
    @Autowired private MemberRepository memberRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private JdbcTemplate jdbcTemplate;

    private Seed seed;

    @AfterEach
    void tearDown() {
        if (seed == null) return;
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(status -> {
            groupMemberRepository.deleteById(seed.requesterMembershipId());
            groupMemberRepository.deleteById(seed.targetMembershipId());
            groupRepository.deleteById(seed.groupId());
            deleteDependencies(jdbcTemplate, seed.requesterId());
            deleteDependencies(jdbcTemplate, seed.targetId());
            memberRepository.deleteById(seed.requesterId());
            memberRepository.deleteById(seed.targetId());
        });
    }

    @Test
    @DisplayName("성공한 누적 poke마다 커밋 뒤 대상에게 별도 GROUP_MEMBER_POKED 알림을 저장한다")
    void poke_Successes_CreateNotificationsAfterCommit() throws InterruptedException {
        seed = createSeed();

        groupPokeCommandService.poke(seed.groupId(), seed.requesterId(), seed.targetId());
        groupPokeCommandService.poke(seed.groupId(), seed.requesterId(), seed.targetId());

        GroupMember target = groupMemberRepository.findById(seed.targetMembershipId()).orElseThrow();
        List<Notification> notifications = awaitNotifications(2);
        assertThat(target.getTotalPokeCount()).isEqualTo(2L);
        assertThat(notifications).hasSize(2).allSatisfy(notification -> {
            assertThat(notification.getCategory()).isEqualTo(NotificationCategory.GROUP_ROUTINE);
            assertThat(notification.getType()).isEqualTo(NotificationType.GROUP_MEMBER_POKED);
            assertThat(notification.getMember().getId()).isEqualTo(seed.targetId());
            assertThat(notification.getReferenceId()).isEqualTo(seed.requesterId());
            assertThat(notification.getReferenceType()).isEqualTo("GROUP_MEMBER");
        });
        assertThat(notifications.stream().map(Notification::getDeduplicationKey).distinct()).hasSize(2);
    }

    @Test
    @DisplayName("탈퇴한 대상은 ACTIVE GroupMember 관계가 남아도 poke 후보에서 제외된다")
    void poke_WithdrawnTarget_DoesNotIncreaseCountOrPublishNotification() {
        seed = createSeed();
        Member target = memberRepository.findById(seed.targetId()).orElseThrow();
        String tombstone = "withdrawn-target-" + seed.targetId();
        target.withdraw(tombstone + "@example.com", tombstone, LocalDateTime.now());
        memberRepository.saveAndFlush(target);

        assertThatThrownBy(() -> groupPokeCommandService.poke(
                seed.groupId(), seed.requesterId(), seed.targetId()))
                .isInstanceOf(GroupException.class)
                .extracting("code")
                .isEqualTo(GroupErrorCode.ACTIVE_GROUP_MEMBER_NOT_FOUND);

        GroupMember targetMembership = groupMemberRepository
                .findById(seed.targetMembershipId()).orElseThrow();
        assertThat(targetMembership.getTotalPokeCount()).isZero();
        assertThat(notificationRepository.findAll().stream()
                .filter(notification -> seed.groupId().equals(notification.getGroupId()))
                .toList()).isEmpty();
    }

    private Seed createSeed() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        return transaction.execute(status -> {
            String suffix = UUID.randomUUID().toString();
            Group group = groupRepository.save(Group.builder().name("poke 알림 그룹")
                    .inviteCode(suffix.replace("-", "").substring(0, 7).toUpperCase()).build());
            Member requester = memberRepository.save(member("requester-" + suffix));
            Member target = memberRepository.save(member("target-" + suffix));
            List<GroupMember> memberships = groupMemberRepository.saveAll(List.of(
                    GroupMember.builder().group(group).member(requester).role(GroupMemberRole.MEMBER).build(),
                    GroupMember.builder().group(group).member(target).role(GroupMemberRole.MEMBER).build()
            ));
            return new Seed(group.getId(), requester.getId(), target.getId(),
                    memberships.getFirst().getId(), memberships.getLast().getId());
        });
    }

    private List<Notification> awaitNotifications(int expectedCount) throws InterruptedException {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
        List<Notification> notifications;
        do {
            notifications = notificationRepository.findAll().stream()
                    .filter(notification -> seed.groupId().equals(notification.getGroupId()))
                    .toList();
            if (notifications.size() == expectedCount) return notifications;
            Thread.sleep(50);
        } while (System.nanoTime() < deadline);
        return notifications;
    }

    private Member member(String identifier) {
        return Member.builder().email(identifier + "@example.com").nickname(identifier)
                .socialProvider(SocialProvider.GOOGLE).role(Role.ROLE_USER).socialId(identifier).build();
    }

    private record Seed(Long groupId, Long requesterId, Long targetId,
                        Long requesterMembershipId, Long targetMembershipId) {
    }
}
