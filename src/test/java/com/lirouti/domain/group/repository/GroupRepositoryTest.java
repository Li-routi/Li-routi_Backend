package com.lirouti.domain.group.repository;

import com.lirouti.domain.group.entity.Group;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
@DisplayName("GroupRepository 잠금 조회 테스트")
class GroupRepositoryTest {
    @Autowired
    private GroupRepository groupRepository;

    @PersistenceContext
    private EntityManager em;

    @Test
    @DisplayName("그룹을 비관적 쓰기 잠금으로 조회한다")
    void findByIdForUpdate_ExistingGroup_ReturnsGroup() {
        // given
        Group group = groupRepository.saveAndFlush(Group.builder()
                .name("잠금 그룹")
                .inviteCode("LCK0001")
                .build());
        em.clear();

        // when
        Group result = groupRepository.findByIdForUpdate(group.getId()).orElseThrow();

        // then
        assertThat(result.getId()).isEqualTo(group.getId());
    }
}
