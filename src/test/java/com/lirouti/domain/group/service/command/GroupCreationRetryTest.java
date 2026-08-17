package com.lirouti.domain.group.service.command;

import com.lirouti.domain.group.dto.request.GroupReqDTO;
import com.lirouti.domain.group.dto.response.GroupResDTO;
import com.lirouti.domain.group.exception.GroupException;
import com.lirouti.domain.group.exception.code.error.GroupErrorCode;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("그룹 통합 생성 초대코드 재시도 테스트")
class GroupCreationRetryTest {
    private static final Long MEMBER_ID = 1L;

    @Mock
    private GroupCreationAttemptService groupCreationAttemptService;
    @Mock
    private GroupInviteCodeUniqueViolationDetector uniqueViolationDetector;
    @Mock
    private Validator validator;
    @InjectMocks
    private GroupCommandService groupCommandService;

    @Test
    @DisplayName("첫 시도의 초대코드 unique 충돌 후 두 번째 전체 생성이 성공한다")
    void createGroup_InviteCodeConflictThenSuccess_RetriesEntireCreation() {
        GroupReqDTO.CreateGroup request = request();
        DataIntegrityViolationException conflict =
                new DataIntegrityViolationException("invite code conflict");
        GroupResDTO.CreateResult expected = result();
        when(validator.validate(request)).thenReturn(Set.of());
        when(groupCreationAttemptService.createOnce(MEMBER_ID, request))
                .thenThrow(conflict)
                .thenReturn(expected);
        when(uniqueViolationDetector.isInviteCodeUniqueViolation(conflict)).thenReturn(true);

        GroupResDTO.CreateResult actual = groupCommandService.createGroup(MEMBER_ID, request);

        assertThat(actual).isSameAs(expected);
        verify(groupCreationAttemptService, times(2)).createOnce(MEMBER_ID, request);
    }

    @Test
    @DisplayName("초대코드 unique 충돌이 최대 횟수까지 계속되면 지정 예외를 반환한다")
    void createGroup_InviteCodeConflictUntilLimit_ThrowsInviteCodeIssueFailed() {
        GroupReqDTO.CreateGroup request = request();
        DataIntegrityViolationException conflict =
                new DataIntegrityViolationException("invite code conflict");
        when(validator.validate(request)).thenReturn(Set.of());
        when(groupCreationAttemptService.createOnce(MEMBER_ID, request)).thenThrow(conflict);
        when(uniqueViolationDetector.isInviteCodeUniqueViolation(conflict)).thenReturn(true);

        assertThatThrownBy(() -> groupCommandService.createGroup(MEMBER_ID, request))
                .isInstanceOf(GroupException.class)
                .extracting("code")
                .isEqualTo(GroupErrorCode.INVITE_CODE_ISSUE_FAILED);
        verify(groupCreationAttemptService, times(10)).createOnce(MEMBER_ID, request);
    }

    @Test
    @DisplayName("초대코드 unique 충돌이 아닌 DB 오류는 재시도하지 않고 그대로 전파한다")
    void createGroup_NonInviteCodeDatabaseError_PropagatesWithoutRetry() {
        GroupReqDTO.CreateGroup request = request();
        DataIntegrityViolationException databaseError =
                new DataIntegrityViolationException("not-null violation");
        when(validator.validate(request)).thenReturn(Set.of());
        when(groupCreationAttemptService.createOnce(MEMBER_ID, request)).thenThrow(databaseError);
        when(uniqueViolationDetector.isInviteCodeUniqueViolation(databaseError)).thenReturn(false);

        assertThatThrownBy(() -> groupCommandService.createGroup(MEMBER_ID, request))
                .isSameAs(databaseError);
        verify(groupCreationAttemptService).createOnce(MEMBER_ID, request);
    }

    private GroupReqDTO.CreateGroup request() {
        return new GroupReqDTO.CreateGroup(
                "아침 모임",
                List.of(),
                List.of(new GroupReqDTO.CreateGroupRoutine(
                        1L,
                        null,
                        "아침 운동",
                        "함께 운동합니다.",
                        List.of(new GroupReqDTO.RoutineSchedule(
                                DayOfWeek.MONDAY,
                                LocalTime.of(7, 0),
                                LocalTime.of(8, 0)
                        ))
                ))
        );
    }

    private GroupResDTO.CreateResult result() {
        return GroupResDTO.CreateResult.builder()
                .groupId(10L)
                .name("아침 모임")
                .customCategories(List.of())
                .routines(List.of())
                .assignmentCount(0)
                .build();
    }
}
