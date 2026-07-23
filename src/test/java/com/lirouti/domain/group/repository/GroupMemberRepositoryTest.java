package com.lirouti.domain.group.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.lirouti.domain.group.entity.Group;
import com.lirouti.domain.group.entity.GroupMember;
import com.lirouti.domain.group.enums.GroupMemberRole;
import com.lirouti.domain.group.enums.GroupMemberStatus;
import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.enums.Role;
import com.lirouti.domain.member.enums.SocialProvider;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
@DisplayName("GroupMemberRepository 활성 구성원 조회 테스트")
class GroupMemberRepositoryTest {
    private final AtomicInteger sequence = new AtomicInteger();

    @Autowired
    private GroupMemberRepository groupMemberRepository;

    @PersistenceContext
    private EntityManager em;

    @Test
    @DisplayName("대상 그룹의 ACTIVE 구성원만 조회한다")
    void findAllByGroupIdAndStatus_ActiveOnly_ReturnsScopedMembers() {
        // given
        Group target = group("A000001");
        Group other = group("A000002");
        GroupMember owner = membership(member(), target, GroupMemberRole.OWNER);
        GroupMember active = membership(member(), target, GroupMemberRole.MEMBER);
        GroupMember left = membership(member(), target, GroupMemberRole.MEMBER);
        GroupMember kicked = membership(member(), target, GroupMemberRole.MEMBER);
        membership(member(), other, GroupMemberRole.MEMBER);
        left.leave();
        kicked.kick();
        em.flush();
        em.clear();

        // when
        List<GroupMember> result = groupMemberRepository
                .findAllByGroupIdAndStatus(target.getId(), GroupMemberStatus.ACTIVE);

        // then
        assertThat(result)
                .extracting(GroupMember::getId)
                .containsExactlyInAnyOrder(owner.getId(), active.getId());
    }

    private Group group(String inviteCode) {
        Group group = Group.builder().name("테스트 그룹").inviteCode(inviteCode).build();
        em.persist(group);
        return group;
    }

    private Member member() {
        int value = sequence.incrementAndGet();
        Member member = Member.builder()
                .email("group-member-" + value + "@example.com")
                .nickname("구성원" + value)
                .socialProvider(SocialProvider.GOOGLE)
                .role(Role.ROLE_USER)
                .socialId("group-member-social-" + value)
                .build();
        em.persist(member);
        return member;
    }

    private GroupMember membership(Member member, Group group, GroupMemberRole role) {
        GroupMember membership = GroupMember.builder()
                .member(member)
                .group(group)
                .role(role)
                .build();
        em.persist(membership);
        return membership;
    }
}
