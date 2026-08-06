package com.lirouti.domain.group.service.query;

import com.lirouti.domain.group.dto.response.GroupResDTO;
import com.lirouti.domain.group.entity.Group;
import com.lirouti.domain.group.entity.GroupMember;
import com.lirouti.domain.group.exception.GroupException;
import com.lirouti.domain.group.exception.code.error.GroupErrorCode;
import com.lirouti.domain.group.service.GroupValidationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;


import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("GroupInviteCodeQueryService 테스트")
class GroupInviteCodeQueryServiceTest {
    private static final Long GROUP_ID = 10L;
    private static final Long OWNER_ID = 1L;
    private static final String INVITE_CODE = "ABC1234";

    @Mock
    private GroupValidationService groupValidationService;
    @Mock
    private GroupMember ownerMembership;

    @InjectMocks
    private GroupInviteCodeQueryService groupInviteCodeQueryService;
    private Group group;

    @BeforeEach
    void setUp() {
        group = Group.builder()
                .name("테스트 그룹")
                .inviteCode(INVITE_CODE)
                .build();
    }

    @Test
    @DisplayName("방장은 그룹에 영구 귀속된 초대코드를 조회할 수 있다")
    void getInviteCode_Owner_ReturnsPermanentInviteCode() {
        // given
        givenOwnerGroup();

        // when
        GroupResDTO.InviteCode result = groupInviteCodeQueryService
                .getInviteCode(GROUP_ID, OWNER_ID);

        // then
        assertThat(result.inviteCode()).isEqualTo(INVITE_CODE);
        verify(groupValidationService).validateGroupOwner(GROUP_ID, OWNER_ID);
    }

    @Test
    @DisplayName("방장 권한 검증에 실패하면 초대코드 조회를 중단한다")
    void getInviteCode_NotOwner_PropagatesException() {
        // given
        GroupException exception = new GroupException(GroupErrorCode.GROUP_OWNER_ACCESS_DENIED);
        when(groupValidationService.validateGroupOwner(GROUP_ID, OWNER_ID))
                .thenThrow(exception);

        // when & then
        assertThatThrownBy(() -> groupInviteCodeQueryService
                .getInviteCode(GROUP_ID, OWNER_ID))
                .isSameAs(exception);
    }

    private void givenOwnerGroup() {
        when(groupValidationService.validateGroupOwner(GROUP_ID, OWNER_ID))
                .thenReturn(ownerMembership);
        when(ownerMembership.getGroup()).thenReturn(group);
    }
}
