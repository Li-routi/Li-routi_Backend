package com.lirouti.domain.verification.repository;

import com.lirouti.domain.group.entity.Group;
import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.enums.Role;
import com.lirouti.domain.member.enums.SocialProvider;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class GroupRoutineVerificationReadRepositoryTest {
    @Autowired private GroupRoutineVerificationReadRepository repository;
    @PersistenceContext private EntityManager entityManager;

    @Test
    void upsertIfAhead_CreatesAndOnlyAdvancesCursor() {
        Group group = Group.builder().name("읽음").inviteCode("READ001").build();
        String id = UUID.randomUUID().toString();
        Member member = Member.builder().email(id + "@example.com").nickname("회원")
                .socialProvider(SocialProvider.GOOGLE).role(Role.ROLE_USER).socialId(id).build();
        entityManager.persist(group);
        entityManager.persist(member);
        entityManager.flush();

        repository.upsertIfAhead(group.getId(), member.getId(), 10L, LocalDateTime.of(2026, 8, 8, 10, 0));
        assertThat(repository.findLastReadVerificationIdByGroupIdAndMemberId(group.getId(), member.getId()))
                .contains(10L);
        repository.upsertIfAhead(group.getId(), member.getId(), 20L, LocalDateTime.of(2026, 8, 8, 11, 0));
        repository.upsertIfAhead(group.getId(), member.getId(), 15L, LocalDateTime.of(2026, 8, 8, 12, 0));
        assertThat(repository.findLastReadVerificationIdByGroupIdAndMemberId(group.getId(), member.getId()))
                .contains(20L);
    }
}
