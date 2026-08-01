package com.lirouti.domain.group.service.command;

import com.lirouti.domain.group.dto.response.GroupResDTO;
import com.lirouti.domain.group.entity.Group;
import com.lirouti.domain.group.repository.GroupRepository;
import com.lirouti.domain.group.service.GroupValidationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("GroupInviteCodeIssueAttemptService 테스트")
class GroupInviteCodeIssueAttemptServiceTest {
    private static final Long GROUP_ID = 10L;
    private static final Long OWNER_ID = 1L;
    private static final LocalDateTime ISSUED_AT = LocalDateTime.of(2026, 7, 29, 10, 0);

    @Mock
    private GroupValidationService groupValidationService;
    @Mock
    private GroupRepository groupRepository;
    @Mock
    private GroupInviteCodeGenerator inviteCodeGenerator;
    @InjectMocks
    private GroupInviteCodeIssueAttemptService issueAttemptService;

    private Group group;

    @BeforeEach
    void setUp() {
        group = Group.builder()
                .name("테스트 그룹")
                .inviteCode("OLD1234")
                .build();
    }

    @Test
    @DisplayName("방장이 발급을 시도하면 새 코드와 10분 후 말소 시각을 저장한다")
    void issueOnce_Owner_SavesCodeAndExpiration() {
        // given
        givenOwnerGroup();

        // when
        GroupResDTO.InviteCode result = issueAttemptService.issueOnce(GROUP_ID, OWNER_ID);

        // then
        assertThat(result.inviteCode()).isEqualTo("NEW1234");
        assertThat(result.expiresAt()).isEqualTo(ISSUED_AT.plusMinutes(10));
        assertThat(group.getInviteCode()).isEqualTo(result.inviteCode());
        assertThat(group.getInviteCodeExpiresAt()).isEqualTo(result.expiresAt());
        verify(groupValidationService).lockActiveGroupForUpdate(GROUP_ID);
        verify(groupValidationService).validateGroupOwner(GROUP_ID, OWNER_ID);
        verify(groupRepository).saveAndFlush(group);
    }

    @Test
    @DisplayName("DB 무결성 오류는 호출자에게 전파한다")
    void issueOnce_SaveFails_PropagatesDataIntegrityViolation() {
        // given
        givenOwnerGroup();
        DataIntegrityViolationException exception =
                new DataIntegrityViolationException("duplicate invite code");
        when(groupRepository.saveAndFlush(group)).thenThrow(exception);

        // when & then
        assertThatThrownBy(() -> issueAttemptService.issueOnce(GROUP_ID, OWNER_ID))
                .isSameAs(exception);
    }

    @Test
    @DisplayName("방장 검증에 실패하면 저장을 시도하지 않는다")
    void issueOnce_NotOwner_PropagatesExceptionWithoutSave() {
        // given
        RuntimeException exception = new IllegalStateException("not owner");
        when(groupValidationService.lockActiveGroupForUpdate(GROUP_ID)).thenReturn(group);
        when(groupValidationService.validateGroupOwner(GROUP_ID, OWNER_ID))
                .thenThrow(exception);

        // when & then
        assertThatThrownBy(() -> issueAttemptService.issueOnce(GROUP_ID, OWNER_ID))
                .isSameAs(exception);
        verify(inviteCodeGenerator, never()).generate();
        verify(groupRepository, never()).saveAndFlush(group);
    }

    private void givenOwnerGroup() {
        when(groupValidationService.lockActiveGroupForUpdate(GROUP_ID)).thenReturn(group);
        when(inviteCodeGenerator.generate()).thenReturn(
                new GroupInviteCodeGenerator.GeneratedInviteCode(
                        "NEW1234",
                        ISSUED_AT.plusMinutes(10)
                )
        );
    }
}
