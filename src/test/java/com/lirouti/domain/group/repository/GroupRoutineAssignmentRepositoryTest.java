package com.lirouti.domain.group.repository;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lirouti.domain.group.entity.Group;
import com.lirouti.domain.group.entity.GroupRoutine;
import com.lirouti.domain.group.entity.GroupRoutineAssignment;
import com.lirouti.domain.group.entity.RoutineCategory;
import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.enums.Role;
import com.lirouti.domain.member.enums.SocialProvider;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
@DisplayName("GroupRoutineAssignmentRepository 제약 테스트")
class GroupRoutineAssignmentRepositoryTest {
    @Autowired
    private GroupRoutineAssignmentRepository groupRoutineAssignmentRepository;

    @PersistenceContext
    private EntityManager em;

    @Test
    @DisplayName("같은 루틴을 같은 회원에게 두 번 할당할 수 없다")
    void save_DuplicateRoutineAndMember_ThrowsDataIntegrityViolation() {
        // given
        Group group = Group.builder().name("할당 그룹").inviteCode("ASG0001").build();
        RoutineCategory category = RoutineCategory.builder().name("할당 카테고리").active(true).build();
        Member member = Member.builder()
                .email("assignment@example.com")
                .nickname("할당회원")
                .socialProvider(SocialProvider.GOOGLE)
                .role(Role.ROLE_USER)
                .socialId("assignment-social")
                .build();
        em.persist(group);
        em.persist(category);
        em.persist(member);
        GroupRoutine routine = GroupRoutine.builder()
                .group(group)
                .category(category)
                .title("할당 루틴")
                .description("할당 테스트")
                .build();
        em.persist(routine);
        groupRoutineAssignmentRepository.saveAndFlush(assignment(routine, member));

        // when & then
        assertThatThrownBy(() -> groupRoutineAssignmentRepository
                .saveAndFlush(assignment(routine, member)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private GroupRoutineAssignment assignment(GroupRoutine routine, Member member) {
        return GroupRoutineAssignment.builder()
                .groupRoutine(routine)
                .member(member)
                .build();
    }
}
