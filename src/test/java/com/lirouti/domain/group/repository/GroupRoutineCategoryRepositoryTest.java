package com.lirouti.domain.group.repository;

import com.lirouti.domain.group.entity.Group;
import com.lirouti.domain.group.entity.GroupRoutineCategory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
@DisplayName("GroupRoutineCategoryRepository 테스트")
class GroupRoutineCategoryRepositoryTest {
    @Autowired
    private GroupRoutineCategoryRepository groupRoutineCategoryRepository;

    @PersistenceContext
    private EntityManager em;

    @Test
    @DisplayName("기본 카테고리와 요청 그룹의 활성 카테고리만 조회한다")
    void findUsableByGroupId_ActiveAndScoped_ReturnsFixedAndOwnedCategories() {
        // given
        Group target = group("CAT1001");
        Group other = group("CAT1002");
        GroupRoutineCategory targetCategory = category(target, "아침 관리", true);
        GroupRoutineCategory inactiveCategory = category(target, "비활성 관리", false);
        GroupRoutineCategory otherCategory = category(other, "다른 그룹 관리", true);
        em.flush();
        em.clear();

        // when
        List<GroupRoutineCategory> result = groupRoutineCategoryRepository
                .findUsableByGroupId(target.getId());

        // then
        assertThat(result)
                .extracting(GroupRoutineCategory::getName)
                .contains("운동", "건강", "자기계발", "생활정리", "마음관리", "취미", "아침 관리")
                .doesNotContain(inactiveCategory.getName(), otherCategory.getName());
        assertThat(result.getLast().getId()).isEqualTo(targetCategory.getId());
    }

    @Test
    @DisplayName("이름 중복 검사는 기본 카테고리와 요청 그룹 범위로 제한한다")
    void existsReservedName_FixedOrOwnedName_ReturnsTrue() {
        // given
        Group target = group("CAT2001");
        Group other = group("CAT2002");
        category(target, "아침 관리", true);
        category(other, "다른 그룹 관리", true);
        em.flush();
        em.clear();

        // when
        boolean fixedExists = groupRoutineCategoryRepository
                .existsReservedName(target.getId(), "운동");
        boolean ownedExists = groupRoutineCategoryRepository
                .existsReservedName(target.getId(), "아침 관리");
        boolean otherExists = groupRoutineCategoryRepository
                .existsReservedName(target.getId(), "다른 그룹 관리");

        // then
        assertThat(fixedExists).isTrue();
        assertThat(ownedExists).isTrue();
        assertThat(otherExists).isFalse();
    }

    @Test
    @DisplayName("같은 그룹의 비활성 카테고리 이름도 재사용하지 못하도록 예약한다")
    void existsReservedName_InactiveName_ReturnsTrue() {
        // given
        Group target = group("CAT3001");
        category(target, "비활성 관리", false);
        em.flush();
        em.clear();

        // when
        boolean result = groupRoutineCategoryRepository
                .existsReservedName(target.getId(), "비활성 관리");

        // then
        assertThat(result).isTrue();
    }

    private Group group(String inviteCode) {
        Group group = Group.builder().name("카테고리 그룹").inviteCode(inviteCode).build();
        em.persist(group);
        return group;
    }

    private GroupRoutineCategory category(Group group, String name, boolean active) {
        GroupRoutineCategory category = GroupRoutineCategory.builder()
                .group(group)
                .name(name)
                .active(active)
                .build();
        em.persist(category);
        return category;
    }
}
