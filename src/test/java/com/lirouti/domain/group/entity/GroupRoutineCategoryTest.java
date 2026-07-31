package com.lirouti.domain.group.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("GroupRoutineCategory 테스트")
class GroupRoutineCategoryTest {

    @Test
    @DisplayName("소유 그룹이 없으면 모든 그룹이 사용하는 고정 카테고리다")
    void isUsableBy_FixedCategory_ReturnsTrue() {
        // given
        GroupRoutineCategory category = GroupRoutineCategory.builder()
                .name("운동")
                .displayOrder(1)
                .active(true)
                .build();

        // when
        boolean fixed = category.isFixed();
        boolean usable = category.isUsableBy(10L);

        // then
        assertThat(fixed).isTrue();
        assertThat(usable).isTrue();
    }

    @Test
    @DisplayName("그룹 사용자 카테고리는 소유 그룹에서만 사용할 수 있다")
    void isUsableBy_CustomCategory_AllowsOnlyOwnerGroup() {
        // given
        Group group = Group.builder().name("그룹").inviteCode("CAT0001").build();
        ReflectionTestUtils.setField(group, "id", 10L);
        GroupRoutineCategory category = GroupRoutineCategory.builder()
                .group(group)
                .name("아침 관리")
                .active(true)
                .build();

        // when
        boolean ownerUsable = category.isUsableBy(10L);
        boolean otherUsable = category.isUsableBy(20L);

        // then
        assertThat(category.isFixed()).isFalse();
        assertThat(ownerUsable).isTrue();
        assertThat(otherUsable).isFalse();
    }
}
