package com.lirouti.domain.group.controller;

import com.lirouti.domain.group.dto.request.GroupReqDTO;
import com.lirouti.domain.group.dto.response.GroupResDTO;
import com.lirouti.domain.group.exception.code.success.GroupSuccessCode;
import com.lirouti.domain.group.service.command.GroupCommandService;
import com.lirouti.domain.group.service.command.GroupInviteCodeCommandService;
import com.lirouti.domain.group.service.query.GroupInviteCodeQueryService;
import com.lirouti.domain.group.service.query.GroupQueryService;
import com.lirouti.domain.member.enums.Role;
import com.lirouti.global.apiPayload.ApiResponse;
import com.lirouti.global.auth.CustomUserDetails;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("GroupController 단위 테스트")
class GroupControllerUnitTest {
    private static final Long MEMBER_ID = 1L;

    @Mock
    private GroupCommandService groupCommandService;
    @Mock
    private GroupQueryService groupQueryService;
    @Mock
    private GroupInviteCodeCommandService groupInviteCodeCommandService;
    @Mock
    private GroupInviteCodeQueryService groupInviteCodeQueryService;
    @InjectMocks
    private GroupController groupController;

    @Test
    @DisplayName("인증 회원 ID와 요청을 통합 생성 서비스에 전달하고 생성 응답을 반환한다")
    void createGroup_AuthenticatedMember_ReturnsCreateResult() {
        // given
        CustomUserDetails principal = new CustomUserDetails(MEMBER_ID, Role.ROLE_USER);
        GroupReqDTO.CreateGroup request = new GroupReqDTO.CreateGroup(
                "아침 모임", List.of(), List.of()
        );
        GroupResDTO.CreateResult result = GroupResDTO.CreateResult.builder()
                .groupId(10L)
                .name("아침 모임")
                .customCategories(List.of())
                .routines(List.of())
                .assignmentCount(0)
                .build();
        when(groupCommandService.createGroup(MEMBER_ID, request)).thenReturn(result);

        // when
        ApiResponse<GroupResDTO.CreateResult> response =
                groupController.createGroup(principal, request);

        // then
        assertThat(response.getIsSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo(GroupSuccessCode.GROUP_CREATE_SUCCESS.getCode());
        assertThat(response.getResult()).isSameAs(result);
        verify(groupCommandService).createGroup(MEMBER_ID, request);
    }
}
