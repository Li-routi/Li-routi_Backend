package com.lirouti.domain.verification.repository;

import com.lirouti.domain.group.entity.Group;
import com.lirouti.domain.group.entity.GroupRoutine;
import com.lirouti.domain.group.entity.GroupRoutineAssignment;
import com.lirouti.domain.group.entity.GroupRoutineCategory;
import com.lirouti.domain.group.enums.GroupRoutineAssignmentStatus;
import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.enums.Role;
import com.lirouti.domain.member.enums.SocialProvider;
import com.lirouti.domain.verification.entity.GroupRoutineVerification;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Limit;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class GroupUnreadVerificationQueryRepositoryTest {
    private static final LocalDate JOIN_DATE = LocalDate.of(2026, 8, 8);

    @Autowired private GroupUnreadVerificationQueryRepository repository;
    @PersistenceContext private EntityManager entityManager;

    @Test
    void findUnreadByCursor_IncludesJoinDayBeforeJoinTime_ExcludesPriorAndMine_AndUsesAscCursor() {
        Group group = Group.builder().name("미조회").inviteCode("UNREAD1").build();
        GroupRoutineCategory category = GroupRoutineCategory.builder().name("카테고리").active(true).build();
        Member viewer = member("조회자");
        Member author = member("작성자");
        entityManager.persist(group);
        entityManager.persist(category);

        GroupRoutineVerification previousDay = verification(group, category, author, "이전날");
        GroupRoutineVerification joinDayBeforeJoinTime = verification(group, category, author, "가입전시각");
        GroupRoutineVerification mine = verification(group, category, viewer, "내인증");
        GroupRoutineVerification later = verification(group, category, author, "나중인증");
        entityManager.flush();
        setCreatedAt(previousDay, JOIN_DATE.minusDays(1).atTime(23, 59));
        setCreatedAt(joinDayBeforeJoinTime, JOIN_DATE.atStartOfDay().plusHours(1));
        setCreatedAt(mine, JOIN_DATE.atTime(2, 0));
        setCreatedAt(later, JOIN_DATE.atTime(13, 0));
        entityManager.clear();

        List<GroupUnreadVerificationQueryRepository.UnreadVerificationProjection> all = repository
                .findUnreadByCursor(group.getId(), viewer.getId(), JOIN_DATE.atStartOfDay(),
                        null, null, Limit.of(20));
        assertThat(all).extracting(GroupUnreadVerificationQueryRepository.UnreadVerificationProjection::verificationId)
                .containsExactly(joinDayBeforeJoinTime.getId(), later.getId());

        List<GroupUnreadVerificationQueryRepository.UnreadVerificationProjection> afterRead = repository
                .findUnreadByCursor(group.getId(), viewer.getId(), JOIN_DATE.atStartOfDay(),
                        joinDayBeforeJoinTime.getId(), null, Limit.of(20));
        assertThat(afterRead).extracting(GroupUnreadVerificationQueryRepository.UnreadVerificationProjection::verificationId)
                .containsExactly(later.getId());
    }

    private Member member(String nickname) {
        String id = UUID.randomUUID().toString();
        Member member = Member.builder().email(id + "@example.com").nickname(nickname)
                .socialProvider(SocialProvider.GOOGLE).role(Role.ROLE_USER).socialId(id).build();
        entityManager.persist(member);
        return member;
    }

    private GroupRoutineVerification verification(
            Group group, GroupRoutineCategory category, Member member, String title
    ) {
        GroupRoutine routine = GroupRoutine.builder().group(group).category(category)
                .title(title).description(title).build();
        entityManager.persist(routine);
        GroupRoutineAssignment assignment = GroupRoutineAssignment.builder().groupRoutine(routine)
                .member(member).assignedDate(JOIN_DATE).scheduledStartTime(LocalTime.of(9, 0))
                .scheduledEndTime(LocalTime.of(10, 0)).status(GroupRoutineAssignmentStatus.COMPLETED).build();
        entityManager.persist(assignment);
        GroupRoutineVerification verification = GroupRoutineVerification.builder().assignment(assignment)
                .verifiedAt(JOIN_DATE.atTime(10, 0)).imageUrl("group-routine-verifications/test.jpg")
                .content(title).build();
        entityManager.persist(verification);
        return verification;
    }

    private void setCreatedAt(GroupRoutineVerification verification, LocalDateTime createdAt) {
        entityManager.createNativeQuery("update group_routine_verification set created_at = :createdAt where id = :id")
                .setParameter("createdAt", createdAt).setParameter("id", verification.getId()).executeUpdate();
    }
}
