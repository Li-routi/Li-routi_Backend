package com.lirouti.domain.group.service.command;

import com.lirouti.domain.group.exception.GroupException;
import com.lirouti.domain.group.exception.code.error.GroupErrorCode;
import com.lirouti.domain.group.repository.GroupRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;


import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("GroupInviteCodeGenerator 테스트")
class GroupInviteCodeGeneratorTest {
    @Mock
    private GroupRepository groupRepository;

    @InjectMocks
    private GroupInviteCodeGenerator inviteCodeGenerator;

    @Test
    @DisplayName("중복되지 않는 7자리 영구 초대코드를 생성한다")
    void generate_AvailableCandidate_ReturnsPermanentCode() {
        // given
        when(groupRepository.existsByInviteCode(anyString())).thenReturn(false);

        // when
        String result = inviteCodeGenerator.generate();

        // then
        assertThat(result).hasSize(7).matches("[A-Z0-9]{7}");
    }

    @Test
    @DisplayName("사전 중복 확인에서 충돌하면 다음 후보를 생성한다")
    void generate_PrecheckFindsDuplicate_UsesNextCandidate() {
        // given
        when(groupRepository.existsByInviteCode(anyString())).thenReturn(true, false);

        // when
        inviteCodeGenerator.generate();

        // then
        verify(groupRepository, times(2)).existsByInviteCode(anyString());
    }

    @Test
    @DisplayName("후보가 최대 횟수까지 중복이면 발급 실패를 반환한다")
    void generate_PrecheckAlwaysFindsDuplicate_ThrowsIssueFailed() {
        // given
        when(groupRepository.existsByInviteCode(anyString())).thenReturn(true);

        // when & then
        assertThatThrownBy(inviteCodeGenerator::generate)
                .isInstanceOf(GroupException.class)
                .extracting("code")
                .isEqualTo(GroupErrorCode.INVITE_CODE_ISSUE_FAILED);
        verify(groupRepository, times(10)).existsByInviteCode(anyString());
    }
}
