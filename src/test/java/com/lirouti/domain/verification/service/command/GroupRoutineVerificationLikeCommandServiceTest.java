package com.lirouti.domain.verification.service.command;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.lirouti.domain.group.service.GroupValidationService;
import com.lirouti.domain.verification.entity.GroupRoutineVerification;
import com.lirouti.domain.verification.repository.GroupRoutineVerificationLikeRepository;
import com.lirouti.domain.verification.repository.GroupRoutineVerificationRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("그룹 루틴 인증 좋아요 명령 서비스 테스트")
class GroupRoutineVerificationLikeCommandServiceTest {
    private static final Long MEMBER_ID = 1L;
    private static final Long GROUP_ID = 2L;
    private static final Long VERIFICATION_ID = 3L;

    @Mock private GroupValidationService groupValidationService;
    @Mock private GroupRoutineVerificationRepository verificationRepository;
    @Mock private GroupRoutineVerificationLikeRepository likeRepository;

    @InjectMocks private GroupRoutineVerificationLikeCommandService service;

    @Test
    @DisplayName("좋아요는 그룹 잠금 후 ACTIVE 검증과 인증 소속 검증을 하고 INSERT한다")
    void like_LocksGroupBeforeAuthorizationAndInsert() {
        when(verificationRepository.findByIdAndGroupId(VERIFICATION_ID, GROUP_ID))
                .thenReturn(Optional.of(org.mockito.Mockito.mock(GroupRoutineVerification.class)));
        when(likeRepository.countByVerificationIds(List.of(VERIFICATION_ID)))
                .thenReturn(Map.of(VERIFICATION_ID, 1L));

        service.like(MEMBER_ID, GROUP_ID, VERIFICATION_ID);

        InOrder order = inOrder(groupValidationService, verificationRepository, likeRepository);
        order.verify(groupValidationService).lockActiveGroupForUpdate(GROUP_ID);
        order.verify(groupValidationService).validateActiveGroupMember(GROUP_ID, MEMBER_ID);
        order.verify(verificationRepository).findByIdAndGroupId(VERIFICATION_ID, GROUP_ID);
        order.verify(likeRepository).insertIfAbsent(VERIFICATION_ID, MEMBER_ID);
    }

    @Test
    @DisplayName("좋아요 취소도 그룹 잠금 후 ACTIVE 검증을 마친 상태에서만 DELETE한다")
    void unlike_LocksGroupBeforeAuthorizationAndDelete() {
        when(verificationRepository.findByIdAndGroupId(VERIFICATION_ID, GROUP_ID))
                .thenReturn(Optional.of(org.mockito.Mockito.mock(GroupRoutineVerification.class)));
        when(likeRepository.countByVerificationIds(List.of(VERIFICATION_ID)))
                .thenReturn(Map.of());

        service.unlike(MEMBER_ID, GROUP_ID, VERIFICATION_ID);

        InOrder order = inOrder(groupValidationService, verificationRepository, likeRepository);
        order.verify(groupValidationService).lockActiveGroupForUpdate(GROUP_ID);
        order.verify(groupValidationService).validateActiveGroupMember(GROUP_ID, MEMBER_ID);
        order.verify(verificationRepository).findByIdAndGroupId(VERIFICATION_ID, GROUP_ID);
        order.verify(likeRepository).deleteLike(VERIFICATION_ID, MEMBER_ID);
    }
}
