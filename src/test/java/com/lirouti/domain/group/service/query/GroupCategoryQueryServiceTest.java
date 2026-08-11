package com.lirouti.domain.group.service.query;

import com.lirouti.domain.group.dto.response.GroupResDTO;
import com.lirouti.domain.group.entity.Group;
import com.lirouti.domain.group.entity.GroupRoutineCategory;
import com.lirouti.domain.group.exception.GroupException;
import com.lirouti.domain.group.exception.code.error.GroupErrorCode;
import com.lirouti.domain.group.repository.GroupRoutineAssignmentRepository;
import com.lirouti.domain.group.repository.GroupRoutineCategoryRepository;
import com.lirouti.domain.group.repository.GroupDetailQueryRepository;
import com.lirouti.domain.group.repository.GroupListQueryRepository;
import com.lirouti.domain.group.service.GroupValidationService;
import com.lirouti.domain.member.service.query.MemberQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("GroupQueryService 그룹 카테고리 테스트")
class GroupCategoryQueryServiceTest {
    private static final Long GROUP_ID = 10L;
    private static final Long MEMBER_ID = 20L;

    @Mock private GroupRoutineAssignmentRepository assignmentRepository;
    @Mock private GroupDetailQueryRepository groupDetailQueryRepository;
    @Mock private GroupListQueryRepository groupListQueryRepository;
    @Mock private GroupRoutineCategoryRepository categoryRepository;
    @Mock private GroupValidationService validationService;
    @Mock private MemberQueryService memberQueryService;

    private GroupQueryService queryService;

    @BeforeEach
    void setUp() {
        queryService = new GroupQueryService(
                assignmentRepository,
                groupDetailQueryRepository,
                groupListQueryRepository,
                categoryRepository,
                validationService,
                memberQueryService,
                Clock.systemUTC()
        );
    }

    @Test
    @DisplayName("ACTIVE 구성원은 기존 정렬 결과와 남은 추가 가능 개수를 조회한다")
    void getCategories_ActiveMember_ReturnsCategories() {
        // given
        GroupRoutineCategory fixed = GroupRoutineCategory.builder()
                .name("운동").displayOrder(1).active(true).build();
        GroupRoutineCategory custom = GroupRoutineCategory.builder()
                .group(Group.builder().name("그룹").inviteCode("QUERY01").build())
                .name("아침 관리").active(true).build();
        when(categoryRepository.findUsableByGroupId(GROUP_ID))
                .thenReturn(List.of(fixed, custom));
        when(categoryRepository.countByGroupIdAndActiveTrue(GROUP_ID)).thenReturn(1L);

        // when
        GroupResDTO.CategoryList result = queryService.getCategories(GROUP_ID, MEMBER_ID);

        // then
        verify(validationService).validateActiveGroupMember(GROUP_ID, MEMBER_ID);
        assertThat(result.categories()).extracting(GroupResDTO.Category::name)
                .containsExactly("운동", "아침 관리");
        assertThat(result.categories()).extracting(GroupResDTO.Category::fixed)
                .containsExactly(true, false);
        assertThat(result.addableCount()).isEqualTo(4);
    }

    @Test
    @DisplayName("비구성원은 카테고리 Repository 조회 전에 거부한다")
    void getCategories_NonMember_ThrowsAccessDenied() {
        // given
        when(validationService.validateActiveGroupMember(GROUP_ID, MEMBER_ID))
                .thenThrow(new GroupException(GroupErrorCode.GROUP_MEMBER_ACCESS_DENIED));

        // when & then
        assertThatThrownBy(() -> queryService.getCategories(GROUP_ID, MEMBER_ID))
                .isInstanceOf(GroupException.class)
                .extracting("code")
                .isEqualTo(GroupErrorCode.GROUP_MEMBER_ACCESS_DENIED);
        verifyNoInteractions(categoryRepository);
    }
}
