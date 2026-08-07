package com.lirouti.domain.verification.service.command;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.lirouti.domain.group.service.GroupValidationService;
import com.lirouti.domain.group.service.command.GroupMemberActivityCommandService;
import com.lirouti.domain.group.entity.GroupMember;
import com.lirouti.domain.group.entity.GroupRoutineAssignment;
import com.lirouti.domain.group.enums.GroupMemberStatus;
import com.lirouti.domain.group.repository.GroupMemberRepository;
import com.lirouti.domain.verification.entity.GroupRoutineVerification;
import com.lirouti.domain.verification.entity.GroupRoutineVerificationLike;
import com.lirouti.domain.verification.repository.GroupRoutineVerificationLikeRepository;
import com.lirouti.domain.verification.repository.GroupRoutineVerificationRepository;
import com.lirouti.domain.member.entity.Member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
@DisplayName("그룹 루틴 인증 좋아요 명령 서비스 테스트")
class GroupRoutineVerificationLikeCommandServiceTest {
    private static final Long MEMBER_ID = 1L;
    private static final Long GROUP_ID = 2L;
    private static final Long VERIFICATION_ID = 3L;

    @Mock private GroupValidationService groupValidationService;
    @Mock private GroupMemberActivityCommandService groupMemberActivityCommandService;
    @Mock private GroupMemberRepository groupMemberRepository;
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
        GroupRoutineVerificationLike existingLike = org.mockito.Mockito
                .mock(GroupRoutineVerificationLike.class);
        GroupRoutineVerification verification = org.mockito.Mockito.mock(GroupRoutineVerification.class);
        GroupRoutineAssignment assignment = org.mockito.Mockito.mock(GroupRoutineAssignment.class);
        Member author = org.mockito.Mockito.mock(Member.class);
        when(verification.getAssignment()).thenReturn(assignment);
        when(assignment.getMember()).thenReturn(author);
        when(assignment.getCreatedAt()).thenReturn(LocalDateTime.now());
        when(verificationRepository.findByIdAndGroupId(VERIFICATION_ID, GROUP_ID))
                .thenReturn(Optional.of(verification));
        when(likeRepository.findByVerificationIdAndMemberIdForUpdate(VERIFICATION_ID, MEMBER_ID))
                .thenReturn(Optional.of(existingLike));
        when(likeRepository.deleteLike(VERIFICATION_ID, MEMBER_ID)).thenReturn(0);
        when(likeRepository.countByVerificationIds(List.of(VERIFICATION_ID)))
                .thenReturn(Map.of());

        service.unlike(MEMBER_ID, GROUP_ID, VERIFICATION_ID);

        InOrder order = inOrder(groupValidationService, verificationRepository, likeRepository);
        order.verify(groupValidationService).lockActiveGroupForUpdate(GROUP_ID);
        order.verify(groupValidationService).validateActiveGroupMember(GROUP_ID, MEMBER_ID);
        order.verify(verificationRepository).findByIdAndGroupId(VERIFICATION_ID, GROUP_ID);
        order.verify(likeRepository).findByVerificationIdAndMemberIdForUpdate(
                VERIFICATION_ID, MEMBER_ID);
        order.verify(likeRepository).deleteLike(VERIFICATION_ID, MEMBER_ID);
    }

    @Test
    @DisplayName("실제 Like INSERT는 ACTIVE 작성자의 현재 가입 회차 누적값만 증가시킨다")
    void like_InsertedForCurrentActiveAuthor_IncreasesTotalLikeCount() {
        GroupMember authorMembership = mock(GroupMember.class);
        GroupRoutineAssignment assignment = mock(GroupRoutineAssignment.class);
        Member author = mock(Member.class);
        GroupRoutineVerification verification = mock(GroupRoutineVerification.class);
        when(assignment.getMember()).thenReturn(author);
        when(author.getId()).thenReturn(9L);
        when(assignment.getCreatedAt()).thenReturn(LocalDateTime.now());
        when(verification.getAssignment()).thenReturn(assignment);
        when(authorMembership.getId()).thenReturn(10L);
        when(authorMembership.getStatus()).thenReturn(GroupMemberStatus.ACTIVE);
        when(authorMembership.getJoinedAt()).thenReturn(LocalDateTime.now().minusSeconds(1));
        when(verificationRepository.findByIdAndGroupId(VERIFICATION_ID, GROUP_ID))
                .thenReturn(Optional.of(verification));
        when(likeRepository.insertIfAbsent(VERIFICATION_ID, MEMBER_ID)).thenReturn(1);
        when(groupMemberActivityCommandService.lockMembership(GROUP_ID, 9L))
                .thenReturn(authorMembership);
        when(groupMemberRepository.incrementTotalLikeCount(10L)).thenReturn(1);
        when(likeRepository.countByVerificationIds(List.of(VERIFICATION_ID)))
                .thenReturn(Map.of(VERIFICATION_ID, 1L));

        service.like(MEMBER_ID, GROUP_ID, VERIFICATION_ID);

        org.mockito.Mockito.verify(groupMemberRepository).incrementTotalLikeCount(10L);
    }

    @Test
    @DisplayName("중복 Like POST는 작성자 누적값을 증가시키지 않는다")
    void like_Duplicate_DoesNotIncreaseTotalLikeCount() {
        GroupRoutineVerification verification = mock(GroupRoutineVerification.class);
        when(verificationRepository.findByIdAndGroupId(VERIFICATION_ID, GROUP_ID))
                .thenReturn(Optional.of(verification));
        when(likeRepository.insertIfAbsent(VERIFICATION_ID, MEMBER_ID)).thenReturn(0);
        when(likeRepository.countByVerificationIds(List.of(VERIFICATION_ID)))
                .thenReturn(Map.of(VERIFICATION_ID, 1L));

        service.like(MEMBER_ID, GROUP_ID, VERIFICATION_ID);

        org.mockito.Mockito.verifyNoInteractions(groupMemberActivityCommandService);
    }
}
