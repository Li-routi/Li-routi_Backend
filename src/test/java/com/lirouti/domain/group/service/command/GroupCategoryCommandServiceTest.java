package com.lirouti.domain.group.service.command;

import com.lirouti.domain.chat.repository.ChatMessageRepository;
import com.lirouti.domain.chat.repository.ChatReadRepository;
import com.lirouti.domain.group.dto.request.GroupReqDTO;
import com.lirouti.domain.group.dto.response.GroupResDTO;
import com.lirouti.domain.group.entity.Group;
import com.lirouti.domain.group.entity.GroupRoutineCategory;
import com.lirouti.domain.group.exception.GroupException;
import com.lirouti.domain.group.exception.code.error.GroupErrorCode;
import com.lirouti.domain.group.repository.GroupRepository;
import com.lirouti.domain.group.repository.GroupMemberRepository;
import com.lirouti.domain.group.repository.GroupRoutineCategoryRepository;
import com.lirouti.domain.group.repository.GroupRoutineRepository;
import com.lirouti.domain.group.service.GroupValidationService;
import com.lirouti.domain.routine.enums.RoutineCategoryColor;
import com.lirouti.domain.verification.repository.GroupRoutineVerificationReadRepository;
import com.lirouti.global.websocket.WebSocketSessionRegistry;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.context.ApplicationEventPublisher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("GroupCommandService 그룹 카테고리 테스트")
class GroupCategoryCommandServiceTest {
    private static final Long GROUP_ID = 10L;
    private static final Long OWNER_ID = 20L;

    @Mock private GroupValidationService validationService;
    @Mock private GroupRepository groupRepository;
    @Mock private GroupMemberRepository groupMemberRepository;
    @Mock private GroupRoutineCategoryRepository categoryRepository;
    @Mock private GroupRoutineRepository routineRepository;
    @Mock private GroupRoutineAssignmentCommandService assignmentService;
    @Mock private GroupRoutineVerificationReadRepository groupRoutineVerificationReadRepository;
    @Mock private ChatReadRepository chatReadRepository;
    @Mock private ChatMessageRepository chatMessageRepository;
    @Mock private GroupCreationAttemptService creationAttemptService;
    @Mock private GroupInviteCodeUniqueViolationDetector uniqueViolationDetector;
    @Mock private Validator validator;
    @Mock private Group group;
    @Mock private WebSocketSessionRegistry webSocketSessionRegistry;
    @Mock private ApplicationEventPublisher eventPublisher;

    private GroupCommandService commandService;

    @BeforeEach
    void setUp() {
        commandService = new GroupCommandService(
                validationService, groupRepository, categoryRepository, routineRepository,
                groupRoutineVerificationReadRepository, chatReadRepository, chatMessageRepository,
                assignmentService, creationAttemptService,
                uniqueViolationDetector, validator,
                webSocketSessionRegistry, groupMemberRepository, eventPublisher
        );
        when(validationService.lockActiveGroupForUpdate(GROUP_ID)).thenReturn(group);
    }

    @Test
    @DisplayName("ACTIVE OWNER가 trim된 이름과 선택 색상으로 카테고리를 생성한다")
    void createCategory_ActiveOwner_CreatesNormalizedCategory() {
        // given
        when(categoryRepository.countByGroupIdAndActiveTrue(GROUP_ID)).thenReturn(1L);
        when(categoryRepository.existsReservedName(GROUP_ID, "아침 관리")).thenReturn(false);
        when(categoryRepository.saveAndFlush(any(GroupRoutineCategory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // when
        GroupResDTO.Category result = commandService.createCategory(
                GROUP_ID, OWNER_ID,
                new GroupReqDTO.CreateCategory("  아침 관리  ", RoutineCategoryColor.BLUE)
        );

        // then
        verify(validationService).lockActiveGroupForUpdate(GROUP_ID);
        verify(validationService).validateGroupOwner(GROUP_ID, OWNER_ID);
        verify(categoryRepository).saveAndFlush(argThat(category ->
                category.getGroup() == group
                        && category.getName().equals("아침 관리")
                        && category.getColor() == RoutineCategoryColor.BLUE
                        && Boolean.TRUE.equals(category.getActive())));
        assertThat(result.name()).isEqualTo("아침 관리");
        assertThat(result.color()).isEqualTo(RoutineCategoryColor.BLUE);
        assertThat(result.fixed()).isFalse();
    }

    @Test
    @DisplayName("일반 MEMBER는 카테고리를 생성할 수 없다")
    void createCategory_RegularMember_ThrowsOwnerAccessDenied() {
        // given
        doThrow(new GroupException(GroupErrorCode.GROUP_OWNER_ACCESS_DENIED))
                .when(validationService).validateGroupOwner(GROUP_ID, OWNER_ID);

        // when & then
        assertThatThrownBy(() -> commandService.createCategory(
                GROUP_ID, OWNER_ID, new GroupReqDTO.CreateCategory("관리", null)))
                .isInstanceOf(GroupException.class)
                .extracting("code")
                .isEqualTo(GroupErrorCode.GROUP_OWNER_ACCESS_DENIED);
        verify(categoryRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("서비스 직접 호출에서도 빈 이름, 길이 초과와 줄바꿈을 거절한다")
    void createCategory_InvalidNames_ThrowsInvalidName() {
        for (String name : new String[]{"   ", "12345678901", "두\n줄"}) {
            assertThatThrownBy(() -> commandService.createCategory(
                    GROUP_ID, OWNER_ID, new GroupReqDTO.CreateCategory(name, null)))
                    .isInstanceOf(GroupException.class)
                    .extracting("code")
                    .isEqualTo(GroupErrorCode.INVALID_GROUP_ROUTINE_CATEGORY_NAME);
        }
        verify(categoryRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("활성 사용자 카테고리가 5개면 추가를 거절한다")
    void createCategory_AtLimit_ThrowsLimitExceeded() {
        // given
        when(categoryRepository.countByGroupIdAndActiveTrue(GROUP_ID))
                .thenReturn((long) GroupRoutineCategory.MAX_GROUP_CATEGORY_COUNT);

        // when & then
        assertThatThrownBy(() -> commandService.createCategory(
                GROUP_ID, OWNER_ID, new GroupReqDTO.CreateCategory("여섯번째", null)))
                .isInstanceOf(GroupException.class)
                .extracting("code")
                .isEqualTo(GroupErrorCode.GROUP_ROUTINE_CATEGORY_LIMIT_EXCEEDED);
        verify(categoryRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("기본 또는 같은 그룹이 사용한 카테고리 이름과 중복되면 거절한다")
    void createCategory_DuplicateReservedName_ThrowsDuplicate() {
        // given
        when(categoryRepository.countByGroupIdAndActiveTrue(GROUP_ID)).thenReturn(0L);
        when(categoryRepository.existsReservedName(GROUP_ID, "운동")).thenReturn(true);

        // when & then
        assertThatThrownBy(() -> commandService.createCategory(
                GROUP_ID, OWNER_ID, new GroupReqDTO.CreateCategory("운동", null)))
                .isInstanceOf(GroupException.class)
                .extracting("code")
                .isEqualTo(GroupErrorCode.DUPLICATE_GROUP_ROUTINE_CATEGORY_NAME);
        verify(categoryRepository, never()).saveAndFlush(any());
    }
}
