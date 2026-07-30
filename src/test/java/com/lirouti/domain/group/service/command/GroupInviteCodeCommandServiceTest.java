package com.lirouti.domain.group.service.command;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import com.lirouti.domain.group.dto.response.GroupResDTO;
import com.lirouti.domain.group.entity.Group;
import com.lirouti.domain.group.entity.GroupMember;
import com.lirouti.domain.group.exception.GroupException;
import com.lirouti.domain.group.exception.code.error.GroupErrorCode;
import com.lirouti.domain.group.repository.GroupRepository;
import com.lirouti.domain.group.service.GroupValidationService;

@ExtendWith(MockitoExtension.class)
@DisplayName("GroupInviteCodeCommandService 테스트")
class GroupInviteCodeCommandServiceTest {
    private static final Long GROUP_ID = 10L;
    private static final Long OWNER_ID = 1L;
    private static final LocalDateTime ISSUED_AT = LocalDateTime.of(2026, 7, 29, 10, 0);

    @Mock
    private GroupValidationService groupValidationService;
    @Mock
    private GroupRepository groupRepository;
    @Mock
    private Clock clock;
    @Mock
    private GroupMember ownerMembership;

    @InjectMocks
    private GroupInviteCodeCommandService groupInviteCodeCommandService;
    
    private Group group;

    @BeforeEach
    void setUp() {
        group = Group.builder()
                .name("테스트 그룹")
                .inviteCode("OLD1234")
                .build();

    }

    @Test
    @DisplayName("방장이 초대코드를 발급하면 10분 후 말소 시각을 반환한다")
    void issueInviteCode_Owner_ReturnsCodeAndExpiration() {
        // when
        givenOwnerGroup();
        GroupResDTO.InviteCode result = groupInviteCodeCommandService
                .issueInviteCode(GROUP_ID, OWNER_ID);

        // then
        assertThat(result.inviteCode())
                .hasSize(7)
                .matches("[A-Z0-9]{7}");
        assertThat(result.expiresAt()).isEqualTo(ISSUED_AT.plusMinutes(10));
        assertThat(result.inviteCode()).isNotEqualTo("OLD1234");
        verify(groupRepository).saveAndFlush(group);
    }

    @Test
    @DisplayName("초대코드를 재발급하면 기존 코드가 교체되고 말소 시각이 갱신된다")
    void issueInviteCode_Reissue_ReplacesCodeAndRefreshesExpiration() {
        // given
        givenOwnerGroup();
        when(clock.instant()).thenReturn(
                Instant.parse("2026-07-29T01:00:00Z"),
                Instant.parse("2026-07-29T03:00:00Z"));
        GroupResDTO.InviteCode firstResult = groupInviteCodeCommandService
                .issueInviteCode(GROUP_ID, OWNER_ID);

        // when
        GroupResDTO.InviteCode reissuedResult = groupInviteCodeCommandService
                .issueInviteCode(GROUP_ID, OWNER_ID);

        // then
        LocalDateTime reissuedAt = ISSUED_AT.plusHours(2);
        assertAll(
                () -> assertThat(reissuedResult.inviteCode())
                        .isNotEqualTo(firstResult.inviteCode()),
                () -> assertThat(reissuedResult.expiresAt())
                        .isEqualTo(reissuedAt.plusMinutes(10)),
                () -> assertThat(group.getInviteCode())
                        .isEqualTo(reissuedResult.inviteCode()),
                () -> assertThat(group.getInviteCodeExpiresAt())
                        .isEqualTo(reissuedAt.plusMinutes(10))
        );
        verify(groupRepository, times(2)).saveAndFlush(group);
    }

    @Test
    @DisplayName("방장 권한 검증에 실패하면 초대코드를 저장하지 않는다")
    void issueInviteCode_NotOwner_PropagatesException() {
        // given
        GroupException exception = new GroupException(GroupErrorCode.GROUP_OWNER_ACCESS_DENIED);
        when(groupValidationService.validateGroupOwner(GROUP_ID, OWNER_ID))
                .thenThrow(exception);

        // when & then
        assertThatThrownBy(() -> groupInviteCodeCommandService
                .issueInviteCode(GROUP_ID, OWNER_ID))
                .isSameAs(exception);
        verify(groupRepository, never()).existsByInviteCode(anyString());
        verify(groupRepository, never()).saveAndFlush(group);
    }

    @Test
    @DisplayName("초대코드 저장 중 무결성 오류가 발생하면 발급 실패 오류를 반환한다")
    void issueInviteCode_SaveFails_ThrowsIssueFailed() {
        // given
        givenOwnerGroup();
        when(groupRepository.saveAndFlush(group))
                .thenThrow(new DataIntegrityViolationException("duplicate invite code"));

        // when & then
        assertThatThrownBy(() -> groupInviteCodeCommandService
                .issueInviteCode(GROUP_ID, OWNER_ID))
                .isInstanceOf(GroupException.class)
                .extracting("code")
                .isEqualTo(GroupErrorCode.INVITE_CODE_ISSUE_FAILED);
    }

    private void givenOwnerGroup() {
        when(clock.instant()).thenReturn(Instant.parse("2026-07-29T01:00:00Z"));
        when(clock.getZone()).thenReturn(ZoneId.of("Asia/Seoul"));
        when(groupValidationService.validateGroupOwner(GROUP_ID, OWNER_ID))
                .thenReturn(ownerMembership);
        when(ownerMembership.getGroup()).thenReturn(group);
        when(groupRepository.existsByInviteCode(anyString())).thenReturn(false);
    }
}
